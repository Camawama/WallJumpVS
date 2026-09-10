package net.camacraft.walljumpunbound.network.message;

import net.camacraft.walljumpunbound.init.ModConfig;
import net.camacraft.walljumpunbound.logic.WallClingPosture;
import net.camacraft.walljumpunbound.network.PacketHandler;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The sender started or stopped clinging, and to which side. The cling itself
 * is worked out on their own client, so this only exists to relay the pose to
 * everyone watching them.
 */
public record MessageWallCling(boolean clinging, Direction wall) {
    public static void encode(MessageWallCling message, FriendlyByteBuf buffer) {
        buffer.writeBoolean(message.clinging);
        buffer.writeByte(message.wall == null ? -1 : message.wall.get2DDataValue());
    }

    public static MessageWallCling decode(FriendlyByteBuf buffer) {
        return new MessageWallCling(buffer.readBoolean(), readWall(buffer));
    }

    /** Anything but one of the four horizontal directions decodes as no wall. */
    static Direction readWall(FriendlyByteBuf buffer) {
        byte wall = buffer.readByte();
        return wall < 0 || wall > 3 ? null : Direction.from2DDataValue(wall);
    }

    public static void handle(MessageWallCling message, Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> {
            ServerPlayer player = supplier.get().getSender();
            boolean wallJumpEnabled = ModConfig.useWallJump || (ModConfig.enableEnchantments && ModConfig.enableWallJump);
            if (player != null && wallJumpEnabled) {
                // The server keeps the flag so the player's pose, and so the box
                // mobs swing at, matches the one their client is drawing.
                if (player instanceof WallClingPosture posture) posture.walljumpunbound$setWallClingPosture(message.clinging);
                PacketHandler.sendToTracking(player, new MessageWallClingSync(player.getId(), message.clinging, message.wall));
            }
        });
        supplier.get().setPacketHandled(true);
    }
}
