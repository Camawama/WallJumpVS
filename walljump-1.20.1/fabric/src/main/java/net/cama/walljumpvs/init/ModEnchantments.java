package net.cama.walljumpvs.init;

import net.cama.walljumpvs.enchantment.DoubleJumpEnchantment;
import net.cama.walljumpvs.enchantment.SpeedBoostEnchantment;
import net.cama.walljumpvs.enchantment.WallJumpEnchantment;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

import static net.cama.walljumpvs.WallJumpMod.MODID;

public class ModEnchantments {
    // Always registered; config gates availability inside the enchantment classes.
    public static final Enchantment WALL_JUMP = register("wall_jump", new WallJumpEnchantment());
    public static final Enchantment DOUBLE_JUMP = register("double_jump", new DoubleJumpEnchantment());
    public static final Enchantment SPEED_BOOST = register("speed_boost", new SpeedBoostEnchantment());

    private static Enchantment register(String name, Enchantment enchantment) {
        return Registry.register(BuiltInRegistries.ENCHANTMENT, new ResourceLocation(MODID, name), enchantment);
    }

    public static void init() {
    }
}
