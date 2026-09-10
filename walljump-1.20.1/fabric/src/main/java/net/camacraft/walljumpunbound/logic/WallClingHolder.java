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

    /**
     * Yaw of the way to the wall, in degrees: the heading a player square to
     * it would have. The cardinal's own yaw on a world wall; on a ship it is
     * the hull's true bearing, which is rarely a cardinal.
     */
    float walljumpunbound$wallClingYaw();

    /** Whether the wall belongs to a Valkyrien Skies ship. */
    boolean walljumpunbound$wallClingOnShip();

    void walljumpunbound$setWallCling(boolean clinging, Direction wall, float yaw, boolean ship);

    /** Which arm has the wall, resolved each tick from the side the wall is on. */
    boolean walljumpunbound$wallClingGripRight();

    /** 0 while the vanilla pose is showing, 1 once the cling pose is fully in. */
    float walljumpunbound$wallClingWeight(float partialTick);

    /** How far above the feet the ledge sits; only meaningful once the ledge weight is up. */
    float walljumpunbound$wallClingLedgeRise();

    /** 0 on an open wall face, 1 once both hands are over the top of it. */
    float walljumpunbound$wallClingLedgeWeight(float partialTick);
}
