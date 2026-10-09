package com.boydti.fawe.jnbt.anvil;

import com.sk89q.jnbt.ByteArrayTag;
import com.sk89q.jnbt.ByteTag;
import com.sk89q.jnbt.CompoundTag;
import com.sk89q.jnbt.IntArrayTag;
import com.sk89q.jnbt.Tag;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChunkSectionDecoderTest {

    private static int index(int x, int y, int z) {
        return y << 8 | z << 4 | x;
    }

    private static void setNibble(byte[] array, int index, int value) {
        int i = index >> 1;
        if ((index & 1) == 0) {
            array[i] = (byte) ((array[i] & 0xF0) | (value & 0xF));
        } else {
            array[i] = (byte) ((array[i] & 0x0F) | (value & 0xF) << 4);
        }
    }

    private static CompoundTag section(Map<String, Tag> extra, byte[] blocks, byte[] data) {
        Map<String, Tag> map = new HashMap<>(extra);
        map.put("Y", new ByteTag((byte) 0));
        map.put("Blocks", new ByteArrayTag(blocks));
        map.put("Data", new ByteArrayTag(data));
        return new CompoundTag(map);
    }

    @Test
    void vanillaWithAdd() {
        byte[] blocks = new byte[4096];
        byte[] data = new byte[2048];
        byte[] add = new byte[2048];
        int a = index(1, 2, 3);
        int b = index(2, 2, 3);
        // 1:0 (stone) at a; 2701:5 at b (needs Add)
        blocks[a] = 1;
        blocks[b] = (byte) (2701 & 0xFF);
        setNibble(add, b, 2701 >> 8);
        setNibble(data, b, 5);
        Map<String, Tag> extra = new HashMap<>();
        extra.put("Add", new ByteArrayTag(add));
        int[] combined = ChunkSectionDecoder.decode(section(extra, blocks, data));
        assertEquals(1 << 4, combined[a]);
        assertEquals(2701 << 4 | 5, combined[b]);
        assertEquals(0, combined[index(0, 0, 0)]);
    }

    @Test
    void notEnoughIdsAdd2() {
        byte[] blocks = new byte[4096];
        byte[] data = new byte[2048];
        byte[] add = new byte[2048];
        byte[] add2 = new byte[2048];
        int i = index(15, 15, 15);
        int id = 6797;
        blocks[i] = (byte) (id & 0xFF);
        setNibble(add, i, (id >> 8) & 0xF);
        setNibble(add2, i, id >> 12);
        setNibble(data, i, 3);
        Map<String, Tag> extra = new HashMap<>();
        extra.put("Add", new ByteArrayTag(add));
        extra.put("Add2", new ByteArrayTag(add2));
        assertEquals(id << 4 | 3, ChunkSectionDecoder.decode(section(extra, blocks, data))[i]);
    }

    @Test
    void roughlyEnoughIdsPalette() {
        // Palette entries are block state ids (id << 4 | meta); Blocks/Data hold palette indexes
        int[] palette = {0, 6797 << 4 | 3, 4095 << 4 | 15, 1 << 4};
        byte[] blocks = new byte[4096];
        byte[] data = new byte[2048];
        int a = index(4, 5, 6);
        int b = index(5, 5, 6);
        int c = index(6, 5, 6);
        setNibble(data, a, 1);
        setNibble(data, b, 2);
        setNibble(data, c, 3);
        Map<String, Tag> extra = new HashMap<>();
        extra.put("Palette", new IntArrayTag(palette));
        int[] combined = ChunkSectionDecoder.decode(section(extra, blocks, data));
        assertEquals(6797 << 4 | 3, combined[a]);
        assertEquals(4095 << 4 | 15, combined[b]);
        assertEquals(1 << 4, combined[c]);
        assertEquals(0, combined[index(0, 0, 0)]);
    }

    @Test
    void missingBlocksIsNull() {
        Map<String, Tag> map = new HashMap<>();
        map.put("Y", new ByteTag((byte) 0));
        assertNull(ChunkSectionDecoder.decode(new CompoundTag(map)));
    }
}
