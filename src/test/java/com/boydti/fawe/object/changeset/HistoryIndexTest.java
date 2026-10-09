package com.boydti.fawe.object.changeset;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HistoryIndexTest {

    @Test
    void indexesNeverRepeatEvenWhenEditsWriteNothing(@TempDir Path dir) throws IOException {
        File folder = dir.toFile();
        Files.createFile(dir.resolve("1.bd"));
        Files.createFile(dir.resolve("2.bd"));

        assertEquals(3, DiskStorageHistory.nextIndex(folder));
        // Edit 3 changed nothing and wrote no files; the next edit must not reuse 3
        assertEquals(4, DiskStorageHistory.nextIndex(folder));
        // Files written with a higher number (e.g. by an older run's leftovers) still win
        Files.createFile(dir.resolve("10.bd"));
        assertEquals(11, DiskStorageHistory.nextIndex(folder));
        assertEquals(12, DiskStorageHistory.nextIndex(folder));
    }

    @Test
    void separateFoldersCountSeparately(@TempDir Path dir) throws IOException {
        File a = dir.resolve("a").toFile();
        File b = dir.resolve("b").toFile();
        a.mkdirs();
        b.mkdirs();
        assertEquals(1, DiskStorageHistory.nextIndex(a));
        assertEquals(1, DiskStorageHistory.nextIndex(b));
        assertEquals(2, DiskStorageHistory.nextIndex(a));
    }
}
