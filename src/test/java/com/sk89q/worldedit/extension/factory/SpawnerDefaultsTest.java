package com.sk89q.worldedit.extension.factory;

import com.sk89q.jnbt.ShortTag;
import com.sk89q.jnbt.StringTag;
import com.sk89q.jnbt.Tag;
import com.sk89q.worldedit.blocks.MobSpawnerBlock;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A spawner set by name (mob_spawner|pig) must get vanilla's timings and ranges; with the zeros
 * WorldEdit's spawner block starts with, the spawner never spawns anything.
 */
class SpawnerDefaultsTest {

    @Test
    void namedSpawnerGetsVanillaDefaults() {
        MobSpawnerBlock spawner = DefaultBlockParser.spawnerWithDefaults(0, "Pig");
        Map<String, Tag> values = spawner.getNbtData().getValue();
        assertEquals("Pig", ((StringTag) values.get("EntityId")).getValue());
        assertEquals(20, value(values, "Delay"));
        assertEquals(200, value(values, "MinSpawnDelay"));
        assertEquals(800, value(values, "MaxSpawnDelay"));
        assertEquals(4, value(values, "SpawnCount"));
        assertEquals(6, value(values, "MaxNearbyEntities"));
        assertEquals(16, value(values, "RequiredPlayerRange"));
        assertEquals(4, value(values, "SpawnRange"));
    }

    private static int value(Map<String, Tag> values, String key) {
        return ((ShortTag) values.get(key)).getValue();
    }
}
