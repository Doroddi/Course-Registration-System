package com.doroddi.courseregistration.config;

import java.text.ParseException;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import com.doroddi.courseregistration.student.auth.JwtClaimsValidator;
import com.doroddi.courseregistration.student.auth.JwtFailureReason;
import com.doroddi.courseregistration.student.auth.JwtRejectedException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.proc.BadJWSException;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfiguration {
    private static final List<String> REQUIRED_CLAIMS = List.of("sub", "iss", "aud", "iat", "exp");

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
            SignedJWT parsed = parseSignedToken(token);
            try {
                var decoded = decoder.decode(token);
                validateOriginalClaims(parsed);
                return decoded;
            } catch (JwtValidationException exception) {
                // 이 예외는 Nimbus의 서명 검증 이후에만 발생한다. 숫자 sub와 만료가
                // 함께 있는 토큰도 잘못된 sub를 먼저 거절하여 단순 만료로 분류하지 않는다.
                validateOriginalClaims(parsed);
                throw new JwtRejectedException(validationReason(exception));
            } catch (JwtRejectedException exception) {
                throw exception;
            } catch (JwtException exception) {
                throw new JwtRejectedException(decodingReason(exception));
            }
        };
    }

    private SignedJWT parseSignedToken(String token) {
        if (token == null || token.isBlank()) {
            throw new JwtRejectedException(JwtFailureReason.MALFORMED_TOKEN);
        }
        try {
            var parsed = JWTParser.parse(token);
            if (!JWSAlgorithm.HS256.equals(parsed.getHeader().getAlgorithm())) {
                throw new JwtRejectedException(JwtFailureReason.UNSUPPORTED_ALGORITHM);
            }
            if (!(parsed instanceof SignedJWT signed)) {
                throw new JwtRejectedException(JwtFailureReason.MALFORMED_TOKEN);
            }
            return signed;
        } catch (ParseException exception) {
            throw new JwtRejectedException(JwtFailureReason.MALFORMED_TOKEN);
        }
    }

    private void validateOriginalClaims(SignedJWT token) {
        var claims = token.getPayload().toJSONObject();
        if (claims == null) {
            throw new JwtRejectedException(JwtFailureReason.MALFORMED_TOKEN);
        }
        // 기본 변환기가 빠진 iat를 exp - 1초로 채우므로 서명 검증 후 원본의 누락을 확인한다.
        if (REQUIRED_CLAIMS.stream().anyMatch(name -> claims.get(name) == null)) {
            throw new JwtRejectedException(JwtFailureReason.MISSING_CLAIM);
        }
        // Nimbus가 숫자 sub를 문자열로 변환하므로 원래 JSON 타입도 확인한다.
        Object subject = claims.get("sub");
        if (!(subject instanceof String)) {
            throw new JwtRejectedException(JwtFailureReason.INVALID_SUBJECT);
        }
    }

    private JwtFailureReason validationReason(JwtValidationException exception) {
        // 검증기가 생성한 고정 오류 코드만 사용하며 라이브러리 메시지는 해석하지 않는다.
        return exception.getErrors().stream().findFirst().map(error -> {
            try {
                return JwtFailureReason.valueOf(error.getErrorCode());
            } catch (IllegalArgumentException unknownCode) {
                return JwtFailureReason.INVALID_TOKEN;
            }
        }).orElse(JwtFailureReason.INVALID_TOKEN);
    }

    private JwtFailureReason decodingReason(JwtException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof BadJWSException) {
                return JwtFailureReason.INVALID_SIGNATURE;
            }
            if (cause instanceof ParseException) {
                return JwtFailureReason.MALFORMED_TOKEN;
            }
        }
        return JwtFailureReason.INVALID_TOKEN;
    }
}
