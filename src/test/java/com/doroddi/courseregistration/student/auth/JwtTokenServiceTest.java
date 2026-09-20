package com.doroddi.courseregistration.student.auth;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;

import com.doroddi.courseregistration.config.JwtConfiguration;
import com.doroddi.courseregistration.config.JwtProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncodingException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtTokenServiceTest {
    // 공개 테스트 키이며 개발·운영 키로 사용하지 않는다.
    private static final String TEST_KEY_BASE64 = "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA=";
    private static final JwtProperties PROPERTIES = new JwtProperties(
            TEST_KEY_BASE64, "jwt-token-test", "jwt-token-test-api");
    private static final Instant NOW = Instant.parse("2026-09-20T04:05:06.987654321Z");
    private static final Instant ISSUED_AT = Instant.parse("2026-09-20T04:05:06Z");
    private final JwtConfiguration configuration = new JwtConfiguration();
    private final JwtEncoder encoder = configuration.jwtEncoder(configuration.jwtSecretKey(PROPERTIES));

    @Test
    void issuesSignedTokenContainingOnlyRequiredClaims() throws Exception {
        var service = service(Clock.fixed(NOW, ZoneOffset.UTC));

        SignedJWT token = SignedJWT.parse(service.issue(202010100));

        assertThat(token.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.HS256);
        assertThat(token.verify(new MACVerifier(Base64.getDecoder().decode(TEST_KEY_BASE64)))).isTrue();
        JWTClaimsSet claims = token.getJWTClaimsSet();
        assertThat(claims.getClaims()).containsOnlyKeys("sub", "iss", "aud", "iat", "exp");
        assertThat(claims.getSubject()).isEqualTo("202010100");
        assertThat(claims.getIssuer()).isEqualTo(PROPERTIES.issuer());
        assertThat(claims.getAudience()).containsExactly(PROPERTIES.audience());
        assertThat(claims.getIssueTime().toInstant()).isEqualTo(ISSUED_AT);
        assertThat(claims.getExpirationTime().toInstant()).isEqualTo(ISSUED_AT.plusSeconds(1800));
        // 실제 직렬화된 iat·exp도 문자열이 아닌 Unix 초 단위 숫자여야 한다.
        assertThat(token.getPayload().toJSONObject())
                .containsEntry("iat", ISSUED_AT.getEpochSecond())
                .containsEntry("exp", ISSUED_AT.plusSeconds(1800).getEpochSecond());
    }

    @Test
    void doesNotShiftInstantForKoreanTimeZone() throws Exception {
        JWTClaimsSet utc = claims(service(Clock.fixed(NOW, ZoneOffset.UTC)).issue(202010100));
        JWTClaimsSet kst = claims(service(Clock.fixed(NOW, ZoneId.of("Asia/Seoul"))).issue(202010100));

        assertThat(kst.getIssueTime()).isEqualTo(utc.getIssueTime());
        assertThat(kst.getExpirationTime()).isEqualTo(utc.getExpirationTime());
        assertThat(kst.getIssueTime().toInstant()).isEqualTo(ISSUED_AT);
    }

    @Test
    void usesNewIssueTimeAndStudentForEachCall() throws Exception {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(NOW, NOW.plusSeconds(90));
        var service = service(clock);

        JWTClaimsSet first = claims(service.issue(202010100));
        JWTClaimsSet second = claims(service.issue(202620499));

        assertThat(first.getSubject()).isEqualTo("202010100");
        assertThat(second.getSubject()).isEqualTo("202620499");
        assertThat(first.getIssueTime().toInstant()).isEqualTo(ISSUED_AT);
        assertThat(second.getIssueTime().toInstant()).isEqualTo(ISSUED_AT.plusSeconds(90));
        assertThat(second.getExpirationTime().toInstant()).isEqualTo(ISSUED_AT.plusSeconds(1890));
    }

    @Test
    void propagatesSigningFailure() {
        JwtEncoder failingEncoder = mock(JwtEncoder.class);
        var failure = new JwtEncodingException("test signing failure");
        when(failingEncoder.encode(any())).thenThrow(failure);
        var service = new JwtTokenService(failingEncoder, PROPERTIES, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.issue(202010100)).isSameAs(failure);
    }

    private JwtTokenService service(Clock clock) {
        return new JwtTokenService(encoder, PROPERTIES, clock);
    }

    private JWTClaimsSet claims(String token) throws Exception {
        return SignedJWT.parse(token).getJWTClaimsSet();
    }
}
