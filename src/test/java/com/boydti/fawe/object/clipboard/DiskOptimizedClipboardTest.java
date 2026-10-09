package com.boydti.fawe.object.clipboard;

import com.sk89q.jnbt.CompoundTag;
import com.sk89q.jnbt.StringTag;
import com.sk89q.jnbt.Tag;
import com.sk89q.worldedit.entity.BaseEntity;
import com.sk89q.worldedit.entity.Entity;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
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
}
