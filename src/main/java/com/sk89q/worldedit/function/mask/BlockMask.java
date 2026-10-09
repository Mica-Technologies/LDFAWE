package com.sk89q.worldedit.function.mask;

import com.boydti.fawe.FaweCache;
import com.boydti.fawe.util.StringMan;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.extent.Extent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.LinkedHashSet;
import javax.annotation.Nullable;


import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A mask that checks whether blocks at the given positions are matched by
 * a block in a list.
 * <p>
 * <p>This mask checks for both an exact block ID and data value match, as well
 * for a block with the same ID but a data value of -1.</p>
 */
public class BlockMask extends AbstractExtentMask {

    /**
     * Matched combined ids ({@code id << 4 | data}) and matched block ids. BitSets rather than
     * fixed arrays because block ids above 4095 (RoughlyEnoughIDs) are legal; a lookup past the
     * highest set bit is simply false.
     */
    public final BitSet blocks = new BitSet(Character.MAX_VALUE + 1);
    public final BitSet blockIds = new BitSet(4096);
    public Collection<BaseBlock> computedLegacyList;

    /**
     * Create a new block mask.
     *
     * @param extent the extent
     * @param blocks a list of blocks to match
     */
    public BlockMask(Extent extent, Collection<BaseBlock> blocks) {
        super(extent);
        checkNotNull(blocks);
        add(blocks);
    }

    /**
     * Create a new block mask.
     *
     * @param extent the extent
     * @param block  an array of blocks to match
     */
    public BlockMask(Extent extent, BaseBlock... block) {
        this(extent, Arrays.asList(checkNotNull(block)));
    }

    /**
     * Add the given blocks to the list of criteria.
     *
     * @param blocks a list of blocks
     */
    public void add(Collection<BaseBlock> blocks) {
        checkNotNull(blocks);
        for (BaseBlock block : blocks) {
            add(block);
        }
    }

    /**
     * Add the given blocks to the list of criteria.
     *
     * @param blocks an array of blocks
     */
    public void add(BaseBlock... blocks) {
        for (BaseBlock block : blocks) {
            add(block);
        }
    }

    public void add(BaseBlock block) {
        blockIds.set(block.getId());
        if (block.getData() == -1) {
            for (int data = 0; data < 16; data++) {
                blocks.set(FaweCache.getCombined(block.getId(), data));
            }
        } else {
            blocks.set(FaweCache.getCombined(block));
        }
        computedLegacyList = null;
    }

    public BitSet getBlockArray() {
        return blockIds;
    }

    /**
     * Get the list of blocks that are tested with.
     *
     * @return a list of blocks
     */
    public Collection<BaseBlock> getBlocks() {
        if (computedLegacyList != null) {
            return computedLegacyList;
        }
        computedLegacyList = new LinkedHashSet<>();
        for (int id = blockIds.nextSetBit(0); id >= 0; id = blockIds.nextSetBit(id + 1)) {
            ArrayList<BaseBlock> tmp = new ArrayList<BaseBlock>(16);
            for (int data = 0; data < 16; data++) {
                if (blocks.get(FaweCache.getCombined(id, data))) {
                    tmp.add(FaweCache.getBlock(id, data));
                }
            }
            if (tmp.size() == 16) {
                computedLegacyList.add(new BaseBlock(id, -1));
            } else {
                computedLegacyList.addAll(tmp);
            }
        }
        return computedLegacyList;
    }

    public Collection<BaseBlock> getInverseBlocks() {
        if (computedLegacyList != null) {
            return computedLegacyList;
        }
        computedLegacyList = new LinkedHashSet<>();
        // The id space is unbounded above, so the inverse is listed for the classic 0-4095 range
        // plus any id beyond it that has some (but not all) data values matched.
        int maxId = Math.max(4096, blockIds.length());
        for (int id = 0; id < maxId; id++) {
            ArrayList<BaseBlock> tmp = new ArrayList<BaseBlock>(16);
            for (int data = 0; data < 16; data++) {
                if (!blocks.get(FaweCache.getCombined(id, data))) {
                    tmp.add(FaweCache.getBlock(id, data));
                }
            }
            if (tmp.size() == 16) {
                computedLegacyList.add(new BaseBlock(id, -1));
            } else {
                computedLegacyList.addAll(tmp);
            }
        }
        return computedLegacyList;
    }

    @Override
    public String toString() {
        return StringMan.getString(getBlocks());
    }

    public boolean test(int blockId) {
        return blockId >= 0 && blockIds.get(blockId);
    }

    public boolean test(int blockId, int data) {
        int combined = FaweCache.getCombined(blockId, data);
        return combined >= 0 && blocks.get(combined);
    }

    @Override
    public boolean test(Vector vector) {
        BaseBlock block = getExtent().getBlock(vector);
        return test(block.getId(), block.getData());
    }

    @Nullable
    @Override
    public Mask2D toMask2D() {
        return null;
    }

    public static Class<?> inject() {
        return BlockMask.class;
    }
}