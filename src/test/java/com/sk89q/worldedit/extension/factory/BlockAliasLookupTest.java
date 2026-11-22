package com.sk89q.worldedit.extension.factory;

import com.sk89q.worldedit.blocks.BlockType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The block parser resolves legacy aliases only when typed exactly. The fuzzy form it used before
 * accepted any name one edit away from an alias, so red_wool became a log (through "redwood").
 */
class BlockAliasLookupTest {

    @Test
    void legacyAliasesResolveAsTyped() {
        assertEquals(BlockType.CLOTH, BlockType.lookup("wool", false));
        assertEquals(BlockType.STEP, BlockType.lookup("step", false));
        assertEquals(BlockType.LOG, BlockType.lookup("log", false));
        assertEquals(BlockType.LOG, BlockType.lookup("redwood", false));
    }

    @Test
    void nearMissesDoNotResolve() {
        assertNull(BlockType.lookup("red_wool", false));
        assertNull(BlockType.lookup("redwool", false));
        // The fuzzy lookup the parser no longer uses is what turned it into a log
        assertEquals(BlockType.LOG, BlockType.lookup("red_wool", true));
    }
}
