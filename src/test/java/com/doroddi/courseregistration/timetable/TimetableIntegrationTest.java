package com.doroddi.courseregistration.timetable;

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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.initial-data.enabled=false", "spring.jpa.open-in-view=false",
        "app.enrollment.academic-year=2025", "app.enrollment.term=1",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "com.doroddi.courseregistration.timetable.TimetableIntegrationTest$SqlCapture"
})
@Testcontainers
public class TimetableIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6");
    private static final int STUDENT=202010100;
    private static final Instant NOW=Instant.parse("2026-09-25T00:00:00Z");
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokens;
    @Autowired ObjectMapper mapper;
    @MockitoBean Clock clock;
    private final HttpClient client=HttpClient.newHttpClient();
    private long department;
    private int sequence=100;
    private String token;

    @BeforeEach
    void fixtures() {
        when(clock.instant()).thenReturn(NOW);
        SqlCapture.pause.set(null);
        for(String table:List.of("enrollment","teaching_assignment","class_meeting","course_offering",
                "subject","professor","student","department")) jdbc.update("delete from "+table);
        department=jdbc.queryForObject("insert into department(name,department_code) values ('개설 학과',10) returning department_id",Long.class);
        for(int i=0;i<2;i++) jdbc.update("insert into student(student_number,name,grade,department_id,password_hash) values (?,'학생',1,?,'test-only')",STUDENT+i,department);
        token=tokens.issue(STUDENT);
        SqlCapture.rows.clear();
    }

    @Test
    void emptyTimetableReturnsZeroWithOnlyBaseQuery() throws Exception {
        var body=read(get("",token));
        assertThat(body.size()).isEqualTo(2);
        assertThat(body.path("content").size()).isZero();
        assertThat(body.path("totalCredits").asInt()).isZero();
        assertThat(SqlCapture.rows).hasSize(1);
        assertReadOnly();
    }

    @Test
    void selectsOnlyJwtStudentAndConfiguredTermInEnrollmentOrder() throws Exception {
        long a=course("가",2,1), b=course("나",3,2), other=course("다",1,3);
        long wrongYear=course("지난 연도",3,4), wrongTerm=course("다른 학기",3,5);
        jdbc.update("update course_offering set academic_year=2026 where offering_id=?",wrongYear);
        jdbc.update("update course_offering set term=2 where offering_id=?",wrongTerm);
        seed(STUDENT,b); seed(STUDENT,a); seed(STUDENT,wrongYear); seed(STUDENT,wrongTerm); seed(STUDENT+1,other);
        var body=read(get("",token));
        assertThat(ids(body)).containsExactly(b,a);
        assertThat(body.path("totalCredits").asInt()).isEqualTo(5);
        assertThat(body.path("content").get(0).path("name").asText()).isEqualTo("나");
        assertThat(SqlCapture.rows).hasSize(3);
        assertReadOnly();
        SqlCapture.rows.clear();
        assertThat(ids(read(get("",tokens.issue(STUDENT+1))))).containsExactly(other);
    }

    @Test
    void multipleMeetingsAndProfessorsDoNotDuplicateCoursesOrCredits() throws Exception {
        long target=course("자료구조",3,3); seed(STUDENT,target);
        jdbc.update("insert into class_meeting(offering_id,day_of_week,starts_at,ends_at) values (?,1,'13:00','15:00'),(?,1,'09:00','10:00'),(?,7,'00:00','01:00')",target,target,target);
        long externalDepartment=jdbc.queryForObject("insert into department(name,department_code) values ('교수 소속 학과',11) returning department_id",Long.class);
        long p1=professor("Z 교수",externalDepartment),p2=professor("A 교수",department);
        jdbc.update("insert into teaching_assignment(professor_id,offering_id) values (?,?),(?,?)",p2,target,p1,target);
        var body=read(get("",token));
        assertThat(ids(body)).containsExactly(target);
        assertThat(body.path("totalCredits").asInt()).isEqualTo(3);
        var item=body.path("content").get(0);
        assertThat(item.size()).isEqualTo(6);
        assertThat(item.has("capacity")).isFalse();
        assertThat(item.has("enrolled")).isFalse();
        assertThat(item.path("departmentName").asText()).isEqualTo("개설 학과");
        var schedules=item.path("schedules");
        assertThat(schedules.size()).isEqualTo(4);
        assertThat(schedules.get(0).path("dayOfWeek").asText()).isEqualTo("MONDAY");
        assertThat(schedules.get(0).path("startTime").asText()).isEqualTo("09:00");
        assertThat(schedules.get(0).path("endTime").asText()).isEqualTo("10:00");
        assertThat(schedules.get(1).path("startTime").asText()).isEqualTo("13:00");
        assertThat(schedules.get(2).path("dayOfWeek").asText()).isEqualTo("WEDNESDAY");
        assertThat(schedules.get(3).path("dayOfWeek").asText()).isEqualTo("SUNDAY");
        assertThat(schedules.get(3).path("startTime").asText()).isEqualTo("00:00");
        assertThat(item.path("professors").get(0).path("id").asLong()).isEqualTo(p1);
        assertThat(item.path("professors").get(1).path("id").asLong()).isEqualTo(p2);
        assertThat(SqlCapture.rows).hasSize(3);
        assertReadOnly();
    }

    @Test
    void returnsAllEighteenCoursesWithoutPaginationOrPerCourseQueries() throws Exception {
        List<Long> expected=new ArrayList<>();
        for(int i=0;i<18;i++) { long id=course("강좌"+i,1,1); seed(STUDENT,id); expected.add(id); }
        var body=read(get("",token));
        assertThat(ids(body)).isEqualTo(expected);
        assertThat(body.path("totalCredits").asInt()).isEqualTo(18);
        assertThat(SqlCapture.rows).hasSize(3);
    }

    @ParameterizedTest
    @ValueSource(strings={"?studentNumber=202010101","?year=2026","?term=2","?page=0","?size=20","?x","?x=1&x=2"})
    void rejectsAnyQueryParameterBeforeDatabase(String query) throws Exception {
        error(get(query,token),400,"INVALID_PARAMETER");
        assertThat(SqlCapture.rows).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings={"missing","expired","forged"})
    void authenticationPrecedesInvalidQuery(String kind) throws Exception {
        String bearer=null,code="TOKEN_REQUIRED";
        if(kind.equals("expired")) {
            when(clock.instant()).thenReturn(NOW.minusSeconds(1800));
            bearer=tokens.issue(STUDENT); when(clock.instant()).thenReturn(NOW); code="TOKEN_EXPIRED";
        } else if(kind.equals("forged")) {
            int p=token.lastIndexOf('.')+1;
            bearer=token.substring(0,p)+(token.charAt(p)=='A'?'B':'A')+token.substring(p+1);
            code="INVALID_TOKEN";
        }
        error(get("?studentNumber=202010101",bearer),401,code);
        assertThat(SqlCapture.rows).isEmpty();
    }

    @Test
    void cancellationAndReenrollmentAppearInNewEnrollmentOrder() throws Exception {
        long a=course("A",3,1),b=course("B",3,2),c=course("C",3,3);
        seed(STUDENT,a); seed(STUDENT,b); seed(STUDENT,c);
        assertThat(ids(read(get("",token)))).containsExactly(a,b,c);
        assertThat(cancel(a).statusCode()).isEqualTo(204);
        assertThat(ids(read(get("",token)))).containsExactly(b,c);
        assertThat(enroll(a).statusCode()).isEqualTo(201);
        var body=read(get("",token));
        assertThat(ids(body)).containsExactly(b,c,a);
        assertThat(body.path("totalCredits").asInt()).isEqualTo(9);
    }

    @Test
    void plainTimetableReadDoesNotWaitForStudentOrOfferingRowLocks() throws Exception {
        long target=course("강좌",3,1); seed(STUDENT,target);
        try(Connection holder=DriverManager.getConnection(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());
            Statement statement=holder.createStatement()) {
            holder.setAutoCommit(false);
            statement.executeQuery("select student_number from student where student_number="+STUDENT+" for no key update").close();
            statement.executeQuery("select offering_id from course_offering where offering_id="+target+" for no key update").close();
            assertThat(ids(read(get("",token)))).containsExactly(target);
            holder.rollback();
        }
        assertReadOnly();
    }

    @ParameterizedTest @ValueSource(strings={"cancel","enroll"})
    void totalCreditsMatchesCapturedContentDuringConcurrentCommit(String change) throws Exception {
        long original=course("기존 강좌",3,1), added=course("추가 강좌",2,2); seed(STUDENT,original);
        Pause pause=new Pause();
        SqlCapture.pause.set(pause);
        try {
            var pending=client.sendAsync(request("/me/timetable",token),HttpResponse.BodyHandlers.ofString());
            assertThat(pause.baseRead.await(10,TimeUnit.SECONDS)).isTrue();
            if(change.equals("cancel")) assertThat(cancel(original).statusCode()).isEqualTo(204);
            else assertThat(enroll(added).statusCode()).isEqualTo(201);
            pause.resume.countDown();
            var captured=read(pending.get(15,TimeUnit.SECONDS));
            assertThat(ids(captured)).containsExactly(original);
            assertThat(captured.path("totalCredits").asInt()).isEqualTo(3);
            assertThat(captured.path("content").get(0).path("schedules").size()).isEqualTo(1);
        } finally { pause.resume.countDown(); SqlCapture.pause.set(null); }
        var fresh=read(get("",token));
        assertThat(ids(fresh)).isEqualTo(change.equals("cancel")?List.of():List.of(original,added));
        assertThat(fresh.path("totalCredits").asInt()).isEqualTo(change.equals("cancel")?0:5);
    }

    private long course(String name,int credits,int day) {
        String code=Integer.toString(++sequence);
        jdbc.update("insert into subject(subject_code) values (?)",code);
        long id=jdbc.queryForObject("insert into course_offering(subject_code,academic_year,term,offering_code,name,credits,capacity,department_id) values (?,2025,1,?,?,?,30,?) returning offering_id",
                Long.class,code,code,name,credits,department);
        jdbc.update("insert into class_meeting(offering_id,day_of_week,starts_at,ends_at) values (?,?,'09:00','10:00')",id,day);
        return id;
    }
    private long professor(String name,long departmentId) {
        return jdbc.queryForObject("insert into professor(name,department_id) values (?,?) returning professor_id",Long.class,name,departmentId);
    }
    private void seed(int student,long offering) { jdbc.update("insert into enrollment(student_number,offering_id) values (?,?)",student,offering); }
    private HttpRequest request(String path,String bearer) {
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(15));
        if(bearer!=null) builder.header("Authorization","Bearer "+bearer);
        return builder.GET().build();
    }
    private HttpResponse<String> get(String query,String bearer) throws Exception {
        return client.send(request("/me/timetable"+query,bearer),HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> cancel(long id) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/enrollments/"+id))
                .timeout(Duration.ofSeconds(15)).header("Authorization","Bearer "+token).DELETE().build(),HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> enroll(long id) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/enrollments"))
                .timeout(Duration.ofSeconds(15)).header("Authorization","Bearer "+token).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"courseOfferingId\":"+id+"}")).build(),HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode read(HttpResponse<String> response) {
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200);
        return mapper.readTree(response.body());
    }
    private List<Long> ids(JsonNode body) {
        List<Long> result=new ArrayList<>();
        for(var item:body.path("content"))result.add(item.path("id").asLong());
        return result;
    }
    private void error(HttpResponse<String> response,int status,String code) {
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(status);
        var body=mapper.readTree(response.body());
        assertThat(body.path("code").asText()).isEqualTo(code);
    }
    private void assertReadOnly() {
        for(var row:SqlCapture.rows) {
            assertThat(row.readOnly()).isTrue();
            assertThat(row.isolation()).isEqualTo(Connection.TRANSACTION_READ_COMMITTED);
            assertThat(row.sql()).doesNotContain("for no key update","for update","password_hash","sum(");
        }
    }
    public record Observation(String sql,boolean readOnly,Integer isolation) {}
    private static class Pause {
        final CountDownLatch baseRead=new CountDownLatch(1),resume=new CountDownLatch(1);
    }
    public static class SqlCapture implements StatementInspector {
        static final Queue<Observation> rows=new ConcurrentLinkedQueue<>();
        static final AtomicReference<Pause> pause=new AtomicReference<>();
        public String inspect(String sql) {
            rows.add(new Observation(sql.toLowerCase(Locale.ROOT),TransactionSynchronizationManager.isCurrentTransactionReadOnly(),
                    TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()));
            // 최초 신청 목록을 읽은 뒤 관계 조회 직전에 커밋을 끼워 넣는다. 테스트 전용이다.
            if(sql.contains("from class_meeting")) {
                Pause gate=pause.getAndSet(null);
                if(gate!=null) {
                    gate.baseRead.countDown();
                    try { if(!gate.resume.await(12,TimeUnit.SECONDS))throw new IllegalStateException("resume timeout"); }
                    catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
                }
            }
            return sql;
        }
    }
}
