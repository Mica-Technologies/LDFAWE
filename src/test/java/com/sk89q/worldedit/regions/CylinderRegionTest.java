package com.sk89q.worldedit.regions;

import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.Vector2D;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A cylinder radius of N reaches N blocks either side of the center, a zero radius gives a one block
 * thick wall instead of nothing, the bounding box holds exactly the blocks it contains, and the
 * radius it was given is the radius it reports.
 */
class CylinderRegionTest {

    private static final Vector CENTER = new Vector(0, 40, 0);

    @Test
    void radiusReachesTheSameDistanceBothWays() {
        CylinderRegion region = new CylinderRegion(CENTER, new Vector2D(5, 5), 40, 44);
        assertTrue(region.contains(CENTER.add(5, 0, 0)));
        assertTrue(region.contains(CENTER.add(-5, 0, 0)));
        assertTrue(region.contains(CENTER.add(0, 4, 5)));
        assertTrue(region.contains(CENTER.add(0, 0, -5)));
        assertFalse(region.contains(CENTER.add(6, 0, 0)));
        assertFalse(region.contains(CENTER.add(0, 0, -6)));
        assertEquals(new Vector(-5, 40, -5), region.getMinimumPoint());
        assertEquals(new Vector(5, 44, 5), region.getMaximumPoint());
        assertEquals(11, region.getWidth());
        assertEquals(11, region.getLength());
    }

    @Test
    void zeroRadiusGivesAOneBlockThickShape() {
        CylinderRegion region = new CylinderRegion(CENTER, new Vector2D(5, 0), 40, 44);
        assertTrue(region.contains(CENTER));
        assertTrue(region.contains(CENTER.add(5, 4, 0)));
        assertTrue(region.contains(CENTER.add(-5, 0, 0)));
        assertFalse(region.contains(CENTER.add(0, 0, 1)));
        assertEquals(1, region.getLength());
        assertEquals(55, countContained(region));
        assertTrue(region.getArea() > 0);
    }

    @Test
    void boundingBoxHoldsExactlyTheContainedBlocks() {
        assertBoundingBoxIsTight(new CylinderRegion(CENTER, new Vector2D(5, 5), 40, 44));
        assertBoundingBoxIsTight(new CylinderRegion(CENTER, new Vector2D(5, 0), 40, 44));
        assertBoundingBoxIsTight(new CylinderRegion(CENTER, new Vector2D(2, 7), 40, 40));
        assertBoundingBoxIsTight(new CylinderRegion(CENTER, new Vector2D(0, 0), 40, 41));
    }

    @Test
    void radiusRoundTrips() {
        CylinderRegion region = new CylinderRegion(CENTER, new Vector2D(3, 7), 40, 44);
        assertEquals(new Vector2D(3, 7), region.getRadius());
        region.setRadius(region.getRadius());
        assertEquals(new Vector2D(3, 7), region.getRadius());
        assertEquals(new Vector2D(3, 7), new CylinderRegion(region).getRadius());
        region.extendRadius(new Vector2D(1, 1));
        assertEquals(new Vector2D(3, 7), region.getRadius());
    }

    private static int countContained(CylinderRegion region) {
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

    private static void assertBoundingBoxIsTight(CylinderRegion region) {
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
}
