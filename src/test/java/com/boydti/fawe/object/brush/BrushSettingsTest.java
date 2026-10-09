package com.boydti.fawe.object.brush;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Saved brushes are reloaded by re-running their constructor without the leading brush name.
 */
class BrushSettingsTest {

    @ParameterizedTest
    @CsvSource(value = {
            "s stone 5|stone 5",
            "s sandstone 5|sandstone 5",
            "sphere grass 3 -h|grass 3 -h",
            "cylinder s 2|s 2",
            "copypaste|''",
    }, delimiter = '|')
    void stripsOnlyTheBrushName(String constructor, String expected) {
        assertEquals(expected, BrushSettings.getArguments(constructor));
    }
}