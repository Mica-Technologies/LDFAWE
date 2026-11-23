package com.sk89q.worldedit.regions;

import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An ellipsoid's height is its vertical diameter, capped at the world height. A radius of N reaches
 * N blocks either side of the center (a zero radius gives a one block thick shape), its bounding box
 * holds exactly the blocks it contains, and the radius it was given is the radius it reports. Its area
 * is the number of blocks it contains, which //size and the selection messages show.
 */
class EllipsoidRegionTest {

    private static final Vector CENTER = new Vector(0, 64, 0);

    @Test
    void heightIsTheVerticalDiameter() {
        // Blocks 59 to 69: five either side of the center, plus the center layer
        EllipsoidRegion region = new EllipsoidRegion((World) null, CENTER, new Vector(5, 5, 5));
        assertEquals(11, region.getHeight());
    }

    @Test
    void radiusReachesTheSameDistanceBothWays() {
        EllipsoidRegion region = new EllipsoidRegion((World) null, CENTER, new Vector(5, 5, 5));
        for (int d : new int[]{-5, 5}) {
            assertTrue(region.contains(CENTER.add(d, 0, 0)));
            assertTrue(region.contains(CENTER.add(0, d, 0)));
            assertTrue(region.contains(CENTER.add(0, 0, d)));
        }
        for (int d : new int[]{-6, 6}) {
            assertFalse(region.contains(CENTER.add(d, 0, 0)));
            assertFalse(region.contains(CENTER.add(0, d, 0)));
            assertFalse(region.contains(CENTER.add(0, 0, d)));
        }
        assertEquals(new Vector(-5, 59, -5), region.getMinimumPoint());
        assertEquals(new Vector(5, 69, 5), region.getMaximumPoint());
        assertEquals(11, region.getWidth());
        assertEquals(11, region.getLength());
    }

    @Test
    void zeroRadiusGivesAFlatDisc() {
        EllipsoidRegion region = new EllipsoidRegion((World) null, CENTER, new Vector(5, 0, 5));
        assertTrue(region.contains(CENTER));
        assertTrue(region.contains(CENTER.add(5, 0, 0)));
        assertTrue(region.contains(CENTER.add(0, 0, -5)));
        assertFalse(region.contains(CENTER.add(0, 1, 0)));
        assertFalse(region.contains(CENTER.add(0, -1, 0)));
        assertEquals(1, region.getHeight());
        assertTrue(region.getArea() > 0);
    }

    @Test
    void boundingBoxHoldsExactlyTheContainedBlocks() {
        assertBoundingBoxIsTight(new EllipsoidRegion((World) null, CENTER, new Vector(5, 5, 5)));
        assertBoundingBoxIsTight(new EllipsoidRegion((World) null, CENTER, new Vector(5, 0, 5)));
        assertBoundingBoxIsTight(new EllipsoidRegion((World) null, CENTER, new Vector(3, 7, 2)));
        assertBoundingBoxIsTight(new EllipsoidRegion((World) null, CENTER, new Vector(0, 0, 0)));
    }

    @Test
    void radiusRoundTrips() {
        EllipsoidRegion region = new EllipsoidRegion((World) null, CENTER, new Vector(3, 7, 2));
        assertEquals(new Vector(3, 7, 2), region.getRadius());
        region.setRadius(region.getRadius());
        assertEquals(new Vector(3, 7, 2), region.getRadius());
        assertEquals(new Vector(3, 7, 2), new EllipsoidRegion(region).getRadius());
        region.extendRadius(new Vector(1, 1, 1));
        assertEquals(new Vector(3, 7, 2), region.getRadius());
    }

    private static void assertBoundingBoxIsTight(EllipsoidRegion region) {
        Vector min = region.getMinimumPoint();
        Vector max = region.getMaximumPoint();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int x = min.getBlockX() - 2; x <= max.getBlockX() + 2; x++) {
            for (int y = min.getBlockY() - 2; y <= max.getBlockY() + 2; y++) {
                for (int z = min.getBlockZ() - 2; z <= max.getBlockZ() + 2; z++) {
                    if (!region.contains(new Vector(x, y, z))) continue;
                    minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
                    maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
                }
            }
        }
        assertEquals(min, new Vector(minX, minY, minZ));
        assertEquals(max, new Vector(maxX, maxY, maxZ));
        assertEquals(region.getWidth(), maxX - minX + 1);
        assertEquals(region.getHeight(), maxY - minY + 1);
        assertEquals(region.getLength(), maxZ - minZ + 1);
    }

    @Test
    void areaIsTheNumberOfContainedBlocks() {
        int[][] radii = {
                {0, 0, 0}, {1, 1, 1}, {4, 4, 4}, {5, 5, 5}, {12, 12, 12}, {31, 31, 31},
                {5, 0, 5}, {9, 0, 4}, {0, 6, 0}, {3, 7, 2}, {10, 3, 6}, {1, 0, 0}
        };
        for (int[] r : radii) {
            EllipsoidRegion region = new EllipsoidRegion((World) null, CENTER, new Vector(r[0], r[1], r[2]));
            assertEquals(countContained(region), region.getArea(), "radius " + r[0] + "," + r[1] + "," + r[2]);
        }
        // The world height clips the shape, and the count with it
        EllipsoidRegion clipped = new EllipsoidRegion((World) null, new Vector(0, 3, 0), new Vector(6, 6, 6));
        assertEquals(countContained(clipped), clipped.getArea());
    }

    @Test
    void areaFollowsRadiusAndCenterChanges() {
        EllipsoidRegion region = new EllipsoidRegion((World) null, CENTER, new Vector(5, 5, 5));
        int before = region.getArea();
        region.setRadius(new Vector(6, 6, 6));
        assertEquals(countContained(region), region.getArea());
        region.setRadius(new Vector(5, 5, 5));
        assertEquals(before, region.getArea());
        region.setCenter(new Vector(0, 2, 0));
        assertEquals(countContained(region), region.getArea());
    }

    private static int countContained(EllipsoidRegion region) {
        Vector min = region.getMinimumPoint();
        Vector max = region.getMaximumPoint();
        int count = 0;
        for (int x = min.getBlockX() - 2; x <= max.getBlockX() + 2; x++) {
            for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                for (int z = min.getBlockZ() - 2; z <= max.getBlockZ() + 2; z++) {
                    if (region.contains(new Vector(x, y, z))) count++;
                }
            }
        }
        return count;
    }

    @Test
    void heightIsCappedAtWorldHeight() {
        EllipsoidRegion region = new EllipsoidRegion((World) null, new Vector(0, 64, 0), new Vector(5, 300, 5));
        assertEquals(256, region.getHeight());
    }
}