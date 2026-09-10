package net.camacraft.walljumpunbound.compat;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Every Valkyrien Skies interaction lives here so the rest of the mod never
 * links against VS classes. Callers must check that VS is installed before
 * touching this class, otherwise it fails to load.
 */
public final class VSCompat {

    /**
     * A ship block the player is touching, and the face they meet it at: the
     * face's outward normal in world space, and how far the probe reaches past
     * that face. The face is the axis the probe penetrates least, which is the
     * one it arrived through.
     */
    public record ShipContact(Ship ship, BlockPos blockPos, Vec3 normal, double depth) {
    }

    /** A face standing within 60 degrees of vertical is a wall; flatter is floor or ceiling. */
    private static final double WALL_TILT_COS = 0.5;

    private static final Map<Direction, ShipContact> SHIP_WALLS = new EnumMap<>(Direction.class);
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
        ShipContact wall = SHIP_WALLS.get(direction);
        return wall == null ? null : wall.blockPos;
    }

    /** Outward normal of the tracked ship wall in world space, null when none is tracked there. */
    public static Vec3 getShipWallNormal(Direction direction) {
        ShipContact wall = SHIP_WALLS.get(direction);
        return wall == null ? null : wall.normal;
    }

    /** How far the last probe reached past the tracked wall's face, 0 when none is tracked there. */
    public static double getShipWallDepth(Direction direction) {
        ShipContact wall = SHIP_WALLS.get(direction);
        return wall == null ? 0.0 : wall.depth;
    }

    public static void recordShipWall(Direction direction, ShipContact contact) {
        SHIP_WALLS.put(direction, contact);
    }

    /**
     * The ship wall the probe is touching, if any. Every ship block the probe
     * overlaps is classified by the face it meets; faces that are floor or
     * ceiling to the given up are ignored, so a deck under the feet or a hull
     * leaning out below them never reads as a wall. Of the walls left, the
     * deepest contact wins, with {@code toward} (the way the player is pressing,
     * may be null) breaking near-ties so a corner is resolved to the face they
     * are actually against. Nothing is recorded; see {@link #recordShipWall}.
     */
    public static ShipContact findShipWall(Level level, AABB probe, Vec3 up, Vec3 toward) {
        ShipContact best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Ship ship : VSGameUtilsKt.getShipsIntersecting(level, probe)) {
            for (ShipContact contact : contacts(level, ship, probe)) {
                if (Math.abs(contact.normal.dot(up)) > WALL_TILT_COS) continue;
                // Depth decides; the press direction only settles contacts within a whisker of each other.
                double score = contact.depth + (toward == null ? 0.0 : 0.02 * -contact.normal.dot(toward));
                if (score > bestScore) {
                    bestScore = score;
                    best = contact;
                }
            }
        }
        return best;
    }

    /**
     * Records a wall found by a raycast (Valkyrien Skies raycasts through
     * ships natively, returning shipyard block positions) when the block
     * belongs to a ship, so the cling anchor can follow that ship.
     */
    public static void registerShipWallAt(Level level, Direction direction, BlockHitResult hit) {
        Ship ship = VSGameUtilsKt.getShipManagingPos(level, hit.getBlockPos());
        if (ship != null) {
            SHIP_WALLS.put(direction, new ShipContact(ship, hit.getBlockPos().immutable(), faceNormal(ship, hit.getDirection()), 0.0));
        }
    }

    /**
     * World-space normal of the face a raycast hit, when the block hit belongs
     * to a ship; null for a world block. The hit's direction is in shipyard
     * space, so it is turned by the ship's rotation.
     */
    public static Vec3 shipHitNormal(Level level, BlockHitResult hit) {
        Ship ship = VSGameUtilsKt.getShipManagingPos(level, hit.getBlockPos());
        return ship == null ? null : faceNormal(ship, hit.getDirection());
    }

    private static Vec3 faceNormal(Ship ship, Direction face) {
        Vector3d n = ship.getShipToWorld().transformDirection(
                new Vector3d(face.getStepX(), face.getStepY(), face.getStepZ())).normalize();
        return new Vec3(n.x, n.y, n.z);
    }

    /**
     * World-space velocity (per tick) of the tracked ship at the player's
     * position: the clung ship, else the ship of any tracked wall, or zero
     * when no ship is involved.
     */
    public static Vec3 getShipPointVelocity(LocalPlayer pl) {
        Ship ship = clingShip;
        if (ship == null) {
            for (ShipContact wall : SHIP_WALLS.values()) {
                ship = wall.ship;
                break;
            }
        }
        if (ship == null) return Vec3.ZERO;
        return pointVelocityPerTick(ship, VectorConversionsMCKt.toJOML(pl.position()));
    }

    /**
     * True when any ship block's collision shape overlaps the world-space box.
     */
    public static boolean intersectsShipBlock(Level level, AABB box) {
        for (Ship ship : VSGameUtilsKt.getShipsIntersecting(level, box)) {
            if (!contacts(level, ship, box).isEmpty()) return true;
        }
        return false;
    }

    /** One line per ship near the box, for the debug log: which ships, and what the probe touched. */
    public static String describeShips(Level level, AABB probe, Vec3 up) {
        StringBuilder out = new StringBuilder();
        for (Ship ship : VSGameUtilsKt.getShipsIntersecting(level, probe)) {
            if (out.length() > 0) out.append("; ");
            out.append("ship ").append(ship.getId()).append(" [");
            boolean first = true;
            for (ShipContact contact : contacts(level, ship, probe)) {
                if (!first) out.append(", ");
                first = false;
                double tilt = contact.normal.dot(up);
                out.append(contact.blockPos.toShortString()).append(' ')
                        .append(Math.abs(tilt) > WALL_TILT_COS ? (tilt > 0 ? "floor" : "ceiling") : "wall")
                        .append(String.format(Locale.ROOT, " n=%.2f,%.2f,%.2f d=%.3f",
                                contact.normal.x, contact.normal.y, contact.normal.z, contact.depth));
            }
            if (first) out.append("no block touched");
            out.append(']');
        }
        return out.length() == 0 ? "no ships intersect the probe" : out.toString();
    }

    /**
     * Every ship block whose collision shape overlaps the world-space box,
     * each with the face the box meets it at. The probe becomes an oriented
     * box in shipyard space and is tested exactly, so rotated ships neither
     * miss touching blocks nor report phantom walls.
     */
    private static List<ShipContact> contacts(Level level, Ship ship, AABB worldBox) {
        Matrix4dc worldToShip = ship.getWorldToShip();
        Matrix4dc shipToWorld = ship.getShipToWorld();
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

        List<ShipContact> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(center.x - extX, center.y - extY, center.z - extZ),
                BlockPos.containing(center.x + extX, center.y + extY, center.z + extZ))) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            for (AABB blockBox : state.getCollisionShape(level, pos).toAabbs()) {
                FaceHit hit = obbContact(center, halfEdges, blockBox.move(pos));
                if (hit == null) continue;
                Vector3d n = new Vector3d();
                n.setComponent(hit.axis, hit.sign);
                shipToWorld.transformDirection(n).normalize();
                found.add(new ShipContact(ship, pos.immutable(), new Vec3(n.x, n.y, n.z), hit.depth));
                break;
            }
        }
        return found;
    }

    /** Which shipyard axis the probe entered a block through, from which side, and how deep. */
    private record FaceHit(int axis, double sign, double depth) {
    }

    /**
     * Separating-axis test between the probe's oriented box and an axis-aligned
     * box, both in shipyard space. Mere surface contact does not count. When
     * they overlap, reports the block face the probe penetrates least: that is
     * the face it came in through.
     */
    private static FaceHit obbContact(Vector3dc center, Vector3d[] halfEdges, AABB aabb) {
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

        int faceAxis = -1;
        double faceSign = 1.0;
        double faceDepth = Double.MAX_VALUE;
        for (int i = 0; i < axes.length; i++) {
            Vector3d axis = axes[i];
            if (axis.lengthSquared() < 1.0e-12) continue;
            double ra = ax * Math.abs(axis.x) + ay * Math.abs(axis.y) + az * Math.abs(axis.z);
            double rb = Math.abs(halfEdges[0].dot(axis)) + Math.abs(halfEdges[1].dot(axis)) + Math.abs(halfEdges[2].dot(axis));
            double along = t.dot(axis);
            double overlap = ra + rb - Math.abs(along);
            if (overlap <= 1.0e-7) return null;
            if (i < 3 && overlap < faceDepth) {
                faceAxis = i;
                faceDepth = overlap;
                faceSign = along < 0.0 ? -1.0 : 1.0;
            }
        }
        return new FaceHit(faceAxis, faceSign, faceDepth);
    }

    /**
     * Remembers the cling position in shipyard space when the cling wall belongs
     * to a ship, so the anchor can follow the ship as it moves.
     */
    public static void captureClingAnchor(LocalPlayer pl, Direction clingDirection) {
        ShipContact wall = SHIP_WALLS.get(clingDirection);
        if (wall == null) {
            clingShip = null;
            return;
        }
        clingShip = wall.ship;
        clingShip.getWorldToShip().transformPosition(VectorConversionsMCKt.toJOML(pl.position()), clingAnchor);
    }

    /**
     * The clung ship's heading in degrees, read off its transform so it matches
     * the rotation the player is actually being swung round by.
     */
    public static Double getClingShipYaw() {
        if (clingShip == null) return null;
        Vector3d forward = clingShip.getShipToWorld().transformDirection(new Vector3d(0.0, 0.0, 1.0));
        return Math.toDegrees(Math.atan2(-forward.x, forward.z));
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
            for (ShipContact wall : SHIP_WALLS.values()) {
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
