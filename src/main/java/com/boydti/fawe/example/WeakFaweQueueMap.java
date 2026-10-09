package com.boydti.fawe.example;

import com.boydti.fawe.config.BBC;
import com.boydti.fawe.object.FawePlayer;
import com.boydti.fawe.util.MainUtil;
import com.sk89q.worldedit.EditSession;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import com.boydti.fawe.object.FaweChunk;
import com.boydti.fawe.object.FaweQueue;
import com.boydti.fawe.object.RunnableVal;
import com.boydti.fawe.util.MathMan;
import com.boydti.fawe.util.SetQueue;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class WeakFaweQueueMap implements IFaweQueueMap {

    private final MappedFaweQueue parent;

    public WeakFaweQueueMap(MappedFaweQueue parent) {
        this.parent = parent;
    }

    public final Long2ObjectOpenHashMap<Reference<FaweChunk>> blocks = new Long2ObjectOpenHashMap<Reference<FaweChunk>>() {
        @Override
        public Reference<FaweChunk> put(Long key, Reference<FaweChunk> value) {
            return put((long) key, value);
        }

        @Override
        public Reference<FaweChunk> put(long key, Reference<FaweChunk> value) {
            if (parent.getProgressTask() != null) {
                try {
                    parent.getProgressTask().run(FaweQueue.ProgressType.QUEUE, size());
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
            synchronized (this) {
                return super.put(key, value);
            }
        }
    };

    @Override
    public Collection<FaweChunk> getFaweCunks() {
        HashSet<FaweChunk> set = new HashSet<>();
        synchronized (blocks) {
            Iterator<Map.Entry<Long, Reference<FaweChunk>>> iter = blocks.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<Long, Reference<FaweChunk>> entry = iter.next();
                FaweChunk value = entry.getValue().get();
                if (value != null) {
                    set.add(value);
                } else {
                    chunkLost();
                    iter.remove();
                }
            }
            return set;
        }
    }

    @Override
    public void forEachChunk(RunnableVal<FaweChunk> onEach) {
        synchronized (blocks) {
            Iterator<Map.Entry<Long, Reference<FaweChunk>>> iter = blocks.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<Long, Reference<FaweChunk>> entry = iter.next();
                FaweChunk value = entry.getValue().get();
                if (value != null) {
                    onEach.run(value);
                } else {
                    chunkLost();
                    iter.remove();
                }
            }
        }
    }

    @Override
    public FaweChunk getFaweChunk(int cx, int cz) {
        if (cx == lastX && cz == lastZ) {
            return lastWrappedChunk;
        }
        long pair = MathMan.pairInt(cx, cz);
        Reference<FaweChunk> chunkReference = this.blocks.get(pair);
        FaweChunk chunk;
        if (chunkReference == null || (chunk = chunkReference.get()) == null) {
            if (chunkReference != null) {
                // The pending changes for this chunk were collected; continuing starts it over
                chunkLost();
            }
            chunk = this.getNewFaweChunk(cx, cz);
            Reference<FaweChunk> previous = this.blocks.put(pair, new SoftReference(chunk));
            if (previous != null) {
                FaweChunk tmp = previous.get();
                if (tmp != null) {
                    chunk = tmp;
                    this.blocks.put(pair, previous);
                }
            }

        }
        return chunk;
    }

    @Override
    public FaweChunk getCachedFaweChunk(int cx, int cz) {
        if (cx == lastX && cz == lastZ) {
            return lastWrappedChunk;
        }
        long pair = MathMan.pairInt(cx, cz);
        Reference<FaweChunk> reference = this.blocks.get(pair);
        if (reference != null) {
            return reference.get();
        } else {
            return null;
        }
    }

    @Override
    public void add(FaweChunk chunk) {
        long pair = MathMan.pairInt(chunk.getX(), chunk.getZ());
        Reference<FaweChunk> previous = this.blocks.put(pair, new SoftReference<FaweChunk>(chunk));
        if (previous != null) {
            FaweChunk previousChunk = previous.get();
            if (previousChunk != null) {
                blocks.put(pair, previous);
            }
        }
    }


    @Override
    public void clear() {
        blocks.clear();
    }

    @Override
    public int size() {
        return blocks.size();
    }

    private final AtomicInteger lostChunks = new AtomicInteger();

    /**
     * A queued chunk was reclaimed by the garbage collector before it was applied, so part of
     * the edit is silently missing from the world and from its history. Tell the console and
     * the players whose edits use this queue (once per queue), so the loss is not invisible.
     */
    private void chunkLost() {
        if (lostChunks.getAndIncrement() != 0) {
            return;
        }
        String message = "Server ran low on memory: part of an edit in " + parent.getWorldName()
                + " was skipped and will not be undoable (PREVENT_CRASHES is enabled)";
        LogManager.getLogger("FAWE").warn(message);
        try {
            for (Object session : parent.getEditSessions()) {
                FawePlayer player = ((EditSession) session).getPlayer();
                if (player != null) {
                    player.sendMessage(BBC.getPrefix() + message);
                }
            }
        } catch (Throwable e) {
            MainUtil.handleError(e);
        }
    }

    private FaweChunk getNewFaweChunk(int cx, int cz) {
        return parent.getFaweChunk(cx, cz);
    }

    private FaweChunk lastWrappedChunk;
    private int lastX = Integer.MIN_VALUE;
    private int lastZ = Integer.MIN_VALUE;

    @Override
    public boolean next(int amount, long time) {
        synchronized (blocks) {
            try {
                boolean skip = parent.getStage() == SetQueue.QueueStage.INACTIVE;
                int added = 0;
                Iterator<Map.Entry<Long, Reference<FaweChunk>>> iter = blocks.entrySet().iterator();
                if (amount == 1) {
                    long start = System.currentTimeMillis();
                    do {
                        if (iter.hasNext()) {
                            Map.Entry<Long, Reference<FaweChunk>> entry = iter.next();
                            Reference<FaweChunk> chunkReference = entry.getValue();
                            FaweChunk chunk = chunkReference.get();
                            if (skip && chunk == lastWrappedChunk) {
                                continue;
                            }
                            iter.remove();
                            if (chunk != null) {
                                parent.start(chunk);
                                chunk.call();
                                parent.end(chunk);
                            } else {
                                chunkLost();
                            }
                        } else {
                            break;
                        }
                    } while (System.currentTimeMillis() - start < time);
                    return !blocks.isEmpty();
                }
                ExecutorCompletionService service = SetQueue.IMP.getCompleterService();
                ForkJoinPool pool = SetQueue.IMP.getForkJoinPool();
                boolean result = true;
                // amount = 8;
                for (int i = 0; i < amount && (result = iter.hasNext()); ) {
                    Map.Entry<Long, Reference<FaweChunk>> item = iter.next();
                    Reference<FaweChunk> chunkReference = item.getValue();
                    FaweChunk chunk = chunkReference.get();
                    if (skip && chunk == lastWrappedChunk) {
                        continue;
                    }
                    iter.remove();
                    if (chunk != null) {
                        parent.start(chunk);
                        service.submit(chunk);
                        added++;
                        i++;
                    } else {
                        chunkLost();
                    }
                }
                // if result, then submitted = amount
                if (result) {
                    long start = System.currentTimeMillis();
                    while (System.currentTimeMillis() - start < time && result) {
                        if (result = iter.hasNext()) {
                            Map.Entry<Long, Reference<FaweChunk>> item = iter.next();
                            Reference<FaweChunk> chunkReference = item.getValue();
                            FaweChunk chunk = chunkReference.get();
                            if (skip && chunk == lastWrappedChunk) {
                                continue;
                            }
                            iter.remove();
                            if (chunk != null) {
                                parent.start(chunk);
                                service.submit(chunk);
                                Future future = service.poll(50, TimeUnit.MILLISECONDS);
                                if (future != null) {
                                    FaweChunk fc = (FaweChunk) future.get();
                                    parent.end(fc);
                                }
                            }
                        }
                    }
                }
                pool.awaitQuiescence(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
                Future future;
                while ((future = service.poll()) != null) {
                    FaweChunk fc = (FaweChunk) future.get();
                    parent.end(fc);
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
            return !blocks.isEmpty();
        }
    }
}
