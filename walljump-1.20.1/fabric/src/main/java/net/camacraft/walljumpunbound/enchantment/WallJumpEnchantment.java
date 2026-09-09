package net.camacraft.walljumpunbound.enchantment;

import net.camacraft.walljumpunbound.init.ServerConfig;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

public class WallJumpEnchantment extends Enchantment {
    public WallJumpEnchantment() {
        super(Rarity.UNCOMMON, EnchantmentCategory.ARMOR_FEET, new EquipmentSlot[]{EquipmentSlot.FEET});
    }

    @Override
    public int getMinLevel() {
        return 1;
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public int getMinCost(int level) {
        return 20;
    }

    @Override
    public int getMaxCost(int level) {
        return 60;
    }

    // The enchantment is always registered; config only controls availability,
    // so existing enchanted items survive config changes.
    @Override
    public boolean isDiscoverable() {
        return ServerConfig.enableEnchantments && ServerConfig.enableWallJump;
    }

    @Override
    public boolean isTradeable() {
        return ServerConfig.enableEnchantments && ServerConfig.enableWallJump;
    }
}
