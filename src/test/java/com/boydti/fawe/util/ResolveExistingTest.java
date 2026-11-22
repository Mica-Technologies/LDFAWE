package com.boydti.fawe.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Names typed into //schematic delete. A player types a schematic the way //schematic load
 * accepts it, with or without the extension, and both must find the same file.
 */
class ResolveExistingTest {

    @TempDir
    Path dir;

    @Test
    void findsNameWithoutExtension() throws IOException {
        File file = Files.createFile(dir.resolve("house.schematic")).toFile();
        assertEquals(file.getCanonicalFile(), MainUtil.resolveExisting(dir.toFile(), "house").getCanonicalFile());
    }

    @Test
    void findsNameAsGiven() throws IOException {
        File file = Files.createFile(dir.resolve("house.schematic")).toFile();
        assertEquals(file.getCanonicalFile(), MainUtil.resolveExisting(dir.toFile(), "house.schematic").getCanonicalFile());
    }

    @Test
    void prefersTheExactName() throws IOException {
        File exact = Files.createFile(dir.resolve("house")).toFile();
        Files.createFile(dir.resolve("house.schematic"));
        assertEquals(exact.getCanonicalFile(), MainUtil.resolveExisting(dir.toFile(), "house").getCanonicalFile());
    }

    @Test
    void returnsNullWhenNothingMatches() {
        assertNull(MainUtil.resolveExisting(dir.toFile(), "missing"));
    }
}
