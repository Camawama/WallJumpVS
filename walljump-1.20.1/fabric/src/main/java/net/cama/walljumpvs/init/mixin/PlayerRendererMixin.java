package net.cama.walljumpvs.init.mixin;

import net.cama.walljumpvs.logic.WallClingPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {

    /**
     * Vanilla drops a crouching player two pixels so the model sits inside the
     * shorter hitbox. The cling pose stands full height, so the drop is eased
     * back out along with it.
     */
    @Inject(method = "getRenderOffset(Lnet/minecraft/client/player/AbstractClientPlayer;F)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private void walljumpvs$wallClingRenderOffset(AbstractClientPlayer player, float partialTick, CallbackInfoReturnable<Vec3> cir) {
        if (!player.isCrouching()) return;
        float weight = WallClingPose.weight(player, partialTick);
        if (weight > 0.0F) cir.setReturnValue(new Vec3(0.0D, -0.125D * (1.0F - weight), 0.0D));
    }
}
