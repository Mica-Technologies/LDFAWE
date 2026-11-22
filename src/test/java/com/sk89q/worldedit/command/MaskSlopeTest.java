package com.sk89q.worldedit.command;

import com.sk89q.worldedit.extension.input.InputParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Slope arguments of the angle masks (/[min][max], ([min][max], ^[min][max]). Each argument is
 * read on its own: a plain number of blocks, or degrees when it ends in "d". A bare "0" must stay
 * 0 and "30" must stay 30 even when the other argument is in degrees, and a bad value must be a
 * normal parse error.
 */
class MaskSlopeTest {

    @Test
    void plainNumbersAreBlocks() throws InputParseException {
        assertEquals(0, MaskCommands.parseSlope("0"), 1e-9);
        assertEquals(30, MaskCommands.parseSlope("30"), 1e-9);
    }

    @Test
    void degreeSuffixIsConverted() throws InputParseException {
        assertEquals(1, MaskCommands.parseSlope("45d"), 1e-9);
        assertEquals(0, MaskCommands.parseSlope("0d"), 1e-9);
    }

    @ParameterizedTest
    @ValueSource(strings = {"d", "abc(", "45dd", ""})
    void unreadableSlopesAreParseErrors(String input) {
        assertThrows(InputParseException.class, () -> MaskCommands.parseSlope(input));
    }
}
