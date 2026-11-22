package com.sk89q.worldedit.extension.factory;

import com.sk89q.worldedit.extension.input.InputParseException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The weight in front of % in patterns such as 50%stone,50%dirt. A weight of 0 or less must be
 * refused while parsing, because the random pattern cannot be built from weights that are all
 * zero (it divides by their common divisor).
 */
class PatternWeightTest {

    @ParameterizedTest
    @CsvSource({
            "50, 50",
            "0.5, 0.5",
            "10*2, 20",
    })
    void positiveWeightsAreRead(String input, double expected) throws InputParseException {
        assertEquals(expected, HashTagPatternParser.parseWeight(input), 1e-9);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5", "0*3", "abc("})
    void zeroNegativeOrUnreadableWeightsAreRefused(String input) {
        assertThrows(InputParseException.class, () -> HashTagPatternParser.parseWeight(input));
    }
}
