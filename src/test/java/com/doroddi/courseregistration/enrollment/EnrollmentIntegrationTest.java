package com.doroddi.courseregistration.enrollment;

import com.doroddi.courseregistration.courseOffering.CourseOfferingRepository;
import com.doroddi.courseregistration.student.StudentRepository;
import com.doroddi.courseregistration.student.auth.JwtTokenService;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.initial-data.enabled=false", "spring.jpa.open-in-view=false",
        "app.enrollment.academic-year=2026", "app.enrollment.term=2",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "com.doroddi.courseregistration.enrollment.EnrollmentIntegrationTest$SqlCapture"
})
@Testcontainers
public class EnrollmentIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6");
    private static final int STUDENT = 202010100;
    private static final Instant NOW = Instant.parse("2026-09-25T00:00:00Z");
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtTokenService tokens;
    @Autowired StudentRepository students;
    @Autowired CourseOfferingRepository offerings;
    @Autowired EnrollmentService service;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean Clock clock;
    private final HttpClient client = HttpClient.newHttpClient();
    private final AtomicInteger sequence = new AtomicInteger(100);
    private long department;
    private String token;

    @BeforeEach
    void fixtures() {
        when(clock.instant()).thenReturn(NOW);
        for (String table : List.of("enrollment", "teaching_assignment", "class_meeting", "course_offering",
                "subject", "professor", "student", "department")) jdbc.update("delete from " + table);
        department = jdbc.queryForObject("insert into department(name,department_code) values ('학과',10) returning department_id", Long.class);
        for (int i=0;i<12;i++) jdbc.update("""
                insert into student(student_number,name,grade,department_id,password_hash)
                values (?,'학생',1,?,'test-only')
                """, STUDENT+i, department);
        token = tokens.issue(STUDENT);
        SqlCapture.rows.clear();
    }

    @Test
    void successUsesJwtStudentAndCommitsExactlyOneEnrollment() throws Exception {
        long id = course(3, 30, 1);
        var response = enroll(id, token);
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(201);
        JsonNode body = mapper.readTree(response.body());
        assertThat(body.size()).isEqualTo(1);
        assertThat(body.path("courseOfferingId").asLong()).isEqualTo(id);
        assertThat(jdbc.queryForObject("select student_number from enrollment where offering_id=?", Integer.class, id)).isEqualTo(STUDENT);
        assertThat(count(id)).isEqualTo(1);
        var sql = SqlCapture.rows.stream().map(Observation::sql).toList();
        int studentLock = index(sql, "from student", "for no key update");
        int registered = index(sql, "from enrollment", "subject_code");
        int offeringLock = index(sql, "from course_offering", "for no key update");
        int capacityCount = java.util.stream.IntStream.range(0, sql.size()).filter(i -> sql.get(i).contains("select count") && sql.get(i).contains("from enrollment") && !sql.get(i).contains("class_meeting")).findFirst().orElse(-1);
        int insert = index(sql, "insert into enrollment");
        assertThat(studentLock).isGreaterThanOrEqualTo(0);
        assertThat(registered).isGreaterThan(studentLock);
        assertThat(offeringLock).isGreaterThan(registered);
        assertThat(capacityCount).isGreaterThan(offeringLock);
        assertThat(insert).isGreaterThan(capacityCount);
        for (Observation row : SqlCapture.rows) {
            assertThat(row.readOnly()).isFalse();
            assertThat(row.isolation()).isEqualTo(Connection.TRANSACTION_READ_COMMITTED);
        }
    }

    @ParameterizedTest
    @ValueSource(strings={"", "null", "[]", "{}", "{", "{\"courseOfferingId\":null}",
            "{\"courseOfferingId\":0}", "{\"courseOfferingId\":-1}", "{\"courseOfferingId\":1.0}",
            "{\"courseOfferingId\":1e1}", "{\"courseOfferingId\":\"1\"}", "{\"courseOfferingId\":true}",
            "{\"courseOfferingId\":9223372036854775808}", "{\"courseOfferingId\":1,\"courseOfferingId\":1}",
            "{\"courseOfferingId\":1,\"studentNumber\":202010101}", "{\"courseOfferingId\":1} {}"})
    void rejectsInvalidBodyBeforeAnyDatabaseAccess(String body) throws Exception {
        error(send(body,token),400,"INVALID_PARAMETER", "잘못된 요청입니다.");
        assertThat(SqlCapture.rows).isEmpty();
    }

    @Test
    void longUpperBoundIsValidInputButMissingOfferingIs404() throws Exception {
        error(enroll(Long.MAX_VALUE,token),404,"COURSE_OFFERING_NOT_FOUND", "존재하지 않는 강좌입니다.");
        assertThat(SqlCapture.rows).hasSize(1);
    }

    @ParameterizedTest @ValueSource(strings={"missing","expired","forged"})
    void authenticationPrecedesInvalidJson(String kind) throws Exception {
        String bearer=null, code="TOKEN_REQUIRED";
        if (kind.equals("expired")) {
            when(clock.instant()).thenReturn(NOW.minusSeconds(1800));
            bearer=tokens.issue(STUDENT);
            when(clock.instant()).thenReturn(NOW);
            code="TOKEN_EXPIRED";
        } else if (kind.equals("forged")) {
            int p=token.lastIndexOf('.')+1;
            bearer=token.substring(0,p)+(token.charAt(p)=='A'?'B':'A')+token.substring(p+1);
            code="INVALID_TOKEN";
        }
        error(send("{",bearer),401,code,null);
        assertThat(SqlCapture.rows).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings={"year","term"})
    void targetTermWinsOverAlreadyEnrolled(String kind) throws Exception {
        long id=course(3,30,1);
        jdbc.update(kind.equals("year") ? "update course_offering set academic_year=2025 where offering_id=?"
                : "update course_offering set term=1 where offering_id=?",id);
        seed(STUDENT,id);
        error(enroll(id,token),409,"INVALID_ENROLLMENT_TERM","신청 대상 학기가 아닙니다.");
        assertThat(SqlCapture.rows.stream().map(Observation::sql)).noneMatch(s->s.contains("for no key update"));
        assertThat(count(id)).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings={"sameOffering","sameSubject","credits","schedule","capacity"})
    void reportsFirstViolatedConditionAndDoesNotSave(String kind) throws Exception {
        long id=course(3,1,1);
        seed(STUDENT+1,id); // 모든 사례에 정원 소진을 겹친다.
        if (kind.equals("sameOffering")) {
            seed(STUDENT,id);
            seed(STUDENT,course(6,30,2)); seed(STUDENT,course(6,30,3)); seed(STUDENT,course(3,30,4));
        } else if (kind.equals("sameSubject")) {
            long other=course(6,30,1);
            jdbc.update("update course_offering set subject_code=(select subject_code from course_offering where offering_id=?) where offering_id=?",id,other);
            seed(STUDENT,other); seed(STUDENT,course(6,30,2)); seed(STUDENT,course(6,30,3));
        } else if (kind.equals("credits")) {
            seed(STUDENT,course(6,30,1)); seed(STUDENT,course(6,30,2)); seed(STUDENT,course(6,30,3));
        } else if (kind.equals("schedule")) seed(STUDENT,course(3,30,1));
        String expected=switch(kind) {
            case "sameOffering" -> "ALREADY_ENROLLED";
            case "sameSubject" -> "SUBJECT_ALREADY_ENROLLED";
            case "credits" -> "CREDIT_LIMIT_EXCEEDED";
            case "schedule" -> "SCHEDULE_CONFLICT";
            default -> "COURSE_FULL";
        };
        long before=total();
        error(enroll(id,token),409,expected,null);
        assertThat(total()).isEqualTo(before);
        if (!kind.equals("capacity")) assertThat(SqlCapture.rows.stream().map(Observation::sql))
                .noneMatch(s->s.contains("from course_offering") && s.contains("for no key update"));
    }

    @Test
    void permitsExactlyEighteenCreditsAndIgnoresOtherTerms() throws Exception {
        seed(STUDENT,course(6,30,1)); seed(STUDENT,course(6,30,2)); seed(STUDENT,course(3,30,3));
        long target=course(3,30,4), old=course(6,30,4);
        jdbc.update("update course_offering set academic_year=2025, subject_code=(select subject_code from course_offering where offering_id=?) where offering_id=?",target,old);
        seed(STUDENT,old);
        assertThat(enroll(target,token).statusCode()).isEqualTo(201);
        assertThat(credits()).isEqualTo(18);
    }

    @ParameterizedTest @ValueSource(strings={"before","after","otherDay"})
    void adjacentTimesAndDifferentDaysAreAllowed(String kind) throws Exception {
        long existing=course(3,30,1), target=course(3,30,1);
        seed(STUDENT,existing);
        if(kind.equals("before")) jdbc.update("update class_meeting set starts_at='08:00',ends_at='09:00' where offering_id=?",target);
        if(kind.equals("after")) jdbc.update("update class_meeting set starts_at='10:00',ends_at='11:00' where offering_id=?",target);
        if(kind.equals("otherDay")) jdbc.update("update class_meeting set day_of_week=2 where offering_id=?",target);
        assertThat(enroll(target,token).statusCode()).isEqualTo(201);
    }

    @Test
    void anyOverlappingMeetingRejectsEvenWhenFirstMeetingDoesNotOverlap() throws Exception {
        seed(STUDENT,course(3,30,1));
        long target=course(3,30,2);
        jdbc.update("insert into class_meeting(offering_id,day_of_week,starts_at,ends_at) values (?,1,'09:30','10:30')",target);
        error(enroll(target,token),409,"SCHEDULE_CONFLICT",null);
    }

    @ParameterizedTest @ValueSource(ints={10,100})
    void studentsCompeteForLastSeatWithoutExceedingCapacity(int competitors) throws Exception {
        for(int i=12;i<competitors;i++) jdbc.update("insert into student(student_number,name,grade,department_id,password_hash) values (?,'학생',1,?,'test-only')",STUDENT+i,department);
        long id=course(3,1,1);
        List<HttpResponse<String>> results=race(java.util.stream.IntStream.range(0,competitors)
                .mapToObj(i->new Attempt(STUDENT+i,id)).toList());
        assertThat(results.stream().filter(r->r.statusCode()==201).count()).isEqualTo(1);
        assertThat(results.stream().filter(r->r.statusCode()==409).count()).isEqualTo(competitors-1);
        for(var response:results) if(response.statusCode()==409) error(response,409,"COURSE_FULL",null);
        assertThat(count(id)).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings={"sameOffering","sameSubject","credits","schedule","independent"})
    void serializesSameStudentRequests(String kind) throws Exception {
        long a=course(3,30,4), b=course(3,30,5);
        if(kind.equals("sameOffering")) b=a;
        if(kind.equals("sameSubject")) jdbc.update("update course_offering set subject_code=(select subject_code from course_offering where offering_id=?) where offering_id=?",a,b);
        if(kind.equals("schedule")) jdbc.update("update class_meeting set day_of_week=4 where offering_id=?",b);
        if(kind.equals("credits")) {
            seed(STUDENT,course(6,30,1)); seed(STUDENT,course(6,30,2)); seed(STUDENT,course(3,30,3));
        }
        long before=total();
        var results=race(List.of(new Attempt(STUDENT,a),new Attempt(STUDENT,b)));
        int successes=kind.equals("independent")?2:1;
        assertThat(results.stream().filter(r->r.statusCode()==201).count()).isEqualTo(successes);
        String code=switch(kind) {
            case "sameOffering" -> "ALREADY_ENROLLED"; case "sameSubject" -> "SUBJECT_ALREADY_ENROLLED";
            case "credits" -> "CREDIT_LIMIT_EXCEEDED"; default -> "SCHEDULE_CONFLICT";
        };
        for(var response:results) if(response.statusCode()!=201) error(response,409,code,null);
        assertThat(total()).isEqualTo(before+successes);
        assertThat(credits()).isLessThanOrEqualTo(18);
    }

    @Test
    void studentWaitReadsPrecedingCommitBeforeCheckingCredits() throws Exception {
        seed(STUDENT,course(6,30,1)); seed(STUDENT,course(6,30,2)); seed(STUDENT,course(3,30,3));
        long prior=course(3,30,4), target=course(3,30,5);
        try(Connection holder=connection()) {
            lock(holder,"student","student_number",STUDENT);
            var pending=async(target,token);
            awaitWaiter("student");
            insert(holder,STUDENT,prior);
            holder.commit();
            error(pending.get(15,TimeUnit.SECONDS),409,"CREDIT_LIMIT_EXCEEDED",null);
        }
        assertThat(credits()).isEqualTo(18);
        assertThat(count(target)).isZero();
    }

    @Test
    void offeringWaitCountsPrecedingCommittedEnrollment() throws Exception {
        long target=course(3,1,1);
        try(Connection holder=connection()) {
            lock(holder,"course_offering","offering_id",target);
            var pending=async(target,token);
            awaitWaiter("course_offering");
            insert(holder,STUDENT+1,target);
            holder.commit();
            error(pending.get(15,TimeUnit.SECONDS),409,"COURSE_FULL",null);
        }
        assertThat(count(target)).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings={"student","course_offering"})
    void lockWaitTimesOutAndRollsBackThenNextRequestCanSucceed(String table) throws Exception {
        long target=course(3,30,1);
        try(Connection holder=connection()) {
            lock(holder,table,table.equals("student")?"student_number":"offering_id",table.equals("student")?STUDENT:target);
            long start=System.nanoTime();
            error(enroll(target,token),503,"ENROLLMENT_TEMPORARILY_UNAVAILABLE",
                    "일시적으로 요청을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요.");
            assertThat(Duration.ofNanos(System.nanoTime()-start)).isGreaterThanOrEqualTo(Duration.ofMillis(2500));
            assertThat(count(target)).isZero();
            holder.rollback();
        }
        assertThat(enroll(target,token).statusCode()).isEqualTo(201);
    }

    @Test
    void jpaStudentAndOfferingLocksAllowForeignKeyKeyShareLocks() throws Exception {
        long target=course(3,30,1);
        new TransactionTemplate(transactionManager).executeWithoutResult(status->{
            students.findForEnrollment(STUDENT).orElseThrow();
            offerings.findForEnrollment(target).orElseThrow();
            try(Connection other=connection(); Statement statement=other.createStatement()) {
                statement.execute("set local lock_timeout='250ms'");
                statement.executeQuery("select student_number from student where student_number="+STUDENT+" for key share").close();
                statement.executeQuery("select offering_id from course_offering where offering_id="+target+" for key share").close();
                other.rollback();
            } catch(SQLException ex) { throw new RuntimeException(ex); }
        });
    }

    @Test
    void insertFailureRollsBackAndReleasesBothLocks() throws Exception {
        long target=course(3,30,1);
        jdbc.execute("""
                create function test_fail_insert() returns trigger language plpgsql as $$
                begin raise exception 'test insert failure'; return new; end $$
                """);
        jdbc.execute("create trigger test_fail after insert on enrollment for each row execute function test_fail_insert()");
        try {
            assertThat(enroll(target,token).statusCode()).isEqualTo(500);
            assertThat(count(target)).isZero();
        } finally {
            jdbc.execute("drop trigger test_fail on enrollment");
            jdbc.execute("drop function test_fail_insert()");
        }
        assertThat(enroll(target,token).statusCode()).isEqualTo(201);
    }

    @Test
    void lockTimeoutSettingIsTransactionLocal() {
        long target=course(3,30,1);
        TransactionTemplate transaction=new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(Connection.TRANSACTION_READ_COMMITTED);
        transaction.executeWithoutResult(status->{
            service.enroll(STUDENT,target);
            assertThat(jdbc.queryForObject("show lock_timeout",String.class)).isEqualTo("3s");
        });
        assertThat(jdbc.queryForObject("show lock_timeout",String.class)).isEqualTo("0");
    }

    @Test
    void deadlockRollsBackAndReturnsTemporaryFailure() throws Exception {
        long target=course(3,30,1);
        try(Connection holder=connection(); var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            try(var statement=holder.createStatement()) {
                statement.execute("set local deadlock_timeout='10s'");
                statement.execute("set local lock_timeout='10s'");
            }
            lock(holder,"course_offering","offering_id",target);
            var pending=async(target,token);
            awaitWaiter("course_offering");
            // 테스트에서만 역순 잠금을 만들어 실제 PostgreSQL 교착 상태를 유도한다.
            var reverse=pool.submit(()->{
                lock(holder,"student","student_number",STUDENT);
                return true;
            });
            error(pending.get(15,TimeUnit.SECONDS),503,"ENROLLMENT_TEMPORARILY_UNAVAILABLE",null);
            assertThat(reverse.get(15,TimeUnit.SECONDS)).isTrue();
            holder.rollback();
        }
        assertThat(count(target)).isZero();
        assertThat(enroll(target,token).statusCode()).isEqualTo(201);
    }
    @Test
    void cancellationDeletesOnlyOwnExactOfferingAndIsIdempotent() throws Exception {
        long target=course(3,30,1), other=course(3,30,2);
        jdbc.update("update course_offering set subject_code=(select subject_code from course_offering where offering_id=?) where offering_id=?",target,other);
        seed(STUDENT,target); seed(STUDENT,other); seed(STUDENT+1,target);
        noContent(cancel(Long.toString(target),token));
        assertThat(count(target)).isEqualTo(1);
        assertThat(count(other)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select student_number from enrollment where offering_id=?",Integer.class,target)).isEqualTo(STUDENT+1);
        noContent(cancel(Long.toString(target),token));
        assertThat(total()).isEqualTo(2);
        var sql=SqlCapture.rows.stream().map(Observation::sql).toList();
        int studentLock=index(sql,"from student","for no key update");
        int offeringLock=index(sql,"from course_offering","for no key update");
        int deletion=index(sql,"delete from enrollment");
        assertThat(studentLock).isGreaterThanOrEqualTo(0);
        assertThat(offeringLock).isGreaterThan(studentLock);
        assertThat(deletion).isGreaterThan(offeringLock);
        assertThat(sql).noneMatch(q->q.contains("select count") || q.contains("from class_meeting"));
        for(Observation row:SqlCapture.rows) {
            assertThat(row.readOnly()).isFalse();
            assertThat(row.isolation()).isEqualTo(Connection.TRANSACTION_READ_COMMITTED);
        }
    }

    @Test
    void cancellationWithoutAnyEnrollmentSucceedsAndAllowsLeadingZeros() throws Exception {
        long target=course(3,30,1);
        noContent(cancel("000"+target,token));
        assertThat(total()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings={"0","-1","+1","1.0","1e1","abc","9223372036854775808","%201","1%20","%EF%BC%91"})
    void cancellationRejectsInvalidPathBeforeDatabase(String path) throws Exception {
        error(cancel(path,token),400,"INVALID_PARAMETER","잘못된 요청입니다.");
        assertThat(SqlCapture.rows).isEmpty();
    }

    @Test
    void cancellationOfMissingOfferingReturns404() throws Exception {
        error(cancel(Long.toString(Long.MAX_VALUE),token),404,"COURSE_OFFERING_NOT_FOUND","존재하지 않는 강좌입니다.");
        assertThat(SqlCapture.rows).hasSize(1);
    }

    @ParameterizedTest @ValueSource(strings={"year","term"})
    void cancellationOutsideTargetTermPreservesEnrollment(String kind) throws Exception {
        long target=course(3,30,1); seed(STUDENT,target);
        jdbc.update(kind.equals("year") ? "update course_offering set academic_year=2025 where offering_id=?"
                : "update course_offering set term=1 where offering_id=?",target);
        error(cancel(Long.toString(target),token),409,"INVALID_ENROLLMENT_TERM",null);
        assertThat(count(target)).isEqualTo(1);
        assertThat(SqlCapture.rows.stream().map(Observation::sql)).noneMatch(q->q.contains("for no key update"));
    }

    @ParameterizedTest @ValueSource(strings={"missing","expired","forged"})
    void cancellationAuthenticatesBeforePathValidation(String kind) throws Exception {
        String bearer=null, code="TOKEN_REQUIRED";
        if(kind.equals("expired")) {
            when(clock.instant()).thenReturn(NOW.minusSeconds(1800));
            bearer=tokens.issue(STUDENT);
            when(clock.instant()).thenReturn(NOW);
            code="TOKEN_EXPIRED";
        } else if(kind.equals("forged")) {
            int p=token.lastIndexOf('.')+1;
            bearer=token.substring(0,p)+(token.charAt(p)=='A'?'B':'A')+token.substring(p+1);
            code="INVALID_TOKEN";
        }
        error(cancel("invalid",bearer),401,code,null);
        assertThat(SqlCapture.rows).isEmpty();
    }

    @Test
    void cancellationAllowsReenrollmentWithNewIdAndPreservesOtherOrder() throws Exception {
        long a=course(3,30,1), b=course(3,30,2);
        assertThat(enroll(a,token).statusCode()).isEqualTo(201);
        assertThat(enroll(b,token).statusCode()).isEqualTo(201);
        long oldId=jdbc.queryForObject("select enrollment_id from enrollment where student_number=? and offering_id=?",Long.class,STUDENT,a);
        noContent(cancel(Long.toString(a),token));
        assertThat(enroll(a,token).statusCode()).isEqualTo(201);
        long newId=jdbc.queryForObject("select enrollment_id from enrollment where student_number=? and offering_id=?",Long.class,STUDENT,a);
        assertThat(newId).isGreaterThan(oldId);
        assertThat(jdbc.queryForList("select offering_id from enrollment where student_number=? order by enrollment_id",Long.class,STUDENT)).containsExactly(b,a);
    }

    @Test
    void concurrentRepeatedCancellationsBothSucceed() throws Exception {
        long target=course(3,30,1); seed(STUDENT,target); seed(STUDENT+1,target);
        var a=cancelAsync(target,token); var b=cancelAsync(target,token);
        noContent(a.get(15,TimeUnit.SECONDS)); noContent(b.get(15,TimeUnit.SECONDS));
        assertThat(count(target)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select student_number from enrollment where offering_id=?",Integer.class,target)).isEqualTo(STUDENT+1);
    }

    @ParameterizedTest @ValueSource(strings={"enroll","cancel"})
    void enrollmentAndCancellationFollowStudentLockCommitOrder(String first) throws Exception {
        long target=course(3,30,1);
        if(first.equals("cancel")) seed(STUDENT,target);
        CountDownLatch locked=new CountDownLatch(1), release=new CountDownLatch(1);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var holder=pool.submit(()->new TransactionTemplate(transactionManager).execute(status->{
                if(first.equals("enroll")) service.enroll(STUDENT,target); else service.cancel(STUDENT,target);
                locked.countDown();
                try { if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("release timeout"); }
                catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
                return true;
            }));
            try {
                assertThat(locked.await(10,TimeUnit.SECONDS)).isTrue();
                var next=first.equals("enroll")?cancelAsync(target,token):async(target,token);
                awaitWaiter("student");
                release.countDown();
                assertThat(holder.get(15,TimeUnit.SECONDS)).isTrue();
                var response=next.get(15,TimeUnit.SECONDS);
                if(first.equals("enroll")) noContent(response); else assertThat(response.statusCode()).isEqualTo(201);
            } finally { release.countDown(); }
        }
        assertThat(count(target)).isEqualTo(first.equals("enroll")?0:1);
    }

    @Test
    void enrollmentSeesSeatFreedByPrecedingCancellationCommit() throws Exception {
        long target=course(3,1,1); seed(STUDENT,target);
        CountDownLatch locked=new CountDownLatch(1), release=new CountDownLatch(1);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var holder=pool.submit(()->new TransactionTemplate(transactionManager).execute(status->{
                service.cancel(STUDENT,target);
                locked.countDown();
                try { if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("release timeout"); }
                catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
                return true;
            }));
            try {
                assertThat(locked.await(10,TimeUnit.SECONDS)).isTrue();
                var next=async(target,tokens.issue(STUDENT+1));
                awaitWaiter("course_offering");
                release.countDown();
                assertThat(holder.get(15,TimeUnit.SECONDS)).isTrue();
                assertThat(next.get(15,TimeUnit.SECONDS).statusCode()).isEqualTo(201);
            } finally { release.countDown(); }
        }
        assertThat(count(target)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select student_number from enrollment where offering_id=?",Integer.class,target)).isEqualTo(STUDENT+1);
    }

    @ParameterizedTest @ValueSource(strings={"student","course_offering"})
    void cancellationLockTimeoutPreservesEnrollmentAndReleasesLocks(String table) throws Exception {
        long target=course(3,30,1); seed(STUDENT,target);
        try(Connection holder=connection()) {
            lock(holder,table,table.equals("student")?"student_number":"offering_id",table.equals("student")?STUDENT:target);
            error(cancel(Long.toString(target),token),503,"ENROLLMENT_TEMPORARILY_UNAVAILABLE",null);
            assertThat(count(target)).isEqualTo(1);
            holder.rollback();
        }
        noContent(cancel(Long.toString(target),token));
        assertThat(count(target)).isZero();
    }

    @Test
    void cancellationDeadlockRollsBackAndPreservesEnrollment() throws Exception {
        long target=course(3,30,1); seed(STUDENT,target);
        try(Connection holder=connection(); var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            try(var statement=holder.createStatement()) {
                statement.execute("set local deadlock_timeout='10s'");
                statement.execute("set local lock_timeout='10s'");
            }
            lock(holder,"course_offering","offering_id",target);
            var pending=cancelAsync(target,token);
            awaitWaiter("course_offering");
            var reverse=pool.submit(()->{lock(holder,"student","student_number",STUDENT); return true;});
            error(pending.get(15,TimeUnit.SECONDS),503,"ENROLLMENT_TEMPORARILY_UNAVAILABLE",null);
            assertThat(reverse.get(15,TimeUnit.SECONDS)).isTrue();
            holder.rollback();
        }
        assertThat(count(target)).isEqualTo(1);
        noContent(cancel(Long.toString(target),token));
    }

    @Test
    void cancellationDeleteFailureRollsBackAndReleasesLocks() throws Exception {
        long target=course(3,30,1); seed(STUDENT,target);
        jdbc.execute("""
                create function test_fail_delete() returns trigger language plpgsql as $$
                begin raise exception 'test delete failure'; return old; end $$
                """);
        jdbc.execute("create trigger test_fail_delete after delete on enrollment for each row execute function test_fail_delete()");
        try {
            assertThat(cancel(Long.toString(target),token).statusCode()).isEqualTo(500);
            assertThat(count(target)).isEqualTo(1);
        } finally {
            jdbc.execute("drop trigger test_fail_delete on enrollment");
            jdbc.execute("drop function test_fail_delete()");
        }
        noContent(cancel(Long.toString(target),token));
        assertThat(count(target)).isZero();
    }

    private HttpRequest cancelRequest(String path,String bearer) {
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/enrollments/"+path))
                .timeout(Duration.ofSeconds(15)).DELETE();
        if(bearer!=null) builder.header("Authorization","Bearer "+bearer);
        return builder.build();
    }
    private HttpResponse<String> cancel(String path,String bearer) throws Exception {
        return client.send(cancelRequest(path,bearer),HttpResponse.BodyHandlers.ofString());
    }
    private CompletableFuture<HttpResponse<String>> cancelAsync(long id,String bearer) {
        return client.sendAsync(cancelRequest(Long.toString(id),bearer),HttpResponse.BodyHandlers.ofString());
    }
    private void noContent(HttpResponse<String> response) {
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(204);
        assertThat(response.body()).isEmpty();
    }
    private long course(int credits,int capacity,int day) {
        String code=Integer.toString(sequence.incrementAndGet());
        jdbc.update("insert into subject(subject_code) values (?)",code);
        long id=jdbc.queryForObject("""
                insert into course_offering(subject_code,academic_year,term,offering_code,name,credits,capacity,department_id)
                values (?,2026,2,?,'강좌',?,?,?) returning offering_id
                """,Long.class,code,code,credits,capacity,department);
        jdbc.update("insert into class_meeting(offering_id,day_of_week,starts_at,ends_at) values (?,?,'09:00','10:00')",id,day);
        return id;
    }
    private void seed(int student,long course) { jdbc.update("insert into enrollment(student_number,offering_id) values (?,?)",student,course); }
    private long count(long course) { return jdbc.queryForObject("select count(*) from enrollment where offering_id=?",Long.class,course); }
    private long total() { return jdbc.queryForObject("select count(*) from enrollment",Long.class); }
    private int credits() { return jdbc.queryForObject("select coalesce(sum(o.credits),0) from enrollment e join course_offering o using(offering_id) where e.student_number=? and o.academic_year=2026 and o.term=2",Integer.class,STUDENT); }
    private HttpRequest request(String body,String bearer) {
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/enrollments"))
                .timeout(Duration.ofSeconds(15)).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if(bearer!=null) builder.header("Authorization","Bearer "+bearer);
        return builder.build();
    }
    private HttpResponse<String> send(String body,String bearer) throws Exception { return client.send(request(body,bearer),HttpResponse.BodyHandlers.ofString()); }
    private HttpResponse<String> enroll(long id,String bearer) throws Exception { return send("{\"courseOfferingId\":"+id+"}",bearer); }
    private CompletableFuture<HttpResponse<String>> async(long id,String bearer) { return client.sendAsync(request("{\"courseOfferingId\":"+id+"}",bearer),HttpResponse.BodyHandlers.ofString()); }
    private void error(HttpResponse<String> response,int status,String code,String message) {
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(status);
        JsonNode body=mapper.readTree(response.body());
        assertThat(body.size()).isEqualTo(2);
        assertThat(body.path("code").asText()).isEqualTo(code);
        if(message!=null) assertThat(body.path("message").asText()).isEqualTo(message);
    }
    @Test
    void twoHundredStudentsCompeteForThirtySeats() throws Exception {
        for (int i=12;i<200;i++) jdbc.update("insert into student(student_number,name,grade,department_id,password_hash) values (?,'학생',1,?,'test-only')", STUDENT+i, department);
        long id=course(3,30,1);
        var results=race(java.util.stream.IntStream.range(0,200)
                .mapToObj(i->new Attempt(STUDENT+i,id)).toList());
        assertThat(results.stream().filter(r->r.statusCode()==201).count()).isEqualTo(30);
        assertThat(results.stream().filter(r->r.statusCode()==409).count()).isEqualTo(170);
        for (var response:results) if(response.statusCode()==409) error(response,409,"COURSE_FULL",null);
        assertThat(count(id)).isEqualTo(30);
        assertThat(jdbc.queryForObject("select count(distinct student_number) from enrollment where offering_id=?", Long.class,id)).isEqualTo(30);
    }

    @Test
    void metricsAreNotExposedInOrdinaryRuns() throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/actuator/prometheus"))
                .header("Authorization","Bearer "+token).GET().build();
        assertThat(client.send(request,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(404);
    }

    private List<HttpResponse<String>> race(List<Attempt> attempts) throws Exception {
        CountDownLatch ready=new CountDownLatch(attempts.size()), start=new CountDownLatch(1);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<HttpResponse<String>>> futures=new ArrayList<>();
            for(var attempt:attempts) {
                String bearer=tokens.issue(attempt.student());
                futures.add(pool.submit(()->{ready.countDown(); if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException("start timeout"); return enroll(attempt.course(),bearer);}));
            }
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue(); start.countDown();
            List<HttpResponse<String>> responses=new ArrayList<>();
            for(var future:futures) responses.add(future.get(20,TimeUnit.SECONDS));
            return responses;
        }
    }
    private Connection connection() throws SQLException {
        Connection c=DriverManager.getConnection(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());
        c.setAutoCommit(false); return c;
    }
    private void lock(Connection c,String table,String column,long id) throws SQLException {
        try(var statement=c.createStatement()) { statement.executeQuery("select "+column+" from "+table+" where "+column+"="+id+" for no key update").close(); }
    }
    private void insert(Connection c,int student,long offering) throws SQLException {
        try(var statement=c.prepareStatement("insert into enrollment(student_number,offering_id) values (?,?)")) {
            statement.setInt(1,student); statement.setLong(2,offering); statement.executeUpdate();
        }
    }
    private void awaitWaiter(String table) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(2500);
        do {
            int waiting=jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query like ?",Integer.class,"%from "+table+"%for no key update%");
            if(waiting>0)return;
            Thread.sleep(15);
        } while(System.nanoTime()<deadline);
        throw new AssertionError("No lock waiter for "+table);
    }
    private int index(List<String> sql,String...parts) {
        for(int i=0;i<sql.size();i++) { String statement=sql.get(i); if(Arrays.stream(parts).allMatch(statement::contains))return i; }
        return -1;
    }
    private record Attempt(int student,long course) {}
    public record Observation(String sql,boolean readOnly,Integer isolation) {}
    public static class SqlCapture implements StatementInspector {
        static final Queue<Observation> rows=new ConcurrentLinkedQueue<>();
        public String inspect(String sql) {
            rows.add(new Observation(sql.toLowerCase(Locale.ROOT),TransactionSynchronizationManager.isCurrentTransactionReadOnly(),TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()));
            return sql;
        }
    }
}
