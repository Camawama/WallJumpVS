package net.camacraft.walljumpunbound.logic;

/**
 * Whether a player is holding a wall, carried on the player itself on BOTH
 * sides: the client works it out, the cling packet tells the server, and both
 * then agree on the pose. Kept apart from {@link WallClingHolder}, which is the
 * client's rendering state and has no meaning on a server.
 */
public interface WallClingPosture {

    boolean walljumpunbound$isWallClingPosture();

    void walljumpunbound$setWallClingPosture(boolean clinging);
}
