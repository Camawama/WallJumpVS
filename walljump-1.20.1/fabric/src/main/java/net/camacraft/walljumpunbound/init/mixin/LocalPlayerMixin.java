package net.camacraft.walljumpunbound.init.mixin;

import net.camacraft.walljumpunbound.WallJumpClient;
import net.camacraft.walljumpunbound.logic.DoubleJumpLogic;
import net.camacraft.walljumpunbound.logic.SpeedBoostLogic;
import net.camacraft.walljumpunbound.logic.StepAssistLogic;
import net.camacraft.walljumpunbound.logic.WallJumpLogic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void aiStep(CallbackInfo ci) {
        LocalPlayer pl = Minecraft.getInstance().player;

        WallJumpClient.playFallingSound(pl);
        WallJumpLogic.doWallJump(pl);
        WallJumpLogic.updateClingPose(pl);
        DoubleJumpLogic.doDoubleJump(pl);
        SpeedBoostLogic.doSpeedBoost(pl);
        StepAssistLogic.doStepAssist(pl);
    }
}
