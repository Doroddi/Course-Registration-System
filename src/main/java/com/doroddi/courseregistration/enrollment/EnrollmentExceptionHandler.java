package com.doroddi.courseregistration.enrollment;

import com.doroddi.courseregistration.common.api.ApiError;
import jakarta.persistence.PersistenceException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

@RestControllerAdvice(assignableTypes = EnrollmentController.class)
public class EnrollmentExceptionHandler {
    @ExceptionHandler(EnrollmentRejectedException.class)
    public ResponseEntity<ApiError> rejected(EnrollmentRejectedException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(new ApiError(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler({DataAccessException.class, PersistenceException.class, TransactionSystemException.class})
    public ResponseEntity<ApiError> databaseFailure(RuntimeException exception) {
        // DB 접근 계층 밖에서 변환한다. 서비스 트랜잭션은 이미 실패·롤백된 상태이다.
        if (!isLockFailure(exception)) throw exception;
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("ENROLLMENT_TEMPORARILY_UNAVAILABLE",
                        "일시적으로 요청을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요."));
    }

    static boolean isLockFailure(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof SQLException sql
                    && ("55P03".equals(sql.getSQLState()) || "40P01".equals(sql.getSQLState()))) return true;
        }
        // 무결성 위반·접속 실패 등 다른 장애를 잠금 대기 실패로 숨기지 않는다.
        return false;
    }
}
