package net.cama.walljumpvs.init.mixin;

import net.cama.walljumpvs.init.ModConfig;
import net.cama.walljumpvs.network.PacketHandler;
import net.cama.walljumpvs.network.message.MessageServerConfig;
import io.netty.buffer.Unpooled;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {

    @Inject(method = "placeNewPlayer", at = @At("TAIL"))
    public void placeNewPlayer(Connection connection, ServerPlayer player, CallbackInfo ci) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        buffer.writeBoolean(ModConfig.allowReClinging);
        buffer.writeBoolean(ModConfig.onFallDoubleJump);
        buffer.writeBoolean(ModConfig.onFallWallCling);
        buffer.writeDouble(ModConfig.exhaustionWallJump);
        buffer.writeDouble(ModConfig.minFallDistance);
        buffer.writeDouble(ModConfig.elytraSpeedBoost);
        buffer.writeDouble(ModConfig.sprintSpeedBoost);
        buffer.writeBoolean(ModConfig.stepAssist);
        buffer.writeBoolean(ModConfig.useDoubleJump);
        buffer.writeBoolean(ModConfig.useWallJump);
        buffer.writeDouble(ModConfig.wallJumpHeight);
        buffer.writeInt(ModConfig.wallSlideDelay);
        buffer.writeInt(ModConfig.stopWallSlideDelay);
        buffer.writeInt(ModConfig.maxWallJumps);
        writeList(buffer, ModConfig.blockList);
        buffer.writeEnum(ModConfig.blockListMode);
        buffer.writeBoolean(ModConfig.enableEnchantments);
        buffer.writeBoolean(ModConfig.enableWallJump);
        buffer.writeBoolean(ModConfig.enableDoubleJump);
        buffer.writeBoolean(ModConfig.enableSpeedBoost);
        buffer.writeDouble(ModConfig.speedBoostMultiplier);

        // Send only the written bytes, not the buffer's whole backing array.
        byte[] data = new byte[buffer.writerIndex()];
        buffer.getBytes(0, data);
        PacketHandler.sendToPlayer(player, new MessageServerConfig(data));
    }

    @Unique
    private void writeList(FriendlyByteBuf buffer, List<String> list) {
        buffer.writeInt(list.size());
        for (String string : list) {
            buffer.writeUtf(string);
        }
    }
}
