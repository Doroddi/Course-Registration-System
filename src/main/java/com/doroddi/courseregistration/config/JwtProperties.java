package com.doroddi.courseregistration.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.jwt")
public record JwtProperties (
    @NotBlank String secretBase64,
    @NotBlank String issuer,
    @NotBlank String audience
) {
    @Override
    public String toString() {
        return "JwtProperties[REDACTED]";
    }
}
