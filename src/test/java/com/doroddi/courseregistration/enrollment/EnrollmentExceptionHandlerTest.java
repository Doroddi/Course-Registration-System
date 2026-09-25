package com.doroddi.courseregistration.enrollment;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.TransactionSystemException;
import java.sql.SQLException;
import static org.assertj.core.api.Assertions.assertThat;

class EnrollmentExceptionHandlerTest {
    @ParameterizedTest
    @ValueSource(strings = {"55P03", "40P01"})
    void recognizesPostgresLockFailuresThroughWrappers(String state) {
        var failure = new TransactionSystemException("commit failed", new SQLException("db", state));
        assertThat(EnrollmentExceptionHandler.isLockFailure(failure)).isTrue();
        assertThat(new EnrollmentExceptionHandler().databaseFailure(failure).getStatusCode().value()).isEqualTo(503);
    }

    @ParameterizedTest
    @ValueSource(strings = {"23505", "08006", "57014", ""})
    void doesNotDisguiseOtherDatabaseFailuresAsLockFailures(String state) {
        assertThat(EnrollmentExceptionHandler.isLockFailure(new SQLException("db", state))).isFalse();
    }
}