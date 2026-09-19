package com.doroddi.courseregistration.seed;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
public class PasswordConfiguration {
    @Bean
    public PasswordEncoder passwordEncoder() {
        // 알고리즘 식별자를 함께 저장하여 이후 인증에서도 동일한 인코더로 검증한다.
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
