package net.camacraft.walljumpunbound.init.mixin;

import net.camacraft.walljumpunbound.logic.WallClingHolder;
import net.camacraft.walljumpunbound.logic.WallClingPose;
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
    private boolean walljumpunbound$gripRight = true;
    @Unique
    private float walljumpunbound$clingWeight;
    @Unique
    private float walljumpunbound$clingWeightO;

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

        this.walljumpunbound$clingWeightO = this.walljumpunbound$clingWeight;
        float target = this.walljumpunbound$clinging ? 1.0F : 0.0F;
        this.walljumpunbound$clingWeight += (target - this.walljumpunbound$clingWeight) * WallClingPose.BLEND_SPEED;
        if (Math.abs(target - this.walljumpunbound$clingWeight) < 0.01F) this.walljumpunbound$clingWeight = target;
    }

    /**
     * Picks the arm that reaches for the wall: the near one. The model faces
     * yBodyRot, so the wall's side is judged against that and not the look
     * direction. Once a grip is taken it only swaps when the wall is clearly
     * round the other side, so a wall held head-on cannot flutter between arms.
     */
    @Unique
    private void walljumpunbound$resolveGripArm(AbstractClientPlayer self) {
        Direction wall = this.walljumpunbound$clingWall;
        if (!this.walljumpunbound$clinging || wall == null) return;

        // +1 is straight off the right shoulder, -1 the left, 0 dead ahead or behind.
        float lateral = Mth.sin(Mth.wrapDegrees(wall.toYRot() - self.yBodyRot) * Mth.DEG_TO_RAD);

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

    @Override
    public boolean walljumpunbound$isWallClinging() {
        return this.walljumpunbound$clinging;
    }

    @Override
    public Direction walljumpunbound$wallClingDirection() {
        return this.walljumpunbound$clingWall;
    }

    @Override
    public void walljumpunbound$setWallCling(boolean clinging, Direction wall) {
        this.walljumpunbound$clinging = clinging;
        this.walljumpunbound$clingWall = wall;
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
