package com.boydti.fawe.forge.v112;

import com.boydti.fawe.Fawe;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Reflection-based bridge to REID's (RoughlyEnoughIDs) BiomeApi.
 *
 * REID replaces vanilla's byte[256] biome storage with an int[256] and makes
 * Chunk.getBiomeArray() return a dummy array. Direct writes to that array are
 * silently lost. This helper detects REID at runtime and routes biome reads
 * and writes through its public API so they actually take effect.
 *
 * When REID is not present, all methods indicate unavailability via
 * {@link #isAvailable()} so callers can fall back to the vanilla path.
 */
public final class REIDBiomeHelper {

    private static volatile boolean initialized;
    private static boolean available;

    // BiomeApi.INSTANCE
    private static Object biomeApiInstance;

    // BiomeApi methods
    private static Method getBiomeAccessorMethod; // getBiomeAccessor(Chunk) -> BiomeAccessor
    private static Method replaceBiomesMethod;    // replaceBiomes(Chunk, int[])
    private static Method updateBiomeMethod;      // updateBiome(Chunk, BlockPos, int)

    // BiomeAccessor methods
    private static Method getBiomesMethod;        // getBiomes() -> int[]
    private static Method getBiomeIdMethod;       // getBiomeId(int, int) -> int

    // MessageManager.sendClientsBiomeChunkChange(World, BlockPos, int[])
    private static Method sendBiomeChunkChangeMethod;

    private REIDBiomeHelper() {}

    public static boolean isAvailable() {
        if (!initialized) {
            init();
        }
        return available;
    }

    private static synchronized void init() {
        if (initialized) return;
        try {
            Class<?> biomeApiClass = Class.forName("tff.reid.api.BiomeApi");
            Field instanceField = biomeApiClass.getField("INSTANCE");
            biomeApiInstance = instanceField.get(null);

            getBiomeAccessorMethod = biomeApiClass.getMethod("getBiomeAccessor", Chunk.class);
            replaceBiomesMethod = biomeApiClass.getMethod("replaceBiomes", Chunk.class, int[].class);
            updateBiomeMethod = biomeApiClass.getMethod("updateBiome", Chunk.class, BlockPos.class, int.class);

            Class<?> biomeAccessorClass = Class.forName("tff.reid.api.biome.BiomeAccessor");
            getBiomesMethod = biomeAccessorClass.getMethod("getBiomes");
            getBiomeIdMethod = biomeAccessorClass.getMethod("getBiomeId", int.class, int.class);

            Class<?> messageManagerClass = Class.forName("org.dimdev.jeid.network.MessageManager");
            sendBiomeChunkChangeMethod = messageManagerClass.getMethod(
                    "sendClientsBiomeChunkChange", World.class, BlockPos.class, int[].class);

            available = true;
            Fawe.debug("REID detected — biome operations will use REID's extended biome API");
        } catch (ClassNotFoundException e) {
            available = false;
            Fawe.debug("REID not detected — using vanilla biome path");
        } catch (Exception e) {
            available = false;
            Fawe.debug("REID detected but API reflection failed — falling back to vanilla biome path");
            Fawe.debug(e.toString());
        }
        initialized = true;
    }

    /**
     * Read the biome ID at a chunk-local position using REID's API.
     *
     * @return the biome ID, or -1 if REID is unavailable or the call fails
     */
    public static int getBiomeId(Chunk chunk, int localX, int localZ) {
        if (!isAvailable()) return -1;
        try {
            Object accessor = getBiomeAccessorMethod.invoke(biomeApiInstance, chunk);
            return (int) getBiomeIdMethod.invoke(accessor, localX, localZ);
        } catch (Exception e) {
            Fawe.debug("REID getBiomeId failed: " + e);
            return -1;
        }
    }

    /**
     * Apply FAWE's biome changes to a chunk through REID's API and notify clients.
     *
     * FAWE stores pending biome changes in a byte[256] where 0 means "no change"
     * and -1 means "set to biome 0". This method reads the chunk's current biomes
     * from REID, overlays the changes, writes the result back, and sends a network
     * packet so clients see the update without needing to rejoin.
     *
     * @param chunk      the NMS chunk to modify
     * @param faweBiomes FAWE's biome change array (byte[256], 0 = no change, -1 = biome 0)
     * @param world      the world containing the chunk (needed for network sync)
     * @param chunkX     chunk X coordinate (block coords = chunkX << 4)
     * @param chunkZ     chunk Z coordinate (block coords = chunkZ << 4)
     * @return true if biomes were applied via REID, false if caller should fall back
     */
    public static boolean applyBiomes(Chunk chunk, byte[] faweBiomes, World world, int chunkX, int chunkZ) {
        if (!isAvailable()) return false;
        try {
            // Read current biomes from REID
            Object accessor = getBiomeAccessorMethod.invoke(biomeApiInstance, chunk);
            int[] currentBiomes = (int[]) getBiomesMethod.invoke(accessor);

            // Overlay FAWE's changes
            boolean anyChanged = false;
            for (int i = 0; i < faweBiomes.length && i < currentBiomes.length; i++) {
                byte biome = faweBiomes[i];
                if (biome != 0) {
                    int newId = (biome == -1) ? 0 : (biome & 0xFF);
                    if (currentBiomes[i] != newId) {
                        currentBiomes[i] = newId;
                        anyChanged = true;
                    }
                }
            }

            if (!anyChanged) return true;

            // Write back through REID's API
            replaceBiomesMethod.invoke(biomeApiInstance, chunk, currentBiomes);

            // Send network packet so clients see the change immediately
            sendBiomeUpdate(world, chunkX, chunkZ, currentBiomes);

            return true;
        } catch (Exception e) {
            Fawe.debug("REID applyBiomes failed: " + e);
            return false;
        }
    }

    /**
     * Send a biome chunk change packet to all clients tracking this chunk.
     * Uses REID's MessageManager to push the updated biome data to players.
     */
    private static void sendBiomeUpdate(World world, int chunkX, int chunkZ, int[] biomes) {
        try {
            BlockPos pos = new BlockPos(chunkX << 4, 0, chunkZ << 4);
            sendBiomeChunkChangeMethod.invoke(null, world, pos, biomes);
        } catch (Exception e) {
            Fawe.debug("REID sendBiomeUpdate failed: " + e);
        }
    }
}
