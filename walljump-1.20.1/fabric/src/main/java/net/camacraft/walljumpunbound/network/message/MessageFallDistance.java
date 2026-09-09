package net.camacraft.walljumpunbound.network.message;

import net.camacraft.walljumpunbound.init.ModConfig;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;

import static net.camacraft.walljumpunbound.WallJumpMod.MODID;

public class MessageFallDistance {
    public static final ResourceLocation ID = new ResourceLocation(MODID, "message_fall_distance");

    // Clients cannot legitimately accumulate more than the world height in tracked fall distance.
    private static final float MAX_FALL_DISTANCE = 512.0F;

    public static void handle(MinecraftServer server, ServerPlayer player, ServerGamePacketListenerImpl handler, FriendlyByteBuf buffer, PacketSender sender) {
        var fallDistance = buffer.readFloat();
        server.execute(() -> {
            if (!anySenderFeatureEnabled() || !Float.isFinite(fallDistance)) return;
            player.fallDistance = Mth.clamp(fallDistance, 0.0F, MAX_FALL_DISTANCE);
        });
    }

    // Only wall jump and double jump send this packet; ignore it when neither is available.
    private static boolean anySenderFeatureEnabled() {
        if (ModConfig.useWallJump || ModConfig.useDoubleJump) return true;
        return ModConfig.enableEnchantments && (ModConfig.enableWallJump || ModConfig.enableDoubleJump);
    }
}
