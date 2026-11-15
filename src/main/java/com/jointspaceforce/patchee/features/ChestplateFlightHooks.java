package com.jointspaceforce.patchee.features;

import net.minecraft.entity.player.EntityPlayer;

/**
 * The one method IC2's patched {@code useJetpack} calls.
 *
 * <p>
 * It runs on every armor tick of every player wearing a Quantum Chestplate, so it
 * does nothing but read two public values off the player and answer the pure
 * decision in {@link ChestplateFlight#guardFires(boolean, double)}. No Forge, no
 * config, no reflection.
 *
 * <p>
 * When it answers {@code true} the patched method returns {@code false} straight
 * away: no motion write, no energy drain, and — the point of the fix — no
 * {@code fallDistance} reset and no {@code floatingTickCount} reset, so a player
 * standing on the ground is not mistaken for a flyer by the server.
 */
public final class ChestplateFlightHooks {

    private ChestplateFlightHooks() {}

    /**
     * The injected entry point: skip the jetpack tick while the player is on the
     * ground and not rising.
     */
    public static boolean skipGrounded(EntityPlayer player) {
        return player != null && ChestplateFlight.guardFires(player.onGround, player.motionY);
    }
}
