package com.sk89q.worldedit.function.mask;

import com.boydti.fawe.FaweCache;
import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.blocks.BlockType;
import com.sk89q.worldedit.extent.Extent;
import javax.annotation.Nullable;

public class SolidBlockMask extends BlockMask {

    public SolidBlockMask(Extent extent) {
        super(extent);
        for (int id = 0; id < 4096; id++) {
            for (int data = 0; data < 16; data++) {
                if (!BlockType.canPassThrough(id, data)) {
                    add(new BaseBlock(id, data));
                }
            }
        }
    }

    /**
     * Blocks above id 4095 are not enumerated in the constructor; fall back to FaweCache, which
     * treats unknown high ids as solid.
     */
    @Override
    public boolean test(int blockId, int data) {
        if (blockId >= 4096) {
            return !FaweCache.canPassThrough(blockId, data);
        }
        return super.test(blockId, data);
    }

    @Nullable
    @Override
    public Mask2D toMask2D() {
        return null; // 9751418
    }

    public static Class<?> inject() {
        return SolidBlockMask.class;
    }
}