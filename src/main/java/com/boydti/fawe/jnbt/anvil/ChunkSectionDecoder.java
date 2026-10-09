package com.boydti.fawe.jnbt.anvil;

import com.sk89q.jnbt.ByteArrayTag;
import com.sk89q.jnbt.CompoundTag;
import com.sk89q.jnbt.IntArrayTag;
import com.sk89q.jnbt.Tag;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Decodes the blocks of a saved 1.12 chunk section to combined ids ({@code id << 4 | data}), in every
 * layout the game may have written:
 * <ul>
 * <li>vanilla: {@code Blocks} (bits 0-7 of the id), {@code Data}, optional {@code Add} (bits 8-11)</li>
 * <li>NotEnoughIDs: additionally {@code Add2} (bits 12-15)</li>
 * <li>RoughlyEnoughIDs / JustEnoughIDs: a {@code Palette} int array of block state ids
 * ({@code id << 4 | meta}), indexed by {@code (Blocks & 0xFF) << 4 | Data}</li>
 * </ul>
 * Section index order is {@code y << 8 | z << 4 | x}, and nibble arrays keep the even index in the
 * low nibble, as in vanilla's NibbleArray.
 */
public final class ChunkSectionDecoder {

    private ChunkSectionDecoder() {
    }

    /**
     * @param section a compound from a chunk's "Sections" list
     * @return 4096 combined ids, or null if the section has no block data
     */
    @Nullable
    public static int[] decode(CompoundTag section) {
        Map<String, Tag> map = section.getValue();
        byte[] blocks = bytes(map.get("Blocks"));
        byte[] data = bytes(map.get("Data"));
        if (blocks == null || blocks.length < 4096) {
            return null;
        }
        int[] combined = new int[4096];
        Tag paletteTag = map.get("Palette");
        if (paletteTag instanceof IntArrayTag) {
            int[] palette = ((IntArrayTag) paletteTag).getValue();
            for (int i = 0; i < 4096; i++) {
                int paletteId = (blocks[i] & 0xFF) << 4 | nibble(data, i);
                combined[i] = paletteId < palette.length ? palette[paletteId] : 0;
            }
            return combined;
        }
        byte[] add = bytes(map.get("Add"));
        byte[] add2 = bytes(map.get("Add2"));
        for (int i = 0; i < 4096; i++) {
            int id = (blocks[i] & 0xFF) | nibble(add, i) << 8 | nibble(add2, i) << 12;
            combined[i] = id << 4 | nibble(data, i);
        }
        return combined;
    }

    private static byte[] bytes(Tag tag) {
        return tag instanceof ByteArrayTag ? ((ByteArrayTag) tag).getValue() : null;
    }

    private static int nibble(byte[] array, int index) {
        if (array == null || (index >> 1) >= array.length) {
            return 0;
        }
        int value = array[index >> 1];
        return (index & 1) == 0 ? value & 0xF : (value >> 4) & 0xF;
    }
}
