package net.camacraft.walljumpunbound.init.mixin;

import net.camacraft.walljumpunbound.init.ModConfig;
import net.camacraft.walljumpunbound.logic.WallClingHolder;
import net.camacraft.walljumpunbound.logic.WallClingPose;
import net.camacraft.walljumpunbound.logic.WallJumpLogic;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin implements WallClingHolder {

    @Unique
    private boolean walljumpunbound$clinging;
    @Unique
    private Direction walljumpunbound$clingWall;
    @Unique
    private float walljumpunbound$clingYaw;
    @Unique
    private boolean walljumpunbound$clingShip;
    @Unique
    private boolean walljumpunbound$gripRight = true;
    @Unique
    private float walljumpunbound$clingWeight;
    @Unique
    private float walljumpunbound$clingWeightO;
    @Unique
    private float walljumpunbound$ledgeRise;
    @Unique
    private float walljumpunbound$ledgeWeight;
    @Unique
    private float walljumpunbound$ledgeWeightO;

    /**
     * The pose eases here rather than in the model so it moves at a fixed rate
     * whatever the frame rate. TAIL, because the local player's flag is set
     * from the aiStep this very call drives.
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void walljumpunbound$tickWallClingPose(CallbackInfo ci) {
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        // Nothing clings from the ground, so this is the backstop for a remote
        // player whose cling ended while nobody was tracking them. The local
        // player rewrites the flag every tick and never needs it.
        if (this.walljumpunbound$clinging && self.onGround() && !self.isLocalPlayer()) this.walljumpunbound$clinging = false;

        this.walljumpunbound$resolveGripArm(self);
        this.walljumpunbound$resolveLedge(self);

        this.walljumpunbound$clingWeightO = this.walljumpunbound$clingWeight;
        float target = this.walljumpunbound$clinging ? 1.0F : 0.0F;
        this.walljumpunbound$clingWeight += (target - this.walljumpunbound$clingWeight) * WallClingPose.BLEND_SPEED;
        if (Math.abs(target - this.walljumpunbound$clingWeight) < 0.01F) this.walljumpunbound$clingWeight = target;
    }

    /**
     * Picks the arm that reaches for the wall: the near one. The model faces
     * yBodyRot, so the wall's side is judged against that and not the look
     * direction, and against the wall's true bearing, so a ship hull turned
     * off the world axes is judged where it really is. Once a grip is taken it
     * only swaps when the wall is clearly round the other side, so a wall held
     * head-on cannot flutter between arms.
     */
    @Unique
    private void walljumpunbound$resolveGripArm(AbstractClientPlayer self) {
        if (!this.walljumpunbound$clinging || this.walljumpunbound$clingWall == null) return;

        // +1 is straight off the right shoulder, -1 the left, 0 dead ahead or behind.
        float lateral = Mth.sin(Mth.wrapDegrees(this.walljumpunbound$clingYaw - self.yBodyRot) * Mth.DEG_TO_RAD);

        if (this.walljumpunbound$clingWeight <= 0.0F) {
            // A fresh grip: the near arm, or the main hand for a head-on wall.
            this.walljumpunbound$gripRight = Math.abs(lateral) < WallClingPose.GRIP_DEAD_ZONE
                    ? self.getMainArm() == HumanoidArm.RIGHT
                    : lateral > 0.0F;
        } else if (lateral > WallClingPose.GRIP_SWITCH) {
            this.walljumpunbound$gripRight = true;
        } else if (lateral < -WallClingPose.GRIP_SWITCH) {
            this.walljumpunbound$gripRight = false;
        }
    }

    /**
     * Looks for the top of the wall within reach, so the pose can put both hands
     * on a ledge rather than leave one grasping at the air above a short wall.
     * Only clinging players are probed, and only when the pose is switched on.
     */
    @Unique
    private void walljumpunbound$resolveLedge(AbstractClientPlayer self) {
        this.walljumpunbound$ledgeWeightO = this.walljumpunbound$ledgeWeight;

        double rise = this.walljumpunbound$clinging && this.walljumpunbound$clingWall != null && ModConfig.wallClingPose
                ? WallJumpLogic.ledgeRise(self, this.walljumpunbound$clingYaw, this.walljumpunbound$clingShip)
                : Double.NaN;
        boolean onLedge = !Double.isNaN(rise);
        // Held past the release so the hands stay put while the pose eases out.
        if (onLedge) this.walljumpunbound$ledgeRise = (float) rise;

        float target = onLedge ? 1.0F : 0.0F;
        this.walljumpunbound$ledgeWeight += (target - this.walljumpunbound$ledgeWeight) * WallClingPose.BLEND_SPEED;
        if (Math.abs(target - this.walljumpunbound$ledgeWeight) < 0.01F) this.walljumpunbound$ledgeWeight = target;
    }

    @Override
    public float walljumpunbound$wallClingLedgeRise() {
        return this.walljumpunbound$ledgeRise;
    }

    @Override
    public float walljumpunbound$wallClingLedgeWeight(float partialTick) {
        return Mth.lerp(partialTick, this.walljumpunbound$ledgeWeightO, this.walljumpunbound$ledgeWeight);
    }

    @Override
    public boolean walljumpunbound$isWallClinging() {
        return this.walljumpunbound$clinging;
    }

    @Override
    public Direction walljumpunbound$wallClingDirection() {
        return this.walljumpunbound$clingWall;
    }

    @Override
    public float walljumpunbound$wallClingYaw() {
        return this.walljumpunbound$clingYaw;
    }

    @Override
    public boolean walljumpunbound$wallClingOnShip() {
        return this.walljumpunbound$clingShip;
    }

    @Override
    public void walljumpunbound$setWallCling(boolean clinging, Direction wall, float yaw, boolean ship) {
        this.walljumpunbound$clinging = clinging;
        this.walljumpunbound$clingWall = wall;
        // The yaw and ship flag are left as they were on release, so the pose
        // keeps reaching the same way while it eases out.
        if (wall != null) {
            this.walljumpunbound$clingYaw = yaw;
            this.walljumpunbound$clingShip = ship;
        }
    }

    @Override
    public boolean walljumpunbound$wallClingGripRight() {
        return this.walljumpunbound$gripRight;
    }

    @Override
    public float walljumpunbound$wallClingWeight(float partialTick) {
        return Mth.lerp(partialTick, this.walljumpunbound$clingWeightO, this.walljumpunbound$clingWeight);
    }
}
