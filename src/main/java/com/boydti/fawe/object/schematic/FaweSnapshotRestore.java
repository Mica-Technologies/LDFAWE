package com.boydti.fawe.object.schematic;

import com.boydti.fawe.FaweCache;
import com.boydti.fawe.jnbt.anvil.ChunkSectionDecoder;
import com.sk89q.jnbt.CompoundTag;
import com.sk89q.jnbt.ListTag;
import com.sk89q.jnbt.Tag;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.Vector2D;
import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.DataException;
import com.sk89q.worldedit.world.storage.ChunkStore;
import com.sk89q.worldedit.world.storage.MissingChunkException;
import com.sk89q.worldedit.world.storage.MissingWorldException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Restores a region from a world snapshot (a backup of the region files).
 * <p>
 * WorldEdit's own SnapshotRestore reads chunks through its legacy chunk classes, which only know the
 * vanilla Blocks/Add/Data layout. Worlds saved with RoughlyEnoughIDs store sections as a palette of
 * extended block state ids, so every modded block above id 4095 came back wrong. This reads the raw
 * chunk NBT and decodes sections with {@link ChunkSectionDecoder}, keeping tile-entity NBT.
 */
public class FaweSnapshotRestore {

    private final ChunkStore chunkStore;
    private final EditSession editSession;
    private final Region region;

    private final List<Vector2D> missingChunks = new ArrayList<>();
    private final List<Vector2D> errorChunks = new ArrayList<>();
    private int chunksRestored;
    private String lastErrorMessage;

    public FaweSnapshotRestore(ChunkStore chunkStore, EditSession editSession, Region region) {
        this.chunkStore = chunkStore;
        this.editSession = editSession;
        this.region = region;
    }

    public void restore() throws MaxChangedBlocksException {
        Vector min = region.getMinimumPoint();
        Vector max = region.getMaximumPoint();
        int minY = Math.max(0, min.getBlockY());
        int maxY = Math.min(255, max.getBlockY());
        for (Vector2D chunkPos : region.getChunks()) {
            CompoundTag level;
            try {
                level = chunkStore.getChunkTag(chunkPos, editSession.getWorld());
            } catch (MissingChunkException | MissingWorldException e) {
                missingChunks.add(chunkPos);
                continue;
            } catch (DataException | IOException e) {
                errorChunks.add(chunkPos);
                lastErrorMessage = e.getMessage();
                continue;
            }
            try {
                restoreChunk(chunkPos, level, min, max, minY, maxY);
                chunksRestored++;
            } catch (MaxChangedBlocksException e) {
                throw e;
            } catch (RuntimeException e) {
                errorChunks.add(chunkPos);
                lastErrorMessage = e.toString();
            }
        }
    }

    private void restoreChunk(Vector2D chunkPos, CompoundTag level, Vector min, Vector max, int minY, int maxY) throws MaxChangedBlocksException {
        int bx = chunkPos.getBlockX() << 4;
        int bz = chunkPos.getBlockZ() << 4;

        int[][] sections = new int[16][];
        Tag sectionsTag = level.getValue().get("Sections");
        if (sectionsTag instanceof ListTag) {
            for (Object tag : ((ListTag) sectionsTag).getValue()) {
                if (!(tag instanceof CompoundTag)) {
                    continue;
                }
                CompoundTag section = (CompoundTag) tag;
                int y = section.asInt("Y");
                if (y >= 0 && y < 16) {
                    sections[y] = ChunkSectionDecoder.decode(section);
                }
            }
        }

        Map<Long, CompoundTag> tiles = new HashMap<>();
        Tag tilesTag = level.getValue().get("TileEntities");
        if (tilesTag instanceof ListTag) {
            for (Object tag : ((ListTag) tilesTag).getValue()) {
                if (tag instanceof CompoundTag) {
                    CompoundTag tile = (CompoundTag) tag;
                    tiles.put(key(tile.asInt("x"), tile.asInt("y"), tile.asInt("z")), tile);
                }
            }
        }

        int x0 = Math.max(bx, min.getBlockX());
        int x1 = Math.min(bx + 15, max.getBlockX());
        int z0 = Math.max(bz, min.getBlockZ());
        int z1 = Math.min(bz + 15, max.getBlockZ());
        Vector mutable = new Vector(0, 0, 0);
        for (int y = minY; y <= maxY; y++) {
            int[] section = sections[y >> 4];
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    mutable.mutX(x);
                    mutable.mutY(y);
                    mutable.mutZ(z);
                    if (!region.contains(mutable)) {
                        continue;
                    }
                    int combined = section == null ? 0 : section[(y & 15) << 8 | (z & 15) << 4 | (x & 15)];
                    int id = FaweCache.getId(combined);
                    int data = FaweCache.getData(combined);
                    CompoundTag tile = tiles.get(key(x, y, z));
                    BaseBlock block = tile != null ? new BaseBlock(id, data, tile) : FaweCache.getBlock(id, data);
                    editSession.setBlock(x, y, z, block);
                }
            }
        }
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (y & 0xFFF);
    }

    public List<Vector2D> getMissingChunks() {
        return missingChunks;
    }

    public List<Vector2D> getErrorChunks() {
        return errorChunks;
    }

    /**
     * @return true if no chunk could be restored at all
     */
    public boolean hadTotalFailure() {
        return chunksRestored == 0;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }
}
