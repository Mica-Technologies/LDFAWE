package com.boydti.fawe.example;

import com.sk89q.worldedit.world.biome.BaseBiome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pending biome changes must keep the full biome id: ids above 255 (RoughlyEnoughIDs) and the
 * vanilla ids 0 and 255 used to collide with the old byte encoding's "unset" and "biome 0".
 */
class CharFaweChunkBiomeTest {

    private static SimpleCharFaweChunk chunk() {
        return new SimpleCharFaweChunk(null, 0, 0);
    }

    @Test
    void noBiomeSetMeansNoArray() {
        SimpleCharFaweChunk chunk = chunk();
        assertNull(chunk.biomes);
        assertNull(chunk.getBiomeArray());
    }

    @Test
    void keepsExtendedBiomeIds() {
        SimpleCharFaweChunk chunk = chunk();
        chunk.setBiome(3, 5, new BaseBiome(300));
        assertEquals(300, chunk.biomes[(5 << 4) + 3]);
        assertEquals(CharFaweChunk.BIOME_UNSET, chunk.biomes[0]);
    }

    @Test
    void biome255IsNotBiome0() {
        SimpleCharFaweChunk chunk = chunk();
        chunk.setBiome(1, 0, new BaseBiome(255));
        chunk.setBiome(2, 0, (byte) 255);
        assertEquals(255, chunk.biomes[1]);
        assertEquals(255, chunk.biomes[2]);
    }

    @Test
    void biome0IsSetNotUnset() {
        SimpleCharFaweChunk chunk = chunk();
        chunk.setBiome(0, 0, new BaseBiome(0));
        assertEquals(0, chunk.biomes[0]);
        // Legacy byte view: 0 = unchanged, -1 = biome 0
        byte[] legacy = chunk.getBiomeArray();
        assertEquals(-1, legacy[0]);
        assertEquals(0, legacy[1]);
    }
}
