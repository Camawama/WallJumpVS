package net.camacraft.walljumpunbound.init;

import net.camacraft.walljumpunbound.enchantment.DoubleJumpEnchantment;
import net.camacraft.walljumpunbound.enchantment.SpeedBoostEnchantment;
import net.camacraft.walljumpunbound.enchantment.WallJumpEnchantment;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import static net.camacraft.walljumpunbound.WallJumpMod.MODID;

public class ModEnchantments {
    public static final DeferredRegister<Enchantment> ENCHANTMENTS = DeferredRegister.create(Registries.ENCHANTMENT, MODID);

    // Always registered; config gates availability inside the enchantment classes.
    public static final RegistryObject<Enchantment> WALL_JUMP = ENCHANTMENTS.register("wall_jump", WallJumpEnchantment::new);
    public static final RegistryObject<Enchantment> DOUBLE_JUMP = ENCHANTMENTS.register("double_jump", DoubleJumpEnchantment::new);
    public static final RegistryObject<Enchantment> SPEED_BOOST = ENCHANTMENTS.register("speed_boost", SpeedBoostEnchantment::new);

    public static void init(IEventBus bus) {
        ENCHANTMENTS.register(bus);
    }
}
