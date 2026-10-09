package com.boydti.fawe.object;

import java.util.Map;
import javax.annotation.Nullable;

/**
 * Converts between numeric block ids/data and the platform's block state names and properties
 * (Forge: the block registry and {@code IBlockState} properties). WorldEdit's bundled block data only
 * knows vanilla blocks, so formats that store names (structure .nbt files) need this for modded ones.
 */
public abstract class BlockStateRegistry {

    private static volatile BlockStateRegistry imp;

    /**
     * @return the platform's registry, or null when it has none
     */
    @Nullable
    public static BlockStateRegistry get() {
        return imp;
    }

    public static void set(@Nullable BlockStateRegistry registry) {
        imp = registry;
    }

    /**
     * @return the registry name of a block (e.g. "minecraft:stone"), or null if the id is unknown
     */
    @Nullable
    public abstract String getName(int id);

    /**
     * @return the state properties (name to value) of a block id and data value, empty if it has
     * none, or null if the id is unknown
     */
    @Nullable
    public abstract Map<String, String> getProperties(int id, int data);

    /**
     * @param name       a registry name
     * @param properties state properties; unknown names or values are ignored
     * @return the combined id ({@code id << 4 | data}), or -1 if the block is unknown
     */
    public abstract int getCombined(String name, @Nullable Map<String, String> properties);
}
