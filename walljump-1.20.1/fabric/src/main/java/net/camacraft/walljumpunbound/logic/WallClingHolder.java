package net.camacraft.walljumpunbound.logic;

import net.minecraft.core.Direction;

/**
 * Client-side wall-cling state, carried by every player entity so the cling
 * pose can be drawn for other players and not just the one doing it: the local
 * player sets it straight from {@link WallJumpLogic}, everyone else from the
 * cling sync packet.
 */
public interface WallClingHolder {

    boolean walljumpunbound$isWallClinging();

    /** Horizontal direction from the player to the wall, null when not clinging. */
    Direction walljumpunbound$wallClingDirection();

    void walljumpunbound$setWallCling(boolean clinging, Direction wall);

    /** Which arm has the wall, resolved each tick from the side the wall is on. */
    boolean walljumpunbound$wallClingGripRight();

    /** 0 while the vanilla pose is showing, 1 once the cling pose is fully in. */
    float walljumpunbound$wallClingWeight(float partialTick);
}
