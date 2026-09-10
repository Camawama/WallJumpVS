package net.camacraft.walljumpunbound.init;

import net.camacraft.walljumpunbound.init.ModConfig.BlockListMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.List;

/**
 * The server's copy of every rule-setting option, received on join (see
 * {@code PlayerListMixin} and {@code MessageServerConfig}) and reset to the
 * local config on disconnect. Field names match {@link ModConfig} exactly:
 * {@link #reset(String)} copies by name.
 */
public class ServerConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("walljumpunbound");

    public static boolean allowReClinging = ModConfig.allowReClinging;
    public static boolean onFallDoubleJump = ModConfig.onFallDoubleJump;
    public static boolean onFallWallCling = ModConfig.onFallWallCling;
    public static double exhaustionWallJump = ModConfig.exhaustionWallJump;
    public static double minFallDistance = ModConfig.minFallDistance;
    public static double elytraSpeedBoost = ModConfig.elytraSpeedBoost;
    public static double sprintSpeedBoost = ModConfig.sprintSpeedBoost;
    public static boolean stepAssist = ModConfig.stepAssist;
    public static boolean doubleJumpEnabled = ModConfig.doubleJumpEnabled;
    public static boolean wallJumpEnabled = ModConfig.wallJumpEnabled;
    public static double wallJumpHeight = ModConfig.wallJumpHeight;
    public static int wallSlideDelay = ModConfig.wallSlideDelay;
    public static int stopWallSlideDelay = ModConfig.stopWallSlideDelay;
    public static int maxWallJumps = ModConfig.maxWallJumps;
    public static List<String> blockList = ModConfig.blockList;
    public static BlockListMode blockListMode = ModConfig.blockListMode;
    public static boolean enableEnchantments = ModConfig.enableEnchantments;
    public static boolean wallJumpEnchantment = ModConfig.wallJumpEnchantment;
    public static boolean doubleJumpEnchantment = ModConfig.doubleJumpEnchantment;
    public static boolean speedBoostEnchantment = ModConfig.speedBoostEnchantment;
    public static double speedBoostMultiplier = ModConfig.speedBoostMultiplier;
    public static boolean ledgeGrab = ModConfig.ledgeGrab;

    public static void reset() {
        for (Field field : ServerConfig.class.getDeclaredFields()) {
            if (field.getName().equals("LOGGER")) continue;
            reset(field.getName());
        }
    }

    public static void reset(String config) {
        try {
            Field serverField = ServerConfig.class.getDeclaredField(config);
            Field modField = ModConfig.class.getDeclaredField(config);
            serverField.set(null, modField.get(null));
        } catch (Exception e) {
            LOGGER.warn("Failed to reset synced server config value '{}'", config, e);
        }
    }
}
