package com.doroddi.courseregistration.student.auth;

import com.doroddi.courseregistration.common.api.InvalidParameterException;
import org.springframework.stereotype.Component;

@Component
public class LoginRequestValidator {
    public void validate(LoginRequest request) {
        if (request == null) {
            throw new InvalidParameterException("잘못된 요청입니다.");
        }

        // D53: 여러 입력이 잘못되어도 학번 오류를 먼저 반환한다.
        Integer studentNumber = request.studentNumber();
        if (studentNumber == null
                || studentNumber < 100_000_000
                || studentNumber > 999_999_999) {
            throw new InvalidParameterException("studentNumber는 필수이며 9자리 정수여야 합니다.");
        }

        String password = request.password();
        if (password == null || password.isBlank()) {
            throw new InvalidParameterException("password는 필수이며 공백이 아닌 문자열이어야 합니다.");
        }
    }
}
