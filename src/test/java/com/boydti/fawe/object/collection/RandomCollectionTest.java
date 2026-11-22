package com.boydti.fawe.object.collection;

import com.boydti.fawe.object.random.SimpleRandom;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Weighted patterns such as 33.3%stone,66.7%dirt. Weights that are not whole hundredths cannot
 * use the lookup-table collection and fall back to SimpleRandomCollection, which must still
 * hand out every entry in proportion to its weight.
 */
class RandomCollectionTest {

    /** Walks evenly through 0..1, so the share of each entry is exact up to rounding. */
    private static SimpleRandom sweep(int steps) {
        return (x, y, z) -> (x + 0.5) / steps;
    }

    private static Map<String, Integer> draw(RandomCollection<String> collection, int steps) {
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < steps; i++) {
            counts.merge(collection.next(i, 0, 0), 1, Integer::sum);
        }
        return counts;
    }

    @Test
    void decimalPercentagesMixBothBlocks() {
        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put("stone", 33.3);
        weights.put("dirt", 66.7);
        int steps = 1000;
        RandomCollection<String> collection = RandomCollection.of(weights, sweep(steps));

        Map<String, Integer> counts = draw(collection, steps);

        assertEquals(333, counts.getOrDefault("stone", 0), 1);
        assertEquals(667, counts.getOrDefault("dirt", 0), 1);
    }

    @Test
    void simpleCollectionFollowsRawWeights() {
        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put("a", 1d);
        weights.put("b", 2d);
        weights.put("c", 5d);
        int steps = 800;
        RandomCollection<String> collection = new SimpleRandomCollection<>(weights, sweep(steps));

        Map<String, Integer> counts = draw(collection, steps);

        assertEquals(100, counts.getOrDefault("a", 0), 1);
        assertEquals(200, counts.getOrDefault("b", 0), 1);
        assertEquals(500, counts.getOrDefault("c", 0), 1);
    }

    @Test
    void weightsBelowOneStillReturnAnEntry() {
        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put("a", 0.001);
        weights.put("b", 0.002);
        RandomCollection<String> collection = new SimpleRandomCollection<>(weights, sweep(300));

        Map<String, Integer> counts = draw(collection, 300);

        assertEquals(300, counts.getOrDefault("a", 0) + counts.getOrDefault("b", 0));
        assertTrue(counts.getOrDefault("b", 0) > counts.getOrDefault("a", 0));
    }
}
