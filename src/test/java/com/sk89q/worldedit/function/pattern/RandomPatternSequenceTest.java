package com.sk89q.worldedit.function.pattern;

import com.sk89q.worldedit.blocks.BaseBlock;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * #linear[stone,stone,dirt] steps through the list it was given. The weighted pattern it is built
 * from merges equal entries for its weights, so the list has to be kept separately.
 */
class RandomPatternSequenceTest {

    @Test
    void sequenceKeepsRepeatsInOrder() {
        Pattern stone = new BlockPattern(new BaseBlock(1));
        Pattern dirt = new BlockPattern(new BaseBlock(3));
        RandomPattern random = new RandomPattern();
        random.add(stone, 1);
        random.add(stone, 1);
        random.add(dirt, 1);

        assertEquals(Arrays.asList(stone, stone, dirt), random.getSequence());
        assertEquals(2, random.getPatterns().size());
    }
}
