package com.boydti.fawe.object.collection;

import com.sk89q.worldedit.Vector;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * //line and //curve collect their points in a set. A LocalBlockVectorSet only spans 1024 blocks
 * from its first entry, so long lines need BlockVectorSet, which must keep every point of a span
 * well beyond that, including negative coordinates and region boundaries.
 */
class BlockVectorSetSpanTest {

    @Test
    void localSetRefusesALongLine() {
        LocalBlockVectorSet local = new LocalBlockVectorSet();
        local.add(2600, 50, 2500);
        assertThrows(UnsupportedOperationException.class, () -> local.add(3700, 50, 2500));
    }

    @Test
    void holdsEveryPointOfALongLine() {
        BlockVectorSet set = new BlockVectorSet();
        Set<String> expected = new HashSet<>();
        for (int x = -3000; x <= 3700; x += 7) {
            int z = 2500 - x;
            int y = 50 + Math.floorMod(x, 100);
            assertTrue(set.add(x, y, z));
            expected.add(x + "," + y + "," + z);
        }
        // A point already present is not added twice
        assertFalse(set.add(2600, 50 + Math.floorMod(2600, 100), 2500 - 2600));
        assertEquals(expected.size(), set.size());

        Set<String> iterated = new HashSet<>();
        for (Vector v : set) {
            // The iterator hands out one reused vector, so copy its coordinates
            iterated.add(v.getBlockX() + "," + v.getBlockY() + "," + v.getBlockZ());
        }
        assertEquals(expected, iterated);

        assertTrue(set.contains(new Vector(-3000, 50 + Math.floorMod(-3000, 100), 5500)));
        assertTrue(set.contains(-3000, 50 + Math.floorMod(-3000, 100), 5500));
        assertFalse(set.contains(new Vector(-2999, 50, 5499)));
    }
}
