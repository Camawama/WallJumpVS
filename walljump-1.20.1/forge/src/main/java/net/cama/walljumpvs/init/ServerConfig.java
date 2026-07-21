package net.cama.walljumpvs.init;

import net.cama.walljumpvs.init.ModConfig.BlockListMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.List;

public class ServerConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("walljumpvs");

    public static boolean allowReClinging = ModConfig.allowReClinging;
    public static boolean onFallDoubleJump = ModConfig.onFallDoubleJump;
    public static boolean onFallWallCling = ModConfig.onFallWallCling;
    public static double exhaustionWallJump = ModConfig.exhaustionWallJump;
    public static double minFallDistance = ModConfig.minFallDistance;
    public static double elytraSpeedBoost = ModConfig.elytraSpeedBoost;
    public static double sprintSpeedBoost = ModConfig.sprintSpeedBoost;
    public static boolean stepAssist = ModConfig.stepAssist;
    public static boolean useDoubleJump = ModConfig.useDoubleJump;
    public static boolean useWallJump = ModConfig.useWallJump;
    public static double wallJumpHeight = ModConfig.wallJumpHeight;
    public static int wallSlideDelay = ModConfig.wallSlideDelay;
    public static int stopWallSlideDelay = ModConfig.stopWallSlideDelay;
    public static int maxWallJumps = ModConfig.maxWallJumps;
    public static List<String> blockList = ModConfig.blockList;
    public static BlockListMode blockListMode = ModConfig.blockListMode;
    public static boolean enableEnchantments = ModConfig.enableEnchantments;
    public static boolean enableWallJump = ModConfig.enableWallJump;
    public static boolean enableDoubleJump = ModConfig.enableDoubleJump;
    public static boolean enableSpeedBoost = ModConfig.enableSpeedBoost;
    public static double speedBoostMultiplier = ModConfig.speedBoostMultiplier;

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
