package com.boydti.fawe.example;

import com.boydti.fawe.Fawe;
import com.boydti.fawe.FaweAPI;
import com.boydti.fawe.FaweCache;
import com.boydti.fawe.config.Settings;
import com.boydti.fawe.object.FaweChunk;
import com.boydti.fawe.object.FaweQueue;
import com.boydti.fawe.object.IntegerPair;
import com.boydti.fawe.object.RunnableVal;
import com.boydti.fawe.object.RunnableVal2;
import com.boydti.fawe.object.exception.FaweException;
import com.boydti.fawe.object.extent.LightingExtent;
import com.boydti.fawe.util.MainUtil;
import com.boydti.fawe.util.MathMan;
import com.boydti.fawe.util.SetQueue;
import com.boydti.fawe.util.TaskManager;
import com.sk89q.jnbt.CompoundTag;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.blocks.BlockMaterial;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.biome.BaseBiome;
import com.sk89q.worldedit.world.registry.BundledBlockData;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

public abstract class MappedFaweQueue<WORLD, CHUNK, CHUNKSECTIONS, SECTION> implements LightingExtent, FaweQueue {

    private WORLD impWorld;

    private IFaweQueueMap map;

    /**
     * Per-thread cache of the most recently accessed chunk/section. Avoids repeated
     * lookups when consecutive block operations hit the same chunk, which is the
     * common case for region iteration. ThreadLocal eliminates the race condition
     * that existed when these were bare public fields shared across threads.
     */
    protected static class SectionCache<CHUNK, CHUNKSECTIONS, SECTION> {
        public int lastSectionX = Integer.MIN_VALUE;
        public int lastSectionZ = Integer.MIN_VALUE;
        public int lastSectionY = Integer.MIN_VALUE;
        public CHUNK lastChunk;
        public CHUNKSECTIONS lastChunkSections;
        public SECTION lastSection;

        public void clear() {
            lastSectionX = Integer.MIN_VALUE;
            lastSectionZ = Integer.MIN_VALUE;
            lastSectionY = -1;
            lastChunk = null;
            lastChunkSections = null;
            lastSection = null;
        }
    }

    private final ThreadLocal<SectionCache<CHUNK, CHUNKSECTIONS, SECTION>> sectionCacheThreadLocal =
            ThreadLocal.withInitial(SectionCache::new);

    protected SectionCache<CHUNK, CHUNKSECTIONS, SECTION> getSectionCache() {
        return sectionCacheThreadLocal.get();
    }

    // Legacy accessors — kept for subclass compatibility
    public int getLastSectionX() { return getSectionCache().lastSectionX; }
    public int getLastSectionZ() { return getSectionCache().lastSectionZ; }
    public int getLastSectionY() { return getSectionCache().lastSectionY; }
    public CHUNK getLastChunk() { return getSectionCache().lastChunk; }
    public CHUNKSECTIONS getLastChunkSections() { return getSectionCache().lastChunkSections; }
    public SECTION getLastSection() { return getSectionCache().lastSection; }


    private World weWorld;
    private String world;
    private ConcurrentLinkedDeque<EditSession> sessions;
    private long modified = System.currentTimeMillis();
    private RunnableVal2<FaweChunk, FaweChunk> changeTask;
    private RunnableVal2<ProgressType, Integer> progressTask;
    private SetQueue.QueueStage stage;
    private Settings settings = Settings.IMP;
    public ConcurrentLinkedDeque<Runnable> tasks = new ConcurrentLinkedDeque<>();

    private CHUNK cachedLoadChunk;
    public final RunnableVal<IntegerPair> loadChunk = new RunnableVal<IntegerPair>() {

        {
            this.value = new IntegerPair(0, 0);
        }

        @Override
        public void run(IntegerPair coord) {
            cachedLoadChunk = loadChunk(getWorld(), coord.x, coord.z, true);
        }
    };

    public MappedFaweQueue(final World world) {
        this(world, null);
    }

    public MappedFaweQueue(final String world) {
        this.world = world;
        map = getSettings().PREVENT_CRASHES ? new WeakFaweQueueMap(this) : new DefaultFaweQueueMap(this);
    }

    public MappedFaweQueue(final String world, IFaweQueueMap map) {
        this.world = world;
        if (map == null) {
            map = getSettings().PREVENT_CRASHES ? new WeakFaweQueueMap(this) : new DefaultFaweQueueMap(this);
        }
        this.map = map;
    }

    public MappedFaweQueue(final World world, IFaweQueueMap map) {
        this.weWorld = world;
        if (world != null) this.world = Fawe.imp().getWorldName(world);
        if (map == null) {
            map = getSettings().PREVENT_CRASHES ? new WeakFaweQueueMap(this) : new DefaultFaweQueueMap(this);
        }
        this.map = map;
    }

    @Override
    public int getMaxY() {
        return weWorld == null ? 255 : weWorld.getMaxY();
    }

    public IFaweQueueMap getFaweQueueMap() {
        return map;
    }

    @Override
    public Collection<FaweChunk> getFaweChunks() {
        return map.getFaweCunks();
    }

    @Override
    public void optimize() {
        final ForkJoinPool pool = TaskManager.IMP.getPublicForkJoinPool();
        map.forEachChunk(new RunnableVal<FaweChunk>() {
            @Override
            public void run(final FaweChunk chunk) {
                pool.submit(new Runnable() {
                    @Override
                    public void run() {
                        chunk.optimize();
                    }
                });
            }
        });
        pool.awaitQuiescence(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
    }

    public abstract WORLD getImpWorld();

    public abstract boolean regenerateChunk(WORLD world, int x, int z, BaseBiome biome, Long seed);

    @Override
    public abstract FaweChunk getFaweChunk(int x, int z);

    public abstract CHUNK loadChunk(WORLD world, int x, int z, boolean generate);

    public abstract CHUNKSECTIONS getSections(CHUNK chunk);

    public abstract CHUNKSECTIONS getCachedSections(WORLD world, int cx, int cz);

    public abstract CHUNK getCachedChunk(WORLD world, int cx, int cz);

    public WORLD getWorld() {
        if (impWorld != null) {
            return impWorld;
        }
        return impWorld = getImpWorld();
    }

    @Override
    public boolean regenerateChunk(int x, int z, BaseBiome biome, Long seed) {
        return regenerateChunk(getWorld(), x, z, biome, seed);
    }

    @Override
    public void addNotifyTask(int x, int z, Runnable runnable) {
        FaweChunk chunk = map.getFaweChunk(x, z);
        chunk.addNotifyTask(runnable);
    }

    @Override
    public boolean setBlock(int x, int y, int z, int id, int data) {
        int cx = x >> 4;
        int cz = z >> 4;
        FaweChunk chunk = map.getFaweChunk(cx, cz);
        chunk.setBlock(x & 15, y, z & 15, id, data);
        return true;
    }

    @Override
    public boolean setBlock(int x, int y, int z, int id) {
        int cx = x >> 4;
        int cz = z >> 4;
        FaweChunk chunk = map.getFaweChunk(cx, cz);
        chunk.setBlock(x & 15, y, z & 15, id);
        return true;
    }

    @Override
    public void setTile(int x, int y, int z, CompoundTag tag) {
        if ((y >= FaweChunk.HEIGHT) || (y < 0)) {
            return;
        }
        int cx = x >> 4;
        int cz = z >> 4;
        FaweChunk chunk = map.getFaweChunk(cx, cz);
        chunk.setTile(x & 15, y, z & 15, tag);
    }

    @Override
    public void setEntity(int x, int y, int z, CompoundTag tag) {
        if ((y >= FaweChunk.HEIGHT) || (y < 0)) {
            return;
        }
        int cx = x >> 4;
        int cz = z >> 4;
        FaweChunk chunk = map.getFaweChunk(cx, cz);
        chunk.setEntity(tag);
    }

    @Override
    public void removeEntity(int x, int y, int z, UUID uuid) {
        if ((y >= FaweChunk.HEIGHT) || (y < 0)) {
            return;
        }
        int cx = x >> 4;
        int cz = z >> 4;
        FaweChunk chunk = map.getFaweChunk(cx, cz);
        chunk.removeEntity(uuid);
    }

    @Override
    public boolean setBiome(int x, int z, BaseBiome biome) {
        int cx = x >> 4;
        int cz = z >> 4;
        FaweChunk chunk = map.getFaweChunk(cx, cz);
        chunk.setBiome(x & 15, z & 15, biome);
        return true;
    }

    @Override
    public boolean next(int amount, long time) {
        return map.next(amount, time);
    }

    public void start(FaweChunk chunk) {
        chunk.start();
    }

    public void end(FaweChunk chunk) {
        if (getProgressTask() != null) {
            getProgressTask().run(ProgressType.DISPATCH, size() + 1);
        }
        chunk.end();
    }

    @Override
    public void runTasks() {
        synchronized (this) {
            this.notifyAll();
        }
        if (getProgressTask() != null) {
            try {
                getProgressTask().run(ProgressType.DONE, 1);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        while (!tasks.isEmpty()) {
            Runnable task = tasks.poll();
            if (task != null) {
                try {
                    task.run();
                } catch (Throwable e) {
                    MainUtil.handleError(e);
                }
            }
        }
        if (getProgressTask() != null) {
            try {
                getProgressTask().run(ProgressType.DONE, 1);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        ArrayDeque<Runnable> tmp = new ArrayDeque<>(tasks);
        tasks.clear();
        for (Runnable run : tmp) {
            try {
                run.run();
            } catch (Throwable e) {
                MainUtil.handleError(e);
            }
        }
    }

    public Settings getSettings() {
        return settings;
    }

    public void setSettings(Settings settings) {
        this.settings = settings == null ? Settings.IMP : settings;
    }

    public void setWorld(String world) {
        this.world = world;
        this.weWorld = null;
    }

    public World getWEWorld() {
        return weWorld != null ? weWorld : (weWorld = FaweAPI.getWorld(world));
    }

    public String getWorldName() {
        return world;
    }

    @Override
    public Collection<EditSession> getEditSessions() {
        Collection<EditSession> tmp = sessions;
        if (tmp == null) tmp = new HashSet<>();
        return tmp;
    }

    @Override
    public void addEditSession(EditSession session) {
        ConcurrentLinkedDeque<EditSession> tmp = sessions;
        if (tmp == null) tmp = new ConcurrentLinkedDeque<>();
        tmp.add(session);
        this.sessions = tmp;
    }

    @Override
    public boolean supports(Capability capability) {
        switch (capability) {
            case CHANGE_TASKS: return true;
        }
        return false;
    }

    public void setSessions(ConcurrentLinkedDeque<EditSession> sessions) {
        this.sessions = sessions;
    }

    public long getModified() {
        return modified;
    }

    public void setModified(long modified) {
        this.modified = modified;
    }

    public RunnableVal2<ProgressType, Integer> getProgressTask() {
        return progressTask;
    }

    public void setProgressTask(RunnableVal2<ProgressType, Integer> progressTask) {
        this.progressTask = progressTask;
    }

    public void setChangeTask(RunnableVal2<FaweChunk, FaweChunk> changeTask) {
        this.changeTask = changeTask;
    }

    public RunnableVal2<FaweChunk, FaweChunk> getChangeTask() {
        return changeTask;
    }

    public SetQueue.QueueStage getStage() {
        return stage;
    }

    public void setStage(SetQueue.QueueStage stage) {
        this.stage = stage;
    }

    public void addNotifyTask(Runnable runnable) {
        this.tasks.add(runnable);
    }

    public void addTask(Runnable whenFree) {
        tasks.add(whenFree);
    }

    @Override
    public int size() {
        int size = map.size();
        if (size == 0 && getStage() == SetQueue.QueueStage.NONE) {
            runTasks();
        }
        return size;
    }

    @Override
    public void clear() {
        getSectionCache().clear();
        map.clear();
        runTasks();
    }

    @Override
    public void setChunk(FaweChunk chunk) {
        map.add(chunk);
    }

    public SECTION getCachedSection(CHUNKSECTIONS chunk, int cy) {
        return (SECTION) getSectionCache().lastChunkSections;
    }

    /**
     * Ensures the section cache is populated for the given chunk/section coordinates.
     * Returns the cached section, or null if the chunk or section doesn't exist.
     */
    protected SECTION ensureSectionCached(int cx, int cy, int cz) throws FaweException.FaweChunkLoadException {
        SectionCache<CHUNK, CHUNKSECTIONS, SECTION> cache = getSectionCache();
        if (cx != cache.lastSectionX || cz != cache.lastSectionZ) {
            cache.lastSectionX = cx;
            cache.lastSectionZ = cz;
            cache.lastSectionY = cy;
            cache.lastChunk = ensureChunkLoaded(cx, cz);
            if (cache.lastChunk != null) {
                cache.lastChunkSections = getSections(cache.lastChunk);
                cache.lastSection = getCachedSection(cache.lastChunkSections, cy);
            } else {
                cache.lastChunkSections = null;
                cache.lastSection = null;
            }
        } else if (cy != cache.lastSectionY) {
            cache.lastSectionY = cy;
            if (cache.lastChunkSections != null) {
                cache.lastSection = getCachedSection(cache.lastChunkSections, cy);
            } else {
                cache.lastSection = null;
            }
        }
        return cache.lastSection;
    }

    public abstract int getCombinedId4Data(SECTION section, int x, int y, int z);

    public int getLocalCombinedId4Data(CHUNK chunk, int x, int y, int z) {
        SectionCache<CHUNK, CHUNKSECTIONS, SECTION> cache = getSectionCache();
        CHUNKSECTIONS sections = getSections(cache.lastChunk);
        SECTION section = getCachedSection(sections, y >> 4);
        if (section == null) {
            return 0;
        }
        return getCombinedId4Data(cache.lastSection, x, y, z);
    }

    public abstract int getBiome(CHUNK chunk, int x, int z);

    public abstract CompoundTag getTileEntity(CHUNK chunk, int x, int y, int z);

    public CHUNK ensureChunkLoaded(int cx, int cz) throws FaweException.FaweChunkLoadException {
        CHUNK chunk = getCachedChunk(getWorld(), cx, cz);
        if (chunk != null) {
            return chunk;
        }
        boolean sync = Fawe.isMainThread();
        if (sync) {
            return loadChunk(getWorld(), cx, cz, true);
        } else if (getSettings().HISTORY.CHUNK_WAIT_MS > 0) {
            cachedLoadChunk = null;
            loadChunk.value.x = cx;
            loadChunk.value.z = cz;
            TaskManager.IMP.syncWhenFree(loadChunk, getSettings().HISTORY.CHUNK_WAIT_MS);
            return cachedLoadChunk;
        } else {
            return null;
        }
    }

    public boolean queueChunkLoad(final int cx, final int cz) {
        CHUNK chunk = getCachedChunk(getWorld(), cx, cz);
        if (chunk == null) {
            SetQueue.IMP.addTask(new Runnable() {
                @Override
                public void run() {
                    loadChunk(getWorld(), cx, cz, true);
                }
            });
            return true;
        }
        return false;
    }

    public boolean queueChunkLoad(final int cx, final int cz, RunnableVal<CHUNK> operation) {
        operation.value = getCachedChunk(getWorld(), cx, cz);
        if (operation.value == null) {
            SetQueue.IMP.addTask(new Runnable() {
                @Override
                public void run() {
                    operation.value = loadChunk(getWorld(), cx, cz, true);
                    if (operation.value != null) TaskManager.IMP.async(operation);
                }
            });
            return true;
        } else {
            TaskManager.IMP.async(operation);
        }
        return false;
    }

    @Override
    public boolean hasBlock(int x, int y, int z) throws FaweException.FaweChunkLoadException {
        SECTION section = ensureSectionCached(x >> 4, y >> 4, z >> 4);
        if (section == null) return false;
        return hasBlock(section, x, y, z);
    }

    public boolean hasBlock(SECTION section, int x, int y, int z) {
        return getCombinedId4Data(section, x, y, z) != 0;
    }

    public int getOpacity(SECTION section, int x, int y, int z) {
        int combined = getCombinedId4Data(section, x, y, z);
        if (combined == 0) {
            return 0;
        }
        BlockMaterial block = BundledBlockData.getInstance().getMaterialById(FaweCache.getId(combined));
        if (block == null) {
            return 15;
        }
        return Math.min(15, block.getLightOpacity());
    }

    public int getBrightness(SECTION section, int x, int y, int z) {
        int combined = getCombinedId4Data(section, x, y, z);
        if (combined == 0) {
            return 0;
        }
        BlockMaterial block = BundledBlockData.getInstance().getMaterialById(FaweCache.getId(combined));
        if (block == null) {
            return 15;
        }
        return Math.min(15, block.getLightValue());
    }

    public int getOpacityBrightnessPair(SECTION section, int x, int y, int z) {
        return MathMan.pair16(Math.min(15, getOpacity(section, x, y, z)), getBrightness(section, x, y, z));
    }

    public abstract int getSkyLight(SECTION sections, int x, int y, int z);

    public abstract int getEmmittedLight(SECTION sections, int x, int y, int z);

    public int getLight(SECTION sections, int x, int y, int z) {
        if (!hasSky()) {
            return getEmmittedLight(sections, x, y, z);
        }
        return Math.max(getSkyLight(sections, x, y, z), getEmmittedLight(sections, x, y, z));
    }

    @Override
    public int getLight(int x, int y, int z) {
        SECTION section = ensureSectionCached(x >> 4, y >> 4, z >> 4);
        if (section == null) return 0;
        return getLight(section, x, y, z);
    }

    @Override
    public int getSkyLight(int x, int y, int z) {
        int cy = y >> 4;
        SECTION section = ensureSectionCached(x >> 4, cy, z >> 4);
        if (section == null) {
            SectionCache<CHUNK, CHUNKSECTIONS, SECTION> cache = getSectionCache();
            if (cache.lastChunkSections == null) {
                return 0;
            }
            int max = FaweChunk.HEIGHT >> 4;
            do {
                if (++cy >= max) {
                    return 15;
                }
                section = getCachedSection(cache.lastChunkSections, cy);
            } while (section == null);
            cache.lastSection = section;
            cache.lastSectionY = cy;
        }
        if (section == null) {
            return getSkyLight(x, y + 16, z);
        }
        return getSkyLight(section, x, y, z);
    }

    @Override
    public int getBlockLight(int x, int y, int z) {
        return getEmmittedLight(x, y, z);
    }

    @Override
    public int getEmmittedLight(int x, int y, int z) {
        SECTION section = ensureSectionCached(x >> 4, y >> 4, z >> 4);
        if (section == null) return 0;
        return getEmmittedLight(section, x, y, z);
    }

    @Override
    public int getOpacity(int x, int y, int z) {
        SECTION section = ensureSectionCached(x >> 4, y >> 4, z >> 4);
        if (section == null) return 0;
        return getOpacity(section, x, y, z);
    }

    @Override
    public int getOpacityBrightnessPair(int x, int y, int z) {
        SECTION section = ensureSectionCached(x >> 4, y >> 4, z >> 4);
        if (section == null) return 0;
        return getOpacityBrightnessPair(section, x, y, z);
    }

    @Override
    public int getBrightness(int x, int y, int z) {
        SECTION section = ensureSectionCached(x >> 4, y >> 4, z >> 4);
        if (section == null) return 0;
        return getBrightness(section, x, y, z);
    }

    @Override
    public int getCachedCombinedId4Data(int x, int y, int z) throws FaweException.FaweChunkLoadException {
        int cx = x >> 4;
        int cz = z >> 4;
        FaweChunk fc = map.getCachedFaweChunk(cx, cz);
        if (fc != null) {
            int combined = fc.getBlockCombinedId(x & 15, y, z & 15);
            if (combined != 0) {
                return combined;
            }
        }
        SECTION section = ensureSectionCached(cx, y >> 4, cz);
        if (section == null) return 0;
        return getCombinedId4Data(section, x, y, z);
    }

    @Override
    public int getCombinedId4Data(int x, int y, int z) throws FaweException.FaweChunkLoadException {
        SECTION section = ensureSectionCached(x >> 4, y >> 4, z >> 4);
        if (section == null) return 0;
        return getCombinedId4Data(section, x, y, z);
    }

    /**
     * Ensures the chunk cache is populated for the given chunk coordinates.
     * Invalidates the section cache since biome/tile lookups don't need a section.
     */
    protected CHUNK ensureChunkCached(int cx, int cz) throws FaweException.FaweChunkLoadException {
        SectionCache<CHUNK, CHUNKSECTIONS, SECTION> cache = getSectionCache();
        cache.lastSectionY = -1;
        if (cx != cache.lastSectionX || cz != cache.lastSectionZ) {
            cache.lastSectionX = cx;
            cache.lastSectionZ = cz;
            cache.lastChunk = ensureChunkLoaded(cx, cz);
            if (cache.lastChunk != null) {
                cache.lastChunkSections = getSections(cache.lastChunk);
            } else {
                cache.lastChunkSections = null;
            }
        }
        return cache.lastChunk;
    }

    @Override
    public int getBiomeId(int x, int z) throws FaweException.FaweChunkLoadException {
        CHUNK chunk = ensureChunkCached(x >> 4, z >> 4);
        if (chunk == null) return 0;
        return getBiome(chunk, x, z) & 0xFF;
    }

    @Override
    public CompoundTag getTileEntity(int x, int y, int z) throws FaweException.FaweChunkLoadException {
        CHUNK chunk = ensureChunkCached(x >> 4, z >> 4);
        if (chunk == null) return null;
        return getTileEntity(chunk, x, y, z);
    }
}
