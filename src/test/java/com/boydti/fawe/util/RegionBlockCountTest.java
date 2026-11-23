package com.boydti.fawe.util;

import com.sk89q.worldedit.BlockVector2D;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.regions.ConvexPolyhedralRegion;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.world.World;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The block count //size and the selection messages show for polygon and convex selections, whose own area
 * is only an estimate. A small selection shows the number of blocks an edit of it would set; one too large
 * to count is marked as approximate rather than shown as exact.
 */
class RegionBlockCountTest {

    @Test
    void polygonCountsEveryContainedBlock() {
        // An 11 by 11 square of columns, 3 high: its mathematical area is 10 * 10 * 3
        Polygonal2DRegion region = new Polygonal2DRegion((World) null, Arrays.asList(
                new BlockVector2D(0, 0), new BlockVector2D(10, 0), new BlockVector2D(10, 10), new BlockVector2D(0, 10)),
                60, 62);
        assertEquals(363, RegionBlockCount.exactCount(region));
        assertEquals("363", RegionBlockCount.format(region));
    }

    @Test
    void convexShapeIsMarkedApproximate() {
        // A tetrahedron in a 9 by 9 by 9 box: its estimate is the box, so it must not be shown as exact
        ConvexPolyhedralRegion region = new ConvexPolyhedralRegion((World) null);
        region.addVertex(new Vector(0, 60, 0));
        region.addVertex(new Vector(8, 60, 0));
        region.addVertex(new Vector(0, 60, 8));
        region.addVertex(new Vector(0, 68, 0));
        assertEquals(-1, RegionBlockCount.exactCount(region));
        assertEquals("~729", RegionBlockCount.format(region));
    }

    @Test
    void largeSelectionIsMarkedApproximate() {
        Polygonal2DRegion region = new Polygonal2DRegion((World) null, Arrays.asList(
                new BlockVector2D(0, 0), new BlockVector2D(1000, 0), new BlockVector2D(1000, 1000)),
                0, 255);
        assertEquals(-1, RegionBlockCount.exactCount(region));
        assertTrue(RegionBlockCount.format(region).startsWith("~"));
    }
}
