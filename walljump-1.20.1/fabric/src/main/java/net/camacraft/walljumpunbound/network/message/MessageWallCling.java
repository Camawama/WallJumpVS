package net.camacraft.walljumpunbound.network.message;

import io.netty.buffer.Unpooled;
import net.camacraft.walljumpunbound.init.ModConfig;
import net.camacraft.walljumpunbound.logic.WallClingPosture;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import static net.camacraft.walljumpunbound.WallJumpMod.MODID;

/**
 * The sender started or stopped clinging, and to which side. The cling itself
 * is worked out on their own client, so this only exists to relay the pose to
 * everyone watching them.
 */
public class MessageWallCling {
    public static final ResourceLocation ID = new ResourceLocation(MODID, "message_wall_cling");

    /** Anything but one of the four horizontal directions decodes as no wall. */
    static Direction readWall(FriendlyByteBuf buffer) {
        byte wall = buffer.readByte();
        return wall < 0 || wall > 3 ? null : Direction.from2DDataValue(wall);
    }

    public static void writeWall(FriendlyByteBuf buffer, Direction wall) {
        buffer.writeByte(wall == null ? -1 : wall.get2DDataValue());
    }

    public static void handle(MinecraftServer server, ServerPlayer player, ServerGamePacketListenerImpl handler, FriendlyByteBuf buffer, PacketSender sender) {
        var clinging = buffer.readBoolean();
        var wall = readWall(buffer);
        server.execute(() -> {
            boolean wallJumpEnabled = ModConfig.useWallJump || (ModConfig.enableEnchantments && ModConfig.enableWallJump);
            if (!wallJumpEnabled) return;

            // The server keeps the flag so the player's pose, and so the box mobs
            // swing at, matches the one their client is drawing.
            if (player instanceof WallClingPosture posture) posture.walljumpunbound$setWallClingPosture(clinging);

            FriendlyByteBuf out = new FriendlyByteBuf(Unpooled.buffer());
            out.writeVarInt(player.getId());
            out.writeBoolean(clinging);
            writeWall(out, wall);

            // One packet, sent to every tracker: a buffer cannot be handed to
            // ServerPlayNetworking.send more than once.
            Packet<?> packet = ServerPlayNetworking.createS2CPacket(MessageWallClingSync.ID, out);
            for (ServerPlayer viewer : PlayerLookup.tracking(player)) viewer.connection.send(packet);
        });
    }
}
