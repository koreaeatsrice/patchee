package com.jointspaceforce.patchee.features;

import java.util.Collections;
import java.util.List;

import com.jointspaceforce.patchee.core.Feature;
import com.jointspaceforce.patchee.core.Outcome;
import com.jointspaceforce.patchee.core.Step;

/**
 * Stops a player who is standing on the ground — with the IC2 Quantum Chestplate's
 * jetpack and hover mode on — from being treated as flying.
 *
 * <p>
 * IC2's {@code useJetpack} ends every tick by clearing {@code fallDistance},
 * {@code field_70140_Q} and the server's {@code floatingTickCount} anti-fly
 * counter, and it runs while hovering even when the player is standing still.
 * {@link com.jointspaceforce.patchee.asm.ChestplateFlightTransformer} injects a
 * three-instruction guard at the start of that method: on the ground and not
 * rising, the jetpack tick is skipped, so those resets do not happen. The moment
 * the player jumps the guard is inert and lift-off is exactly as before. This
 * feature owns the config switch ({@code enableChestplateGroundGuard}) and the
 * boot line.
 *
 * <p>
 * The guard reads the player's own {@code onGround} and {@code motionY}, so it
 * works on both sides; the anti-fly counter it protects only exists on the
 * server.
 */
public final class ChestplateGroundGuardFeature implements Feature {

    public static final String ID = ChestplateFlight.GUARD_ID;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String configKey() {
        return ChestplateFlight.GUARD_KEY;
    }

    @Override
    public boolean defaultEnabled() {
        return ChestplateFlight.DEFAULT_ENABLED;
    }

    @Override
    public String description() {
        return "Feature: standing still on the ground with the IC2 Quantum Chestplate's jetpack and hover mode on "
            + "is not treated as flying. true = while you are on the ground and not rising the jetpack tick is "
            + "skipped, so the game does not keep clearing your fall distance and its anti-fly counter; the moment "
            + "you jump the jetpack works exactly as before. false = IC2's own behaviour. Applied while IC2's class "
            + "loads; the anti-fly counter it protects lives on the server.";
    }

    @Override
    public String disabledMessage() {
        return "[ChestplateGroundGuard] disabled in config — IC2's own on-the-ground behaviour kept";
    }

    @Override
    public String failureMessage() {
        return "[ChestplateGroundGuard] failed — IC2's own on-the-ground behaviour kept";
    }

    /** The transformer handles a missing IC2 by itself, so there is no boot-time target gate here. */
    @Override
    public String targetClass() {
        return null;
    }

    @Override
    public String missingTargetMessage() {
        return null;
    }

    @Override
    public List<Step> steps() {
        return Collections.singletonList(armedStep());
    }

    /** One loud boot line, and only when the guard was really injected this boot. */
    private Step armedStep() {
        return ctx -> {
            if (!ChestplateFlight.guardPatched()) {
                ctx.log()
                    .info(
                        "[ChestplateGroundGuard] IC2's Quantum Chestplate class was not patched this boot (IC2 absent, "
                            + "or its useJetpack has a shape this patch does not know — see the [ChestplateFlight] "
                            + "line above); IC2's on-the-ground behaviour is unchanged");
                return Outcome.SKIPPED;
            }
            ctx.log()
                .info(
                    "[ChestplateGroundGuard] armed — on the ground and not rising, the jetpack tick is skipped so "
                        + "standing still is not counted as flying");
            return Outcome.APPLIED;
        };
    }
}
