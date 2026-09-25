package com.doroddi.courseregistration.enrollment;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class EnrollmentRejectedException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public EnrollmentRejectedException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }
}
