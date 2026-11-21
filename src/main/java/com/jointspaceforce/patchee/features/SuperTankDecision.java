package com.jointspaceforce.patchee.features;

/**
 * Whether one player's Super Tank debuffs should be cleared this tick.
 *
 * <p>
 * This is the whole decision, kept as one pure function so it can be tested
 * without Minecraft: nothing here imports Minecraft or FML.
 *
 * <p>
 * The rule has two sides. On the <b>logical client</b> the answer is always yes
 * while a qualifying tank is carried — that client is running Patchee, so it can
 * safely wipe its own local copy of the effects. On the <b>server</b> the answer
 * is yes only when the client's own FML handshake said it runs Patchee: a stock
 * client is then left completely alone and keeps exactly GregTech's own
 * behaviour, so a player who never installed the mod sees no change at all.
 */
public final class SuperTankDecision {

    private SuperTankDecision() {}

    /**
     * Decide whether the four tank debuffs should be removed this tick.
     *
     * @param logicalClient    true when this side is the client ({@code worldObj.isRemote})
     * @param clientHasPatchee true when the client on this connection runs Patchee
     * @param carries          true when the player is carrying a qualifying filled tank/chest
     * @param hasEffects       true when the player has any of the four debuffs active
     * @return true when the effects should be cleared
     */
    public static boolean shouldClear(boolean logicalClient, boolean clientHasPatchee, boolean carries,
        boolean hasEffects) {
        return carries && hasEffects && (logicalClient || clientHasPatchee);
    }
}
