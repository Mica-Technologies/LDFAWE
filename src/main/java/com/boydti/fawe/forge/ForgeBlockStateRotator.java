package com.boydti.fawe.forge;

import com.boydti.fawe.object.BlockStateRotator;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.math.transform.Transform;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;

/**
 * Turns blocks through {@link IBlockState#withMirror} and {@link IBlockState#withRotation}, the same
 * calls vanilla structure templates use, so any mod that implements them (facing in metadata, as most
 * do) rotates correctly with //rotate, //flip and rotated pastes.
 */
public class ForgeBlockStateRotator extends BlockStateRotator {

    private static final Mirror[] MIRRORS = {Mirror.NONE, Mirror.LEFT_RIGHT};

    /** The last transform decomposed, and its result; transforms are reused for a whole paste. */
    private volatile Object[] last;

    @Override
    public int transform(int id, int data, Transform transform) {
        Object[] decomposed = decompose(transform);
        if (decomposed == null) {
            return -1;
        }
        Mirror mirror = (Mirror) decomposed[0];
        Rotation rotation = (Rotation) decomposed[1];
        if (mirror == Mirror.NONE && rotation == Rotation.NONE) {
            return -1;
        }
        try {
            Block block = Block.getBlockById(id);
            if (block == null || (block == Blocks.AIR && id != 0)) {
                return -1;
            }
            IBlockState state = block.getStateFromMeta(data);
            IBlockState turned = state.withMirror(mirror).withRotation(rotation);
            if (turned == state) {
                // Block's default withMirror/withRotation return the state unchanged, and many mods
                // (City Super Mod among them) never override them although they keep their facing
                // in metadata. Turn horizontal facing and axis properties directly instead.
                turned = turnProperties(state, mirror, rotation);
                if (turned == state) {
                    return -1;
                }
            }
            int meta = block.getMetaFromState(turned);
            return (id << 4) | (meta & 15);
        } catch (Throwable ignore) {
            // A mod's state handling failed for this meta; leave the block as it is
            return -1;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static IBlockState turnProperties(IBlockState state, Mirror mirror, Rotation rotation) {
        boolean quarterTurn = rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
        IBlockState result = state;
        for (IProperty property : state.getPropertyKeys()) {
            Object value = state.getValue(property);
            Object newValue = value;
            if (value instanceof EnumFacing && ((EnumFacing) value).getAxis().isHorizontal()) {
                newValue = rotation.rotate(mirror.mirror((EnumFacing) value));
            } else if (value instanceof EnumFacing.Axis && quarterTurn && value != EnumFacing.Axis.Y) {
                newValue = value == EnumFacing.Axis.X ? EnumFacing.Axis.Z : EnumFacing.Axis.X;
            }
            if (newValue != value && property.getAllowedValues().contains(newValue)) {
                result = result.withProperty(property, (Comparable) newValue);
            }
        }
        return result;
    }

    /**
     * Express a transform as a mirror followed by a rotation, by where it sends north and east.
     *
     * @return {mirror, rotation}, or null if the transform is not one of the eight horizontal
     * symmetries (for example a vertical flip, or an angle that is not a multiple of 90 degrees)
     */
    private Object[] decompose(Transform transform) {
        Object[] cached = last;
        if (cached != null && cached[2] == transform) {
            return (Object[]) cached[3];
        }
        Object[] result = null;
        EnumFacing north = closestHorizontal(transform, new Vector(0, 0, -1));
        EnumFacing east = closestHorizontal(transform, new Vector(1, 0, 0));
        if (north != null && east != null) {
            search:
            for (Mirror mirror : MIRRORS) {
                for (Rotation rotation : Rotation.values()) {
                    if (rotation.rotate(mirror.mirror(EnumFacing.NORTH)) == north
                            && rotation.rotate(mirror.mirror(EnumFacing.EAST)) == east) {
                        result = new Object[]{mirror, rotation};
                        break search;
                    }
                }
            }
        }
        last = new Object[]{null, null, transform, result};
        return result;
    }

    private static EnumFacing closestHorizontal(Transform transform, Vector direction) {
        Vector origin = transform.apply(Vector.ZERO);
        Vector applied = transform.apply(direction);
        double dx = applied.getX() - origin.getX();
        double dy = applied.getY() - origin.getY();
        double dz = applied.getZ() - origin.getZ();
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length == 0) {
            return null;
        }
        dx /= length;
        dy /= length;
        dz /= length;
        // Only exact axis-aligned images count: 45 degree rotations or tilts have no block equivalent
        if (Math.abs(dy) > 1e-6) {
            return null;
        }
        if (Math.abs(Math.abs(dx) - 1) < 1e-6) {
            return dx > 0 ? EnumFacing.EAST : EnumFacing.WEST;
        }
        if (Math.abs(Math.abs(dz) - 1) < 1e-6) {
            return dz > 0 ? EnumFacing.SOUTH : EnumFacing.NORTH;
        }
        return null;
    }
}
