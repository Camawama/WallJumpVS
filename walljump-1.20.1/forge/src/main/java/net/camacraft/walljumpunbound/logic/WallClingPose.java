package net.camacraft.walljumpunbound.logic;

import net.camacraft.walljumpunbound.init.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The wall-cling pose: one arm thrown up onto the wall, the other hanging
 * free, the opposite knee drawn up and the torso leaning into the grip.
 *
 * It is applied after the vanilla animation has already run, so it takes
 * priority over the crouch pose the wall-jump keybind otherwise leaves the
 * player in, and it is blended by weight so the two never snap.
 */
@OnlyIn(Dist.CLIENT)
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

        pose(gripArm, weight, GRIP_ARM_X + sway, GRIP_ARM_Z * grip, ARM_Y);
        pose(freeArm, weight, FREE_ARM_X - sway * 0.5F, FREE_ARM_Z * free, ARM_Y);
        pose(tuckLeg, weight, TUCK_LEG_X - sway * 0.4F, TUCK_LEG_Z * free, LEG_Y);
        pose(trailLeg, weight, TRAIL_LEG_X, TRAIL_LEG_Z * grip, LEG_Y);

        body.xRot = Mth.lerp(weight, body.xRot, BODY_X);
        body.y = Mth.lerp(weight, body.y, 0.0F);
        // The head keeps looking wherever the player looks; only the crouch drop
        // and a little of the torso's lean carry over to it.
        head.y = Mth.lerp(weight, head.y, 0.0F);

        // Vanilla rewrites every limb rotation each frame, so blending off the
        // current value unwinds itself. It never touches these two rolls, so
        // they are scaled straight off the weight and so return to zero alone.
        body.zRot = -BODY_LEAN_Z * grip * weight;
        head.zRot = -BODY_LEAN_Z * 0.5F * grip * weight;

        return weight;
    }

    private static void pose(ModelPart part, float weight, float xRot, float zRot, float y) {
        part.xRot = Mth.lerp(weight, part.xRot, xRot);
        part.yRot = Mth.lerp(weight, part.yRot, 0.0F);
        part.zRot = Mth.lerp(weight, part.zRot, zRot);
        part.y = Mth.lerp(weight, part.y, y);
        part.z = Mth.lerp(weight, part.z, 0.0F);
    }
}
