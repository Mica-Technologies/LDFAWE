package com.boydti.fawe.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Scrolling through brush masks, patterns and modes wraps an index around a closed range.
 */
class MathManTest {

    @ParameterizedTest
    @CsvSource({
            // value, min, max, expected
            "0, 0, 2, 0",
            "2, 0, 2, 2",
            "3, 0, 2, 0",
            "4, 0, 2, 1",
            "6, 0, 2, 0",
            "-1, 0, 2, 2",
            "-2, 0, 2, 1",
            "-3, 0, 2, 0",
            "-4, 0, 2, 2",
            "4, 5, 10, 10",
            "11, 5, 10, 5",
            "7, 7, 7, 7",
            "3, 5, 4, 3",
    })
    void wrapStaysInsideInclusiveRange(int value, int min, int max, int expected) {
        assertEquals(expected, MathMan.wrap(value, min, max));
    }
}