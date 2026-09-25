package com.doroddi.courseregistration.common.api;

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

import java.math.BigInteger;
import java.net.URI;
import java.net.http.*;
import java.sql.Connection;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.initial-data.enabled=false", "spring.jpa.open-in-view=false",
        "app.enrollment.academic-year=2025", "app.enrollment.term=1",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "com.doroddi.courseregistration.common.api.CourseOfferingListIntegrationTest$SqlCapture"
})
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CourseOfferingListIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6");
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtTokenService tokens;
    @MockitoBean Clock clock;
    private final HttpClient client = HttpClient.newHttpClient();
    private final List<Fixture> fixtures = new ArrayList<>();
    private Long departmentA, departmentB, emptyDepartment, professorA, professorB, enrolledId;
    private String token;
    private static final Instant NOW = Instant.parse("2026-09-23T00:00:00Z");

    @BeforeAll
    void fixtures() {
        when(clock.instant()).thenReturn(NOW);
        departmentA = department("강좌학과", 10);
        departmentB = department("다른학과", 11);
        emptyDepartment = department("과거강좌학과", 12);
        professorA = professor(departmentB);
        professorB = professor(departmentA);
        jdbc.update("insert into subject(subject_code) values ('1')");
        jdbc.update("""
                insert into student(student_number,name,grade,department_id,password_hash)
                values (202010100,'학생1',1,?,'test-only'), (202010101,'학생2',2,?,'test-only')
                """, departmentA, departmentB);
        List<String> codes = new ArrayList<>(List.of("001", "1", "2", "10", "9223372036854775808",
                "999999999999999999999999999998", "999999999999999999999999999999"));
        for (int i = 100; i < 200; i++) codes.add(Integer.toString(i));
        for (int i = codes.size() - 1; i >= 0; i--) {
            long department = i % 2 == 0 ? departmentA : departmentB;
            long id = offering(2025, 1, codes.get(i), department);
            fixtures.add(new Fixture(id, codes.get(i), department));
            jdbc.update("""
                    insert into class_meeting(offering_id, day_of_week, starts_at, ends_at) values
                    (?,7,'17:00','18:00'), (?,1,'10:00','12:00'),
                    (?,1,'09:00','10:00'), (?,1,'10:00','11:00')
                    """, id, id, id, id);
            jdbc.update("insert into teaching_assignment(professor_id,offering_id) values (?,?),(?,?)",
                    professorB, id, professorA, id);
            if (codes.get(i).equals("001")) enrolledId = id;
        }
        fixtures.sort(Comparator.comparing((Fixture f) -> new BigInteger(f.code())).thenComparingLong(Fixture::id));
        jdbc.update("insert into enrollment(student_number,offering_id) values (202010100,?),(202010101,?)",
                enrolledId, enrolledId);
        offering(2024, 1, "000", departmentA);
        offering(2025, 2, "000", departmentA);
        offering(2026, 2, "000", emptyDepartment);
        token = tokens.issue(202010100);
    }

    @BeforeEach
    void resetObservations() {
        when(clock.instant()).thenReturn(NOW);
        SqlCapture.rows.clear();
    }

    @Test
    void defaultPageUsesConfiguredTermAndNumericCodeOrder() throws Exception {
        JsonNode body = ok("");
        page(body, 0, 20, 107, 6, 20);
        assertThat(ids(body)).containsExactlyElementsOf(fixtures.subList(0,20).stream().map(Fixture::id).toList());
        JsonNode item = body.path("content").get(0);
        assertThat(item.size()).isEqualTo(8);
        assertThat(item.path("name").asText()).isEqualTo("동일강좌명");
        assertThat(item.path("credits").asInt()).isEqualTo(3);
        assertThat(item.path("capacity").asInt()).isEqualTo(30);
        sqlCount(5);
    }

    @ParameterizedTest @ValueSource(ints = {1, 100})
    void queryCountDoesNotGrowWithPageSize(int size) throws Exception {
        page(ok("?size=" + size), 0, size, 107, (107 + size - 1) / size, size);
        sqlCount(5);
    }

    @Test
    void allPagesPreservePreciseThirtyDigitOrderingAndEqualNumericCodeTieBreak() throws Exception {
        List<Long> actual = new ArrayList<>();
        for (int p=0; p<6; p++) actual.addAll(ids(ok("?page=" + p)));
        assertThat(actual).containsExactlyElementsOf(fixtures.stream().map(Fixture::id).toList());
        assertThat(actual).doesNotHaveDuplicates();
        assertThat(jdbc.queryForObject("select offering_code from course_offering where offering_id=?",
                String.class, enrolledId)).isEqualTo("001");
    }

    @Test
    void manyMeetingsAndProfessorsDoNotMultiplyEnrollmentCountOrCourses() throws Exception {
        JsonNode body = ok("");
        for (JsonNode item : body.path("content")) {
            assertThat(item.path("enrolled").asLong()).isEqualTo(item.path("id").asLong() == enrolledId ? 2 : 0);
            JsonNode meetings = item.path("schedules");
            assertThat(meetings.size()).isEqualTo(4);
            assertThat(meetings.get(0).path("dayOfWeek").asText()).isEqualTo("MONDAY");
            assertThat(meetings.get(0).path("startTime").asText()).isEqualTo("09:00");
            assertThat(meetings.get(1).path("startTime").asText()).isEqualTo("10:00");
            assertThat(meetings.get(1).path("endTime").asText()).isEqualTo("11:00");
            assertThat(meetings.get(2).path("endTime").asText()).isEqualTo("12:00");
            assertThat(meetings.get(3).path("dayOfWeek").asText()).isEqualTo("SUNDAY");
            assertThat(meetings.get(0).size()).isEqualTo(3);
            JsonNode professors = item.path("professors");
            assertThat(professors.size()).isEqualTo(2);
            assertThat(professors.get(0).path("id").asLong()).isEqualTo(professorA);
            assertThat(professors.get(1).path("id").asLong()).isEqualTo(professorB);
            assertThat(professors.get(0).size()).isEqualTo(2);
        }
    }

    @Test
    void filtersByOfferingDepartmentNotTeachingProfessorDepartment() throws Exception {
        JsonNode body = ok("?departmentId=" + departmentA);
        var expected = fixtures.stream().filter(f -> f.department() == departmentA).map(Fixture::id).toList();
        page(body, 0, 20, expected.size(), 3, 20);
        assertThat(ids(body)).containsExactlyElementsOf(expected.subList(0,20));
        for (JsonNode item : body.path("content")) {
            assertThat(item.path("departmentName").asText()).isEqualTo("강좌학과");
        }
        sqlCount(6);
    }

    @Test
    void lastPagePreservesRequestedSize() throws Exception {
        page(ok("?page=5"), 5, 20, 107, 6, 7);
    }

    @ParameterizedTest @ValueSource(strings = {"?page=6", "?page=2147483647&size=1"})
    void emptyPageRetainsTotalsAndSkipsDetailQueries(String query) throws Exception {
        JsonNode body = ok(query);
        assertThat(body.path("content").isEmpty()).isTrue();
        assertThat(body.path("totalElements").asLong()).isEqualTo(107);
        assertThat(body.path("page").asInt()).isEqualTo(query.contains("2147483647") ? Integer.MAX_VALUE : 6);
        assertThat(body.path("totalPages").asInt()).isEqualTo(query.contains("2147483647") ? 107 : 6);
        sqlCount(2);
    }

    @Test
    void departmentWithOnlyOtherTermReturnsEmptySuccess() throws Exception {
        page(ok("?departmentId=" + emptyDepartment), 0, 20, 0, 0, 0);
        sqlCount(2);
    }

    @Test
    void unknownDepartmentReturns404() throws Exception {
        error("?departmentId=9223372036854775807", token, 404, "DEPARTMENT_NOT_FOUND");
        sqlCount(1);
    }

    @ParameterizedTest @ValueSource(strings = {"?size=20&size=20", "?grade=2", "?academicYear=2024",
            "?departmentId=9223372036854775807&size=101", "?page=2147483647&size=100"})
    void invalidParametersNeverAccessDatabase(String query) throws Exception {
        error(query, token, 400, "INVALID_PARAMETER");
        assertThat(SqlCapture.rows).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"missing", "expired", "forged"})
    void authenticationPrecedesInputAndDatabase(String failure) throws Exception {
        String bearer = null;
        String code = "TOKEN_REQUIRED";
        if (failure.equals("expired")) {
            when(clock.instant()).thenReturn(NOW.minusSeconds(1800));
            bearer = tokens.issue(202010100);
            when(clock.instant()).thenReturn(NOW);
            code = "TOKEN_EXPIRED";
        } else if (failure.equals("forged")) {
            int i = token.lastIndexOf('.') + 1;
            bearer = token.substring(0,i) + (token.charAt(i) == 'A' ? 'B' : 'A') + token.substring(i+1);
            code = "INVALID_TOKEN";
        }
        error("?size=101", bearer, 401, code);
        assertThat(SqlCapture.rows).isEmpty();
    }

    private long department(String name, int code) {
        return jdbc.queryForObject("insert into department(name,department_code) values (?,?) returning department_id",
                Long.class, name, code);
    }
    private long professor(long department) {
        return jdbc.queryForObject("insert into professor(name,department_id) values ('동명이인',?) returning professor_id",
                Long.class, department);
    }
    private long offering(int year, int term, String code, long department) {
        return jdbc.queryForObject("""
                insert into course_offering(subject_code,academic_year,term,offering_code,name,credits,capacity,department_id)
                values ('1',?,?,?,'동일강좌명',3,30,?) returning offering_id
                """, Long.class, year, term, code, department);
    }
    private HttpResponse<String> get(String query, String bearer) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/course-offerings" + query))
                .timeout(Duration.ofSeconds(15)).GET();
        if (bearer != null) builder.header("Authorization", "Bearer " + bearer);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode ok(String query) throws Exception {
        var response = get(query, token);
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200);
        JsonNode body = mapper.readTree(response.body());
        assertThat(body.size()).isEqualTo(5);
        return body;
    }
    private void error(String query, String bearer, int status, String code) throws Exception {
        var response = get(query, bearer);
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(status);
        JsonNode body = mapper.readTree(response.body());
        assertThat(body.size()).isEqualTo(2);
        assertThat(body.path("code").asText()).isEqualTo(code);
    }
    private List<Long> ids(JsonNode body) {
        List<Long> ids = new ArrayList<>();
        body.path("content").forEach(item -> ids.add(item.path("id").asLong()));
        return ids;
    }
    private void page(JsonNode body, int page, int size, long total, int pages, int count) {
        assertThat(body.path("page").asInt()).isEqualTo(page);
        assertThat(body.path("size").asInt()).isEqualTo(size);
        assertThat(body.path("totalElements").asLong()).isEqualTo(total);
        assertThat(body.path("totalPages").asInt()).isEqualTo(pages);
        assertThat(body.path("content").size()).isEqualTo(count);
    }
    private void sqlCount(int count) {
        assertThat(SqlCapture.rows).hasSize(count);
        for (Observation row : SqlCapture.rows) {
            assertThat(row.sql().stripLeading().toLowerCase(Locale.ROOT)).startsWith("select ")
                    .doesNotContain("for update", "for share", "for no key update", "password_hash");
            assertThat(row.readOnly()).isTrue();
            assertThat(row.isolation()).isEqualTo(Connection.TRANSACTION_READ_COMMITTED);
        }
    }
    private record Fixture(long id, String code, long department) {}
    public record Observation(String sql, boolean readOnly, Integer isolation) {}
    public static class SqlCapture implements StatementInspector {
        static final Queue<Observation> rows = new ConcurrentLinkedQueue<>();
        public String inspect(String sql) {
            rows.add(new Observation(sql, TransactionSynchronizationManager.isCurrentTransactionReadOnly(),
                    TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()));
            return sql;
        }
    }
}
