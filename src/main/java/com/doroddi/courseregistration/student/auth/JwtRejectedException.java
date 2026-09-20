package com.doroddi.courseregistration.student.auth;

import java.util.Objects;
import org.springframework.security.oauth2.jwt.BadJwtException;

public final class JwtRejectedException extends BadJwtException {
    private final JwtFailureReason reason;

    public JwtRejectedException(JwtFailureReason reason) {
        // 원본 토큰이나 라이브러리 예외 메시지를 인증 실패 경로로 전달하지 않는다.
        super("JWT 검증에 실패했습니다.");
        this.reason = Objects.requireNonNull(reason);
    }

    public JwtFailureReason reason() {
        return reason;
    }
}
