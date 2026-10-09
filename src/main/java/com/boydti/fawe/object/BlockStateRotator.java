package com.boydti.fawe.object;

import com.sk89q.worldedit.math.transform.Transform;
import javax.annotation.Nullable;

/**
 * Rotates and mirrors blocks the way the platform itself does (Forge: {@code IBlockState#withRotation}
 * and {@code withMirror}). WorldEdit's bundled block registry only knows vanilla blocks, so without
 * this a modded block keeps its facing when a clipboard is rotated or flipped.
 */
public abstract class BlockStateRotator {

    private static volatile BlockStateRotator imp;

    /**
     * @return the platform's rotator, or null when it has none (block data is then left as is)
     */
    @Nullable
    public static BlockStateRotator get() {
        return imp;
    }

    public static void set(@Nullable BlockStateRotator rotator) {
        imp = rotator;
    }

    /**
     * Apply the horizontal part of a transform to a block.
     *
     * @param id        the block id
     * @param data      the block data value
     * @param transform the transform to apply to the block's orientation
     * @return the combined id ({@code id << 4 | data}) of the transformed block, or -1 when the block is
     * unchanged or cannot be transformed (unknown block, or a transform that is not a multiple of 90
     * degrees about the vertical axis, optionally mirrored)
     */
    public abstract int transform(int id, int data, Transform transform);
}
