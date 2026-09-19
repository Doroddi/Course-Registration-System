package com.doroddi.courseregistration.seed;

import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class SeedPassword {
    private final Environment environment;
    private final PasswordEncoder encoder;

    public SeedPassword(Environment environment, PasswordEncoder encoder) {
        this.environment = environment;
        this.encoder = encoder;
    }

    public String createHash() {
        String password = environment.getProperty("INITIAL_STUDENT_PASSWORD");
        if (password == null || password.isBlank()
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException(
                    "INITIAL_STUDENT_PASSWORD는 공백이 아닌 UTF-8 72바이트 이하의 값이어야 합니다.");
        }
        // 최초 개발 데이터의 공통 비밀번호만 한 번 인코딩한다. 실제 계정 변경 시에는 개별 인코딩한다.
        return encoder.encode(password);
    }
}
