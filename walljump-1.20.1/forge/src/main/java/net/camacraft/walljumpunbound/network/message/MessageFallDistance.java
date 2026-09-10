package net.camacraft.walljumpunbound.network.message;

import net.camacraft.walljumpunbound.init.ModConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record MessageFallDistance(float fallDistance) {
    // Clients cannot legitimately accumulate more than the world height in tracked fall distance.
    private static final float MAX_FALL_DISTANCE = 512.0F;

    public static void encode(MessageFallDistance message, FriendlyByteBuf buffer) {
        buffer.writeFloat(message.fallDistance);
    }

    public static MessageFallDistance decode(FriendlyByteBuf buffer) {
        return new MessageFallDistance(buffer.readFloat());
    }

    public static void handle(MessageFallDistance message, Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> {
            ServerPlayer player = supplier.get().getSender();
            if (player == null || !anySenderFeatureEnabled()) return;
            float fallDistance = message.fallDistance;
            if (!Float.isFinite(fallDistance)) return;
            player.fallDistance = Mth.clamp(fallDistance, 0.0F, MAX_FALL_DISTANCE);
        });
        supplier.get().setPacketHandled(true);
    }

    // Only wall jump and double jump send this packet; ignore it when neither is available.
    private static boolean anySenderFeatureEnabled() {
        if (ModConfig.wallJumpEnabled || ModConfig.doubleJumpEnabled) return true;
        return ModConfig.enableEnchantments && (ModConfig.wallJumpEnchantment || ModConfig.doubleJumpEnchantment);
    }
}
