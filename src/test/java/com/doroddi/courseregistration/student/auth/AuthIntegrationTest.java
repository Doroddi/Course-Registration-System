package com.doroddi.courseregistration.student.auth;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.crypto.SecretKey;

import com.doroddi.courseregistration.config.JwtProperties;
import com.doroddi.courseregistration.student.StudentRepository;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.initial-data.enabled=false")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(AuthIntegrationTest.ProbeConfiguration.class)
class AuthIntegrationTest {
    private static final int STUDENT = 202610100;
    private static final int OTHER_STUDENT = 202610101;
    private static final int ASCII_PASSWORD_STUDENT = 202610102;
    private static final int KOREAN_PASSWORD_STUDENT = 202610103;
    private static final String PASSWORD = "integration-password";
    private static final String SPACED_PASSWORD = "  original-password  ";
    private static final String ASCII_LIMIT_PASSWORD = "a".repeat(72);
    private static final String KOREAN_LIMIT_PASSWORD = "가".repeat(24);
    private static final Instant NOW = Instant.parse("2026-09-20T04:00:00Z");
    private static final String INVALID_REQUEST = "잘못된 요청입니다.";
    private static final String INVALID_STUDENT = "studentNumber는 필수이며 9자리 정수여야 합니다.";
    private static final String INVALID_PASSWORD = "password는 필수이며 공백이 아닌 문자열이어야 합니다.";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6");

    @LocalServerPort
    int port;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    SecretKey key;
    @Autowired
    JwtProperties properties;
    @MockitoBean
    Clock clock;
    @MockitoSpyBean
    StudentRepository students;
    private HttpClient client;

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfiguration {
        @Bean
        ProtectedProbe protectedProbe() {
            return new ProtectedProbe();
        }
    }

    // 실제 업무 API가 아닌, 보안 필터와 검증된 principal 확인용 테스트 전용 경로이다.
    @RestController
    static class ProtectedProbe {
        @RequestMapping(value = "/test/authenticated", method = {RequestMethod.GET, RequestMethod.POST})
        Map<String, Object> authenticated(@AuthenticationPrincipal Jwt jwt) {
            return Map.of("studentNumber", Integer.valueOf(jwt.getSubject()));
        }
    }

    @BeforeAll
    void prepareRealAccounts() {
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        Long departmentId = jdbc.queryForObject(
                "INSERT INTO department (name, department_code) VALUES (?, ?) RETURNING department_id",
                Long.class, "인증통합테스트학과", 10);
        jdbc.update("INSERT INTO student (student_number, name, grade, department_id, password_hash) VALUES (?, ?, ?, ?, ?)",
                STUDENT, "인증학생", 1, departmentId, passwordEncoder.encode(PASSWORD));
        jdbc.update("INSERT INTO student (student_number, name, grade, department_id, password_hash) VALUES (?, ?, ?, ?, ?)",
                OTHER_STUDENT, "공백학생", 2, departmentId, passwordEncoder.encode(SPACED_PASSWORD));
        jdbc.update("INSERT INTO student (student_number, name, grade, department_id, password_hash) VALUES (?, ?, ?, ?, ?)",
                ASCII_PASSWORD_STUDENT, "영문경계학생", 1, departmentId, passwordEncoder.encode(ASCII_LIMIT_PASSWORD));
        jdbc.update("INSERT INTO student (student_number, name, grade, department_id, password_hash) VALUES (?, ?, ?, ?, ?)",
                KOREAN_PASSWORD_STUDENT, "한글경계학생", 1, departmentId, passwordEncoder.encode(KOREAN_LIMIT_PASSWORD));
    }

    @BeforeEach
    void resetRequestState() {
        when(clock.instant()).thenReturn(NOW);
        clearInvocations(students);
    }

    @AfterAll
    void closeClient() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void logsInWithDatabasePasswordAndReturnsSignedBearerToken() throws Exception {
        HttpResponse<String> response = login(STUDENT, PASSWORD);
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = mapper.readTree(response.body());
        assertThat(body.size()).isEqualTo(3);
        assertThat(body.get("tokenType").stringValue()).isEqualTo("Bearer");
        assertThat(body.get("expiresIn").longValue()).isEqualTo(1800);
        SignedJWT token = SignedJWT.parse(body.get("accessToken").stringValue());
        assertThat(token.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.HS256);
        assertThat(token.verify(new MACVerifier(key))).isTrue();
        var claims = token.getJWTClaimsSet();
        assertThat(claims.getSubject()).isEqualTo(Integer.toString(STUDENT));
        assertThat(claims.getIssuer()).isEqualTo(properties.issuer());
        assertThat(claims.getAudience()).containsExactly(properties.audience());
        assertThat(claims.getIssueTime().toInstant()).isEqualTo(NOW);
        assertThat(claims.getExpirationTime().toInstant()).isEqualTo(NOW.plusSeconds(1800));
        assertThat(claims.getClaims()).containsOnlyKeys("sub", "iss", "aud", "iat", "exp");
        assertThat(response.headers().firstValue("Cache-Control").orElseThrow()).contains("no-store");
        assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
        assertThat(response.body()).doesNotContain("password", "passwordHash", PASSWORD);
        verify(students).findById(STUDENT);
    }

    @Test
    void usesTokenSubjectWithoutAnotherStudentLookupOrRequestOverride() throws Exception {
        String token = tokenFrom(login(STUDENT, PASSWORD));
        clearInvocations(students);
        HttpResponse<String> response = exchange("GET", "/test/authenticated?studentNumber=" + OTHER_STUDENT,
                null, "Authorization", "Bearer " + token);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(response.body()).get("studentNumber").intValue()).isEqualTo(STUDENT);
        verifyNoInteractions(students);
    }

    @Test
    void doesNotPersistAuthenticationBetweenRequests() throws Exception {
        String token = tokenFrom(login(STUDENT, PASSWORD));
        HttpResponse<String> authenticated = exchange("POST", "/test/authenticated", "{}",
                "Content-Type", "application/json", "Authorization", "Bearer " + token);
        assertThat(authenticated.statusCode()).isEqualTo(200);
        assertThat(authenticated.headers().allValues("Set-Cookie")).isEmpty();
        assertUnauthorized(exchange("GET", "/test/authenticated", null));
    }

    @Test
    void keepsPasswordWhitespaceAndDoesNotTrimIt() throws Exception {
        assertThat(login(OTHER_STUDENT, SPACED_PASSWORD).statusCode()).isEqualTo(200);
        assertError(login(OTHER_STUDENT, SPACED_PASSWORD.trim()), 401,
                "INVALID_CREDENTIALS", "학번 또는 비밀번호가 올바르지 않습니다.");
    }

    @ParameterizedTest
    @MethodSource("passwordByteLimitAccounts")
    void acceptsPasswordAtBcryptByteLimitButRejectsAppendedSuffix(int studentNumber, String password) throws Exception {
        assertThat(password.getBytes(StandardCharsets.UTF_8)).hasSize(72);
        String token = tokenFrom(login(studentNumber, password));
        assertThat(SignedJWT.parse(token).getJWTClaimsSet().getSubject()).isEqualTo(Integer.toString(studentNumber));
        verify(students).findById(studentNumber);

        clearInvocations(students);
        assertError(login(studentNumber, password + "x"), 401,
                "INVALID_CREDENTIALS", "학번 또는 비밀번호가 올바르지 않습니다.");
        verifyNoInteractions(students);
    }

    static Stream<Arguments> passwordByteLimitAccounts() {
        return Stream.of(
                Arguments.of(ASCII_PASSWORD_STUDENT, ASCII_LIMIT_PASSWORD),
                Arguments.of(KOREAN_PASSWORD_STUDENT, KOREAN_LIMIT_PASSWORD));
    }

    @Test
    void returnsSameFailureForUnknownStudentAndWrongPassword() throws Exception {
        HttpResponse<String> unknown = login(202610499, PASSWORD);
        HttpResponse<String> wrong = login(STUDENT, "wrong-password");
        assertError(unknown, 401, "INVALID_CREDENTIALS", "학번 또는 비밀번호가 올바르지 않습니다.");
        assertThat(wrong.statusCode()).isEqualTo(unknown.statusCode());
        assertThat(wrong.body()).isEqualTo(unknown.body());
        assertThat(wrong.body()).doesNotContain("accessToken", "passwordHash", "wrong-password");
    }

    @ParameterizedTest
    @MethodSource("invalidLoginBodies")
    void rejectsInvalidInputBeforeDatabaseAccess(String json, String message) throws Exception {
        HttpResponse<String> response = exchange("POST", "/auth/login", json, "Content-Type", "application/json");
        assertError(response, 400, "INVALID_PARAMETER", message);
        verifyNoInteractions(students);
    }

    static Stream<Arguments> invalidLoginBodies() {
        return Stream.of(
                Arguments.of("", INVALID_REQUEST),
                Arguments.of("null", INVALID_REQUEST),
                Arguments.of("[]", INVALID_REQUEST),
                Arguments.of("1", INVALID_REQUEST),
                Arguments.of("{}", INVALID_STUDENT),
                Arguments.of("{\"password\":\"x\"}", INVALID_STUDENT),
                Arguments.of("{\"studentNumber\":null,\"password\":\"x\"}", INVALID_STUDENT),
                Arguments.of("{\"studentNumber\":99999999,\"password\":\"x\"}", INVALID_STUDENT),
                Arguments.of("{\"studentNumber\":1000000000,\"password\":\"x\"}", INVALID_STUDENT),
                Arguments.of("{\"studentNumber\":-1,\"password\":\"\"}", INVALID_STUDENT),
                Arguments.of("{\"studentNumber\":\"202610100\",\"password\":\"x\"}", INVALID_REQUEST),
                Arguments.of("{\"studentNumber\":202610100.0,\"password\":\"x\"}", INVALID_REQUEST),
                Arguments.of("{\"studentNumber\":2.026101e8,\"password\":\"x\"}", INVALID_REQUEST),
                Arguments.of("{\"studentNumber\":999999999999999999999,\"password\":\"x\"}", INVALID_REQUEST),
                Arguments.of("{\"studentNumber\":true,\"password\":\"x\"}", INVALID_REQUEST),
                Arguments.of("{\"studentNumber\":{},\"password\":\"x\"}", INVALID_REQUEST),
                Arguments.of("{\"studentNumber\":[],\"password\":\"x\"}", INVALID_REQUEST),
                Arguments.of("{\"studentNumber\":202610100}", INVALID_PASSWORD),
                Arguments.of("{\"studentNumber\":202610100,\"password\":null}", INVALID_PASSWORD),
                Arguments.of("{\"studentNumber\":202610100,\"password\":\"\"}", INVALID_PASSWORD),
                Arguments.of("{\"studentNumber\":202610100,\"password\":\" \\t \"}", INVALID_PASSWORD),
                Arguments.of("{\"studentNumber\":202610100,\"password\":123}", INVALID_REQUEST),
                Arguments.of("{\"studentNumber\":202610100,\"password\":false}", INVALID_REQUEST),
                Arguments.of("{\"studentNumber\":202610100,\"password\":{}}", INVALID_REQUEST),
                Arguments.of("{\"studentNumber\":202610100,\"password\":[]}", INVALID_REQUEST),
                Arguments.of("{\"studentNumber\":202610100,\"password\":\"x\",\"extra\":true}", INVALID_REQUEST),
                Arguments.of("{\"studentNumber\":202610100,\"studentNumber\":202610101,\"password\":\"x\"}", INVALID_REQUEST),
                Arguments.of("{\"studentNumber\":202610100,\"password\":\"x\",\"password\":\"y\"}", INVALID_REQUEST),
                Arguments.of("{\"studentNumber\":202610100,\"password\":\"x\"} {}", INVALID_REQUEST),
                Arguments.of("{\"studentNumber\":202610100,\"password\":\"secret-input\"", INVALID_REQUEST),
                // JSON 타입 오류는 DTO 생성 전에 판정하므로 값 범위 오류보다 먼저 처리한다.
                Arguments.of("{\"studentNumber\":1,\"password\":123}", INVALID_REQUEST));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET /course-offerings", "GET /students", "GET /professors",
            "POST /enrollments", "DELETE /enrollments/1", "GET /me/timetable"})
    void protectsAllPlannedBusinessPathsBeforeInputHandling(String route) throws Exception {
        String[] parts = route.split(" ");
        assertUnauthorized(exchange(parts[0], parts[1], "{broken", "Content-Type", "application/json"));
        verifyNoInteractions(students);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bearer malformed", "Bearer ", "Basic dGVzdDp0ZXN0", "Bearer a b", "Bearer a, Bearer b"})
    void rejectsMalformedOrUnsupportedAuthorization(String header) throws Exception {
        assertUnauthorized(exchange("GET", "/test/authenticated", null, "Authorization", header));
    }

    @ParameterizedTest
    @ValueSource(strings = {"iss", "aud", "sub", "iat", "exp"})
    void rejectsMissingRequiredClaim(String missing) throws Exception {
        Map<String, Object> claims = validClaims();
        claims.remove(missing);
        assertUnauthorized(withToken(sign(claims)));
    }

    @ParameterizedTest
    @MethodSource("invalidClaims")
    void rejectsSignedButInvalidClaims(String name, Object value) throws Exception {
        Map<String, Object> claims = validClaims();
        claims.put(name, value);
        assertUnauthorized(withToken(sign(claims)));
    }

    static Stream<Arguments> invalidClaims() {
        return Stream.of(
                Arguments.of("iss", "other-issuer"),
                Arguments.of("aud", List.of("other-api")),
                Arguments.of("aud", List.of()),
                Arguments.of("sub", "20261010"),
                Arguments.of("sub", "000000001"),
                Arguments.of("sub", "not-a-student"),
                Arguments.of("sub", 202610100),
                Arguments.of("iat", NOW.plusSeconds(1).getEpochSecond()),
                Arguments.of("iat", "not-a-time"),
                Arguments.of("exp", NOW.minusSeconds(1).getEpochSecond()),
                Arguments.of("exp", NOW.plusSeconds(1801).getEpochSecond()),
                Arguments.of("exp", "not-a-time"),
                Arguments.of("nbf", NOW.plusSeconds(1).getEpochSecond()));
    }

    @Test
    void rejectsWrongSigningKeyAndAlgorithmAndUnsignedToken() throws Exception {
        byte[] wrongKey = key.getEncoded().clone();
        wrongKey[0] ^= 1;
        assertUnauthorized(withToken(sign(validClaims(), JWSAlgorithm.HS256, wrongKey)));
        assertUnauthorized(withToken(sign(validClaims(), JWSAlgorithm.HS512, new byte[64])));
        String unsigned = new com.nimbusds.jwt.PlainJWT(com.nimbusds.jwt.JWTClaimsSet.parse(validClaims())).serialize();
        assertUnauthorized(withToken(unsigned));
    }

    @Test
    void rejectsPayloadTampering() throws Exception {
        String original = sign(validClaims());
        Map<String, Object> altered = validClaims();
        altered.put("sub", Integer.toString(OTHER_STUDENT));
        String[] parts = original.split("\\.");
        String tampered = parts[0] + "." + new Payload(altered).toBase64URL() + "." + parts[2];
        assertUnauthorized(withToken(tampered));
    }

    @Test
    void rejectsExactlyAtExpiryWithoutClockSkew() throws Exception {
        String token = tokenFrom(login(STUDENT, PASSWORD));
        Instant expiry = NOW.plusSeconds(1800);
        when(clock.instant()).thenReturn(expiry.minusNanos(1));
        assertThat(withToken(token).statusCode()).isEqualTo(200);
        when(clock.instant()).thenReturn(expiry);
        assertUnauthorized(withToken(token));
        when(clock.instant()).thenReturn(expiry.plusSeconds(1));
        assertUnauthorized(withToken(token));
    }

    @Test
    void acceptsOptionalNotBeforeAtCurrentTime() throws Exception {
        Map<String, Object> claims = validClaims();
        claims.put("nbf", NOW.getEpochSecond());
        assertThat(withToken(sign(claims)).statusCode()).isEqualTo(200);
    }

    @Test
    void refusesTokensInQueryAndCookies() throws Exception {
        String token = sign(validClaims());
        assertUnauthorized(exchange("GET", "/test/authenticated?access_token=" + token, null));
        assertUnauthorized(exchange("GET", "/test/authenticated", null, "Cookie", "accessToken=" + token));
        assertUnauthorized(exchange("POST", "/test/authenticated", "access_token=" + token,
                "Content-Type", "application/x-www-form-urlencoded"));
    }

    @Test
    void publicEndpointsRemainUsableWithStaleAuthorization() throws Exception {
        assertThat(exchange("GET", "/health", null, "Authorization", "Bearer expired-token").statusCode()).isEqualTo(503);
        String body = mapper.writeValueAsString(new LoginRequest(STUDENT, PASSWORD));
        assertThat(exchange("POST", "/auth/login", body, "Content-Type", "application/json",
                "Authorization", "Bearer expired-token").statusCode()).isEqualTo(200);
    }

    @Test
    void missingEndpointStays404AfterSuccessfulAuthentication() throws Exception {
        HttpResponse<String> response = exchange("GET", "/not-an-endpoint", null,
                "Authorization", "Bearer " + sign(validClaims()));
        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void dtoStringRepresentationsDoNotExposeCredentialsOrTokens() {
        assertThat(new LoginRequest(STUDENT, PASSWORD).toString()).doesNotContain(PASSWORD);
        assertThat(new LoginResponse("sensitive-token", "Bearer", 1800).toString()).doesNotContain("sensitive-token");
    }

    private HttpResponse<String> login(int student, String password) throws Exception {
        return exchange("POST", "/auth/login", mapper.writeValueAsString(new LoginRequest(student, password)),
                "Content-Type", "application/json");
    }

    private String tokenFrom(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(200);
        return mapper.readTree(response.body()).get("accessToken").stringValue();
    }

    private HttpResponse<String> withToken(String token) throws Exception {
        return exchange("GET", "/test/authenticated", null, "Authorization", "Bearer " + token);
    }

    private HttpResponse<String> exchange(String method, String path, String body, String... headers) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        for (int index = 0; index < headers.length; index += 2) {
            request.header(headers[index], headers[index + 1]);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertUnauthorized(HttpResponse<String> response) {
        assertError(response, 401, "UNAUTHORIZED", "인증이 필요합니다.");
        assertThat(response.headers().firstValue("WWW-Authenticate")).contains("Bearer");
        assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
    }

    private void assertError(HttpResponse<String> response, int status, String code, String message) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        JsonNode error = mapper.readTree(response.body());
        assertThat(error.size()).isEqualTo(2);
        assertThat(error.get("code").stringValue()).isEqualTo(code);
        assertThat(error.get("message").stringValue()).isEqualTo(message);
        assertThat(response.body()).doesNotContain("secret-input", "accessToken", "passwordHash");
    }

    private Map<String, Object> validClaims() {
        return new HashMap<>(Map.of("sub", Integer.toString(STUDENT), "iss", properties.issuer(),
                "aud", List.of(properties.audience()), "iat", NOW.getEpochSecond(),
                "exp", NOW.plusSeconds(1800).getEpochSecond()));
    }

    private String sign(Map<String, Object> claims) throws Exception {
        return sign(claims, JWSAlgorithm.HS256, key.getEncoded());
    }

    private String sign(Map<String, Object> claims, JWSAlgorithm algorithm, byte[] signingKey) throws Exception {
        var token = new JWSObject(new JWSHeader(algorithm), new Payload(claims));
        token.sign(new MACSigner(signingKey));
        return token.serialize();
    }
}
