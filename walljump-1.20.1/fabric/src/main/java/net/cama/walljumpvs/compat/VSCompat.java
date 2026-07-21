package net.cama.walljumpvs.compat;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

import java.util.EnumMap;
import java.util.Map;

/**
 * Every Valkyrien Skies interaction lives here so the rest of the mod never
 * links against VS classes. Callers must check that VS is installed before
 * touching this class, otherwise it fails to load.
 */
public final class VSCompat {

    private record ShipWall(Ship ship, BlockPos blockPos) {
    }

    private static final Map<Direction, ShipWall> SHIP_WALLS = new EnumMap<>(Direction.class);
    private static Ship clingShip;
    private static final Vector3d clingAnchor = new Vector3d();

    private VSCompat() {
    }

    public static void clearShipWalls() {
        SHIP_WALLS.clear();
    }

    public static void forgetShipWall(Direction direction) {
        SHIP_WALLS.remove(direction);
    }

    public static boolean isShipWall(Direction direction) {
        return SHIP_WALLS.containsKey(direction);
    }

    public static BlockPos getShipWallPos(Direction direction) {
        ShipWall wall = SHIP_WALLS.get(direction);
        return wall == null ? null : wall.blockPos;
    }

    /**
     * Probes the world-space AABB against every intersecting ship's block collision
     * shapes and records the wall for this direction. Returns the shipyard position
     * of the colliding block, or null when no ship wall is there.
     */
    public static BlockPos findShipWall(LocalPlayer pl, AABB probe, Direction direction) {
        Level level = pl.level();
        for (Ship ship : VSGameUtilsKt.getShipsIntersecting(level, probe)) {
            BlockPos hit = findCollidingShipBlock(level, ship, probe);
            if (hit != null) {
                SHIP_WALLS.put(direction, new ShipWall(ship, hit));
                return hit;
            }
        }
        return null;
    }

    /**
     * True when any ship block's collision shape overlaps the world-space box.
     */
    public static boolean intersectsShipBlock(Level level, AABB box) {
        for (Ship ship : VSGameUtilsKt.getShipsIntersecting(level, box)) {
            if (findCollidingShipBlock(level, ship, box) != null) return true;
        }
        return false;
    }

    private static BlockPos findCollidingShipBlock(Level level, Ship ship, AABB worldBox) {
        // The probe becomes an oriented box in shipyard space; test it exactly so
        // rotated ships neither miss touching blocks nor report phantom walls.
        Matrix4dc worldToShip = ship.getWorldToShip();
        Vector3d center = worldToShip.transformPosition(new Vector3d(
                (worldBox.minX + worldBox.maxX) / 2.0,
                (worldBox.minY + worldBox.maxY) / 2.0,
                (worldBox.minZ + worldBox.maxZ) / 2.0));
        Vector3d[] halfEdges = {
                worldToShip.transformDirection(new Vector3d((worldBox.maxX - worldBox.minX) / 2.0, 0.0, 0.0)),
                worldToShip.transformDirection(new Vector3d(0.0, (worldBox.maxY - worldBox.minY) / 2.0, 0.0)),
                worldToShip.transformDirection(new Vector3d(0.0, 0.0, (worldBox.maxZ - worldBox.minZ) / 2.0))};

        double extX = Math.abs(halfEdges[0].x) + Math.abs(halfEdges[1].x) + Math.abs(halfEdges[2].x);
        double extY = Math.abs(halfEdges[0].y) + Math.abs(halfEdges[1].y) + Math.abs(halfEdges[2].y);
        double extZ = Math.abs(halfEdges[0].z) + Math.abs(halfEdges[1].z) + Math.abs(halfEdges[2].z);

        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(center.x - extX, center.y - extY, center.z - extZ),
                BlockPos.containing(center.x + extX, center.y + extY, center.z + extZ))) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            for (AABB blockBox : state.getCollisionShape(level, pos).toAabbs()) {
                if (obbIntersectsAabb(center, halfEdges, blockBox.move(pos))) {
                    return pos.immutable();
                }
            }
        }
        return null;
    }

    /**
     * Separating-axis test between the probe's oriented box and an axis-aligned
     * box, both in shipyard space. Mere surface contact does not count.
     */
    private static boolean obbIntersectsAabb(Vector3dc center, Vector3d[] halfEdges, AABB aabb) {
        Vector3d t = new Vector3d(center).sub(
                (aabb.minX + aabb.maxX) / 2.0, (aabb.minY + aabb.maxY) / 2.0, (aabb.minZ + aabb.maxZ) / 2.0);
        double ax = (aabb.maxX - aabb.minX) / 2.0;
        double ay = (aabb.maxY - aabb.minY) / 2.0;
        double az = (aabb.maxZ - aabb.minZ) / 2.0;

        Vector3d[] axes = new Vector3d[15];
        axes[0] = new Vector3d(1.0, 0.0, 0.0);
        axes[1] = new Vector3d(0.0, 1.0, 0.0);
        axes[2] = new Vector3d(0.0, 0.0, 1.0);
        axes[3] = halfEdges[0];
        axes[4] = halfEdges[1];
        axes[5] = halfEdges[2];
        int count = 6;
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                axes[count++] = axes[i].cross(halfEdges[j], new Vector3d());

        for (Vector3d axis : axes) {
            if (axis.lengthSquared() < 1.0e-12) continue;
            double ra = ax * Math.abs(axis.x) + ay * Math.abs(axis.y) + az * Math.abs(axis.z);
            double rb = Math.abs(halfEdges[0].dot(axis)) + Math.abs(halfEdges[1].dot(axis)) + Math.abs(halfEdges[2].dot(axis));
            if (Math.abs(t.dot(axis)) >= ra + rb - 1.0e-7) return false;
        }
        return true;
    }

    /**
     * Remembers the cling position in shipyard space when the cling wall belongs
     * to a ship, so the anchor can follow the ship as it moves.
     */
    public static void captureClingAnchor(LocalPlayer pl, Direction clingDirection) {
        ShipWall wall = SHIP_WALLS.get(clingDirection);
        if (wall == null) {
            clingShip = null;
            return;
        }
        clingShip = wall.ship;
        clingShip.getWorldToShip().transformPosition(VectorConversionsMCKt.toJOML(pl.position()), clingAnchor);
    }

    public static void clearClingAnchor() {
        clingShip = null;
    }

    /**
     * Current world-space position of the cling anchor, following the ship's
     * movement and rotation. Null when the player is not clinging to a ship.
     */
    public static Vec3 getClingWorldPos() {
        if (clingShip == null) return null;
        Vector3d world = clingShip.getShipToWorld().transformPosition(clingAnchor, new Vector3d());
        return new Vec3(world.x, world.y, world.z);
    }

    /**
     * World-space velocity (per tick) of the clung ship at the anchor point, or
     * zero when not anchored. Includes the ship's spin, so the player can ride
     * the wall of a turning ship.
     */
    public static Vec3 getClingPointVelocity() {
        if (clingShip == null) return Vec3.ZERO;
        Vector3d world = clingShip.getShipToWorld().transformPosition(clingAnchor, new Vector3d());
        return pointVelocityPerTick(clingShip, world);
    }

    /**
     * Player velocity relative to the ship whose wall is being used (per tick).
     * Falls back to plain world velocity when no ship wall is tracked.
     */
    public static Vec3 getRelativeVelocity(LocalPlayer pl) {
        Ship ship = clingShip;
        if (ship == null) {
            for (ShipWall wall : SHIP_WALLS.values()) {
                ship = wall.ship;
                break;
            }
        }
        Vec3 velocity = pl.getDeltaMovement();
        if (ship == null) return velocity;
        return velocity.subtract(pointVelocityPerTick(ship, VectorConversionsMCKt.toJOML(pl.position())));
    }

    /**
     * Velocity of the ship at a world-space point: linear plus rotational part.
     * Ship velocity is blocks per second; the caller wants blocks per tick.
     */
    private static Vec3 pointVelocityPerTick(Ship ship, Vector3dc worldPoint) {
        Vector3dc shipCenter = ship.getTransform().getPositionInWorld();
        Vector3d r = new Vector3d(worldPoint).sub(shipCenter);
        Vector3d vel = new Vector3d(ship.getOmega()).cross(r).add(ship.getVelocity());
        return new Vec3(vel.x / 20.0, vel.y / 20.0, vel.z / 20.0);
    }
}
