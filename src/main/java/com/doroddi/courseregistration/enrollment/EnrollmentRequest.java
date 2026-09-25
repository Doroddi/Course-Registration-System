package com.doroddi.courseregistration.enrollment;

@tools.jackson.databind.annotation.JsonDeserialize(using = EnrollmentRequestDeserializer.class)
public record EnrollmentRequest(Long courseOfferingId) {}
