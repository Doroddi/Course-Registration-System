package com.doroddi.courseregistration.student.auth;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.doroddi.courseregistration.config.JwtConfiguration;
import com.doroddi.courseregistration.config.JwtProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtFailureDiagnosticsTest {
    // 공개 테스트 키이며 개발·운영 키로 사용하지 않는다.
    private static final String TEST_KEY_BASE64 = "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA=";
    private static final JwtProperties PROPERTIES = new JwtProperties(
            TEST_KEY_BASE64, "jwt-diagnostics-test", "jwt-diagnostics-test-api");
    private static final Instant NOW = Instant.parse("2026-09-20T04:00:00Z");
    private static final byte[] KEY = Base64.getDecoder().decode(TEST_KEY_BASE64);
    private final JwtConfiguration configuration = new JwtConfiguration();
    private final JwtDecoder decoder = configuration.jwtDecoder(configuration.jwtSecretKey(PROPERTIES),
            PROPERTIES, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void acceptsSignedTokenWhenEveryConditionPasses() throws Exception {
        assertThat(decoder.decode(sign(validClaims())).getSubject()).isEqualTo("202610100");
    }

    @Test
    void acceptsNotBeforeExactlyAtTheCurrentTime() throws Exception {
        Map<String, Object> claims = validClaims();
        claims.put("nbf", NOW.getEpochSecond());
        assertThat(decoder.decode(sign(claims)).getSubject()).isEqualTo("202610100");
    }

    @ParameterizedTest
    @ValueSource(strings = {"sub", "iss", "aud", "iat", "exp"})
    void identifiesMissingRequiredClaim(String name) throws Exception {
        Map<String, Object> claims = validClaims();
        claims.remove(name);
        assertReason(sign(claims), JwtFailureReason.MISSING_CLAIM);
    }

    @ParameterizedTest
    @MethodSource("invalidClaims")
    void identifiesTheClaimFailure(String name, Object value, JwtFailureReason reason) throws Exception {
        Map<String, Object> claims = validClaims();
        claims.put(name, value);
        assertReason(sign(claims), reason);
    }

    static Stream<Arguments> invalidClaims() {
        return Stream.of(
                Arguments.of("sub", "20261010", JwtFailureReason.INVALID_SUBJECT),
                Arguments.of("sub", "000000001", JwtFailureReason.INVALID_SUBJECT),
                Arguments.of("sub", 202610100, JwtFailureReason.INVALID_SUBJECT),
                Arguments.of("iss", "other-issuer", JwtFailureReason.ISSUER_MISMATCH),
                Arguments.of("aud", List.of("other-api"), JwtFailureReason.AUDIENCE_MISMATCH),
                Arguments.of("aud", List.of(), JwtFailureReason.AUDIENCE_MISMATCH),
                Arguments.of("iat", NOW.plusSeconds(1).getEpochSecond(), JwtFailureReason.ISSUED_AT_IN_FUTURE),
                Arguments.of("exp", NOW.plusSeconds(1801).getEpochSecond(), JwtFailureReason.INVALID_LIFETIME),
                Arguments.of("nbf", NOW.plusSeconds(1).getEpochSecond(), JwtFailureReason.NOT_YET_VALID),
                Arguments.of("iat", "not-a-time", JwtFailureReason.MALFORMED_TOKEN),
                Arguments.of("exp", "not-a-time", JwtFailureReason.MALFORMED_TOKEN));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1})
    void reportsExpiryAtTheBoundaryAndAfterwards(long secondsAfterExpiry) throws Exception {
        Map<String, Object> claims = validClaims();
        claims.put("iat", NOW.minusSeconds(1800 + secondsAfterExpiry).getEpochSecond());
        claims.put("exp", NOW.minusSeconds(secondsAfterExpiry).getEpochSecond());
        assertReason(sign(claims), JwtFailureReason.TOKEN_EXPIRED);
    }

    @Test
    void acceptsTokenImmediatelyBeforeExpiry() throws Exception {
        JwtDecoder beforeExpiry = configuration.jwtDecoder(configuration.jwtSecretKey(PROPERTIES),
                PROPERTIES, Clock.fixed(NOW.minusNanos(1), ZoneOffset.UTC));
        assertThat(beforeExpiry.decode(sign(expiredClaims())).getSubject()).isEqualTo("202610100");
    }

    @ParameterizedTest
    @MethodSource("expiredClaimsWithAnotherFailure")
    void reportsTheOtherFailureInsteadOfExpiry(String name, Object value, JwtFailureReason reason) throws Exception {
        Map<String, Object> claims = expiredClaims();
        claims.put(name, value);
        assertReason(sign(claims), reason);
    }

    static Stream<Arguments> expiredClaimsWithAnotherFailure() {
        return Stream.of(
                Arguments.of("iss", "other-issuer", JwtFailureReason.ISSUER_MISMATCH),
                Arguments.of("sub", 202610100, JwtFailureReason.INVALID_SUBJECT),
                Arguments.of("iat", NOW.minusSeconds(1801).getEpochSecond(), JwtFailureReason.INVALID_LIFETIME));
    }

    @Test
    void identifiesBadSignatureBeforeTrustingExpiredClaims() throws Exception {
        byte[] wrongKey = KEY.clone();
        wrongKey[0] ^= 1;
        assertReason(sign(expiredClaims(), JWSAlgorithm.HS256, wrongKey), JwtFailureReason.INVALID_SIGNATURE);
    }

    @Test
    void identifiesPayloadTamperingAsAnInvalidSignature() throws Exception {
        String original = sign(validClaims());
        Map<String, Object> altered = validClaims();
        altered.put("sub", "202610101");
        String[] parts = original.split("\\.");
        String tampered = parts[0] + "." + new Payload(altered).toBase64URL() + "." + parts[2];
        assertReason(tampered, JwtFailureReason.INVALID_SIGNATURE);
    }

    @Test
    void identifiesUnsupportedAlgorithm() throws Exception {
        assertReason(sign(validClaims(), JWSAlgorithm.HS512, new byte[64]),
                JwtFailureReason.UNSUPPORTED_ALGORITHM);
    }

    @Test
    void rejectsUnsignedToken() throws Exception {
        String unsigned = new PlainJWT(JWTClaimsSet.parse(validClaims())).serialize();
        assertReason(unsigned, JwtFailureReason.UNSUPPORTED_ALGORITHM);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-jwt", "a.b.c", ""})
    void identifiesMalformedSerialization(String token) {
        assertReason(token, JwtFailureReason.MALFORMED_TOKEN);
    }

    private void assertReason(String token, JwtFailureReason expected) {
        JwtRejectedException failure = assertThrows(JwtRejectedException.class, () -> decoder.decode(token));
        assertThat(failure.reason()).isEqualTo(expected);
        if (!token.isEmpty()) {
            assertThat(failure.getMessage()).doesNotContain(token);
        }
    }

    private Map<String, Object> validClaims() {
        return new HashMap<>(Map.of("sub", "202610100", "iss", PROPERTIES.issuer(),
                "aud", List.of(PROPERTIES.audience()), "iat", NOW.getEpochSecond(),
                "exp", NOW.plusSeconds(1800).getEpochSecond()));
    }

    private Map<String, Object> expiredClaims() {
        Map<String, Object> claims = validClaims();
        claims.put("iat", NOW.minusSeconds(1800).getEpochSecond());
        claims.put("exp", NOW.getEpochSecond());
        return claims;
    }

    private String sign(Map<String, Object> claims) throws Exception {
        return sign(claims, JWSAlgorithm.HS256, KEY);
    }

    private String sign(Map<String, Object> claims, JWSAlgorithm algorithm, byte[] signingKey) throws Exception {
        var token = new JWSObject(new JWSHeader(algorithm), new Payload(claims));
        token.sign(new MACSigner(signingKey));
        return token.serialize();
    }
}
