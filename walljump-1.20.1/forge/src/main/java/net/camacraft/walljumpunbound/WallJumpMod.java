package net.camacraft.walljumpunbound;

import net.camacraft.walljumpunbound.init.ModConfig;
import net.camacraft.walljumpunbound.init.ModEnchantments;
import net.camacraft.walljumpunbound.network.PacketHandler;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(WallJumpMod.MODID)
public class WallJumpMod {

    public static final String MODID = "walljumpunbound";

    public WallJumpMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();

        // One file for both sides: config/walljumpunbound.toml. The server's
        // copy of the rules reaches clients over the mod's own sync on join.
        ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON, ModConfig.SPEC, MODID + ".toml");
        bus.addListener(WallJumpMod::onConfigLoading);
        bus.addListener(WallJumpMod::onConfigReloading);

        ModEnchantments.init(bus);
        PacketHandler.init();
    }

    private static void onConfigLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == ModConfig.SPEC) ModConfig.load();
    }

    private static void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == ModConfig.SPEC) ModConfig.load();
    }
}
