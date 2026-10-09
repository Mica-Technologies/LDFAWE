package com.boydti.fawe.object.changeset;

import com.boydti.fawe.object.FaweInputStream;
import com.boydti.fawe.object.change.MutableBlockChange;
import com.boydti.fawe.object.change.MutableFullBlockChange;
import com.sk89q.jnbt.NBTInputStream;
import com.sk89q.jnbt.NBTOutputStream;
import com.sk89q.worldedit.Vector2D;
import com.sk89q.worldedit.WorldVector;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.extent.inventory.BlockBag;
import com.sk89q.worldedit.history.UndoContext;
import com.sk89q.worldedit.history.change.Change;
import com.sk89q.worldedit.world.biome.BaseBiome;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trips block changes through the history stream format, which undo and redo read back.
 * Block ids above 4095 (RoughlyEnoughIDs) give combined ids that do not fit in 16 bits.
 */
class FaweStreamChangeSetTest {

    /** Minimal in-memory change set: only the block and biome streams are backed by real storage. */
    static class InMemoryChangeSet extends FaweStreamChangeSet {
        private ByteArrayOutputStream blocks;
        private ByteArrayOutputStream biomes;

        InMemoryChangeSet(boolean storeRedo, boolean smallLoc) {
            super("test", 0, storeRedo, smallLoc);
        }

        /** Replays raw bytes (header included) as if they had been read back from disk. */
        InMemoryChangeSet(byte[] raw) {
            super("test", 0, true, false);
            blocks = new ByteArrayOutputStream();
            blocks.write(raw, 0, raw.length);
        }

        @Override
        public OutputStream getBlockOS(int x, int y, int z) throws IOException {
            if (blocks == null) {
                blocks = new ByteArrayOutputStream();
                writeHeader(blocks, x, y, z);
            }
            return blocks;
        }

        @Override
        public FaweInputStream getBlockIS() throws IOException {
            if (blocks == null) {
                return null;
            }
            FaweInputStream is = new FaweInputStream(new ByteArrayInputStream(blocks.toByteArray()));
            readHeader(is);
            return is;
        }

        @Override
        public int getCompressedSize() {
            return blocks == null ? 0 : blocks.size();
        }

        @Override
        public long getSizeInMemory() {
            return getCompressedSize();
        }

        @Override
        public OutputStream getBiomeOS() {
            if (biomes == null) {
                biomes = new ByteArrayOutputStream();
            }
            return biomes;
        }

        @Override
        public NBTOutputStream getEntityCreateOS() {
            return null;
        }

        @Override
        public NBTOutputStream getEntityRemoveOS() {
            return null;
        }

        @Override
        public NBTOutputStream getTileCreateOS() {
            return null;
        }

        @Override
        public NBTOutputStream getTileRemoveOS() {
            return null;
        }

        @Override
        public FaweInputStream getBiomeIS() {
            return biomes == null ? null : new FaweInputStream(new ByteArrayInputStream(biomes.toByteArray()));
        }

        @Override
        public NBTInputStream getEntityCreateIS() {
            return null;
        }

        @Override
        public NBTInputStream getEntityRemoveIS() {
            return null;
        }

        @Override
        public NBTInputStream getTileCreateIS() {
            return null;
        }

        @Override
        public NBTInputStream getTileRemoveIS() {
            return null;
        }
    }

    private static int combined(int id, int data) {
        return (id << 4) | data;
    }

    private static List<int[]> readBlocks(FaweStreamChangeSet set, boolean redo) throws IOException {
        List<int[]> out = new ArrayList<>();
        Iterator<MutableBlockChange> iter = set.getBlockIterator(redo);
        while (iter.hasNext()) {
            MutableBlockChange c = iter.next();
            out.add(new int[]{c.x, c.y, c.z, c.id, c.data});
        }
        return out;
    }

    @ParameterizedTest(name = "storeRedo={0}, smallLoc={1}")
    @CsvSource({"true,false", "true,true", "false,false", "false,true"})
    void highIdsSurviveUndoAndRedo(boolean storeRedo, boolean smallLoc) throws IOException {
        InMemoryChangeSet set = new InMemoryChangeSet(storeRedo, smallLoc);
        // (from, to) pairs covering low ids, the 4095:15 boundary and ids well above 4095
        int[][] changes = {
                {combined(6797, 3), combined(1, 0)},
                {combined(4095, 15), combined(6658, 1)},
                {combined(4096, 0), 0},
                {combined(2, 0), combined(200000, 15)},
        };
        for (int i = 0; i < changes.length; i++) {
            set.add(100 + i, 64 + i, -50 - i, changes[i][0], changes[i][1]);
        }

        List<int[]> undo = readBlocks(set, false);
        assertEquals(changes.length, undo.size());
        for (int i = 0; i < changes.length; i++) {
            int[] c = undo.get(i);
            assertEquals(100 + i, c[0]);
            assertEquals(64 + i, c[1]);
            assertEquals(-50 - i, c[2]);
            assertEquals(changes[i][0] >> 4, c[3], "undo id of change " + i);
            assertEquals(changes[i][0] & 15, c[4], "undo data of change " + i);
        }

        if (storeRedo) {
            List<int[]> redo = readBlocks(set, true);
            for (int i = 0; i < changes.length; i++) {
                assertEquals(changes[i][1] >> 4, redo.get(i)[3], "redo id of change " + i);
                assertEquals(changes[i][1] & 15, redo.get(i)[4], "redo data of change " + i);
            }
        }
    }

    @Test
    void blockBagRedoPlacesTheNewBlock() throws IOException {
        InMemoryChangeSet set = new InMemoryChangeSet(true, false);
        set.add(0, 10, 0, combined(6797, 3), combined(1, 0));

        Iterator<MutableFullBlockChange> undo = set.getFullBlockIterator(null, 0, false);
        MutableFullBlockChange u = undo.next();
        assertEquals(combined(6797, 3), u.from, "undo places the old block");
        assertEquals(combined(1, 0), u.to);

        Iterator<MutableFullBlockChange> redo = set.getFullBlockIterator(null, 0, true);
        MutableFullBlockChange r = redo.next();
        assertEquals(combined(1, 0), r.from, "redo places the new block");
        assertEquals(combined(6797, 3), r.to);
    }

    @Test
    void legacyTwoByteStreamStillDecodes() throws IOException {
        // Mode 3 (store redo, full positions) without the wide-id flag, as written by older
        // versions: header (mode + origin x/z), then per change a 5-byte position delta and two
        // little-endian 16-bit combined ids.
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        raw.write(3);
        for (int i = 0; i < 8; i++) {
            raw.write(0);
        }
        int from = combined(4095, 15);
        int to = combined(35, 14);
        raw.write(7);
        raw.write(0);   // dx = 7
        raw.write(9);
        raw.write(0);   // dz = 9
        raw.write(70);  // dy = 70
        raw.write(from & 0xff);
        raw.write(from >> 8);
        raw.write(to & 0xff);
        raw.write(to >> 8);

        InMemoryChangeSet set = new InMemoryChangeSet(raw.toByteArray());
        List<int[]> undo = readBlocks(set, false);
        assertEquals(1, undo.size());
        assertEquals(7, undo.get(0)[0]);
        assertEquals(70, undo.get(0)[1]);
        assertEquals(9, undo.get(0)[2]);
        assertEquals(4095, undo.get(0)[3]);
        assertEquals(15, undo.get(0)[4]);

        List<int[]> redo = readBlocks(set, true);
        assertEquals(35, redo.get(0)[3]);
        assertEquals(14, redo.get(0)[4]);

        MutableFullBlockChange full = set.getFullBlockIterator(null, 0, false).next();
        assertEquals(from, full.from, "16-bit ids above 0x7FFF must not sign-extend");
    }

    /** Undoes every change of the set, recording the biome columns that get set. */
    private static List<String> undoBiomes(Iterator<? extends Change> changes) throws Exception {
        List<String> columns = new ArrayList<>();
        Extent extent = (Extent) Proxy.newProxyInstance(Extent.class.getClassLoader(), new Class[]{Extent.class}, (proxy, method, args) -> {
            if (method.getName().equals("setBiome") && args.length == 2) {
                Vector2D pos = (Vector2D) args[0];
                columns.add(pos.getBlockX() + "," + pos.getBlockZ() + "=" + ((BaseBiome) args[1]).getId());
                return true;
            }
            Class<?> type = method.getReturnType();
            return type == boolean.class ? Boolean.FALSE : type == int.class ? 0 : null;
        });
        UndoContext context = new UndoContext();
        context.setExtent(extent);
        while (changes.hasNext()) {
            changes.next().undo(context);
        }
        return columns;
    }

    @Test
    void biomeUndoOnlyTouchesRecordedColumns() throws Exception {
        InMemoryChangeSet set = new InMemoryChangeSet(true, false);
        set.addBiomeChange(5, -7, new BaseBiome(4), new BaseBiome(2));

        List<String> undone = undoBiomes(set.getBiomeIterator(false));
        assertEquals(Collections.singletonList("5,-7=4"), undone, "no stray biome change at 0,0");
    }

    @Test
    void biomeIdsAbove255SurviveUndo() throws Exception {
        InMemoryChangeSet set = new InMemoryChangeSet(true, false);
        set.addBiomeChange(5, -7, new BaseBiome(300), new BaseBiome(0));
        set.addBiomeChange(-3, 9, new BaseBiome(0), new BaseBiome(4000));

        List<String> undone = undoBiomes(set.getBiomeIterator(false));
        assertEquals(java.util.Arrays.asList("5,-7=300", "-3,9=0"), undone);
    }

    @Test
    void legacyOneByteBiomeStreamStillDecodes() throws Exception {
        // Written by older versions: no header, then x and z as big-endian ints and 1-byte ids
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        int x = 5, z = -7;
        for (int v : new int[]{x, z}) {
            raw.write(v >> 24);
            raw.write(v >> 16);
            raw.write(v >> 8);
            raw.write(v);
        }
        raw.write(200);
        raw.write(2);
        InMemoryChangeSet set = new InMemoryChangeSet(true, false);
        set.getBiomeOS().write(raw.toByteArray());

        List<String> undone = undoBiomes(set.getBiomeIterator(false));
        assertEquals(Collections.singletonList("5,-7=200"), undone);
    }

    @Test
    void blockBagUndoAlsoUndoesBiomes() throws Exception {
        // A biome-only edit has no block stream; inventory-mode undo must not fail on it
        InMemoryChangeSet set = new InMemoryChangeSet(true, false);
        set.addBiomeChange(5, -7, new BaseBiome(4), new BaseBiome(2));

        List<String> undone = undoBiomes(set.getIterator(new BlockBagStub(), 1, false));
        assertEquals(Collections.singletonList("5,-7=4"), undone);
    }

    /** Block bag that is never consulted, since the edit has no block changes. */
    static class BlockBagStub extends BlockBag {
        @Override
        public void fetchBlock(int id) {
        }

        @Override
        public void storeBlock(int id) {
        }

        @Override
        public void flushChanges() {
        }

        @Override
        public void addSourcePosition(WorldVector pos) {
        }

        @Override
        public void addSingleSourcePosition(WorldVector pos) {
        }
    }

    @Test
    void newHistoriesUseTheWideIdFormat() throws IOException {
        InMemoryChangeSet set = new InMemoryChangeSet(true, false);
        set.add(0, 0, 0, 0, combined(1, 0));
        byte mode = set.blocks.toByteArray()[0];
        assertTrue((mode & FaweStreamChangeSet.MODE_WIDE_IDS) != 0);
        assertEquals(3, mode & 0x0F);
        assertFalse(readBlocks(set, false).isEmpty());
    }
}
