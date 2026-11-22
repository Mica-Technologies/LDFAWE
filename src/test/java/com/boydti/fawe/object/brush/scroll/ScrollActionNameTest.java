package com.boydti.fawe.object.brush.scroll;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Action names for /br scroll. Every name the command offers must be recognised, in any case, and a
 * name nothing implements must be reported as unknown so the command keeps the old action instead
 * of clearing it.
 */
class ScrollActionNameTest {

    @ParameterizedTest
    @CsvSource({"none", "clipboard", "mask", "pattern", "range", "size", "target", "targetoffset", "MASK"})
    void knownActionsAreRecognised(String name) {
        assertTrue(ScrollAction.isAction(name));
    }

    @ParameterizedTest
    @CsvSource({"visual", "foo", "''"})
    void unknownActionsAreRejected(String name) {
        assertFalse(ScrollAction.isAction(name));
    }

    @ParameterizedTest
    @CsvSource({"none", "clipboard", "mask", "pattern", "range", "size", "target", "targetoffset"})
    void everyRecognisedActionIsListed(String name) {
        assertTrue(ScrollAction.VALID_ACTIONS.contains(name));
    }
}
