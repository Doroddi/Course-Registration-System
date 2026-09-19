package com.doroddi.courseregistration.seed;

import org.junit.jupiter.api.Test;
import com.doroddi.courseregistration.config.EnrollmentTermProperties;
import com.doroddi.courseregistration.health.InitialDataReadiness;
import org.springframework.boot.DefaultApplicationArguments;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InitialDataRunnerTest {
    private final InitialDataService service = mock(InitialDataService.class);
    private final InitialDataValidator validator = mock(InitialDataValidator.class);
    private final InitialDataReadiness readiness = new InitialDataReadiness();
    private final InitialDataRunner runner = new InitialDataRunner(service, validator, new EnrollmentTermProperties(2030, (short) 1), readiness);
    private final DefaultApplicationArguments args = new DefaultApplicationArguments(new String[0]);

    @Test
    void createsDataOnlyForEmptyDatabase() {
        when(validator.isEmpty()).thenReturn(true);
        runner.run(args);
        assertTrue(readiness.isReady());
        verify(service).createInitialData();
        verify(validator, never()).validateExistingData(any(), any());
    }

    @Test
    void validatesExistingDataWithoutRecreatingIt() {
        runner.run(args);
        assertTrue(readiness.isReady());
        verify(validator).validateExistingData(2030, (short) 1);
        verifyNoInteractions(service);
    }

    @Test
    void propagatesValidationFailureWithoutRepair() {
        var failure = new IllegalStateException("초기 데이터가 불완전합니다");
        doThrow(failure).when(validator).validateExistingData(2030, (short) 1);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> runner.run(args)));
        assertFalse(readiness.isReady());
        verifyNoInteractions(service);
    }

    @Test
    void propagatesCreationFailureWithoutRetry() {
        when(validator.isEmpty()).thenReturn(true);
        var failure = new IllegalStateException("초기 데이터 생성 실패");
        doThrow(failure).when(service).createInitialData();
        assertSame(failure, assertThrows(IllegalStateException.class, () -> runner.run(args)));
        assertFalse(readiness.isReady());
        verify(service, times(1)).createInitialData();
    }
}
