package net.camacraft.walljumpunbound.network.message;

import net.camacraft.walljumpunbound.init.ModConfig;
import net.camacraft.walljumpunbound.init.ServerConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record MessageWallJump(boolean didWallJump) {
    public static void encode(MessageWallJump message, FriendlyByteBuf buffer) {
        buffer.writeBoolean(message.didWallJump);
    }

    public static MessageWallJump decode(FriendlyByteBuf buffer) {
        return new MessageWallJump(buffer.readBoolean());
    }


    public static void handle(MessageWallJump message, Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> {
            ServerPlayer player = supplier.get().getSender();
            boolean wallJumpEnabled = ModConfig.wallJumpEnabled || (ModConfig.enableEnchantments && ModConfig.wallJumpEnchantment);
            if (player != null && message.didWallJump && wallJumpEnabled) {
                player.resetFallDistance();
                player.causeFoodExhaustion((float) ServerConfig.exhaustionWallJump);
            }
        });
        supplier.get().setPacketHandled(true);
    }
}
