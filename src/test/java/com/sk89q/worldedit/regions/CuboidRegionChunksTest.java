package com.sk89q.worldedit.regions;

import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.Vector2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * A cuboid's chunk list holds each chunk the region touches exactly once and nothing else, its size
 * matches what it iterates, and every element is a separate object that later elements do not change.
 */
class CuboidRegionChunksTest {

    @Test
    void oneChunk() {
        assertChunks(new Vector(3, 0, 3), new Vector(10, 5, 12), 0, 0, 0, 0);
    }

    @Test
    void oneChunkWideInXSeveralInZ() {
        // //pos1 21504,50,21504 and //pos2 21510,50,21540 cover chunk x 1344, z 1344 to 1346
        assertChunks(new Vector(21504, 50, 21504), new Vector(21510, 50, 21540), 1344, 1344, 1344, 1346);
    }

    @Test
    void severalChunksInXOneInZ() {
        assertChunks(new Vector(0, 0, 0), new Vector(47, 0, 15), 0, 2, 0, 0);
    }

    @Test
    void severalChunksBothWays() {
        assertChunks(new Vector(5, 0, 5), new Vector(40, 0, 70), 0, 2, 0, 4);
    }

    @Test
    void negativeCoordinates() {
        assertChunks(new Vector(-40, 0, -1), new Vector(-1, 0, 20), -3, -1, -1, 1);
        assertChunks(new Vector(-16, 0, -33), new Vector(-16, 0, -17), -1, -1, -3, -2);
    }

    private static void assertChunks(Vector pos1, Vector pos2, int minX, int maxX, int minZ, int maxZ) {
        Set<Vector2D> chunks = new CuboidRegion(pos1, pos2).getChunks();
        List<Vector2D> listed = new ArrayList<>();
        for (Vector2D chunk : chunks) {
            listed.add(chunk);
        }
        // Compared as text: Vector2D and BlockVector2D hash differently
        Set<String> expected = new HashSet<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                expected.add(x + "," + z);
            }
        }
        Set<String> actual = new HashSet<>();
        for (Vector2D chunk : listed) {
            actual.add(chunk.getBlockX() + "," + chunk.getBlockZ());
        }
        assertEquals(expected.size(), chunks.size());
        assertEquals(expected.size(), listed.size());
        assertEquals(expected, actual);
        for (int i = 0; i < listed.size(); i++) {
            for (int j = i + 1; j < listed.size(); j++) {
                assertNotSame(listed.get(i), listed.get(j));
            }
        }
        assertFalse(chunks.contains(new Vector2D(minX - 1, minZ)));
        assertFalse(chunks.contains(new Vector2D(maxX, maxZ + 1)));
    }
}
