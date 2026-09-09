package net.cama.walljumpvs.init.mixin;

import net.cama.walljumpvs.logic.WallClingPose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin<T extends LivingEntity> extends HumanoidModel<T> {

    @Shadow
    @Final
    public ModelPart leftSleeve;
    @Shadow
    @Final
    public ModelPart rightSleeve;
    @Shadow
    @Final
    public ModelPart leftPants;
    @Shadow
    @Final
    public ModelPart rightPants;
    @Shadow
    @Final
    public ModelPart jacket;
    @Shadow
    @Final
    private ModelPart cloak;

    private PlayerModelMixin(ModelPart root) {
        super(root);
    }

    /**
     * TAIL, so the cling pose wins over the crouch pose the wall-jump keybind
     * leaves the player in. The overlay parts are re-copied afterwards because
     * vanilla copied them from the limbs before this moved them.
     */
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void walljumpvs$wallClingPose(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        float weight = WallClingPose.apply(entity, ageInTicks, this.head, this.body, this.rightArm, this.leftArm, this.rightLeg, this.leftLeg);
        if (weight <= 0.0F) return;

        this.hat.copyFrom(this.head);
        this.jacket.copyFrom(this.body);
        this.leftSleeve.copyFrom(this.leftArm);
        this.rightSleeve.copyFrom(this.rightArm);
        this.leftPants.copyFrom(this.leftLeg);
        this.rightPants.copyFrom(this.rightLeg);

        // A cape hangs off a standing back here, not a crouched one.
        boolean chestplate = !entity.getItemBySlot(EquipmentSlot.CHEST).isEmpty();
        this.cloak.z = Mth.lerp(weight, this.cloak.z, chestplate ? -1.1F : 0.0F);
        this.cloak.y = Mth.lerp(weight, this.cloak.y, chestplate ? -0.85F : 0.0F);
    }
}
