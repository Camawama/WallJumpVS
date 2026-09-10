package net.camacraft.walljumpunbound.network.message;

import net.camacraft.walljumpunbound.init.ModConfig;
import net.camacraft.walljumpunbound.init.ModDamageTypes;
import net.camacraft.walljumpunbound.logic.WallClingPosture;
import net.camacraft.walljumpunbound.network.PacketHandler;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The sender started or stopped clinging, to which side, at what bearing, and
 * whether on a ship. The cling itself is worked out on their own client; this
 * relays the pose to everyone watching them, and tells the server a fall has
 * just been caught.
 */
public record MessageWallCling(boolean clinging, Direction wall, float yaw, boolean ship) {
    public static void encode(MessageWallCling message, FriendlyByteBuf buffer) {
        buffer.writeBoolean(message.clinging);
        writeWall(buffer, message.wall);
        buffer.writeFloat(message.yaw);
        buffer.writeBoolean(message.ship);
    }

    public static MessageWallCling decode(FriendlyByteBuf buffer) {
        return new MessageWallCling(buffer.readBoolean(), readWall(buffer), buffer.readFloat(), buffer.readBoolean());
    }

    static void writeWall(FriendlyByteBuf buffer, Direction wall) {
        buffer.writeByte(wall == null ? -1 : wall.get2DDataValue());
    }

    /** Anything but one of the four horizontal directions decodes as no wall. */
    static Direction readWall(FriendlyByteBuf buffer) {
        byte wall = buffer.readByte();
        return wall < 0 || wall > 3 ? null : Direction.from2DDataValue(wall);
    }

    public static void handle(MessageWallCling message, Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> {
            ServerPlayer player = supplier.get().getSender();
            boolean wallJumpEnabled = ModConfig.wallJumpEnabled || (ModConfig.enableEnchantments && ModConfig.wallJumpEnchantment);
            if (player == null || !wallJumpEnabled) return;

            float yaw = Float.isFinite(message.yaw) ? message.yaw : 0.0F;
            // The server keeps the flag so the player's pose, and so the box
            // mobs swing at, matches the one their client is drawing. A fresh
            // grip is also where a long fall is paid for, from the server's own
            // record of the fall, read before the client's cling resets it.
            if (player instanceof WallClingPosture posture) {
                if (message.clinging && !posture.walljumpunbound$isWallClingPosture()) ModDamageTypes.hurtForCatchingWall(player);
                posture.walljumpunbound$setWallClingPosture(message.clinging);
            }
            PacketHandler.sendToTracking(player, new MessageWallClingSync(player.getId(), message.clinging, message.wall, yaw, message.ship));
        });
        supplier.get().setPacketHandled(true);
    }
}
