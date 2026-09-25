package com.doroddi.courseregistration.common.api;

import com.doroddi.courseregistration.department.DepartmentRepository;
import com.doroddi.courseregistration.professor.ProfessorRepository;
import com.doroddi.courseregistration.student.StudentRepository;
import com.doroddi.courseregistration.student.auth.JwtTokenService;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.initial-data.enabled=false",
        "spring.jpa.open-in-view=false",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "com.doroddi.courseregistration.common.api.PeopleListIntegrationTest$SqlCapture"
})
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PeopleListIntegrationTest {
    private static final int STUDENT = 202010100;
    private static final String PASSWORD = "people-list-test-password";
    private static final Instant NOW = Instant.parse("2026-09-22T04:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6");

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtTokenService tokens;
    @MockitoBean Clock clock;
    @MockitoSpyBean StudentRepository students;
    @MockitoSpyBean ProfessorRepository professors;
    @MockitoSpyBean DepartmentRepository departments;

    private HttpClient client;
    private Long departmentA;
    private Long departmentB;
    private Long emptyDepartment;
    private String token;

    @BeforeAll
    void prepareCommittedFixturesAndLogin() throws Exception {
        when(clock.instant()).thenReturn(NOW);
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        departmentA = department("전산학과", 10);
        departmentB = department("수학과", 11);
        emptyDepartment = department("빈학과", 12);
        // 테스트 전용 데이터. 학번 발급 정보와 현재 소속을 분리하고 역순으로 삽입한다.
        jdbc.update("""
                insert into student (student_number, name, grade, department_id, password_hash)
                select (2020 + g / 1600) * 100000 + (10 + (g / 400) % 4) * 1000 + 100 + g % 400,
                       '학생' || g, g % 4 + 1, case when g % 2 = 0 then ? else ? end, ?
                from generate_series(0, 9999) g order by g desc
                """, departmentA, departmentB, encoder.encode(PASSWORD));
        jdbc.update("""
                insert into professor (professor_id, name, department_id) overriding system value
                select g, case when g <= 2 then '동명이인교수' else '교수' || (101 - g) end,
                       case when g % 2 = 0 then ? else ? end
                from generate_series(1, 100) g order by g desc
                """, departmentA, departmentB);
        jdbc.update("insert into subject (subject_code) values ('9001')");
        Long offeringId = jdbc.queryForObject("""
                insert into course_offering
                    (subject_code, academic_year, term, offering_code, name, credits, capacity, department_id)
                values ('9001', 2026, 2, '900101', '타학과 강좌', 3, 30, ?) returning offering_id
                """, Long.class, departmentB);
        jdbc.update("insert into teaching_assignment (professor_id, offering_id) values (2, ?)", offeringId);
        HttpRequest login = HttpRequest.newBuilder(uri("/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(
                        java.util.Map.of("studentNumber", STUDENT, "password", PASSWORD))))
                .build();
        HttpResponse<String> response = client.send(login, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        token = mapper.readTree(response.body()).path("accessToken").asText();
        assertThat(token).isNotBlank();
    }

    @BeforeEach
    void clearRequestObservations() {
        when(clock.instant()).thenReturn(NOW);
        clearInvocations(students, professors, departments);
        SqlCapture.queries.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/students", "/professors"})
    void returnsDefaultPageWithOnlyContractFields(String path) throws Exception {
        JsonNode body = ok(path);
        assertPage(body, 0, 20, totalFor(path), path.equals("/students") ? 500 : 5, 20);
        List<Long> ids = ids(body, path);
        assertThat(ids).isSorted().doesNotHaveDuplicates();
        JsonNode first = body.path("content").get(0);
        assertThat(first.size()).isEqualTo(path.equals("/students") ? 4 : 3);
        assertThat(first.has("name")).isTrue();
        assertThat(first.has("departmentName")).isTrue();
        assertThat(first.has("departmentId")).isFalse();
        assertThat(first.has("passwordHash")).isFalse();
        if (path.equals("/students")) {
            assertThat(first.path("studentNumber").isIntegralNumber()).isTrue();
            assertThat(first.path("studentNumber").asInt()).isEqualTo(STUDENT);
            assertThat(first.path("grade").asInt()).isEqualTo(1);
        } else {
            assertThat(first.path("id").asLong()).isEqualTo(1);
            assertThat(first.path("name").asText()).isEqualTo(body.path("content").get(1).path("name").asText());
        }
        assertSql(2);
        verifyNoInteractions(departments);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/students", "/professors"})
    void maximumSizeStillUsesOnlyPageAndCountQueries(String path) throws Exception {
        JsonNode body = ok(path + "?size=100");
        assertPage(body, 0, 100, totalFor(path), path.equals("/students") ? 100 : 1, 100);
        assertSql(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"department", "grade", "both"})
    void studentFiltersUseTheSameConditionsForContentAndCount(String filter) throws Exception {
        String query = switch (filter) {
            case "department" -> "departmentId=" + departmentA;
            case "grade" -> "grade=1";
            default -> "departmentId=" + departmentA + "&grade=1";
        };
        JsonNode body = ok("/students?" + query);
        boolean hasDepartment = !filter.equals("grade");
        long expectedTotal = filter.equals("department") ? 5000 : 2500;
        assertPage(body, 0, 20, expectedTotal, (int) (expectedTotal / 20), 20);
        for (JsonNode item : body.path("content")) {
            if (hasDepartment) assertThat(item.path("departmentName").asText()).isEqualTo("전산학과");
            if (!filter.equals("department")) assertThat(item.path("grade").asInt()).isEqualTo(1);
        }
        assertSql(hasDepartment ? 3 : 2);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 4})
    void includesStudentGradeBoundaryValues(int grade) throws Exception {
        JsonNode body = ok("/students?grade=" + grade);
        assertThat(body.path("totalElements").asLong()).isEqualTo(2500);
        for (JsonNode item : body.path("content")) assertThat(item.path("grade").asInt()).isEqualTo(grade);
    }

    @Test
    void studentDepartmentFilterUsesCurrentDepartmentRatherThanDigitsInStudentNumber() throws Exception {
        JsonNode body = ok("/students?departmentId=" + departmentB);
        JsonNode first = body.path("content").get(0);
        assertThat(first.path("studentNumber").asInt()).isEqualTo(STUDENT + 1);
        assertThat(first.path("departmentName").asText()).isEqualTo("수학과");
        assertThat(body.path("totalElements").asLong()).isEqualTo(5000);
    }

    @Test
    void professorFilterUsesOwnDepartmentRatherThanTheDepartmentOfTaughtCourses() throws Exception {
        JsonNode body = ok("/professors?departmentId=" + departmentA);
        assertPage(body, 0, 20, 50, 3, 20);
        assertThat(ids(body, "/professors")).contains(2L).allMatch(id -> id % 2 == 0);
        for (JsonNode item : body.path("content")) assertThat(item.path("departmentName").asText()).isEqualTo("전산학과");
        assertSql(3);
        JsonNode other = ok("/professors?departmentId=" + departmentB);
        assertThat(ids(other, "/professors")).doesNotContain(2L).allMatch(id -> id % 2 == 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/students", "/professors"})
    void adjacentPagesAreSortedAndDoNotOverlap(String path) throws Exception {
        List<Long> first = ids(ok(path + "?size=7"), path);
        List<Long> second = ids(ok(path + "?page=1&size=7"), path);
        assertThat(first).isSorted().doesNotHaveDuplicates();
        assertThat(second).isSorted().doesNotHaveDuplicates();
        assertThat(second.getFirst()).isGreaterThan(first.getLast());
        assertThat(second).doesNotContainAnyElementsOf(first);
    }

    @ParameterizedTest
    @CsvSource({"/students,101,99,10000,102,1", "/professors,3,30,100,4,10"})
    void partialLastPagePreservesRequestedSize(String path, int page, int size, long total, int pages, int count) throws Exception {
        assertPage(ok(path + "?page=" + page + "&size=" + size), page, size, total, pages, count);
    }

    @ParameterizedTest
    @CsvSource({"/students,500,10000,500", "/professors,5,100,5"})
    void outOfRangePageKeepsActualTotals(String path, int page, long total, int pages) throws Exception {
        assertPage(ok(path + "?page=" + page), page, 20, total, pages, 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/students", "/professors"})
    void largestAllowedOffsetReturnsEmptyPageInsteadOfServerError(String path) throws Exception {
        assertPage(ok(path + "?page=2147483647&size=1"), Integer.MAX_VALUE, 1, totalFor(path), (int) totalFor(path), 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/students", "/professors"})
    void existingDepartmentWithoutPeopleReturnsEmptySuccess(String path) throws Exception {
        assertPage(ok(path + "?departmentId=" + emptyDepartment), 0, 20, 0, 0, 0);
    }

    @Test
    void studentFilterCombinationWithoutMatchesReturnsEmptySuccess() throws Exception {
        assertPage(ok("/students?departmentId=" + departmentA + "&grade=2"), 0, 20, 0, 0, 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/students", "/professors"})
    void validMissingDepartmentReturns404WithoutQueryingPeople(String path) throws Exception {
        assertError(get(path + "?departmentId=9223372036854775807", token), 404,
                "DEPARTMENT_NOT_FOUND", "해당 학과를 찾을 수 없습니다.");
        verify(departments).existsById(Long.MAX_VALUE);
        verifyNoInteractions(students, professors);
        assertSql(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/students?departmentId=9223372036854775807&size=101",
            "/professors?departmentId=9223372036854775807&size=101",
            "/students?size=20&size=20", "/professors?size=20&size=50",
            "/students?page=-1&size=101", "/professors?size=101&page=-1",
            "/students?szie=20", "/professors?grade=2",
            "/students?page=2147483647&size=100", "/professors?size="
    })
    void invalidInputReturns400BeforeDatabaseAccess(String path) throws Exception {
        assertError(get(path, token), 400, "INVALID_PARAMETER", null);
        assertNoDatabaseQueries();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/students", "/professors"})
    void missingAuthenticationWinsOverInputErrors(String path) throws Exception {
        assertError(get(path + "?size=101", null), 401, "TOKEN_REQUIRED", "로그인이 필요합니다.");
        assertNoDatabaseQueries();
    }

    @ParameterizedTest
    @CsvSource({"/students,expired", "/professors,expired", "/students,forged", "/professors,forged"})
    void invalidAuthenticationRejectsProtectedListsBeforeDatabaseAccess(String path, String failure) throws Exception {
        String rejectedToken;
        if (failure.equals("expired")) {
            when(clock.instant()).thenReturn(NOW.minusSeconds(1800));
            rejectedToken = tokens.issue(STUDENT);
            when(clock.instant()).thenReturn(NOW);
        } else {
            int signature = token.lastIndexOf('.') + 1;
            char replacement = token.charAt(signature) == 'A' ? 'B' : 'A';
            rejectedToken = token.substring(0, signature) + replacement + token.substring(signature + 1);
        }
        assertError(get(path, rejectedToken), 401,
                failure.equals("expired") ? "TOKEN_EXPIRED" : "INVALID_TOKEN", null);
        assertNoDatabaseQueries();
    }

    private Long department(String name, int code) {
        return jdbc.queryForObject("insert into department (name, department_code) values (?, ?) returning department_id",
                Long.class, name, code);
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private HttpResponse<String> get(String path, String bearer) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(15)).GET();
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode ok(String path) throws Exception {
        HttpResponse<String> response = get(path, token);
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200);
        JsonNode body = mapper.readTree(response.body());
        assertThat(body.size()).isEqualTo(5);
        return body;
    }

    private void assertError(HttpResponse<String> response, int status, String code, String message) {
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(status);
        JsonNode body = mapper.readTree(response.body());
        assertThat(body.size()).isEqualTo(2);
        assertThat(body.path("code").asText()).isEqualTo(code);
        if (message != null) assertThat(body.path("message").asText()).isEqualTo(message);
    }

    private void assertPage(JsonNode body, int page, int size, long total, int pages, int count) {
        assertThat(body.path("page").asInt()).isEqualTo(page);
        assertThat(body.path("size").asInt()).isEqualTo(size);
        assertThat(body.path("totalElements").asLong()).isEqualTo(total);
        assertThat(body.path("totalPages").asInt()).isEqualTo(pages);
        assertThat(body.path("content").size()).isEqualTo(count);
    }

    private List<Long> ids(JsonNode body, String path) {
        List<Long> result = new ArrayList<>();
        String field = path.equals("/students") ? "studentNumber" : "id";
        body.path("content").forEach(item -> result.add(item.path(field).asLong()));
        return result;
    }

    private long totalFor(String path) {
        return path.equals("/students") ? 10000 : 100;
    }

    private void assertNoDatabaseQueries() {
        verifyNoInteractions(students, professors, departments);
        assertThat(SqlCapture.queries).isEmpty();
    }

    private void assertSql(int expectedCount) {
        assertThat(SqlCapture.queries).hasSize(expectedCount);
        for (SqlObservation observation : SqlCapture.queries) {
            assertThat(observation.sql().toLowerCase(java.util.Locale.ROOT))
                    .startsWith("select ").doesNotContain("password_hash", "for update", "for no key update", "for share");
            assertThat(observation.readOnly()).isTrue();
            assertThat(observation.isolation()).isEqualTo(Connection.TRANSACTION_READ_COMMITTED);
        }
    }

    public record SqlObservation(String sql, boolean readOnly, Integer isolation) {}

    public static class SqlCapture implements StatementInspector {
        static final ConcurrentLinkedQueue<SqlObservation> queries = new ConcurrentLinkedQueue<>();

        @Override
        public String inspect(String sql) {
            queries.add(new SqlObservation(sql, TransactionSynchronizationManager.isCurrentTransactionReadOnly(),
                    TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()));
            return sql;
        }
    }
}
