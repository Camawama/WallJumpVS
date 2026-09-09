package net.camacraft.walljumpunbound.network.message;

import net.camacraft.walljumpunbound.logic.WallClingHolder;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import static net.camacraft.walljumpunbound.WallJumpMod.MODID;

/** Another player's cling state, so their wall-cling pose can be drawn here. */
public class MessageWallClingSync {
    public static final ResourceLocation ID = new ResourceLocation(MODID, "message_wall_cling_sync");

    public static void handle(Minecraft client, ClientPacketListener handler, FriendlyByteBuf buffer, PacketSender sender) {
        var entityId = buffer.readVarInt();
        var clinging = buffer.readBoolean();
        Direction wall = MessageWallCling.readWall(buffer);
        client.execute(() -> {
            if (client.level == null) return;
            Entity entity = client.level.getEntity(entityId);
            if (entity instanceof WallClingHolder holder) holder.walljumpunbound$setWallCling(clinging, wall);
        });
    }
}
