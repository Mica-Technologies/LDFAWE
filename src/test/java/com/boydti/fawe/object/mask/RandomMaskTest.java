package com.boydti.fawe.object.mask;

import com.sk89q.worldedit.Vector;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * %[chance] should let through the requested share of blocks, not 25% plus half of it.
 */
class RandomMaskTest {

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 0.1, 0.5, 0.9, 1.0})
    void passesTheRequestedShare(double chance) {
        RandomMask mask = new RandomMask(chance);
        Vector position = new Vector(0, 0, 0);
        int samples = 200_000;
        int passed = 0;
        for (int i = 0; i < samples; i++) {
            if (mask.test(position)) {
                passed++;
            }
        }
        assertEquals(chance, passed / (double) samples, 0.01);
    }
}