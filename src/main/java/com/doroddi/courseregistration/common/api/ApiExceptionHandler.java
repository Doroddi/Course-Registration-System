package com.doroddi.courseregistration.common.api;

import com.doroddi.courseregistration.student.auth.InvalidCredentialsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(InvalidParameterException.class)
    public ResponseEntity<ApiError> handleInvalidParameter(InvalidParameterException exception) {
        ApiError error = new ApiError("INVALID_PARAMETER", exception.getMessage());
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials() {
        ApiError error = new ApiError("INVALID_CREDENTIALS", "학번 또는 비밀번호가 올바르지 않습니다.");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableMessage() {
        // 파싱 예외의 원문에는 입력 비밀번호가 포함될 수 있어 고정 메시지를 반환한다.
        ApiError error = new ApiError("INVALID_PARAMETER", "잘못된 요청입니다.");
        return ResponseEntity.badRequest().body(error);
    }
}
