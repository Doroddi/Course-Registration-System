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
    private static final OAuth2Error INVALID_TOKEN = new OAuth2Error("invalid_token", "유효하지 않은 토큰입니다.", null);
    private final JwtProperties properties;
    private final Clock clock;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        Instant now = clock.instant();
        Instant issuedAt = token.getIssuedAt();
        Instant expiresAt = token.getExpiresAt();
        Instant notBefore = token.getNotBefore();
        String subject = token.getSubject();
        List<String> audience = token.getAudience();
        // 이 서버가 발급하는 필수 클레임과 30분 유효기간을 함께 검사한다.
        boolean valid = subject != null && subject.matches("[1-9][0-9]{8}")
                && properties.issuer().equals(token.getClaimAsString("iss"))
                && audience != null && audience.contains(properties.audience())
                && issuedAt != null && expiresAt != null
                && !issuedAt.isAfter(now)
                && Duration.between(issuedAt, expiresAt).equals(JwtTokenService.TOKEN_LIFETIME)
                // 기본 시계 오차 허용 없이 만료 시각과 같아지는 순간부터 거절한다.
                && now.isBefore(expiresAt)
                && (notBefore == null || !notBefore.isAfter(now));
        return valid ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
    }
}
