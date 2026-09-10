package net.camacraft.walljumpunbound.logic;

import net.camacraft.walljumpunbound.init.ModConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * The wall-cling pose: one arm thrown up onto the wall, the other hanging
 * free, the opposite knee drawn up and the torso leaning into the grip.
 *
 * It is applied after the vanilla animation has already run, so it takes
 * priority over the crouch pose the wall-jump keybind otherwise leaves the
 * player in, and it is blended by weight so the two never snap.
 */
@Environment(EnvType.CLIENT)
public final class WallClingPose {

    /** Fraction of the remaining gap the pose eases through each tick. */
    public static final float BLEND_SPEED = 0.35F;
    /** Below this sideways offset the wall counts as head-on and the main hand takes it. */
    public static final float GRIP_DEAD_ZONE = 0.10F;
    /** How far round the other side the wall must get before the arms swap. */
    public static final float GRIP_SWITCH = 0.35F;

    // Model-space radians. A negative xRot swings a limb up and forward; a
    // positive zRot swings a right-side limb away from the body (and a
    // left-side one into it), which is what the grip/free signs below flip.
    private static final float GRIP_ARM_X = -2.70F;
    private static final float GRIP_ARM_Z = 0.26F;
    private static final float FREE_ARM_X = 0.45F;
    private static final float FREE_ARM_Z = 0.34F;
    private static final float TUCK_LEG_X = -0.85F;
    private static final float TUCK_LEG_Z = 0.22F;
    private static final float TRAIL_LEG_X = 0.32F;
    private static final float TRAIL_LEG_Z = 0.05F;
    private static final float BODY_X = 0.06F;
    private static final float BODY_LEAN_Z = 0.10F;

    // Both hands over the top of the wall: the arms are aimed by reach instead
    // of by a fixed angle, so only the spread and the dangling legs are set here.
    private static final float LEDGE_ARM_Z = 0.13F;
    private static final float LEDGE_LEG_X = 0.12F;
    private static final float LEDGE_LEG_Z = 0.07F;
    private static final float LEDGE_BODY_X = 0.05F;
    /** How far the arms may swing round to aim at a wall that is off to one side. */
    private static final float LEDGE_ARM_YAW_LIMIT = 1.2F;
    /**
     * How far in front of the shoulder a hand has to get to be on top of a
     * ledge: past the wall face, which is half the body's width out, and on by
     * most of the hand itself, so it rests on the ledge rather than against it.
     */
    private static final double LEDGE_HAND_FORWARD = 0.42;
    /** Idle breathing, so a long cling is not a frozen statue. */
    private static final float SWAY = 0.05F;

    // Standing part offsets, i.e. the ones the crouch branch of HumanoidModel
    // moves and this pose has to put back.
    private static final float ARM_Y = 2.0F;
    private static final float LEG_Y = 12.0F;

    private WallClingPose() {
    }

    public static float weight(LivingEntity entity, float partialTick) {
        if (!ModConfig.wallClingPose || !(entity instanceof WallClingHolder holder)) return 0.0F;
        return holder.walljumpunbound$wallClingWeight(partialTick);
    }

    /**
     * Poses the model onto the wall, blended over whatever vanilla just
     * animated. Returns the weight it was applied at, 0 if it was not.
     */
    public static float apply(LivingEntity entity, float ageInTicks, ModelPart head, ModelPart body,
                              ModelPart rightArm, ModelPart leftArm, ModelPart rightLeg, ModelPart leftLeg) {
        if (!ModConfig.wallClingPose || !(entity instanceof WallClingHolder holder)) return 0.0F;
        float weight = holder.walljumpunbound$wallClingWeight(Minecraft.getInstance().getFrameTime());
        if (weight <= 0.0F) return 0.0F;

        // The arm nearest the wall reaches for it (see WallClingHolder).
        boolean rightGrip = holder.walljumpunbound$wallClingGripRight();
        ModelPart gripArm = rightGrip ? rightArm : leftArm;
        ModelPart freeArm = rightGrip ? leftArm : rightArm;
        // The knee opposite the grip comes up, the way a climber counterbalances.
        ModelPart tuckLeg = rightGrip ? leftLeg : rightLeg;
        ModelPart trailLeg = rightGrip ? rightLeg : leftLeg;
        float grip = rightGrip ? 1.0F : -1.0F;
        float free = -grip;

        float sway = Mth.cos(ageInTicks * 0.09F) * SWAY;

        // Near the top of a wall the one-armed cling is dropped for both hands on
        // the ledge, so the grip stops hanging in the air above a short wall.
        float partialTick = Minecraft.getInstance().getFrameTime();
        float ledge = holder.walljumpunbound$wallClingLedgeWeight(partialTick);
        float ledgeArm = ledgeArmAngle(holder.walljumpunbound$wallClingLedgeRise());
        float ledgeYaw = holder.walljumpunbound$isWallClinging() || ledge > 0.0F
                ? ledgeArmYaw(entity, holder.walljumpunbound$wallClingYaw(), partialTick)
                : 0.0F;

        pose(gripArm, weight,
                Mth.lerp(ledge, GRIP_ARM_X + sway, ledgeArm + sway * 0.4F),
                ledge * ledgeYaw,
                Mth.lerp(ledge, GRIP_ARM_Z * grip, LEDGE_ARM_Z * grip), ARM_Y);
        pose(freeArm, weight,
                Mth.lerp(ledge, FREE_ARM_X - sway * 0.5F, ledgeArm + sway * 0.4F),
                ledge * ledgeYaw,
                Mth.lerp(ledge, FREE_ARM_Z * free, LEDGE_ARM_Z * free), ARM_Y);
        pose(tuckLeg, weight,
                Mth.lerp(ledge, TUCK_LEG_X - sway * 0.4F, LEDGE_LEG_X + sway * 0.3F),
                0.0F,
                Mth.lerp(ledge, TUCK_LEG_Z * free, LEDGE_LEG_Z * free), LEG_Y);
        pose(trailLeg, weight,
                Mth.lerp(ledge, TRAIL_LEG_X, LEDGE_LEG_X - sway * 0.3F),
                0.0F,
                Mth.lerp(ledge, TRAIL_LEG_Z * grip, LEDGE_LEG_Z * grip), LEG_Y);

        body.xRot = Mth.lerp(weight, body.xRot, Mth.lerp(ledge, BODY_X, LEDGE_BODY_X));
        body.y = Mth.lerp(weight, body.y, 0.0F);
        // The head keeps looking wherever the player looks; only the crouch drop
        // and a little of the torso's lean carry over to it.
        head.y = Mth.lerp(weight, head.y, 0.0F);

        // Vanilla rewrites every limb rotation each frame, so blending off the
        // current value unwinds itself. It never touches these two rolls, so
        // they are scaled straight off the weight and so return to zero alone.
        // Two hands on a ledge is a square-on hold, so the lean goes with it.
        float lean = (1.0F - ledge) * weight;
        body.zRot = -BODY_LEAN_Z * grip * lean;
        head.zRot = -BODY_LEAN_Z * 0.5F * grip * lean;

        return weight;
    }

    /**
     * The arm angle that puts the hands on a ledge this far above the feet.
     * The arm swings about the shoulder, so one angle fixes where the hand
     * goes: a ledge level with the shoulder has the arms straight out, one at
     * full reach overhead has them all but straight up. The hands must also
     * get out past the wall face to be on the ledge at all, so the arm is
     * never let hang nearer vertical than that reach allows: a low ledge, met
     * from a grab that landed high, is held with the arms angled down and
     * forward onto it rather than dangling in the air beside it.
     */
    private static float ledgeArmAngle(float ledgeRise) {
        // The ledge relative to the shoulder: above (+) or below (-) it.
        double rise = ledgeRise - WallJumpLogic.SHOULDER_HEIGHT;
        double arm = WallJumpLogic.ARM_LENGTH;
        // How far forward a straight arm's hand sits when it is exactly level with the ledge...
        double level = arm * arm - rise * rise;
        double forward = level > 0.0 ? Math.sqrt(level) : 0.0;
        // ...unless that leaves it short of the ledge, in which case aim the arm at the ledge instead.
        if (forward < LEDGE_HAND_FORWARD) forward = LEDGE_HAND_FORWARD;
        return -(float) Math.atan2(forward, -rise);
    }

    /**
     * Swings the reach round towards a ledge that is off to one side. The wall
     * is given by its true bearing, so a ship hull turned off the world axes
     * is reached for where it actually is and not at the nearest cardinal.
     */
    private static float ledgeArmYaw(LivingEntity entity, float wallYaw, float partialTick) {
        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        float bearing = Mth.wrapDegrees(wallYaw - bodyYaw) * Mth.DEG_TO_RAD;
        return Mth.clamp(bearing, -LEDGE_ARM_YAW_LIMIT, LEDGE_ARM_YAW_LIMIT);
    }

    private static void pose(ModelPart part, float weight, float xRot, float yRot, float zRot, float y) {
        part.xRot = Mth.lerp(weight, part.xRot, xRot);
        part.yRot = Mth.lerp(weight, part.yRot, yRot);
        part.zRot = Mth.lerp(weight, part.zRot, zRot);
        part.y = Mth.lerp(weight, part.y, y);
        part.z = Mth.lerp(weight, part.z, 0.0F);
    }
}
