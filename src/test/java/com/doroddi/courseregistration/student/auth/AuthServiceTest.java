package com.doroddi.courseregistration.student.auth;

import com.doroddi.courseregistration.department.Department;
import com.doroddi.courseregistration.seed.PasswordConfiguration;
import com.doroddi.courseregistration.student.Student;
import com.doroddi.courseregistration.student.StudentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private static final Integer STUDENT_NUMBER = 202010100;
    private static final String PASSWORD = "auth-service-test-only";
    private static final String FAILURE_MESSAGE = "학번 또는 비밀번호가 올바르지 않습니다.";
    // 조회 결과만 대체하고 실제 서비스와 같은 인코더로 bcrypt 대조를 검증한다.
    private static final PasswordEncoder ENCODER = new PasswordConfiguration().passwordEncoder();
    private static final String PASSWORD_HASH = ENCODER.encode(PASSWORD);
    private static final String SPACED_PASSWORD_HASH = ENCODER.encode("  " + PASSWORD + "  ");

    private final StudentRepository students = mock(StudentRepository.class);
    private final AuthService service = new AuthService(students, ENCODER);

    @Test
    void returnsStudentNumberForCorrectPassword() {
        givenStudent(PASSWORD_HASH);

        assertEquals(STUDENT_NUMBER, service.authenticate(STUDENT_NUMBER, PASSWORD));
    }

    @Test
    void rejectsUnknownStudentWithCommonFailureMessage() {
        when(students.findById(STUDENT_NUMBER)).thenReturn(Optional.empty());

        var failure = assertThrows(InvalidCredentialsException.class,
                () -> service.authenticate(STUDENT_NUMBER, PASSWORD));

        assertEquals(FAILURE_MESSAGE, failure.getMessage());
    }

    @Test
    void rejectsWrongPasswordWithSameFailureMessage() {
        givenStudent(PASSWORD_HASH);

        var failure = assertThrows(InvalidCredentialsException.class,
                () -> service.authenticate(STUDENT_NUMBER, "wrong-password"));

        assertEquals(FAILURE_MESSAGE, failure.getMessage());
    }

    @Test
    void acceptsOriginalPasswordIncludingSurroundingSpaces() {
        givenStudent(SPACED_PASSWORD_HASH);

        assertEquals(STUDENT_NUMBER, service.authenticate(STUDENT_NUMBER, "  " + PASSWORD + "  "));
    }

    @Test
    void rejectsTrimmedVersionOfPasswordContainingSpaces() {
        givenStudent(SPACED_PASSWORD_HASH);

        var failure = assertThrows(InvalidCredentialsException.class,
                () -> service.authenticate(STUDENT_NUMBER, PASSWORD));

        assertEquals(FAILURE_MESSAGE, failure.getMessage());
    }

    @ParameterizedTest
    @MethodSource("passwordsAtBcryptByteLimit")
    void acceptsPasswordAtBcryptByteLimit(String password) {
        givenStudent(ENCODER.encode(password));

        assertEquals(STUDENT_NUMBER, service.authenticate(STUDENT_NUMBER, password));
    }

    @ParameterizedTest
    @MethodSource("passwordsAtBcryptByteLimit")
    void rejectsSuffixBeyondBcryptByteLimitBeforeDatabaseOrEncoderAccess(String password) {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        var guardedService = new AuthService(students, encoder);

        var failure = assertThrows(InvalidCredentialsException.class,
                () -> guardedService.authenticate(STUDENT_NUMBER, password + "x"));

        assertEquals(FAILURE_MESSAGE, failure.getMessage());
        verifyNoInteractions(students, encoder);
    }

    private static Stream<String> passwordsAtBcryptByteLimit() {
        return Stream.of("a".repeat(72), "가".repeat(24));
    }

    private void givenStudent(String passwordHash) {
        var student = new Student(STUDENT_NUMBER, "김민준", (short) 1,
                new Department("컴퓨터공학부", (short) 10), passwordHash);
        when(students.findById(STUDENT_NUMBER)).thenReturn(Optional.of(student));
    }
}
