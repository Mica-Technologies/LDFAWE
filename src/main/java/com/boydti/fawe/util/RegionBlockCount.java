package com.boydti.fawe.util;

import com.boydti.fawe.object.regions.PolyhedralRegion;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.regions.ConvexPolyhedralRegion;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.CylinderRegion;
import com.sk89q.worldedit.regions.EllipsoidRegion;
import com.sk89q.worldedit.regions.Region;

/**
 * The number of blocks in a selection, as shown to players. Cuboids, ellipsoids and cylinders count their blocks
 * exactly. Other regions only estimate theirs (a polygon's mathematical area, a convex shape's bounding box), so a
 * small one is counted block by block and a large one, or a convex shape, is marked as approximate.
 */
public final class RegionBlockCount {

    /**
     * Bounding boxes up to this many blocks are counted block by block. It keeps the count well under a tick even
     * for a polygon with many points.
     */
    static final long COUNT_LIMIT = 250_000;

    private RegionBlockCount() {
    }

    /**
     * @return the block count as text: a plain number when it is exact, or "~" and the region's estimate
     */
    public static String format(Region region) {
        long exact = exactCount(region);
        return exact >= 0 ? Long.toString(exact) : "~" + region.getArea();
    }

    /**
     * @return the exact number of blocks the region contains, or -1 if it would take too long to count
     */
    public static long exactCount(Region region) {
        if (region instanceof CuboidRegion || region instanceof EllipsoidRegion || region instanceof CylinderRegion) {
            return region.getArea();
        }
        if (region instanceof ConvexPolyhedralRegion || region instanceof PolyhedralRegion) {
            // contains() first tries the last face that rejected a point. Adding a vertex can leave that face
            // pointing at one already removed from the hull, so a fresh selection rejects some inside blocks
            // until another face takes its place: the same shape counts differently on a second pass. Counting
            // here would also change which blocks a later edit of the selection gets, so the estimate is shown.
            return -1;
        }
        Vector min = region.getMinimumPoint();
        Vector max = region.getMaximumPoint();
        long volume = (long) (max.getBlockX() - min.getBlockX() + 1)
                * (max.getBlockY() - min.getBlockY() + 1)
                * (max.getBlockZ() - min.getBlockZ() + 1);
        if (volume > COUNT_LIMIT) {
            return -1;
        }
        long count = 0;
        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                    if (region.contains(new Vector(x, y, z))) {
                        count++;
                    }
                }
            }
        }
        return count;
    }
}
