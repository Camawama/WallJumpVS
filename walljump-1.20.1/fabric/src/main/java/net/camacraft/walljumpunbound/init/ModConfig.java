package net.camacraft.walljumpunbound.init;

import com.google.common.collect.Lists;
import com.jahirtrap.configlib.TXFConfig;

import java.util.List;

/**
 * The mod's config, saved to {@code config/walljumpunbound.json}. Every option
 * has a tooltip in the in-game config screen (the {@code .tooltip} lang keys)
 * and is written up in {@code CONFIG.md} at the repository root; the JSON file
 * itself cannot carry comments.
 *
 * <p>Options are either <b>server</b> (the server's value is sent to every
 * joining client and sets the rules: see {@link ServerConfig}) or
 * <b>client</b> (purely local: visual, audible, or about detection). Each
 * option says which.
 */
public class ModConfig extends TXFConfig {
    public static final String WALL_JUMP = "wallJump", MOVEMENT = "movement", BLOCK_LIST = "blockList",
            VALKYRIEN_SKIES = "valkyrienSkies", ENCHANTMENTS = "enchantments";

    // ---------------------------------------------------------------- wall jump

    /** Server. Everyone can cling and wall jump; off means only the Wall Jump enchantment grants it. */
    @Entry(category = WALL_JUMP, name = "Wall Jump Enabled")
    public static boolean wallJumpEnabled = true;
    /** Server. Upward speed of a wall jump. */
    @Entry(category = WALL_JUMP, name = "Wall Jump Height", min = 0.0, max = 1.0)
    public static double wallJumpHeight = 0.55;
    /** Server. Wall jumps allowed before touching the ground again; 72000 is as good as unlimited. */
    @Entry(category = WALL_JUMP, name = "Max Wall Jumps", min = 0, max = Integer.MAX_VALUE)
    public static int maxWallJumps = 72000;
    /** Server. Hunger spent per wall jump. */
    @Entry(category = WALL_JUMP, name = "Wall Jump Exhaustion", min = 0.0, max = 5.0)
    public static double exhaustionWallJump = 0.8;
    /** Server. Cling again to the wall just jumped off, without falling a block first. */
    @Entry(category = WALL_JUMP, name = "Allow Re-Clinging")
    public static boolean allowReClinging = true;
    /** Server. A wall can be caught while falling faster than 0.8 blocks a tick. */
    @Entry(category = WALL_JUMP, name = "Cling While Falling Fast")
    public static boolean onFallWallCling = true;
    /** Client. Snap the view to face away from the wall on cling; skipped on ship walls. */
    @Entry(category = WALL_JUMP, name = "Auto Rotation")
    public static boolean autoRotation = false;
    /** Server. Ticks a cling holds before it starts to slide. */
    @Entry(category = WALL_JUMP, name = "Wall Slide Delay", min = 0, max = Integer.MAX_VALUE)
    public static int wallSlideDelay = 15;
    /** Server. Ticks of sliding before the grip gives out; 72000 is as good as unlimited. */
    @Entry(category = WALL_JUMP, name = "Stop Wall Slide Delay", min = 0, max = Integer.MAX_VALUE)
    public static int stopWallSlideDelay = 72000;
    /** Server. Hang from the top of a wall within arm's reach instead of sliding. */
    @Entry(category = WALL_JUMP, name = "Ledge Grab")
    public static boolean ledgeGrab = true;
    /** Server. Falls up to this many blocks do no damage; vanilla is 3. */
    @Entry(category = WALL_JUMP, name = "Min Fall Distance", min = 3.0, max = 256)
    public static double minFallDistance = 3.0;
    /** Server. Catching a wall after a long fall hurts. */
    @Entry(category = WALL_JUMP, name = "Cling Fall Damage")
    public static boolean clingFallDamage = true;
    /** Server. The fall, in blocks, from which catching a wall starts to hurt. */
    @Entry(category = WALL_JUMP, name = "Cling Fall Damage Min Distance", min = 3.0, max = 256)
    public static double clingFallDamageMinDistance = 6.0;
    /** Server. Share of the fall damage the ground would have done, dealt by catching a wall instead. */
    @Entry(category = WALL_JUMP, name = "Cling Fall Damage Fraction", min = 0.0, max = 1.0, precision = 2, isSlider = true)
    public static double clingFallDamageFraction = 0.35;
    /** Client. Draw the climbing pose on clinging players. */
    @Entry(category = WALL_JUMP, name = "Wall Cling Pose")
    public static boolean wallClingPose = true;
    /** Client. Rushing wind while falling fast. */
    @Entry(category = WALL_JUMP, name = "Play Falling Sound")
    public static boolean playFallingSound = true;
    /** Client. Scrape the wall's own sound while sliding down it. */
    @Entry(category = WALL_JUMP, name = "Play Wall Slide Sound")
    public static boolean playSlideSound = true;

    // ------------------------------------------------------------ other movement

    /** Server. Everyone gets one mid-air jump; off means only the Double Jump enchantment grants them. */
    @Entry(category = MOVEMENT, name = "Double Jump Enabled")
    public static boolean doubleJumpEnabled = false;
    /** Server. The mid-air jump works while falling faster than 0.8 blocks a tick. */
    @Entry(category = MOVEMENT, name = "Double Jump While Falling Fast")
    public static boolean onFallDoubleJump = true;
    /** Server. Extra sprint speed for everyone, on top of the enchantment. */
    @Entry(category = MOVEMENT, name = "Sprint Speed Boost", min = 0.0, max = 5.0)
    public static double sprintSpeedBoost = 0.0;
    /** Server. Extra elytra speed for everyone while holding sprint, on top of the enchantment. */
    @Entry(category = MOVEMENT, name = "Elytra Speed Boost", min = 0.0, max = 5.0)
    public static double elytraSpeedBoost = 0.0;
    /** Server. Walk up single steps, even mid-air, and hop fences. */
    @Entry(category = MOVEMENT, name = "Step Assist")
    public static boolean stepAssist = true;

    // ---------------------------------------------------------------- block list

    /** Server. How the block list is read. */
    @Entry(category = BLOCK_LIST, name = "Block List Mode")
    public static BlockListMode blockListMode = BlockListMode.BLACKLIST;
    /**
     * Server. Block ids, {@code #tags}, {@code *wildcards} or {@code /regexes/}
     * (see {@code BlockListMatcher}) of blocks that cannot, or alone can, be clung to.
     */
    @Entry(category = BLOCK_LIST, name = "Block List", idMode = 1)
    public static List<String> blockList = Lists.newArrayList();

    // ----------------------------------------------------------- valkyrien skies

    /** Client. Master switch for clinging to ship hulls; does nothing without Valkyrien Skies. */
    @Entry(category = VALKYRIEN_SKIES, name = "Enable Valkyrien Skies Compat")
    public static boolean enableVSCompat = true;
    /** Client. How far past the hitbox ship walls are felt for. */
    @Entry(category = VALKYRIEN_SKIES, name = "Ship Wall Detection Range", min = 0.01, max = 0.5)
    public static double shipWallDetectionRange = 0.06;
    /** Client. Carry a clinging player with the ship's movement and spin. */
    @Entry(category = VALKYRIEN_SKIES, name = "Cling Sticks To Moving Ships")
    public static boolean stickToMovingShips = true;
    /** Client. Write ship cling and wall detection details to the log. */
    @Entry(category = VALKYRIEN_SKIES, name = "Debug Ship Cling (logs to console)")
    public static boolean debugShipCling = false;

    // -------------------------------------------------------------- enchantments

    /** Server. Master switch: off hides every enchantment from tables and trades; existing items keep working. */
    @Entry(category = ENCHANTMENTS, name = "Enable Enchantments", itemDisplay = "minecraft:enchanted_book")
    public static boolean enableEnchantments = false;
    /** Server. The boots enchantment that grants wall jumping to its wearer. */
    @Entry(category = ENCHANTMENTS, name = "Wall Jump Enchantment", itemDisplay = "minecraft:enchanted_book")
    public static boolean wallJumpEnchantment = true;
    /** Server. The boots enchantment that grants a mid-air jump per level. */
    @Entry(category = ENCHANTMENTS, name = "Double Jump Enchantment", itemDisplay = "minecraft:enchanted_book")
    public static boolean doubleJumpEnchantment = true;
    /** Server. The boots and chestplate enchantment that speeds up sprinting and gliding. */
    @Entry(category = ENCHANTMENTS, name = "Speed Boost Enchantment", itemDisplay = "minecraft:enchanted_book")
    public static boolean speedBoostEnchantment = true;
    /** Server. Speed each Speed Boost level adds. */
    @Entry(category = ENCHANTMENTS, name = "Speed Boost Enchantment Multiplier", min = 0.25, max = 1, precision = 4, isSlider = true, itemDisplay = "minecraft:enchanted_book")
    public static double speedBoostMultiplier = 0.5;

    public enum BlockListMode {DISABLED, BLACKLIST, WHITELIST}
}
