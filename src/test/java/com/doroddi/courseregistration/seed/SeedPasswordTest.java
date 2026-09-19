package com.doroddi.courseregistration.seed;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;

class SeedPasswordTest {
    @Test
    void encodesConfiguredPasswordAndPreservesSurroundingSpaces() {
        var encoder = new PasswordConfiguration().passwordEncoder();
        String raw = "  seed-test-only  ";
        var environment = new MockEnvironment().withProperty("INITIAL_STUDENT_PASSWORD", raw);
        String hash = new SeedPassword(environment, encoder).createHash();
        assertTrue(hash.startsWith("{bcrypt}"));
        assertTrue(encoder.matches(raw, hash));
        assertFalse(encoder.matches(raw.strip(), hash));
        assertNotEquals(raw, hash);
    }

    @Test
    void rejectsMissingBlankAndOversizedValuesWithoutExposingThem() {
        var encoder = new PasswordConfiguration().passwordEncoder();
        var missing = new SeedPassword(new MockEnvironment(), encoder);
        assertThrows(IllegalStateException.class, missing::createHash);
        for (String raw : new String[]{"", "   ", "x".repeat(73), "가".repeat(25)}) {
            var source = new SeedPassword(
                    new MockEnvironment().withProperty("INITIAL_STUDENT_PASSWORD", raw), encoder);
            assertThrows(IllegalStateException.class, source::createHash);
        }
    }

    @Test
    void acceptsSeventyTwoUtf8Bytes() {
        var encoder = new PasswordConfiguration().passwordEncoder();
        String raw = "가".repeat(24);
        String hash = new SeedPassword(new MockEnvironment()
                .withProperty("INITIAL_STUDENT_PASSWORD", raw), encoder).createHash();
        assertTrue(encoder.matches(raw, hash));
    }
}
