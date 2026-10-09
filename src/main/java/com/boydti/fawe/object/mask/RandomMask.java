package com.boydti.fawe.object.mask;

import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.function.mask.AbstractMask;
import java.util.SplittableRandom;

public class RandomMask extends AbstractMask implements ResettableMask {
    private transient SplittableRandom random;
    private final double chance;

    /**
     * @param chance the probability (0 to 1) that a position passes the mask
     */
    public RandomMask(double chance) {
        this.random = new SplittableRandom();
        this.chance = chance;
    }

    @Override
    public boolean test(Vector vector) {
        return random.nextDouble() < chance;
    }

    @Override
    public void reset() {
        random = new SplittableRandom();
    }
}