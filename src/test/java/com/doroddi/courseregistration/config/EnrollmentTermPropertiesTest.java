package com.doroddi.courseregistration.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class EnrollmentTermPropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(EnrollmentTermProperties.class)
    static class Config {}

    @Test
    void bindsConfiguredYearAndTerm() {
        runner.withPropertyValues("app.enrollment.academic-year=2030", "app.enrollment.term=1")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(EnrollmentTermProperties.class))
                            .isEqualTo(new EnrollmentTermProperties(2030, (short) 1));
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"999", "10000", "abc"})
    void rejectsInvalidYear(String year) {
        runner.withPropertyValues("app.enrollment.academic-year=" + year, "app.enrollment.term=1")
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "3", "spring"})
    void rejectsInvalidTerm(String term) {
        runner.withPropertyValues("app.enrollment.academic-year=2026", "app.enrollment.term=" + term)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsMissingSettings() {
        runner.run(context -> assertThat(context).hasFailed());
    }
}
