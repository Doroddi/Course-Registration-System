package com.doroddi.courseregistration.student.auth;

import java.nio.charset.StandardCharsets;

import com.doroddi.courseregistration.student.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;

    public Integer authenticate(Integer studentNumber, String password) {
        // bcrypt가 72바이트 이후의 접미사를 무시하여 다른 비밀번호를 인증하지 않게 한다.
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            throw new InvalidCredentialsException();
        }

        var student = studentRepository.findById(studentNumber).orElseThrow(
            InvalidCredentialsException::new
        );

        if(passwordEncoder.matches(password, student.getPasswordHash())) {
            return student.getStudentNumber();
        }

        throw new InvalidCredentialsException();
    }
}
