package com.doroddi.courseregistration.student.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import com.doroddi.courseregistration.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

@RequiredArgsConstructor
public class JwtClaimsValidator implements OAuth2TokenValidator<Jwt> {
    private static final List<String> REQUIRED_CLAIMS = List.of("sub", "iss", "aud", "iat", "exp");
    private final JwtProperties properties;
    private final Clock clock;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (REQUIRED_CLAIMS.stream().anyMatch(name -> token.getClaim(name) == null)) {
            return failure(JwtFailureReason.MISSING_CLAIM);
        }

        Instant now = clock.instant();
        Instant issuedAt = token.getIssuedAt();
        Instant expiresAt = token.getExpiresAt();
        Instant notBefore = token.getNotBefore();
        String subject = token.getSubject();
        List<String> audience = token.getAudience();
        if (subject == null || !subject.matches("[1-9][0-9]{8}")) {
            return failure(JwtFailureReason.INVALID_SUBJECT);
        }
        if (!properties.issuer().equals(token.getClaimAsString("iss"))) {
            return failure(JwtFailureReason.ISSUER_MISMATCH);
        }
        if (audience == null || !audience.contains(properties.audience())) {
            return failure(JwtFailureReason.AUDIENCE_MISMATCH);
        }
        if (issuedAt == null || expiresAt == null) {
            return failure(JwtFailureReason.MISSING_CLAIM);
        }
        if (issuedAt.isAfter(now)) {
            return failure(JwtFailureReason.ISSUED_AT_IN_FUTURE);
        }
        if (!Duration.between(issuedAt, expiresAt).equals(JwtTokenService.TOKEN_LIFETIME)) {
            return failure(JwtFailureReason.INVALID_LIFETIME);
        }
        if (notBefore != null && notBefore.isAfter(now)) {
            return failure(JwtFailureReason.NOT_YET_VALID);
        }
        // 다른 조건을 모두 통과한 토큰만 만료로 분류하며 시계 오차를 허용하지 않는다.
        if (!now.isBefore(expiresAt)) {
            return failure(JwtFailureReason.TOKEN_EXPIRED);
        }
        return OAuth2TokenValidatorResult.success();
    }

    private OAuth2TokenValidatorResult failure(JwtFailureReason reason) {
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error(reason.name(), "JWT 검증에 실패했습니다.", null));
    }
}
