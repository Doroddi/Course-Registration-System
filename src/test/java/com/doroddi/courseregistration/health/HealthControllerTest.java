package com.doroddi.courseregistration.health;

import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class HealthControllerTest {
    private final ApplicationAvailability availability = mock(ApplicationAvailability.class);
    private final InitialDataReadiness data = new InitialDataReadiness();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new HealthController(availability, data)).build();

    @Test
    void refusesTrafficBeforeInitializationEvenIfBootIsReady() throws Exception {
        when(availability.getReadinessState()).thenReturn(ReadinessState.ACCEPTING_TRAFFIC);
        mvc.perform(get("/health")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("NOT_READY"));
    }

    @Test
    void waitsForRemainingApplicationRunners() throws Exception {
        data.markReady();
        when(availability.getReadinessState()).thenReturn(ReadinessState.REFUSING_TRAFFIC);
        mvc.perform(get("/health")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void reportsReadyThenRefusesTrafficOnShutdown() throws Exception {
        data.markReady();
        when(availability.getReadinessState()).thenReturn(ReadinessState.ACCEPTING_TRAFFIC);
        mvc.perform(get("/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        when(availability.getReadinessState()).thenReturn(ReadinessState.REFUSING_TRAFFIC);
        mvc.perform(get("/health")).andExpect(status().isServiceUnavailable());
    }
}
