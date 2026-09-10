package net.camacraft.walljumpunbound;

import fuzs.forgeconfigapiport.api.config.v2.ForgeConfigRegistry;
import fuzs.forgeconfigapiport.api.config.v2.ModConfigEvents;
import net.camacraft.walljumpunbound.init.ModConfig;
import net.camacraft.walljumpunbound.init.ModEnchantments;
import net.camacraft.walljumpunbound.network.PacketHandler;
import net.fabricmc.api.ModInitializer;

public class WallJumpMod implements ModInitializer {

    public static final String MODID = "walljumpunbound";

    @Override
    public void onInitialize() {
        // One file for both sides: config/walljumpunbound.toml, written by
        // Forge's config system by way of Forge Config API Port. The server's
        // copy of the rules reaches clients over the mod's own sync on join.
        ForgeConfigRegistry.INSTANCE.register(MODID, net.minecraftforge.fml.config.ModConfig.Type.COMMON, ModConfig.SPEC, MODID + ".toml");
        ModConfigEvents.loading(MODID).register(config -> {
            if (config.getSpec() == ModConfig.SPEC) ModConfig.load();
        });
        ModConfigEvents.reloading(MODID).register(config -> {
            if (config.getSpec() == ModConfig.SPEC) ModConfig.load();
        });

        ModEnchantments.init();
        PacketHandler.init();
    }
}
