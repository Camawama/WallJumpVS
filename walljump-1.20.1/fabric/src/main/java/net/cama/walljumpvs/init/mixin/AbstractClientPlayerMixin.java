package net.cama.walljumpvs.init.mixin;

import net.cama.walljumpvs.logic.WallClingHolder;
import net.cama.walljumpvs.logic.WallClingPose;
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
    private boolean walljumpvs$clinging;
    @Unique
    private Direction walljumpvs$clingWall;
    @Unique
    private boolean walljumpvs$gripRight = true;
    @Unique
    private float walljumpvs$clingWeight;
    @Unique
    private float walljumpvs$clingWeightO;

    /**
     * The pose eases here rather than in the model so it moves at a fixed rate
     * whatever the frame rate. TAIL, because the local player's flag is set
     * from the aiStep this very call drives.
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void walljumpvs$tickWallClingPose(CallbackInfo ci) {
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        // Nothing clings from the ground, so this is the backstop for a remote
        // player whose cling ended while nobody was tracking them. The local
        // player rewrites the flag every tick and never needs it.
        if (this.walljumpvs$clinging && self.onGround() && !self.isLocalPlayer()) this.walljumpvs$clinging = false;

        this.walljumpvs$resolveGripArm(self);

        this.walljumpvs$clingWeightO = this.walljumpvs$clingWeight;
        float target = this.walljumpvs$clinging ? 1.0F : 0.0F;
        this.walljumpvs$clingWeight += (target - this.walljumpvs$clingWeight) * WallClingPose.BLEND_SPEED;
        if (Math.abs(target - this.walljumpvs$clingWeight) < 0.01F) this.walljumpvs$clingWeight = target;
    }

    /**
     * Picks the arm that reaches for the wall: the near one. The model faces
     * yBodyRot, so the wall's side is judged against that and not the look
     * direction. Once a grip is taken it only swaps when the wall is clearly
     * round the other side, so a wall held head-on cannot flutter between arms.
     */
    @Unique
    private void walljumpvs$resolveGripArm(AbstractClientPlayer self) {
        Direction wall = this.walljumpvs$clingWall;
        if (!this.walljumpvs$clinging || wall == null) return;

        // +1 is straight off the right shoulder, -1 the left, 0 dead ahead or behind.
        float lateral = Mth.sin(Mth.wrapDegrees(wall.toYRot() - self.yBodyRot) * Mth.DEG_TO_RAD);

        if (this.walljumpvs$clingWeight <= 0.0F) {
            // A fresh grip: the near arm, or the main hand for a head-on wall.
            this.walljumpvs$gripRight = Math.abs(lateral) < WallClingPose.GRIP_DEAD_ZONE
                    ? self.getMainArm() == HumanoidArm.RIGHT
                    : lateral > 0.0F;
        } else if (lateral > WallClingPose.GRIP_SWITCH) {
            this.walljumpvs$gripRight = true;
        } else if (lateral < -WallClingPose.GRIP_SWITCH) {
            this.walljumpvs$gripRight = false;
        }
    }

    @Override
    public boolean walljumpvs$isWallClinging() {
        return this.walljumpvs$clinging;
    }

    @Override
    public Direction walljumpvs$wallClingDirection() {
        return this.walljumpvs$clingWall;
    }

    @Override
    public void walljumpvs$setWallCling(boolean clinging, Direction wall) {
        this.walljumpvs$clinging = clinging;
        this.walljumpvs$clingWall = wall;
    }

    @Override
    public boolean walljumpvs$wallClingGripRight() {
        return this.walljumpvs$gripRight;
    }

    @Override
    public float walljumpvs$wallClingWeight(float partialTick) {
        return Mth.lerp(partialTick, this.walljumpvs$clingWeightO, this.walljumpvs$clingWeight);
    }
}
