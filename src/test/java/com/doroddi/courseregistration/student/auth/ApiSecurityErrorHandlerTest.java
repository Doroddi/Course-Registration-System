package com.doroddi.courseregistration.student.auth;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ApiSecurityErrorHandlerTest {
    private static final String RAW_TOKEN = "sensitive-header-token";
    private static final String RAW_CLAIM = "sensitive-claim-value";
    private static final String RAW_EXCEPTION = "sensitive-exception-message";
    private static final String CLIENT_REQUEST_ID = "client-controlled-request-id";
    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final ApiSecurityErrorHandler handler = new ApiSecurityErrorHandler(mapper);
    private final Logger logger = (Logger) LoggerFactory.getLogger(ApiSecurityErrorHandler.class);
    private ListAppender<ILoggingEvent> events;
    private MockHttpServletRequest request;

    @BeforeEach
    void captureFailureLogs() {
        events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        request = new MockHttpServletRequest("GET", "/test/authenticated");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + RAW_TOKEN);
        request.addHeader("X-Request-Id", CLIENT_REQUEST_ID);
        request.setQueryString("access_token=" + RAW_TOKEN);
        request.setContent(("{\"sub\":\"" + RAW_CLAIM + "\"}").getBytes(StandardCharsets.UTF_8));
    }

    @AfterEach
    void releaseLogCapture() {
        logger.detachAppender(events);
        events.stop();
    }

    @Test
    void reportsMissingAuthorizationWithOnlyTheBearerChallenge() throws Exception {
        request.removeHeader(HttpHeaders.AUTHORIZATION);
        var response = new MockHttpServletResponse();

        handler.commence(request, response, new InsufficientAuthenticationException(RAW_EXCEPTION));

        assertFailure(response, JwtFailureReason.TOKEN_MISSING, "TOKEN_REQUIRED", "로그인이 필요합니다.", "Bearer");
    }

    @Test
    void exposesVerifiedExpiryWithAnInvalidTokenChallenge() throws Exception {
        var response = new MockHttpServletResponse();

        handler.commence(request, response, wrappedFailure(JwtFailureReason.TOKEN_EXPIRED));

        assertFailure(response, JwtFailureReason.TOKEN_EXPIRED, "TOKEN_EXPIRED",
                "인증이 만료되었습니다. 다시 로그인해주세요.", "Bearer error=\"invalid_token\"");
    }

    @ParameterizedTest
    @EnumSource(value = JwtFailureReason.class, names = {"TOKEN_MISSING", "TOKEN_EXPIRED"},
            mode = EnumSource.Mode.EXCLUDE)
    void keepsOtherReasonsInternalWhileReturningTheSamePublicError(JwtFailureReason reason) throws Exception {
        var response = new MockHttpServletResponse();

        handler.commence(request, response, wrappedFailure(reason));

        assertFailure(response, reason, "INVALID_TOKEN", "유효하지 않은 인증 정보입니다.",
                "Bearer error=\"invalid_token\"");
    }

    @Test
    void identifiesUnsupportedAuthorizationAsMalformed() throws Exception {
        request.removeHeader(HttpHeaders.AUTHORIZATION);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic " + RAW_TOKEN);
        var response = new MockHttpServletResponse();

        handler.commence(request, response, new InsufficientAuthenticationException(RAW_EXCEPTION));

        assertFailure(response, JwtFailureReason.MALFORMED_AUTHORIZATION, "INVALID_TOKEN",
                "유효하지 않은 인증 정보입니다.", "Bearer error=\"invalid_token\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid_request", "invalid_token"})
    void identifiesBearerResolverFailureWithoutLoggingItsDescription(String errorCode) throws Exception {
        var response = new MockHttpServletResponse();
        var failure = new OAuth2AuthenticationException(new OAuth2Error(errorCode, RAW_EXCEPTION, null));

        handler.commence(request, response, failure);

        assertFailure(response, JwtFailureReason.MALFORMED_AUTHORIZATION, "INVALID_TOKEN",
                "유효하지 않은 인증 정보입니다.", "Bearer error=\"invalid_token\"");
    }

    @Test
    void handlesUnclassifiedBearerFailureWithoutTrustingTheExceptionMessage() throws Exception {
        var response = new MockHttpServletResponse();

        handler.commence(request, response, new InvalidBearerTokenException(RAW_EXCEPTION));

        assertFailure(response, JwtFailureReason.INVALID_TOKEN, "INVALID_TOKEN",
                "유효하지 않은 인증 정보입니다.", "Bearer error=\"invalid_token\"");
    }

    @Test
    void generatesANewServerRequestIdForEveryRejectedRequest() throws Exception {
        var first = new MockHttpServletResponse();
        var second = new MockHttpServletResponse();

        handler.commence(request, first, wrappedFailure(JwtFailureReason.INVALID_SIGNATURE));
        handler.commence(request, second, wrappedFailure(JwtFailureReason.INVALID_SIGNATURE));

        assertThat(events.list).hasSize(2);
        String firstId = first.getHeader("X-Request-Id");
        String secondId = second.getHeader("X-Request-Id");
        assertThat(UUID.fromString(firstId).toString()).isEqualTo(firstId);
        assertThat(UUID.fromString(secondId).toString()).isEqualTo(secondId);
        assertThat(firstId).isNotEqualTo(CLIENT_REQUEST_ID).isNotEqualTo(secondId);
        assertThat(secondId).isNotEqualTo(CLIENT_REQUEST_ID);
        assertThat(events.list.get(0).getFormattedMessage()).contains("requestId=" + firstId);
        assertThat(events.list.get(1).getFormattedMessage()).contains("requestId=" + secondId);
    }

    private InvalidBearerTokenException wrappedFailure(JwtFailureReason reason) {
        return new InvalidBearerTokenException(RAW_EXCEPTION, new JwtRejectedException(reason));
    }

    private void assertFailure(MockHttpServletResponse response, JwtFailureReason reason,
            String code, String message, String challenge) throws Exception {
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo(challenge);
        String requestId = response.getHeader("X-Request-Id");
        assertThat(UUID.fromString(requestId).toString()).isEqualTo(requestId);
        assertThat(requestId).isNotEqualTo(CLIENT_REQUEST_ID);
        JsonNode body = mapper.readTree(response.getContentAsString());
        assertThat(body.size()).isEqualTo(2);
        assertThat(body.get("code").stringValue()).isEqualTo(code);
        assertThat(body.get("message").stringValue()).isEqualTo(message);
        assertThat(response.getContentAsString())
                .doesNotContain(RAW_TOKEN, RAW_CLAIM, RAW_EXCEPTION, CLIENT_REQUEST_ID);

        assertThat(events.list).hasSize(1);
        ILoggingEvent event = events.list.getFirst();
        assertThat(event.getFormattedMessage())
                .isEqualTo("event=JWT_REJECTED reason=" + reason.name() + " requestId=" + requestId)
                .doesNotContain(RAW_TOKEN, RAW_CLAIM, RAW_EXCEPTION, CLIENT_REQUEST_ID);
        assertThat(event.getThrowableProxy()).isNull();
    }
}
