package net.cama.walljumpvs.init.mixin;

import net.cama.walljumpvs.WallJumpClient;
import net.cama.walljumpvs.logic.DoubleJumpLogic;
import net.cama.walljumpvs.logic.SpeedBoostLogic;
import net.cama.walljumpvs.logic.StepAssistLogic;
import net.cama.walljumpvs.logic.WallJumpLogic;
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
        DoubleJumpLogic.doDoubleJump(pl);
        SpeedBoostLogic.doSpeedBoost(pl);
        StepAssistLogic.doStepAssist(pl);
    }
}
