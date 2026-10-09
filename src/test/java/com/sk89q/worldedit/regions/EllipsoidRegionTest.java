package com.sk89q.worldedit.regions;

import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An ellipsoid's height is its vertical diameter, capped at the world height.
 */
class EllipsoidRegionTest {

    @Test
    void heightIsTheVerticalDiameter() {
        EllipsoidRegion region = new EllipsoidRegion((World) null, new Vector(0, 64, 0), new Vector(5, 5, 5));
        assertEquals(10, region.getHeight());
    }

    @Test
    void heightIsCappedAtWorldHeight() {
        EllipsoidRegion region = new EllipsoidRegion((World) null, new Vector(0, 64, 0), new Vector(5, 300, 5));
        assertEquals(256, region.getHeight());
    }
}