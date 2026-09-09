package net.camacraft.walljumpunbound;

import com.jahirtrap.configlib.TXFConfig;
import net.camacraft.walljumpunbound.init.ModConfig;
import net.camacraft.walljumpunbound.init.ModEnchantments;
import net.camacraft.walljumpunbound.network.PacketHandler;
import net.fabricmc.api.ModInitializer;

public class WallJumpMod implements ModInitializer {

    public static final String MODID = "walljumpunbound";

    @Override
    public void onInitialize() {
        TXFConfig.init(MODID, ModConfig.class);
        ModEnchantments.init();
        PacketHandler.init();
    }
}
