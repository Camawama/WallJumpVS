package net.cama.walljumpvs.network.message;

import net.cama.walljumpvs.init.ServerConfig;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import static net.cama.walljumpvs.WallJumpMod.MODID;

public class MessageWallJump {
    public static final ResourceLocation ID = new ResourceLocation(MODID, "message_wall_jump");

    public static void handle(MinecraftServer server, ServerPlayer player, ServerGamePacketListenerImpl handler, FriendlyByteBuf buffer, PacketSender sender) {
        var didWallJump = buffer.readBoolean();
        server.execute(() -> {
            if (didWallJump) {
                player.resetFallDistance();
                player.causeFoodExhaustion((float) ServerConfig.exhaustionWallJump);
            }
        });
    }
}
