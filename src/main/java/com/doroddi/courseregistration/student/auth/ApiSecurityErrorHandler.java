package com.doroddi.courseregistration.student.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.doroddi.courseregistration.common.api.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        JwtFailureReason reason = failureReason(request, exception);
        // 클라이언트가 보낸 ID를 신뢰하지 않고 이 실패 응답과 로그를 연결할 ID를 생성한다.
        String requestId = UUID.randomUUID().toString();
        response.setHeader("X-Request-Id", requestId);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, reason == JwtFailureReason.TOKEN_MISSING
                ? "Bearer" : "Bearer error=\"invalid_token\"");
        // 토큰·헤더·클레임 값이나 라이브러리 예외 원문을 로그 인자로 넘기지 않는다.
        log.warn("event=JWT_REJECTED reason={} requestId={}", reason, requestId);

        ApiError error = switch (reason) {
            case TOKEN_MISSING -> new ApiError("TOKEN_REQUIRED", "로그인이 필요합니다.");
            case TOKEN_EXPIRED -> new ApiError("TOKEN_EXPIRED", "인증이 만료되었습니다. 다시 로그인해주세요.");
            default -> new ApiError("INVALID_TOKEN", "유효하지 않은 인증 정보입니다.");
        };
        write(response, HttpServletResponse.SC_UNAUTHORIZED, error.code(), error.message());
    }

    private JwtFailureReason failureReason(HttpServletRequest request, AuthenticationException exception) {
        // Spring Security가 decoder의 BadJwtException을 인증 예외로 감싸므로 원인에서 찾는다.
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof JwtRejectedException rejected) {
                return rejected.reason();
            }
        }
        if (exception instanceof OAuth2AuthenticationException oauth) {
            // resolver는 일반 OAuth2 예외, JWT 인증 provider는 InvalidBearerTokenException을 사용한다.
            String errorCode = oauth.getError().getErrorCode();
            boolean malformedHeader = !(oauth instanceof InvalidBearerTokenException)
                    && ("invalid_request".equals(errorCode) || "invalid_token".equals(errorCode));
            return malformedHeader ? JwtFailureReason.MALFORMED_AUTHORIZATION : JwtFailureReason.INVALID_TOKEN;
        }
        return request.getHeader(HttpHeaders.AUTHORIZATION) == null
                ? JwtFailureReason.TOKEN_MISSING : JwtFailureReason.MALFORMED_AUTHORIZATION;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException exception) throws IOException {
        write(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다.");
    }

    private void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        // 보안 필터의 예외는 MVC Advice를 거치지 않으므로 같은 오류 DTO를 직접 반환한다.
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), new ApiError(code, message));
    }
}
