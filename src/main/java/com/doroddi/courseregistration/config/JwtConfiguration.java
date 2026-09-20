package com.doroddi.courseregistration.config;

import java.time.Clock;
import java.text.ParseException;
import com.nimbusds.jwt.SignedJWT;
import java.util.Base64;
import com.doroddi.courseregistration.student.auth.JwtClaimsValidator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfiguration {
    @Bean
    public Clock jwtClock() {
        return Clock.systemUTC();
    }

    @Bean
    public SecretKey jwtSecretKey(JwtProperties properties) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(properties.secretBase64());
        } catch (IllegalArgumentException exception) {
            // 설정 원문을 예외 메시지나 원인에 포함하지 않는다.
            throw new IllegalStateException("JWT 비밀키는 올바른 Base64 형식이어야 합니다.");
        }

        // HS256의 최소 키 길이는 Base64 문자열 길이가 아닌 원본 바이트 기준이다.
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT 비밀키는 최소 32바이트여야 합니다.");
        }
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return NimbusJwtEncoder.withSecretKey(jwtSecretKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey, JwtProperties properties, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new JwtClaimsValidator(properties, clock));
        return token -> {
            var decoded = decoder.decode(token);
            // Nimbus도 숫자 sub를 문자열로 바꾼다. 서명 검증 후 원래 JSON 타입을 확인한다.
            try {
                var originalClaims = SignedJWT.parse(token).getPayload().toJSONObject();
                if (originalClaims == null || !(originalClaims.get("sub") instanceof String)) {
                    throw new BadJwtException("유효하지 않은 토큰입니다.");
                }
            } catch (ParseException exception) {
                throw new BadJwtException("유효하지 않은 토큰입니다.");
            }
            return decoded;
        };
    }
}
