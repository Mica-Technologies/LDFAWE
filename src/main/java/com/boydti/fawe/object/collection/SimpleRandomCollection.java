package com.boydti.fawe.object.collection;

import com.boydti.fawe.object.random.SimpleRandom;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public class SimpleRandomCollection<E> extends RandomCollection<E> {

    private final NavigableMap<Double, E> map = new TreeMap<Double, E>();
    private double total = 0;

    public SimpleRandomCollection(Map<E, Double> weights, SimpleRandom random) {
        super(weights, random);
        for (Map.Entry<E, Double> entry : weights.entrySet()) {
            add(entry.getValue(), entry.getKey());
        }
    }

    public void add(double weight, E result) {
        if (weight <= 0) return;
        total += weight;
        map.put(total, result);
    }

    public E next(int x, int y, int z) {
        // The keys are running totals of the raw weights, so the 0-1 random value has to be
        // scaled to that total. Unscaled, it always landed on the first entry.
        Map.Entry<Double, E> entry = map.higherEntry(random.nextDouble(x, y, z) * total);
        if (entry == null) {
            entry = map.lastEntry();
        }
        return entry.getValue();
    }
}
