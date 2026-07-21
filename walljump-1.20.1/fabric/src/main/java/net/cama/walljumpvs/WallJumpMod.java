package net.cama.walljumpvs;

import com.jahirtrap.configlib.TXFConfig;
import net.cama.walljumpvs.init.ModConfig;
import net.cama.walljumpvs.init.ModEnchantments;
import net.cama.walljumpvs.network.PacketHandler;
import net.fabricmc.api.ModInitializer;

public class WallJumpMod implements ModInitializer {

    public static final String MODID = "walljumpvs";

    @Override
    public void onInitialize() {
        TXFConfig.init(MODID, ModConfig.class);
        ModEnchantments.init();
        PacketHandler.init();
    }
}
