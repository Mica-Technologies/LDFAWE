package com.boydti.fawe.object.clipboard;

import com.sk89q.jnbt.CompoundTag;
import com.sk89q.jnbt.StringTag;
import com.sk89q.jnbt.Tag;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.entity.BaseEntity;
import com.sk89q.worldedit.entity.Entity;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.util.Location;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A disk clipboard reopened from its file (as after a restart or relog) must keep its tile entities
 * and entities, which are not part of the mapped block file.
 */
class DiskOptimizedClipboardTest {

    @Test
    void tilesAndEntitiesSurviveReopening(@TempDir File dir) {
        File file = new File(dir, "player.bd");
        DiskOptimizedClipboard original = new DiskOptimizedClipboard(4, 3, 2, file);
        BlockArrayClipboard clipboard = original.toClipboard();

        Map<String, Tag> sign = new HashMap<>();
        sign.put("Text1", new StringTag("hello"));
        original.setTile(1, 2, 1, new CompoundTag(sign));
        Map<String, Tag> entityData = new HashMap<>();
        entityData.put("CustomName", new StringTag("Bob"));
        original.createEntity(clipboard, 1.5, 2.0, 0.5, 90f, 10f, new BaseEntity("Pig", new CompoundTag(entityData)));
        original.close();
        assertTrue(new File(dir, "player.bd.nbt").exists());

        DiskOptimizedClipboard reopened = new DiskOptimizedClipboard(file);
        BlockArrayClipboard restored = reopened.toClipboard();

        List<CompoundTag> tiles = reopened.getTileEntities();
        assertEquals(1, tiles.size());
        assertEquals("hello", tiles.get(0).getString("Text1"));
        assertEquals(1, tiles.get(0).getInt("x"));
        assertEquals(2, tiles.get(0).getInt("y"));

        List<? extends Entity> entities = reopened.getEntities();
        assertEquals(1, entities.size());
        Entity entity = entities.get(0);
        assertEquals(restored, entity.getExtent());
        assertEquals("Pig", entity.getState().getTypeId());
        assertEquals("Bob", entity.getState().getNbtData().getString("CustomName"));
        Location loc = entity.getLocation();
        assertEquals(1.5, loc.getX());
        assertEquals(90f, loc.getYaw());
        reopened.close();

        // A new clipboard on the same file starts without the old one's tiles and entities
        DiskOptimizedClipboard fresh = new DiskOptimizedClipboard(4, 3, 2, file);
        assertFalse(new File(dir, "player.bd.nbt").exists());
        assertTrue(fresh.getTileEntities().isEmpty());
        fresh.close();
    }

    /**
     * The copy's world position survives reopening, so //paste -o after a relog lands where the copy
     * was made; a file without the recorded position says so instead of pretending it starts at 0,0,0.
     */
    @Test
    void worldPositionSurvivesReopening(@TempDir File dir) {
        File file = new File(dir, "player.bd");
        DiskOptimizedClipboard original = new DiskOptimizedClipboard(3, 3, 3, file);
        BlockArrayClipboard clipboard = original.toClipboard();
        Vector min = new Vector(21905, 4, -21907);
        clipboard.init(new CuboidRegion(min, min.add(2, 2, 2)), original);
        clipboard.setOrigin(new Vector(21900, 4, -21900));
        original.close();

        DiskOptimizedClipboard reopened = new DiskOptimizedClipboard(file);
        BlockArrayClipboard restored = reopened.toClipboard();
        assertTrue(restored.isPositionKnown());
        assertEquals(min, restored.getRegion().getMinimumPoint());
        assertEquals(min.add(2, 2, 2), restored.getRegion().getMaximumPoint());
        assertEquals(new Vector(21900, 4, -21900), restored.getOrigin());
        reopened.close();

        // A file from an older build has no position sidecar
        assertTrue(new File(dir, "player.bd.pos").delete());
        DiskOptimizedClipboard old = new DiskOptimizedClipboard(file);
        BlockArrayClipboard oldClipboard = old.toClipboard();
        assertFalse(oldClipboard.isPositionKnown());
        assertEquals(new Vector(0, 0, 0), oldClipboard.getRegion().getMinimumPoint());
        assertEquals(new Vector(-5, 0, 7), oldClipboard.getOrigin());
        old.close();

        // A new clipboard on the same file does not inherit the previous position
        DiskOptimizedClipboard copied = new DiskOptimizedClipboard(3, 3, 3, file);
        copied.toClipboard().init(new CuboidRegion(min, min.add(2, 2, 2)), copied);
        assertTrue(new File(dir, "player.bd.pos").exists());
        copied.close();
        DiskOptimizedClipboard fresh = new DiskOptimizedClipboard(3, 3, 3, file);
        assertFalse(new File(dir, "player.bd.pos").exists());
        assertFalse(fresh.toClipboard().isPositionKnown());
        fresh.close();
    }
}
