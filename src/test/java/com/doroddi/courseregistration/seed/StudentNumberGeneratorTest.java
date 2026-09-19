package com.doroddi.courseregistration.seed;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class StudentNumberGeneratorTest {
    private final StudentNumberGenerator generator = new StudentNumberGenerator();

    @Test
    void buildsNineDigitNumbersWithInclusiveIndexBoundaries() {
        var numbers = generator.generate(2026, 10, 400);
        assertEquals(400, numbers.size());
        assertEquals(202610100, numbers.getFirst());
        assertEquals(202610499, numbers.getLast());
        assertEquals(202610255, numbers.get(155));
    }

    @Test
    void allAllowedCombinationsAreUniqueAndWithinTheirFields() {
        var unique = new HashSet<Integer>();
        for (int year = 2020; year <= 2026; year++) {
            for (int code = 10; code <= 99; code++) {
                for (int number : generator.generate(year, code, 400)) {
                    assertTrue(unique.add(number), "서로 다른 생성 조합의 학번이 중복됨");
                    assertEquals(year, number / 100_000);
                    assertEquals(code, number / 1_000 % 100);
                    assertTrue(number % 1_000 >= 100 && number % 1_000 <= 499);
                    assertTrue(number >= 100_000_000 && number <= 999_999_999);
                }
            }
        }
        assertEquals(252_000, unique.size());
    }

    @Test
    void sameInputsAreReproducibleAndZeroCountIsEmpty() {
        assertEquals(generator.generate(2020, 99, 143), generator.generate(2020, 99, 143));
        assertTrue(generator.generate(2020, 10, 0).isEmpty());
    }

    @Test
    void rejectsInputsThatWouldBreakTheGenerationContract() {
        for (int year : new int[]{2019, 2027}) {
            assertThrows(IllegalArgumentException.class, () -> generator.generate(year, 10, 1));
        }
        for (int code : new int[]{9, 100}) {
            assertThrows(IllegalArgumentException.class, () -> generator.generate(2026, code, 1));
        }
        for (int count : new int[]{-1, 401}) {
            assertThrows(IllegalArgumentException.class, () -> generator.generate(2026, 10, count));
        }
    }
}
