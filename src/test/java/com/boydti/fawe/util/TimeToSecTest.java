package com.boydti.fawe.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Durations typed into /frb and /anvil deletealloldregions. A unit must count once, the compact
 * form the help text shows ("8h5m12s") must work, and anything unreadable must come back as 0 so
 * the command refuses it.
 */
class TimeToSecTest {

    @ParameterizedTest
    @CsvSource({
            "90, 90",
            "12s, 12",
            "30m, 1800",
            "8h, 28800",
            "1d, 86400",
            "1w, 604800",
            "1y, 31536000",
            "8h5m12s, 29112",
            "8h 5m 12s, 29112",
            "2 days 3 hours, 183600",
            "1D, 86400",
            "5000w, 3024000000",
    })
    void readsEachUnitOnce(String input, long expected) {
        assertEquals(expected, MainUtil.timeToSec(input));
    }

    @ParameterizedTest
    @CsvSource({
            "false",
            "abc",
            "5x",
            "8h5",
            "h8",
            "8h junk",
    })
    void unreadableInputIsZero(String input) {
        assertEquals(0, MainUtil.timeToSec(input));
    }
}
