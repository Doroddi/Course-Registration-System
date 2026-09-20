package com.doroddi.courseregistration.student.auth;

public record LoginResponse (
    String accessToken,
    String tokenType,
    long expiresIn
){
    @Override
    public String toString() {
        return "LoginResponse[REDACTED]";
    }
}
