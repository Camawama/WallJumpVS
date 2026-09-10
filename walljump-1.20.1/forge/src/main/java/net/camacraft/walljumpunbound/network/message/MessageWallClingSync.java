package net.camacraft.walljumpunbound.network.message;

import net.camacraft.walljumpunbound.logic.WallClingHolder;
import net.camacraft.walljumpunbound.logic.WallClingPosture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Another player's cling state, so their wall-cling pose can be drawn here. */
public record MessageWallClingSync(int entityId, boolean clinging, Direction wall) {
    public static void encode(MessageWallClingSync message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.entityId);
        buffer.writeBoolean(message.clinging);
        buffer.writeByte(message.wall == null ? -1 : message.wall.get2DDataValue());
    }

    public static MessageWallClingSync decode(FriendlyByteBuf buffer) {
        return new MessageWallClingSync(buffer.readVarInt(), buffer.readBoolean(), MessageWallCling.readWall(buffer));
    }

    public static void handle(MessageWallClingSync message, Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> ClientHandler.apply(message));
        supplier.get().setPacketHandled(true);
    }

    // Kept apart so the client-only classes are never loaded on a server.
    private static final class ClientHandler {
        static void apply(MessageWallClingSync message) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) return;
            Entity entity = minecraft.level.getEntity(message.entityId);
            if (entity instanceof WallClingHolder holder) holder.walljumpunbound$setWallCling(message.clinging, message.wall);
            if (entity instanceof WallClingPosture posture) posture.walljumpunbound$setWallClingPosture(message.clinging);
        }
    }
}
