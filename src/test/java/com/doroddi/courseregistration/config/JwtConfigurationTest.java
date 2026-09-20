package com.doroddi.courseregistration.config;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtConfigurationTest {
    @ParameterizedTest
    @ValueSource(ints = {32, 64})
    void bindsSettingsAndSignsWithConfiguredKey(int byteCount) {
        byte[] expectedKey = testKey(byteCount);
        Map<String, String> settings = validSettings(expectedKey);
        runner(settings).run(context -> {
            assertThat(context).hasNotFailed();
            JwtProperties properties = context.getBean(JwtProperties.class);
            assertThat(properties.issuer()).isEqualTo("jwt-config-test");
            assertThat(properties.audience()).isEqualTo("jwt-config-test-api");
            assertThat(properties.toString()).doesNotContain(settings.get("secret-base64"));
            assertThat(context.getBean(SecretKey.class).getEncoded()).containsExactly(expectedKey);

            Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
            JwtClaimsSet claims = JwtClaimsSet.builder()
                    .issuer(properties.issuer())
                    .audience(List.of(properties.audience()))
                    .subject("202010100")
                    .issuedAt(issuedAt)
                    .expiresAt(issuedAt.plusSeconds(1800))
                    .build();
            String token = context.getBean(JwtEncoder.class).encode(JwtEncoderParameters.from(
                    JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

            NimbusJwtDecoder decoder = decoder(expectedKey);
            Jwt decoded = decoder.decode(token);
            assertThat(decoded.getHeaders()).containsEntry("alg", "HS256");
            assertThat(decoded.getSubject()).isEqualTo("202010100");
            assertThat(decoded.getClaimAsString("iss")).isEqualTo(properties.issuer());
            assertThat(decoded.getAudience()).containsExactly(properties.audience());
            assertThat(decoded.getIssuedAt()).isEqualTo(issuedAt);
            assertThat(decoded.getExpiresAt()).isEqualTo(issuedAt.plusSeconds(1800));

            byte[] differentKey = expectedKey.clone();
            differentKey[0] ^= 1;
            assertThatThrownBy(() -> decoder(differentKey).decode(token)).isInstanceOf(JwtException.class);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"secret-base64", "issuer", "audience"})
    void rejectsMissingRequiredSetting(String setting) {
        Map<String, String> settings = validSettings(testKey(32));
        settings.remove(setting);
        runner(settings).run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @MethodSource("blankSettings")
    void rejectsBlankRequiredSetting(String setting, String value) {
        Map<String, String> settings = validSettings(testKey(32));
        settings.put(setting, value);
        runner(settings).run(context -> assertThat(context).hasFailed());
    }

    static Stream<Arguments> blankSettings() {
        return Stream.of("secret-base64", "issuer", "audience")
                .flatMap(setting -> Stream.of("", " ").map(value -> Arguments.of(setting, value)));
    }

    @Test
    void rejectsMalformedBase64WithoutEchoingTheSetting() {
        Map<String, String> settings = validSettings(testKey(32));
        String invalidValue = "not-a-base64-key!";
        settings.put("secret-base64", invalidValue);
        runner(settings).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseMessage("JWT 비밀키는 올바른 Base64 형식이어야 합니다.")
                    .hasStackTraceContaining("IllegalStateException");
            for (Throwable cause = context.getStartupFailure(); cause != null; cause = cause.getCause()) {
                assertThat(cause.getMessage()).doesNotContain(invalidValue);
            }
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 31})
    void rejectsDecodedKeyBelowMinimum(int byteCount) {
        runner(validSettings(testKey(byteCount))).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseMessage("JWT 비밀키는 최소 32바이트여야 합니다.");
        });
    }

    private ApplicationContextRunner runner(Map<String, String> settings) {
        String[] values = settings.entrySet().stream()
                .map(entry -> "app.jwt." + entry.getKey() + "=" + entry.getValue())
                .toArray(String[]::new);
        return new ApplicationContextRunner()
                .withUserConfiguration(JwtConfiguration.class)
                .withPropertyValues(values);
    }

    private Map<String, String> validSettings(byte[] key) {
        return new HashMap<>(Map.of(
                "secret-base64", Base64.getEncoder().encodeToString(key),
                "issuer", "jwt-config-test",
                "audience", "jwt-config-test-api"));
    }

    private NimbusJwtDecoder decoder(byte[] key) {
        return NimbusJwtDecoder.withSecretKey(new SecretKeySpec(key, "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    private byte[] testKey(int byteCount) {
        // 공개된 테스트 전용 값이며 개발·운영 키로 사용하지 않는다.
        byte[] bytes = new byte[byteCount];
        for (int index = 0; index < byteCount; index++) {
            bytes[index] = (byte) (index + 1);
        }
        return bytes;
    }
}
