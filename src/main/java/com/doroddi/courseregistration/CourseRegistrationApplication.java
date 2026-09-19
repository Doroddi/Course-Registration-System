package com.doroddi.courseregistration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.doroddi.courseregistration.config.EnrollmentTermProperties;

@SpringBootApplication
@EnableConfigurationProperties(EnrollmentTermProperties.class)
public class CourseRegistrationApplication {
    public static void main(String[] args) {
        SpringApplication.run(CourseRegistrationApplication.class, args);
    }
}
