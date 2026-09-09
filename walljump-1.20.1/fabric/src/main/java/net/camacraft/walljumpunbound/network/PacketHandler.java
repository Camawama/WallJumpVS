package net.camacraft.walljumpunbound.network;

import net.camacraft.walljumpunbound.network.message.MessageFallDistance;
import net.camacraft.walljumpunbound.network.message.MessageServerConfig;
import net.camacraft.walljumpunbound.network.message.MessageWallCling;
import net.camacraft.walljumpunbound.network.message.MessageWallClingSync;
import net.camacraft.walljumpunbound.network.message.MessageWallJump;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class PacketHandler {
    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(MessageFallDistance.ID, MessageFallDistance::handle);
        ServerPlayNetworking.registerGlobalReceiver(MessageWallJump.ID, MessageWallJump::handle);
        ServerPlayNetworking.registerGlobalReceiver(MessageWallCling.ID, MessageWallCling::handle);
    }

    public static void initClient() {
        ClientPlayNetworking.registerGlobalReceiver(MessageServerConfig.ID, MessageServerConfig::handle);
        ClientPlayNetworking.registerGlobalReceiver(MessageWallClingSync.ID, MessageWallClingSync::handle);
    }
}
