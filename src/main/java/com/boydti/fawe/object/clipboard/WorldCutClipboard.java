package com.boydti.fawe.object.clipboard;

import com.boydti.fawe.object.FawePlayer;
import com.boydti.fawe.object.changeset.MemoryOptimizedHistory;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.regions.Region;

public class WorldCutClipboard extends WorldCopyClipboard {
    public WorldCutClipboard(EditSession editSession, Region region, boolean copyEntities, boolean copyBiome) {
        super(editSession, region, copyEntities, copyBiome);
    }

    public WorldCutClipboard(EditSession editSession, Region region) {
        super(editSession, region);
    }

    @Override
    public BaseBlock getBlock(int x, int y, int z) {
        int xx = mx + x;
        int yy = my + y;
        int zz = mz + z;
        BaseBlock block = editSession.getLazyBlock(xx, yy, zz);
        editSession.setBlock(xx, yy, zz, EditSession.nullBlock);
        return block;
    }

    public BaseBlock getBlockAbs(int x, int y, int z) {
        BaseBlock block = editSession.getLazyBlock(x, y, z);
        editSession.setBlock(x, y, z, EditSession.nullBlock);
        return block;
    }

    /**
     * The source is only removed when the clipboard is pasted, through the //lazycut command's
     * EditSession. That session was already remembered (and skipped, having no changes) when
     * //lazycut finished, so the removal has to become its own undo entry here.
     * <p>
     * It is recorded in memory rather than the session's original disk history: that history's
     * index was picked when //lazycut ran and no file claimed it, so the paste's own history,
     * created later, gets the same index and the two would write to the same files.
     */
    @Override
    public void forEach(BlockReader task, boolean air) {
        FawePlayer fp = editSession.getPlayer();
        boolean undoable = fp != null && editSession.getChangeSet() != null;
        if (undoable) {
            editSession.setChangeSet(new MemoryOptimizedHistory(editSession.getWorld()));
        }
        super.forEach(task, air);
        editSession.flushQueue();
        if (undoable) {
            fp.getSession().remember(editSession);
        }
    }
}