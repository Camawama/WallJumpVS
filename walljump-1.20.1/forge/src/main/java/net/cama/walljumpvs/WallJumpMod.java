package net.cama.walljumpvs;

import com.jahirtrap.configlib.TXFConfig;
import net.cama.walljumpvs.init.ModConfig;
import net.cama.walljumpvs.init.ModEnchantments;
import net.cama.walljumpvs.network.PacketHandler;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(WallJumpMod.MODID)
public class WallJumpMod {

    public static final String MODID = "walljumpvs";

    public WallJumpMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();

        TXFConfig.init(MODID, ModConfig.class);
        ModEnchantments.init(bus);
        PacketHandler.init();
    }
}
