package com.boydti.fawe.jnbt.anvil;

import com.sk89q.jnbt.ByteArrayTag;
import com.sk89q.jnbt.ByteTag;
import com.sk89q.jnbt.CompoundTag;
import com.sk89q.jnbt.IntArrayTag;
import com.sk89q.jnbt.ListTag;
import com.sk89q.jnbt.NBTInputStream;
import com.sk89q.jnbt.NBTOutputStream;
import com.sk89q.jnbt.Tag;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Anvil operations only understand 8-bit block ids; chunks with wider ids must be refused rather
 * than loaded truncated (and then written back corrupted).
 */
class MCAChunkGuardTest {

    private static NBTInputStream chunk(String extraKey, Tag extraValue) throws IOException {
        Map<String, Tag> section = new HashMap<>();
        section.put("Y", new ByteTag((byte) 0));
        section.put("Blocks", new ByteArrayTag(new byte[4096]));
        section.put("Data", new ByteArrayTag(new byte[2048]));
        section.put("SkyLight", new ByteArrayTag(new byte[2048]));
        section.put("BlockLight", new ByteArrayTag(new byte[2048]));
        if (extraKey != null) {
            section.put(extraKey, extraValue);
        }
        Map<String, Tag> level = new HashMap<>();
        level.put("Sections", new ListTag(CompoundTag.class, Collections.<Tag>singletonList(new CompoundTag(section))));
        Map<String, Tag> root = new HashMap<>();
        root.put("Level", new CompoundTag(level));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (NBTOutputStream out = new NBTOutputStream(bytes)) {
            out.writeNamedTag("", new CompoundTag(root));
        }
        return new NBTInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    }

    @Test
    void plainSectionLoads() {
        assertDoesNotThrow(() -> new MCAChunk(chunk(null, null), null, 0, 0, false));
    }

    @Test
    void addNibblesAreRefused() {
        assertThrows(IOException.class, () -> new MCAChunk(chunk("Add", new ByteArrayTag(new byte[2048])), null, 0, 0, false));
    }

    @Test
    void roughlyEnoughIdsPaletteIsRefused() {
        assertThrows(IOException.class, () -> new MCAChunk(chunk("Palette", new IntArrayTag(new int[]{0, 6797 << 4})), null, 0, 0, false));
    }
}
