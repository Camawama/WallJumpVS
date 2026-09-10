package net.camacraft.walljumpunbound.init.mixin;

import net.camacraft.walljumpunbound.init.ServerConfig;
import net.camacraft.walljumpunbound.logic.WallClingPosture;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerMixin implements WallClingPosture {

    @Unique
    private boolean walljumpunbound$clinging;

    @Shadow
    public abstract void playSound(SoundEvent sound, float volume, float pitch);

    /**
     * A player on a wall is hanging off it, not crouching behind it, so they keep
     * the standing box even though the wall-jump key is also the sneak key. The
     * crouch box is only 1.5 tall against the pose's full 1.8, which left the
     * whole of a ledge-hanging player below the lip: nothing above could see or
     * reach them. This runs on both sides, since the cling reaches the server
     * over the network, so the box mobs swing at is the one the pose draws.
     */
    @Inject(method = "updatePlayerPose", at = @At("TAIL"))
    private void walljumpunbound$standWhileClinging(CallbackInfo ci) {
        if (!this.walljumpunbound$clinging) return;

        Player self = (Player) (Object) this;
        if (self.getPose() != Pose.CROUCHING) return;

        // Never stand up into a block: this is the fit test vanilla itself makes
        // before settling on a pose, spelled out because it is not public.
        AABB standing = self.getDimensions(Pose.STANDING).makeBoundingBox(self.position()).deflate(1.0E-7D);
        if (self.level().noCollision(self, standing)) self.setPose(Pose.STANDING);
    }

    @Override
    public boolean walljumpunbound$isWallClingPosture() {
        return this.walljumpunbound$clinging;
    }

    @Override
    public void walljumpunbound$setWallClingPosture(boolean clinging) {
        this.walljumpunbound$clinging = clinging;
    }

    @ModifyArg(method = "causeFallDamage", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;causeFallDamage(FFLnet/minecraft/world/damagesource/DamageSource;)Z"), index = 0)
    private float causeFallDamage(float value) {
        if (value > 3 && value <= ServerConfig.minFallDistance) {
            playSound(SoundEvents.GENERIC_SMALL_FALL, 0.5f, 1f);
            return 3;
        }

        return value;
    }
}
