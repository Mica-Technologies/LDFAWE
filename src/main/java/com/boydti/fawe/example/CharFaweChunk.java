package com.boydti.fawe.example;

import com.boydti.fawe.FaweCache;
import com.boydti.fawe.object.FaweChunk;
import com.boydti.fawe.object.FaweQueue;
import com.boydti.fawe.util.MathMan;
import com.sk89q.jnbt.CompoundTag;

import java.util.*;

public abstract class CharFaweChunk<T, V extends FaweQueue> extends FaweChunk<T> {

    /**
     * Sentinel value stored in the char[][] ids array to indicate that the real combined block ID
     * is in the overflow map (because it exceeds Character.MAX_VALUE).
     */
    public static final char OVERFLOW_SENTINEL = 0xFFFF;

    /**
     * The combined ID that the sentinel value would normally represent: block ID 4095, data 15.
     * If a block legitimately has this combined value, it is NOT an overflow — only values stored
     * via the overflow map are overflow blocks.
     */
    private static final int SENTINEL_COMBINED = (0xFFF << 4) | 0xF;

    public final char[][] ids;
    public final short[] count;
    public final short[] air;
    public final byte[] heightMap;

    public byte[] biomes;
    public HashMap<Short, CompoundTag> tiles;
    public HashSet<CompoundTag> entities;
    public HashSet<UUID> entityRemoves;

    /**
     * Sparse overflow map for blocks with combined IDs > Character.MAX_VALUE.
     * Key: (section << 12) | positionInSection, Value: full int combined ID.
     * Only allocated when overflow blocks are actually present.
     */
    private HashMap<Integer, Integer> overflowIds;

    public T chunk;

    public CharFaweChunk(FaweQueue parent, int x, int z, char[][] ids, short[] count, short[] air, byte[] heightMap) {
        super(parent, x, z);
        this.ids = ids;
        this.count = count;
        this.air = air;
        this.heightMap = heightMap;
    }

    /**
     * A FaweSections object represents a chunk and the blocks that you wish to change in it.
     *
     * @param parent
     * @param x
     * @param z
     */
    public CharFaweChunk(FaweQueue parent, int x, int z) {
        super(parent, x, z);
        this.ids = new char[HEIGHT >> 4][];
        this.count = new short[HEIGHT >> 4];
        this.air = new short[HEIGHT >> 4];
        this.heightMap = new byte[256];
    }

    @Override
    public V getParent() {
        return (V) super.getParent();
    }

    @Override
    public T getChunk() {
        if (this.chunk == null) {
            this.chunk = getNewChunk();
        }
        return this.chunk;
    }

    public abstract T getNewChunk();

    @Override
    public void setLoc(final FaweQueue parent, int x, int z) {
        super.setLoc(parent, x, z);
        this.chunk = null;
    }

    /**
     * Get the number of block changes in a specified section
     *
     * @param i
     * @return
     */
    public int getCount(final int i) {
        return this.count[i];
    }

    public int getAir(final int i) {
        return this.air[i];
    }

    public void setCount(final int i, final short value) {
        this.count[i] = value;
    }

    public int getTotalCount() {
        int total = 0;
        for (int i = 0; i < count.length; i++) {
            total += Math.min(4096, this.count[i]);
        }
        return total;
    }

    public int getTotalAir() {
        int total = 0;
        for (int i = 0; i < air.length; i++) {
            total += Math.min(4096, this.air[i]);
        }
        return total;
    }

    @Override
    public int getBitMask() {
        int bitMask = 0;
        for (int section = 0; section < ids.length; section++) {
            if (ids[section] != null) {
                bitMask += 1 << section;
            }
        }
        return bitMask;
    }

    @Deprecated
    public void setBitMask(int ignore) {
        // Remove
    }

    /**
     * Get the raw data for a section
     *
     * @param i
     * @return
     */
    @Override
    public char[] getIdArray(final int i) {
        return this.ids[i];
    }

    @Override
    public char[][] getCombinedIdArrays() {
        return this.ids;
    }

    @Override
    public byte[] getBiomeArray() {
        return this.biomes;
    }

    @Override
    public int getBlockCombinedId(int x, int y, int z) {
        short i = FaweCache.getI(y, z, x);
        char[] array = getIdArray(i);
        if (array == null) {
            return 0;
        }
        int j = FaweCache.getJ(y, z, x);
        char value = array[j];
        if (value == OVERFLOW_SENTINEL && overflowIds != null) {
            Integer overflow = overflowIds.get((i << 12) | j);
            if (overflow != null) {
                return overflow;
            }
        }
        return value;
    }

    /**
     * Store a combined block ID that exceeds Character.MAX_VALUE in the overflow map.
     */
    private void setOverflow(int section, int j, int combined) {
        if (overflowIds == null) {
            overflowIds = new HashMap<>();
        }
        overflowIds.put((section << 12) | j, combined);
    }

    /**
     * Remove an overflow entry (when a block is overwritten with a normal-range value).
     */
    private void clearOverflow(int section, int j) {
        if (overflowIds != null) {
            overflowIds.remove((section << 12) | j);
        }
    }

    /**
     * Get the overflow map for this chunk. May be null if no overflow blocks exist.
     */
    public HashMap<Integer, Integer> getOverflowIds() {
        return overflowIds;
    }

    /**
     * Set the overflow map (used when copying chunks).
     */
    public void setOverflowIds(HashMap<Integer, Integer> overflow) {
        this.overflowIds = overflow;
    }

    @Override
    public void setTile(int x, int y, int z, CompoundTag tile) {
        if (tiles == null) {
            tiles = new HashMap<>();
        }
        short pair = MathMan.tripleBlockCoord(x, y, z);
        tiles.put(pair, tile);
    }

    @Override
    public CompoundTag getTile(int x, int y, int z) {
        if (tiles == null) {
            return null;
        }
        short pair = MathMan.tripleBlockCoord(x, y, z);
        return tiles.get(pair);
    }

    @Override
    public Map<Short, CompoundTag> getTiles() {
        return tiles == null ? new HashMap<Short, CompoundTag>() : tiles;
    }

    @Override
    public Set<CompoundTag> getEntities() {
        return entities == null ? Collections.emptySet() : entities;
    }

    @Override
    public void setEntity(CompoundTag tag) {
        if (entities == null) {
            entities = new HashSet<>();
        }
        entities.add(tag);
    }

    @Override
    public void removeEntity(UUID uuid) {
        if (entityRemoves == null) {
            entityRemoves = new HashSet<>();
        }
        entityRemoves.add(uuid);
    }

    @Override
    public HashSet<UUID> getEntityRemoves() {
        return entityRemoves == null ? new HashSet<UUID>() : entityRemoves;
    }

    @Override
    public void setBlock(int x, int y, int z, int id) {
        final int i = FaweCache.getI(y,z,x);
        final int j = FaweCache.getJ(y,z,x);
        char[] vs = this.ids[i];
        if (vs == null) {
            vs = this.ids[i] = new char[4096];
            this.count[i]++;
        } else {
            switch (vs[j]) {
                case 0:
                    this.count[i]++;
                    break;
                case 1:
                    this.air[i]--;
                    break;
            }
        }
        // Clear any previous overflow for this position
        clearOverflow(i, j);
        switch (id) {
            case 0:
                this.air[i]++;
                vs[j] = (char) 1;
                return;
            case 11:
            case 39:
            case 40:
            case 51:
            case 74:
            case 89:
            case 122:
            case 124:
            case 138:
            case 169:
            case 213:
            case 130:
            case 76:
            case 62:
            case 50:
            case 10:
            default:
                int combined = id << 4;
                if (combined > Character.MAX_VALUE) {
                    vs[j] = OVERFLOW_SENTINEL;
                    setOverflow(i, j, combined);
                } else {
                    vs[j] = (char) combined;
                }
                heightMap[z << 4 | x] = (byte) y;
                return;
        }
    }

    @Override
    public void setBlock(final int x, final int y, final int z, final int id, int data) {
        final int i = FaweCache.getI(y,z,x);
        final int j = FaweCache.getJ(y,z,x);
        char[] vs = this.ids[i];
        if (vs == null) {
            vs = this.ids[i] = new char[4096];
            this.count[i]++;
        } else {
            switch (vs[j]) {
                case 0:
                    this.count[i]++;
                    break;
                case 1:
                    this.air[i]--;
                    break;
            }
        }
        // Clear any previous overflow for this position
        clearOverflow(i, j);
        switch (id) {
            case 0:
                this.air[i]++;
                vs[j] = (char) 1;
                return;
            case 39:
            case 40:
            case 51:
            case 74:
            case 89:
            case 122:
            case 124:
            case 138:
            case 169:
            case 213:
            case 2:
            case 4:
            case 13:
            case 14:
            case 15:
            case 20:
            case 21:
            case 22:
            case 30:
            case 32:
            case 37:
            case 41:
            case 42:
            case 45:
            case 46:
            case 47:
            case 48:
            case 49:
            case 56:
            case 57:
            case 58:
            case 7:
            case 73:
            case 79:
            case 80:
            case 81:
            case 82:
            case 83:
            case 85:
            case 87:
            case 88:
            case 101:
            case 102:
            case 103:
            case 110:
            case 112:
            case 113:
            case 121:
            case 129:
            case 133:
            case 165:
            case 166:
            case 172:
            case 173:
            case 174:
            case 190:
            case 191:
            case 192: {
                int combined = id << 4;
                if (combined > Character.MAX_VALUE) {
                    vs[j] = OVERFLOW_SENTINEL;
                    setOverflow(i, j, combined);
                } else {
                    vs[j] = (char) combined;
                }
                heightMap[z << 4 | x] = (byte) y;
                return;
            }
            case 130:
            case 76:
            case 62:
            case 50:
            case 10:
            case 54:
            case 146:
            case 137:
            case 188:
            case 189:
            case 61:
            case 65:
            case 68: // removed
            default: {
                int combined = (id << 4) + data;
                if (combined > Character.MAX_VALUE) {
                    vs[j] = OVERFLOW_SENTINEL;
                    setOverflow(i, j, combined);
                } else {
                    vs[j] = (char) combined;
                }
                heightMap[z << 4 | x] = (byte) y;
                return;
            }
        }
    }

    @Override
    public void setBiome(final int x, final int z, byte biome) {
        if (this.biomes == null) {
            this.biomes = new byte[256];
        }
        if (biome == 0) biome = -1;
        biomes[((z & 15) << 4) + (x & 15)] = biome;
    }

    @Override
    public abstract CharFaweChunk<T, V> copy(boolean shallow);
}
