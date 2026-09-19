package com.doroddi.courseregistration.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.enrollment")
public record EnrollmentTermProperties(
        @Min(1000) @Max(9999) int academicYear,
        @Min(1) @Max(2) short term) {
}
