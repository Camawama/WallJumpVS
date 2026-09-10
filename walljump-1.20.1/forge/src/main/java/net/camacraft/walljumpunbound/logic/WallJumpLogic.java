package net.camacraft.walljumpunbound.logic;

import net.camacraft.walljumpunbound.WallJumpClient;
import net.camacraft.walljumpunbound.compat.GravityCompat;
import net.camacraft.walljumpunbound.compat.VSCompat;
import net.camacraft.walljumpunbound.init.ModConfig;
import net.camacraft.walljumpunbound.init.ModConfig.BlockListMode;
import net.camacraft.walljumpunbound.init.ModEnchantments;
import net.camacraft.walljumpunbound.init.ServerConfig;
import net.camacraft.walljumpunbound.network.PacketHandler;
import net.camacraft.walljumpunbound.network.message.MessageFallDistance;
import net.camacraft.walljumpunbound.network.message.MessageWallCling;
import net.camacraft.walljumpunbound.network.message.MessageWallJump;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.ModList;
import org.joml.Quaternionf;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
public class WallJumpLogic {

    private static final Logger LOGGER = LoggerFactory.getLogger("walljumpunbound");

    // VS classes are only touched through VSCompat, and only when the mod is present.
    private static final boolean VS_LOADED = ModList.get().isLoaded("valkyrienskies");

    /** How often a held cling is repeated to the server, in ticks. */
    private static final int CLING_SYNC_INTERVAL = 20;
    /** Height of the gripping hand above the feet, in blocks. */
    private static final double HAND_HEIGHT = 1.9;
    /** How far the gripping hand sits to its own side of the body. */
    private static final double HAND_REACH = 0.3;
    /**
     * Shoulder height above the feet, and the distance from that shoulder to the
     * hand: the reach envelope. The arm cube is 12px tall but hangs from y=-2 to
     * y=10 about its pivot, so only 10px of it is below the shoulder — using the
     * cube's full length here left the hands short of a ledge overhead.
     */
    public static final double SHOULDER_HEIGHT = 1.375;
    public static final double ARM_LENGTH = 0.625;
    /** A ledge is grabbable while it sits within one arm of the shoulder. */
    public static final double LEDGE_RISE_MIN = SHOULDER_HEIGHT - ARM_LENGTH;
    public static final double LEDGE_RISE_MAX = SHOULDER_HEIGHT + ARM_LENGTH;
    /** How far past the wall face the ledge probe sits, so it is inside the wall. */
    private static final double LEDGE_PROBE_OUT = 0.15;
    /** Side of the cube used to ask whether the wall is still there at a height. */
    private static final double LEDGE_PROBE_SIZE = 0.15;
    /** Bisection steps; six over the reach envelope lands inside a quarter pixel. */
    private static final int LEDGE_PROBE_STEPS = 6;
    /** How far the player may turn from their grip before letting go, in degrees. */
    private static final float LEDGE_RELEASE_YAW = 90.0F;

    public static int ticksWallClinged;
    public static int ticksWallSlid;
    public static boolean stopSlid = false;
    public static int wallJumpCount;
    private static int ticksKeyDown;
    private static float clingYaw;
    private static int lastHurtTime;
    private static Vec3 lastPos;
    private static double clingFall;
    private static Vec3 lastShipHold;
    private static Vec3 heldPos;
    private static Vec3 shipCarry = Vec3.ZERO;
    private static Double lastShipYaw;
    private static Vec3 drift = Vec3.ZERO;
    private static boolean clingSent;
    private static Direction clingWallSent;
    private static int ticksSinceClingSent;
    private static double clingX, clingZ;
    private static double lastJumpHeight = Double.MAX_VALUE;
    private static Set<Direction> walls = new HashSet<>();
    private static Set<Direction> staleWalls = new HashSet<>();

    // GRAVITY UNBOUND: while the player's gravity frame is rotated, every
    // direction below is expressed in that frame — "horizontal" is the
    // frame's tangent plane, "up" its up axis, and the player's velocity
    // and yaw are frame-local already. Null under plain gravity.
    private static Quaternionf gravityFrame = null;
    private static Vec3 gravityUp = new Vec3(0.0, 1.0, 0.0);
    // the point the cling holds (frame mode pins the tangential position to it)
    private static Vec3 clingAnchor = null;
    // the wall block each probed direction found (frame mode)
    private static final Map<Direction, BlockPos> FRAME_WALLS = new EnumMap<>(Direction.class);

    /** Whether the player's box would be inside something at this position. */
    private static boolean blockedAt(LocalPlayer pl, Vec3 at) {
        // Narrowed off the sides so the wall being clung to is not itself the
        // obstruction, the same way the ground test in canWallCling does it.
        AABB box = pl.getBoundingBox().move(at.subtract(pl.position())).deflate(0.05, 0.0, 0.05);
        if (collidesWithBlock(pl.level(), box)) return true;
        return vsEnabled() && VSCompat.intersectsShipBlock(pl.level(), box);
    }

    private static String fmt(Vec3 v) {
        return String.format("%.3f,%.3f,%.3f", v.x, v.y, v.z);
    }

    private static String fmt(double d) {
        return String.format("%.3f", d);
    }

    private static boolean collidesWithBlock(Level level, AABB box) {
        return !level.noCollision(box);
    }

    // The compat can be switched off in the config even when VS is installed.
    private static boolean vsEnabled() {
        return VS_LOADED && ModConfig.enableVSCompat;
    }

    /** The player's height along its frame's up (plain Y under vanilla gravity). */
    private static double height(LocalPlayer pl) {
        return gravityFrame != null ? pl.position().dot(gravityUp) : pl.getY();
    }

    public static void doWallJump(LocalPlayer pl) {
        if (!canWallJump(pl))
            return;

        gravityFrame = GravityCompat.isActive(pl) ? GravityCompat.frame(pl) : null;
        gravityUp = gravityFrame != null ? GravityCompat.up(gravityFrame) : new Vec3(0.0, 1.0, 0.0);

        // Both tracked before any early return below. A hit knocks the player off
        // the wall, and the rise of hurtTime is the only sign of it the client
        // gets; the ground the player covered this tick sizes the ship probe.
        boolean justHurt = pl.hurtTime > lastHurtTime;
        lastHurtTime = pl.hurtTime;
        drift = lastPos == null ? Vec3.ZERO : pl.position().subtract(lastPos);
        lastPos = pl.position();

        if (pl.onGround() || pl.getAbilities().flying || !pl.level().getFluidState(pl.blockPosition()).isEmpty() || pl.isHandsBusy()) {
            ticksWallClinged = 0;
            ticksWallSlid = 0;
            stopSlid = false;
            clingX = Double.NaN;
            clingZ = Double.NaN;
            clingAnchor = null;
            clingFall = 0.0;
            lastShipHold = null;
            heldPos = null;
            shipCarry = Vec3.ZERO;
            lastShipYaw = null;
            lastJumpHeight = Double.MAX_VALUE;
            staleWalls.clear();
            wallJumpCount = 0;
            FRAME_WALLS.clear();
            if (VS_LOADED) {
                VSCompat.clearShipWalls();
                VSCompat.clearClingAnchor();
            }

            return;
        }

        // In a wall-jump sequence — clinging, or airborne between wall jumps —
        // the planet-walk surface the sequence started from stays snapped
        // (see GravityCompat.sustainHeldSurface); the sequence ends on the
        // ground, where the reset above runs.
        if (gravityFrame != null && (ticksWallClinged > 0 || wallJumpCount > 0)) {
            GravityCompat.sustainHeldSurface(pl);
        }

        if (stopSlid) return;

        updateWalls(pl);
        ticksKeyDown = WallJumpClient.KEY_WALL_JUMP.isDown() ? ticksKeyDown + 1 : 0;

        if (ticksWallClinged < 1) {
            if (ticksKeyDown > 0 && ticksKeyDown < 4 && !walls.isEmpty() && canWallCling(pl)) {
                // A rotated ship's wall normal is rarely axis-aligned, so snapping
                // the camera to a cardinal direction looks wrong there.
                if (ModConfig.autoRotation && !(vsEnabled() && VSCompat.isShipWall(getClingDirection()))) {
                    pl.setYRot(getClingDirection().getOpposite().toYRot());
                    pl.yRotO = pl.getYRot();
                }

                ticksWallClinged = 1;
                // A grip arrests the player, so a ship cling starts held rather
                // than shedding an arrival speed. Seeding this from a measurement
                // meant any error in the ship's reported motion was paid off as a
                // slide, which on a falling ship ran the whole wall.
                clingFall = 0.0;
                lastShipHold = null;
                clingYaw = pl.getYRot();
                clingX = pl.getX();
                clingZ = pl.getZ();
                clingAnchor = pl.position();
                if (vsEnabled()) VSCompat.captureClingAnchor(pl, getClingDirection());
                // Where the hold starts, and where the ship had it at that moment:
                // from here the hold is carried by the ship's steps, never moved to
                // wherever the anchor claims to be.
                heldPos = pl.position();
                lastShipHold = vsEnabled() ? VSCompat.getClingWorldPos() : null;
                lastShipYaw = vsEnabled() ? VSCompat.getClingShipYaw() : null;

                playHitSound(pl, getWallPos(pl));
                spawnWallParticle(pl, getWallPos(pl));
            }

            return;
        }

        // Worked out once here: the release below needs it, and so does the slide.
        boolean atLedge = ServerConfig.ledgeGrab && !walls.isEmpty()
                && !Double.isNaN(ledgeRise(pl, getClingDirection()));

        // A grip turns with the ship. Valkyrien Skies swings a rider's yaw round as
        // the ship turns, which the release below otherwise reads as the player
        // turning away from a wall they are still square to.
        if (vsEnabled()) {
            Double shipYaw = VSCompat.getClingShipYaw();
            if (shipYaw != null) {
                if (lastShipYaw != null) clingYaw += Mth.wrapDegrees((float) (shipYaw - lastShipYaw));
                lastShipYaw = shipYaw;
            }
        }

        if (!WallJumpClient.KEY_WALL_JUMP.isDown() || pl.onGround() || !pl.level().getFluidState(pl.blockPosition()).isEmpty() || walls.isEmpty() || pl.getFoodData().getFoodLevel() < 1
                || justHurt || (atLedge && turnedFromCling(pl))) {
            if (ModConfig.debugShipCling && ticksWallClinged > 0) {
                LOGGER.info("[cling] release: key={} ground={} fluid={} noWalls={} food={} hurt={} turned={}",
                        !WallJumpClient.KEY_WALL_JUMP.isDown(), pl.onGround(),
                        !pl.level().getFluidState(pl.blockPosition()).isEmpty(), walls.isEmpty(),
                        pl.getFoodData().getFoodLevel() < 1, justHurt, atLedge && turnedFromCling(pl));
            }
            ticksWallClinged = 0;
            if (VS_LOADED) VSCompat.clearClingAnchor();
            // Hand the ship's motion back on the way out, so letting go of a
            // moving ship does not leave the player standing still beside it.
            if (shipCarry.lengthSqr() > 0.0) {
                pl.setDeltaMovement(shipCarry);
                shipCarry = Vec3.ZERO;
            }

            if ((pl.input.forwardImpulse != 0 || pl.input.leftImpulse != 0) && !pl.onGround() && !walls.isEmpty()) {
                if (wallJumpCount >= ServerConfig.maxWallJumps) return;
                pl.resetFallDistance();
                PacketHandler.sendToServer(new MessageWallJump(true));

                wallJump(pl, (float) ServerConfig.wallJumpHeight);
                staleWalls = new HashSet<>(walls);
            }

            return;
        }

        Vec3 anchor = clingAnchor;
        Vec3 shipAnchor = null;
        if (vsEnabled() && ModConfig.stickToMovingShips) {
            shipAnchor = VSCompat.getClingWorldPos();
            if (shipAnchor != null) {
                clingX = shipAnchor.x;
                clingZ = shipAnchor.z;
                anchor = shipAnchor;
            }
        }
        // How far the ship carried the hold since last tick, taken from where its
        // transform actually put that hold. The ship's own reported velocity is
        // deliberately not used: it comes from separate physics fields, and on a
        // moving or turning ship any disagreement between them and the transform
        // was charged to the player as a fall they then had to slide off.
        Vec3 shipStep = shipAnchor != null && lastShipHold != null
                ? shipAnchor.subtract(lastShipHold) : Vec3.ZERO;
        lastShipHold = shipAnchor;
        // A ship cling is held by position at the end instead, all three axes of
        // it, so nothing is pinned here.
        if (shipAnchor == null) {
            if (gravityFrame != null && anchor != null) {
                // hold the position in the frame's tangent plane; only motion
                // along the frame's up is free (the slide below)
                Vec3 pos = pl.position();
                double along = pos.subtract(anchor).dot(gravityUp);
                Vec3 pinned = anchor.add(gravityUp.scale(along));
                pl.setPos(pinned.x, pinned.y, pinned.z);
            } else {
                pl.setPos(clingX, pl.getY(), clingZ);
            }
        }
        // Slide states are judged relative to the ship so its motion neither
        // triggers nor cancels them.
        double motionY;
        if (shipAnchor != null) {
            // Carried forward as state, not measured. The hold below is a
            // position, so measuring the player here only reads back the motion
            // that hold imposed: the slide feeds itself and runs away, which on a
            // fast ship is a cling that grabs for a frame and drops like a stone.
            motionY = clingFall;
        } else {
            motionY = pl.getDeltaMovement().y;
        }
        if (motionY > 0.0) {
            motionY = 0.0;
        } else if (motionY < -0.6) {
            motionY = motionY + 0.2;
            spawnWallParticle(pl, getWallPos(pl));
        } else if (atLedge) {
            // Hands over the top of the wall: a grip on the ledge does not slip,
            // so neither the slide nor the give-up timer below ever starts.
            motionY = 0.0;
        } else if (ticksWallClinged++ > ServerConfig.wallSlideDelay) {
            if (ticksWallSlid++ > ServerConfig.stopWallSlideDelay) stopSlid = true;
            motionY = -0.1;
            spawnWallParticle(pl, getWallPos(pl));
        } else {
            motionY = 0.0;
        }

        if (pl.fallDistance > 2) {
            pl.resetFallDistance();
            PacketHandler.sendToServer(new MessageFallDistance((float) (motionY * motionY * 8)));
        }

        if (ModConfig.debugShipCling && shipAnchor != null && pl.tickCount % 4 == 0) {
            Vec3 p = pl.position();
            LOGGER.info("[cling] pos={} anchorMinusPos={} shipStep={} shipVel={} fall={} motionY={} slid={}",
                    fmt(p), fmt(shipAnchor.subtract(p)), fmt(shipStep),
                    fmt(VSCompat.getShipPointVelocity(pl)), fmt(clingFall), fmt(motionY), ticksWallSlid);
        }

        if (shipAnchor != null) {
            clingFall = motionY;
            // Dead reckoned: carry the hold by how far the ship moved it, rather
            // than teleporting to where the anchor says it is. A transform that
            // disagrees with the player by some offset cancels out of a
            // difference but yanks in an absolute, which is the pull down the
            // wall and off to one side. Starting from the held position rather
            // than the player's also drops the gravity they picked up this tick,
            // so nothing accumulates.
            Vec3 base = heldPos != null ? heldPos : pl.position();
            Vec3 hold = base.add(shipStep).add(gravityUp.scale(motionY));
            // Holding by position skips the collision that a velocity would have
            // been checked against, so the descent has to test for itself or it
            // slides straight through the ship's own floor.
            if (motionY < 0.0 && blockedAt(pl, hold)) hold = base.add(shipStep);
            heldPos = hold;
            pl.setPos(hold.x, hold.y, hold.z);
            // Held so a wall jump off a moving ship keeps the ship's momentum.
            // Frame-local under a gravity frame, as the player's velocity is.
            // Left at rest rather than carrying the ship's speed. Commanding it
            // meant move() drove the player that far through the ship's own
            // geometry before this code ran next tick, and the collision that
            // caused reported them as landed, which drops the cling on its very
            // first check. The position hold above already carries them.
            shipCarry = gravityFrame != null ? GravityCompat.toLocal(shipStep, gravityFrame) : shipStep;
            pl.setDeltaMovement(Vec3.ZERO);
        } else {
            pl.setDeltaMovement(0.0, motionY, 0.0);
        }
    }

    /** True while the player is held to a wall; a forced slide stop is not a cling. */
    public static boolean isClinging() {
        return ticksWallClinged > 0 && !stopSlid;
    }

    /**
     * Publishes the cling for the pose. The local player is posed straight from
     * this flag; the server hears about it whenever it flips, so that everyone
     * tracking the player sees the pose too. The repeat covers a player who
     * only comes into view partway through someone else's cling.
     */
    public static void updateClingPose(LocalPlayer pl) {
        boolean clinging = isClinging();
        Direction wall = clinging && !walls.isEmpty() ? getClingDirection() : null;
        if (pl instanceof WallClingHolder holder) holder.walljumpunbound$setWallCling(clinging, wall);
        if (pl instanceof WallClingPosture posture) posture.walljumpunbound$setWallClingPosture(clinging);

        if (clinging != clingSent || wall != clingWallSent || (clinging && ++ticksSinceClingSent >= CLING_SYNC_INTERVAL)) {
            clingSent = clinging;
            clingWallSent = wall;
            ticksSinceClingSent = 0;
            PacketHandler.sendToServer(new MessageWallCling(clinging, wall));
        }
    }

    /**
     * True once the player has turned far enough from the way they were facing
     * when they took hold. Hands on a ledge cannot follow the body round a full
     * turn, so past a right angle the grip is given up rather than swivelling.
     */
    private static boolean turnedFromCling(LocalPlayer pl) {
        return Math.abs(Mth.wrapDegrees(pl.getYRot() - clingYaw)) > LEDGE_RELEASE_YAW;
    }

    private static boolean canWallJump(LocalPlayer pl) {
        if (ServerConfig.useWallJump) return true;
        if (!ServerConfig.enableEnchantments || !ServerConfig.enableWallJump)
            return false;
        ItemStack stack = pl.getItemBySlot(EquipmentSlot.FEET);
        if (!stack.isEmpty()) {
            Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(stack);
            return enchantments.containsKey(ModEnchantments.WALL_JUMP.get());
        }

        return false;
    }

    private static boolean canWallCling(LocalPlayer pl) {
        if (pl.onClimbable() || pl.getFoodData().getFoodLevel() < 1) return false;
        Vec3 velocity = clingVelocity(pl);
        if (velocity.y > 0.1) return false;
        if (gravityFrame != null) {
            // ground within 0.8 below the feet, down the FRAME (Valkyrien
            // Skies raycasts through ships natively, so this covers ships)
            if (groundBelow(pl, 0.8 * bodyScale(pl))) return false;
        } else {
            AABB below = pl.getBoundingBox().move(0, -0.8, 0);
            if (collidesWithBlock(pl.level(), below)) return false;
            // Deflated so millimeter penetration into the cling wall itself does not
            // read as ground; ships resolve collisions with a little slop.
            if (vsEnabled() && VSCompat.intersectsShipBlock(pl.level(), below.deflate(0.05, 0.0, 0.05))) return false;
        }
        if (!ServerConfig.onFallWallCling && velocity.y < -0.8) return false;
        if (ServerConfig.allowReClinging || height(pl) < lastJumpHeight - 1) return true;
        return !staleWalls.containsAll(walls);
    }

    private static double bodyScale(LocalPlayer pl) {
        return Math.max(0.05, Math.min(1.0, pl.getBbHeight() / 1.8));
    }

    /**
     * How far above the feet the wall being clung to ends, or NaN when it has no
     * top within arm's reach — either it carries on past the hands, or there is
     * nothing up there to hold.
     *
     * This asks "is the wall still solid at this height" the same way the rest of
     * the mod does — a small box tested against the world and, separately, against
     * ship collision shapes — because that is what sees a Valkyrien Skies hull at
     * any rotation. The top is then bisected between a solid height and a clear
     * one, which needs the wall to actually stop inside the envelope: a wall that
     * is still solid at full reach has no grabbable top and returns NaN.
     */
    public static double ledgeRise(LivingEntity entity, Direction wall) {
        Quaternionf frame = GravityCompat.isActive(entity) ? GravityCompat.frame(entity) : null;
        Vec3 up = frame != null ? GravityCompat.up(frame) : new Vec3(0.0, 1.0, 0.0);
        Vec3 into = Vec3.atLowerCornerOf(wall.getNormal());
        if (frame != null) into = GravityCompat.toWorld(into, frame);

        if (wallAt(entity, into, up, LEDGE_RISE_MAX)) return Double.NaN;
        if (!wallAt(entity, into, up, LEDGE_RISE_MIN)) return Double.NaN;

        double solid = LEDGE_RISE_MIN, clear = LEDGE_RISE_MAX;
        for (int i = 0; i < LEDGE_PROBE_STEPS; i++) {
            double mid = (solid + clear) / 2;
            if (wallAt(entity, into, up, mid)) solid = mid;
            else clear = mid;
        }
        // The probe clears the wall once its underside passes the top, so the top
        // itself is half a probe lower than the lowest clear height found.
        return clear - LEDGE_PROBE_SIZE / 2;
    }

    /**
     * Whether the clung wall is still solid this far above the feet. The probe is
     * a thin slice of the player's own footprint reaching towards the wall, which
     * is how updateWalls finds a ship wall: a hull turned off the world axes meets
     * the player at a corner of their box, well off a probe aimed down the middle.
     */
    private static boolean wallAt(LivingEntity entity, Vec3 into, Vec3 up, double rise) {
        Vec3 at = entity.position().add(up.scale(rise));
        double half = entity.getBbWidth() / 2, thick = LEDGE_PROBE_SIZE / 2;
        AABB slice = new AABB(at.x - half, at.y - thick, at.z - half, at.x + half, at.y + thick, at.z + half)
                .expandTowards(into.x * LEDGE_PROBE_OUT, into.y * LEDGE_PROBE_OUT, into.z * LEDGE_PROBE_OUT);
        if (collidesWithBlock(entity.level(), slice)) return true;
        return vsEnabled() && VSCompat.intersectsShipBlock(entity.level(), slice);
    }

    private static boolean groundBelow(LocalPlayer pl, double depth) {
        Vec3 from = pl.position().add(gravityUp.scale(0.05));
        BlockHitResult hit = pl.level().clip(new ClipContext(
                from, from.subtract(gravityUp.scale(0.05 + depth)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, pl));
        return hit.getType() == HitResult.Type.BLOCK;
    }

    /**
     * Velocity used for cling checks: relative to the ship when the candidate
     * wall belongs to one, so cling behaves the same on a moving ship.
     */
    private static Vec3 clingVelocity(LocalPlayer pl) {
        if (vsEnabled() && hasShipWall()) {
            Vec3 shipVelocity = VSCompat.getShipPointVelocity(pl);
            // A ship holding still is left exactly as it was.
            if (shipVelocity.lengthSqr() > 1.0E-6) {
                // Measured rather than assumed: the ground the player actually
                // covered this tick, less the ground the ship covered under them.
                // Subtracting the ship out of getDeltaMovement only holds if the
                // ship's motion was ever put in there, and a rider carried by
                // position rather than by velocity reads as flying upwards at the
                // ship's falling speed — which refused every cling on a ship on
                // its way down. A displacement is the same either way.
                Vec3 relative = drift.subtract(shipVelocity);
                return gravityFrame != null ? GravityCompat.toLocal(relative, gravityFrame) : relative;
            }
        }
        return pl.getDeltaMovement();
    }

    private static boolean hasShipWall() {
        for (Direction direction : walls) {
            if (VSCompat.isShipWall(direction)) return true;
        }
        return false;
    }

    private static void updateWalls(LocalPlayer pl) {
        if (gravityFrame != null) {
            updateWallsInFrame(pl);
            return;
        }

        Vec3 pos = pl.position();
        AABB box = new AABB(pos.x - 0.001, pos.y, pos.z - 0.001, pos.x + 0.001, pos.y + pl.getEyeHeight(), pos.z + 0.001);

        double dist = (pl.getBbWidth() / 2) + (ticksWallClinged > 0 ? 0.1 : 0.06);
        AABB[] axes = {box.expandTowards(0, 0, dist), box.expandTowards(-dist, 0, 0), box.expandTowards(0, 0, -dist), box.expandTowards(dist, 0, 0)};

        // A rotated ship stops the player at a corner of their bounding box, out
        // of reach of the thin centered probes above, so ships are probed with
        // the whole box instead: it already touches the wall at any rotation.
        boolean vs = vsEnabled();
        AABB[] shipProbes = null;
        if (vs) {
            double reach = ModConfig.shipWallDetectionRange + (ticksWallClinged > 0 ? 0.04 : 0.0);
            AABB bb = pl.getBoundingBox();
            shipProbes = new AABB[]{bb.expandTowards(0, 0, reach), bb.expandTowards(-reach, 0, 0), bb.expandTowards(0, 0, -reach), bb.expandTowards(reach, 0, 0)};
        }

        walls = new HashSet<>();
        FRAME_WALLS.clear();
        if (VS_LOADED) VSCompat.clearShipWalls();

        for (int i = 0; i < 4; i++) {
            Direction direction = Direction.from2DDataValue(i);

            if (collidesWithBlock(pl.level(), axes[i])) {
                if (ServerConfig.blockListMode == BlockListMode.DISABLED || ServerConfig.blockList.isEmpty() || areBlocksAllowed(getBlockId(pl, pl.blockPosition().relative(direction)), getBlockId(pl, pl.blockPosition().above().relative(direction)))) {
                    walls.add(direction);
                    pl.horizontalCollision = true;
                    continue;
                }
            }

            if (vs) {
                BlockPos shipWall = VSCompat.findShipWall(pl, shipProbes[i], direction);
                if (shipWall != null) {
                    if (ServerConfig.blockListMode == BlockListMode.DISABLED || ServerConfig.blockList.isEmpty() || areBlocksAllowed(getBlockId(pl, shipWall), getBlockId(pl, shipWall.above()))) {
                        walls.add(direction);
                        pl.horizontalCollision = true;
                    } else {
                        VSCompat.forgetShipWall(direction);
                    }
                }
            }
        }
    }

    /**
     * Wall detection under a rotated gravity frame. The world-axis box
     * probes cannot express the frame's tangent plane (and, standing on a
     * plated wall, found the very floor under the player as a "wall" beside
     * it — the cling-to-air). Cast short rays from the body's axis along
     * each frame-horizontal direction instead, at three heights along the
     * frame's up; Valkyrien Skies raycasts ships natively, so a ship wall
     * is found the same way and registered for the moving-ship anchor.
     */
    private static void updateWallsInFrame(LocalPlayer pl) {
        walls = new HashSet<>();
        FRAME_WALLS.clear();
        if (VS_LOADED) VSCompat.clearShipWalls();

        double reach = (pl.getBbWidth() / 2) + (ticksWallClinged > 0 ? 0.1 : 0.06) * bodyScale(pl);
        double h = pl.getBbHeight();
        double[] heights = {0.15 * h, 0.5 * h, 0.85 * h};
        Direction upDir = Direction.getNearest(gravityUp.x, gravityUp.y, gravityUp.z);

        for (int i = 0; i < 4; i++) {
            Direction direction = Direction.from2DDataValue(i);
            Vec3 dirWorld = GravityCompat.toWorld(Vec3.atLowerCornerOf(direction.getNormal()), gravityFrame).normalize();

            BlockPos wallPos = null;
            for (double hh : heights) {
                Vec3 from = pl.position().add(gravityUp.scale(hh));
                BlockHitResult hit = pl.level().clip(new ClipContext(
                        from, from.add(dirWorld.scale(reach)),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, pl));
                if (hit.getType() == HitResult.Type.BLOCK) {
                    wallPos = hit.getBlockPos().immutable();
                    break;
                }
            }
            if (wallPos == null) continue;

            if (ServerConfig.blockListMode != BlockListMode.DISABLED && !ServerConfig.blockList.isEmpty()
                    && !areBlocksAllowed(getBlockId(pl, wallPos), getBlockId(pl, wallPos.relative(upDir)))) {
                continue;
            }

            walls.add(direction);
            FRAME_WALLS.put(direction, wallPos);
            pl.horizontalCollision = true;
            if (vsEnabled()) VSCompat.registerShipWallAt(pl.level(), direction, wallPos);
        }
    }

    private static String getBlockId(LocalPlayer pl, BlockPos pos) {
        BlockState state = pl.level().getBlockState(pos);
        return (state.isSolid()) ? BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString() : null;
    }

    private static boolean areBlocksAllowed(String... blocks) {
        for (String block : blocks)
            if ((block != null) && switch (ServerConfig.blockListMode) {
                case BLACKLIST -> !ServerConfig.blockList.contains(block);
                case WHITELIST -> ServerConfig.blockList.contains(block);
                default -> true;
            }) return true;
        return false;
    }

    private static Direction getClingDirection() {
        return walls.isEmpty() ? Direction.UP : walls.iterator().next();
    }

    private static BlockPos getWallPos(LocalPlayer player) {
        Direction direction = getClingDirection();
        if (gravityFrame != null) {
            BlockPos frameWall = FRAME_WALLS.get(direction);
            if (frameWall != null) return frameWall;
        }
        if (VS_LOADED) {
            BlockPos shipWall = VSCompat.getShipWallPos(direction);
            if (shipWall != null) return shipWall;
        }
        BlockPos blockPos = player.getOnPos().relative(direction);
        return player.level().getBlockState(blockPos).isSolid() ? blockPos : blockPos.relative(Direction.UP);
    }

    private static void wallJump(LocalPlayer pl, float up) {
        float strafe = Math.signum(pl.input.leftImpulse) * up * up;
        float forward = Math.signum(pl.input.forwardImpulse) * up * up;

        float f = (float) (1.0F / Math.sqrt(strafe * strafe + up * up + forward * forward));
        strafe = strafe * f;
        forward = forward * f;

        float f1 = (float) (Math.sin(pl.getYRot() * 0.017453292F) * 0.45F);
        float f2 = (float) (Math.cos(pl.getYRot() * 0.017453292F) * 0.45F);

        int jumpBoostLevel = 0;
        MobEffectInstance jumpBoostEffect = pl.getEffect(MobEffects.JUMP);
        if (jumpBoostEffect != null) jumpBoostLevel = jumpBoostEffect.getAmplifier() + 1;

        // (yaw and velocity are frame-local under a gravity frame, so this
        // composes the jump in the right frame as it is)
        Vec3 motion = pl.getDeltaMovement();
        pl.setDeltaMovement(motion.x + (strafe * f2 - forward * f1), up + (jumpBoostLevel * 0.125), motion.z + (forward * f2 + strafe * f1));

        lastJumpHeight = height(pl);
        playBreakSound(pl, getWallPos(pl));
        spawnWallParticle(pl, getWallPos(pl));
        wallJumpCount++;
    }

    private static void playHitSound(Entity entity, BlockPos blockPos) {
        BlockState state = entity.level().getBlockState(blockPos);
        SoundType soundtype = state.getBlock().getSoundType(state, entity.level(), blockPos, entity);
        entity.playSound(soundtype.getHitSound(), soundtype.getVolume() * 0.25F, soundtype.getPitch());
    }

    private static void playBreakSound(Entity entity, BlockPos blockPos) {
        BlockState state = entity.level().getBlockState(blockPos);
        SoundType soundtype = state.getBlock().getSoundType(state, entity.level(), blockPos, entity);
        entity.playSound(soundtype.getFallSound(), soundtype.getVolume() * 0.5F, soundtype.getPitch());
    }

    /**
     * Where the cling scuffs the wall: the wall face beside the gripping hand,
     * so the scrape comes off the hand the pose has on the wall rather than off
     * the feet. Falls back to the entity's own position when there is no pose to
     * match it to — pushing off in a wall jump, or the pose switched off.
     */
    private static Vec3 clingContactPos(Entity entity) {
        if (!ModConfig.wallClingPose || !isClinging() || walls.isEmpty()
                || !(entity instanceof WallClingHolder holder)) return entity.position();

        // Yaw, the wall direction and the offset are all frame-local under a
        // gravity frame, so the whole offset is converted in one go at the end.
        float yaw = entity instanceof LivingEntity living ? living.yBodyRot : entity.getYRot();
        float rad = yaw * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(rad), 0.0, -Mth.sin(rad));

        Vec3 offset = right.scale(holder.walljumpunbound$wallClingGripRight() ? HAND_REACH : -HAND_REACH)
                .add(0.0, HAND_HEIGHT, 0.0)
                .add(Vec3.atLowerCornerOf(getClingDirection().getNormal()).scale(entity.getBbWidth() / 2));
        if (gravityFrame != null) offset = GravityCompat.toWorld(offset, gravityFrame);

        return entity.position().add(offset);
    }

    private static void spawnWallParticle(Entity entity, BlockPos blockPos) {
        BlockState state = entity.level().getBlockState(blockPos);
        if (state.getRenderShape() != RenderShape.INVISIBLE) {
            Vec3 pos = clingContactPos(entity);
            Vec3i motion = getClingDirection().getNormal();
            Vec3 velocity = new Vec3(motion.getX() * -1.0D, -1.0D, motion.getZ() * -1.0D);
            if (gravityFrame != null) velocity = GravityCompat.toWorld(velocity, gravityFrame);

            entity.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state).setPos(blockPos), pos.x, pos.y, pos.z,
                    velocity.x, velocity.y, velocity.z);
        }
    }
}
