package com.doroddi.courseregistration.student.auth;

@tools.jackson.databind.annotation.JsonDeserialize(using = LoginRequestDeserializer.class)
public record LoginRequest(Integer studentNumber, String password) {
    @Override
    public String toString() {
        return "LoginRequest[REDACTED]";
    }
}
