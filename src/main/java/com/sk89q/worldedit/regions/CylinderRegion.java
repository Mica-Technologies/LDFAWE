/*
 * WorldEdit, a Minecraft world manipulation toolkit
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldEdit team and contributors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.sk89q.worldedit.regions;

import com.sk89q.worldedit.BlockVector;
import com.sk89q.worldedit.BlockVector2D;
import com.sk89q.worldedit.LocalWorld;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.Vector2D;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.math.geom.Polygons;
import com.sk89q.worldedit.regions.iterator.FlatRegion3DIterator;
import com.sk89q.worldedit.regions.iterator.FlatRegionIterator;
import com.sk89q.worldedit.world.World;

import java.util.Iterator;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Represents a cylindrical region.
 */
public class CylinderRegion extends AbstractRegion implements FlatRegion {

    private BlockVector2D center;
    /**
     * The radii plus 0.5 on each axis. The padding makes a radius of N reach exactly N blocks from the
     * center, and keeps a zero radius one block thick rather than empty; rounding it to whole blocks
     * would lose it.
     */
    private Vector2D radius;
    private Vector2D radiusInverse;
    private int minY;
    private int maxY;
    private boolean hasY = false;

    /**
     * Construct the region
     */
    public CylinderRegion() {
        this((World) null);
    }

    /**
     * @deprecated cast {@code world} to {@link World}
     */
    @Deprecated
    public CylinderRegion(LocalWorld world) {
        this((World) world);
    }
    /**
     * Construct the region.
     *
     * @param world the world
     */
    public CylinderRegion(World world) {
        this(world, new Vector(), new Vector2D(), 0, 0);
        hasY = false;
    }

    @Deprecated
    public CylinderRegion(LocalWorld world, Vector center, Vector2D radius, int minY, int maxY) {
        this((World) world, center, radius, minY, maxY);
    }

    /**
     * Construct the region.
     *
     * @param world the world
     * @param center the center position
     * @param radius the radius along the X and Z axes
     * @param minY the minimum Y, inclusive
     * @param maxY the maximum Y, inclusive
     */
    public CylinderRegion(World world, Vector center, Vector2D radius, int minY, int maxY) {
        super(world);
        setCenter(center.toVector2D());
        setRadius(radius);
        this.minY = minY;
        this.maxY = maxY;
        hasY = true;
    }

    /**
     * Construct the region.
     *
     * @param center the center position
     * @param radius the radius along the X and Z axes
     * @param minY the minimum Y, inclusive
     * @param maxY the maximum Y, inclusive
     */
    public CylinderRegion(Vector center, Vector2D radius, int minY, int maxY) {
        super(null);
        setCenter(center.toVector2D());
        setRadius(radius);
        this.minY = minY;
        this.maxY = maxY;
        hasY = true;
    }

    public CylinderRegion(CylinderRegion region) {
        this(region.world, region.getCenter(), region.getRadius(), region.minY, region.maxY);
        hasY = region.hasY;
    }

    @Override
    public Vector getCenter() {
        return center.toVector((getMaximumY() + getMinimumY()) / 2);
    }

    /**
     * Sets the main center point of the region
     *
     * @deprecated replaced by {@link #setCenter(Vector2D)}
     */
    @Deprecated
    public void setCenter(Vector center) {
        setCenter(center.toVector2D());
    }

    /**
     * Sets the main center point of the region
     *
     * @param center the center point
     */
    public void setCenter(Vector2D center) {
        this.center = new BlockVector2D(center);
    }

    /**
     * Returns the radius of the cylinder
     *
     * @return the radius along the X and Z axes
     */
    public Vector2D getRadius() {
        return radius.subtract(0.5, 0.5);
    }

    /**
     * Sets the radius of the cylinder
     *
     * @param radius the radius along the X and Z axes
     */
    public void setRadius(Vector2D radius) {
        this.radius = new Vector2D(radius.getX() + 0.5, radius.getZ() + 0.5);
        this.radiusInverse = Vector2D.ONE.divide(this.radius);
    }

    /**
     * The furthest whole number of blocks from the center that {@link #contains} accepts on one axis.
     */
    private static int reach(double paddedRadius) {
        return (int) Math.floor(paddedRadius);
    }

    private Vector2D reach() {
        return new Vector2D(reach(radius.getX()), reach(radius.getZ()));
    }

    /**
     * Extends the radius to be at least the given radius
     *
     * @param minRadius the minimum radius
     */
    public void extendRadius(Vector2D minRadius) {
        setRadius(Vector2D.getMaximum(minRadius, getRadius()));
    }

    /**
     * Set the minimum Y.
     *
     * @param y the y
     */
    public void setMinimumY(int y) {
        hasY = true;
        minY = y;
    }

    /**
     * Se the maximum Y.
     *
     * @param y the y
     */
    public void setMaximumY(int y) {
        hasY = true;
        maxY = y;
    }

    @Override
    public Vector getMinimumPoint() {
        return center.subtract(reach()).toVector(getMinimumY());
    }

    @Override
    public Vector getMaximumPoint() {
        return center.add(reach()).toVector(getMaximumY());
    }

    @Override
    public int getMaximumY() {
        int worldMax = world != null ? world.getMaxY() - 1 : 255;
        if (maxY > worldMax) {
            return maxY = worldMax;
        }
        return maxY;
    }

    @Override
    public int getMinimumY() {
        if (minY < 0) {
            return minY = 0;
        }
        return minY;
    }

    /**
     * The number of columns last counted and the radius they were counted for. The radius is replaced, never
     * changed in place, so comparing the reference is enough.
     */
    private transient Vector2D columnsRadius;
    private transient long columns;

    /**
     * The number of blocks {@link #contains} accepts, which is what an edit of this region sets. A volume formula
     * is several percent off at the radii players select. Every layer holds the same columns, so only one layer
     * is counted, a few square-root estimates per row.
     */
    @Override
    public int getArea() {
        if (columnsRadius != radius) {
            int reachX = reach(radius.getX());
            int reachZ = reach(radius.getZ());
            long count = 0;
            for (int dx = -reachX; dx <= reachX; dx++) {
                double xn = Math.abs(dx) * radiusInverse.getX();
                double rest = 1 - xn * xn;
                int dz = rest > 0 ? Math.min(reachZ, (int) Math.floor(radius.getZ() * Math.sqrt(rest))) : 0;
                // The estimate can be one off either way; settle it with the same test contains() uses.
                while (dz >= 0 && !containsColumn(dx, dz)) dz--;
                while (dz < reachZ && containsColumn(dx, dz + 1)) dz++;
                if (dz >= 0) count += 2 * dz + 1;
            }
            columns = count;
            columnsRadius = radius;
        }
        return (int) Math.min(Integer.MAX_VALUE, columns * getHeight());
    }

    @Override
    public int getWidth() {
        return 2 * reach(radius.getX()) + 1;
    }

    @Override
    public int getHeight() {
        return getMaximumY() - getMinimumY() + 1;
    }

    @Override
    public int getLength() {
        return 2 * reach(radius.getZ()) + 1;
    }

    private Vector2D calculateDiff2D(Vector... changes) throws RegionOperationException {
        Vector2D diff = new Vector2D();
        for (Vector change : changes) {
            diff = diff.add(change.toVector2D());
        }

        if ((diff.getBlockX() & 1) + (diff.getBlockZ() & 1) != 0) {
            throw new RegionOperationException("Cylinders changes must be even for each horizontal dimensions.");
        }

        return diff.divide(2).floor();
    }

    private Vector2D calculateChanges2D(Vector... changes) {
        Vector2D total = new Vector2D();
        for (Vector change : changes) {
            total = total.add(change.toVector2D().positive());
        }

        return total.divide(2).floor();
    }

    /**
     * Expand the region.
     * Expand the region.
     *
     * @param changes array/arguments with multiple related changes
     * @throws RegionOperationException
     */
    @Override
    public void expand(Vector... changes) throws RegionOperationException {
        center = center.add(calculateDiff2D(changes)).toBlockVector2D();
        radius = radius.add(calculateChanges2D(changes));
        this.radiusInverse = Vector2D.ONE.divide(radius);
        for (Vector change : changes) {
            int changeY = change.getBlockY();
            if (changeY > 0) {
                maxY += changeY;
            } else {
                minY += changeY;
            }
        }
    }

    /**
     * Contract the region.
     *
     * @param changes array/arguments with multiple related changes
     * @throws RegionOperationException
     */
    @Override
    public void contract(Vector... changes) throws RegionOperationException {
        center = center.subtract(calculateDiff2D(changes)).toBlockVector2D();
        Vector2D newRadius = radius.subtract(calculateChanges2D(changes));
        radius = Vector2D.getMaximum(new Vector2D(1.5, 1.5), newRadius);
        this.radiusInverse = Vector2D.ONE.divide(radius);
        for (Vector change : changes) {
            int height = maxY - minY;
            int changeY = change.getBlockY();
            if (changeY > 0) {
                minY += Math.min(height, changeY);
            } else {
                maxY += Math.max(-height, changeY);
            }
        }
    }

    @Override
    public void shift(Vector change) throws RegionOperationException {
        center = center.add(change.toVector2D()).toBlockVector2D();

        int changeY = change.getBlockY();
        maxY += changeY;
        minY += changeY;
    }

    /**
     * Checks to see if a point is inside this region.
     */
    @Override
    public boolean contains(Vector position) {
        final int pt = position.getBlockY();
        if (pt < getMinimumY() || pt > getMaximumY()) {
            return false;
        }
        return containsColumn(position.getBlockX() - center.getBlockX(), position.getBlockZ() - center.getBlockZ());
    }

    private boolean containsColumn(int offsetX, int offsetZ) {
        double dx = Math.abs(offsetX) * radiusInverse.getX();
        double dz = Math.abs(offsetZ) * radiusInverse.getZ();

        return dx * dx + dz * dz <= 1;
    }


    /**
     * Sets the height of the cylinder to fit the specified Y.
     *
     * @param y the y value
     * @return true if the area was expanded
     */
    public boolean setY(int y) {
        if (!hasY) {
            minY = y;
            maxY = y;
            hasY = true;
            return true;
        } else if (y < minY) {
            minY = y;
            return true;
        } else if (y > maxY) {
            maxY = y;
            return true;
        }

        return false;
    }

    @Override
    public Iterator<BlockVector> iterator() {
        return new FlatRegion3DIterator(this);
    }

    @Override
    public Iterable<Vector2D> asFlatRegion() {
        return new Iterable<Vector2D>() {
            @Override
            public Iterator<Vector2D> iterator() {
                return new FlatRegionIterator(CylinderRegion.this);
            }
        };
    }

    /**
     * Returns string representation in the format
     * "(centerX, centerZ) - (radiusX, radiusZ) - (minY, maxY)"
     *
     * @return string
     */
    @Override
    public String toString() {
        return center + " - " + radius + "(" + minY + ", " + maxY + ")";
    }

    @Override
    public CylinderRegion clone() {
        return (CylinderRegion) super.clone();
    }

    @Override
    public List<BlockVector2D> polygonize(int maxPoints) {
        return Polygons.polygonizeCylinder(center, radius, maxPoints);
    }

    /**
     * Return a new instance with the given center and radius in the X and Z
     * axes with a Y that extends from the bottom of the extent to the top
     * of the extent.
     *
     * @param extent the extent
     * @param center the center position
     * @param radius the radius in the X and Z axes
     * @return a region
     */
    public static CylinderRegion createRadius(Extent extent, Vector center, double radius) {
        checkNotNull(extent);
        checkNotNull(center);
        Vector2D radiusVec = new Vector2D(radius, radius);
        int minY = extent.getMinimumPoint().getBlockY();
        int maxY = extent.getMaximumPoint().getBlockY();
        return new CylinderRegion(center, radiusVec, minY, maxY);
    }

    public static Class<CylinderRegion> inject() {
        return CylinderRegion.class;
    }
}
