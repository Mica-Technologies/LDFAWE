package com.boydti.fawe.command;

import com.sk89q.worldedit.extension.input.InputParseException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How mask and pattern input is split on top-level "&" and ",". An unbracketed expression that
 * starts with "=" (as in //gmask =y>5&&y<8) must stay whole so its operators reach the expression,
 * while ordinary lists still split.
 */
class FaweParserSplitTest {

    @Test
    void leadingExpressionKeepsItsOperators() throws InputParseException {
        List<Map.Entry<FaweParser.ParseEntry, List<String>>> parsed = FaweParser.parse("=y>5&&y<8||x==1,z>2");
        assertEquals(1, parsed.size());
        assertEquals("=y>5&&y<8||x==1,z>2", parsed.get(0).getKey().full);
    }

    @Test
    void bracketedExpressionIsUnchanged() throws InputParseException {
        List<Map.Entry<FaweParser.ParseEntry, List<String>>> parsed = FaweParser.parse("=[y>5&&y<8]");
        assertEquals(1, parsed.size());
        assertEquals("=", parsed.get(0).getKey().input);
        assertEquals("y>5&&y<8", parsed.get(0).getValue().get(0));
    }

    @Test
    void ordinaryListsStillSplit() throws InputParseException {
        List<Map.Entry<FaweParser.ParseEntry, List<String>>> parsed = FaweParser.parse("stone,dirt&glass");
        assertEquals(3, parsed.size());
        assertEquals("stone", parsed.get(0).getKey().full);
        assertEquals("dirt", parsed.get(1).getKey().full);
        assertFalse(parsed.get(1).getKey().and);
        assertEquals("glass", parsed.get(2).getKey().full);
        assertTrue(parsed.get(2).getKey().and);
    }

    @Test
    void expressionAfterAnAndRunsToTheEnd() throws InputParseException {
        List<Map.Entry<FaweParser.ParseEntry, List<String>>> parsed = FaweParser.parse("stone&=y>5&&y<8");
        assertEquals(2, parsed.size());
        assertEquals("stone", parsed.get(0).getKey().full);
        assertEquals("=y>5&&y<8", parsed.get(1).getKey().full);
    }
}
