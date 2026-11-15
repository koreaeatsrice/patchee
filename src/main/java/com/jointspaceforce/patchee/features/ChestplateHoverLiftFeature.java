package com.jointspaceforce.patchee.features;

import java.util.Collections;
import java.util.List;

import com.jointspaceforce.patchee.core.Feature;
import com.jointspaceforce.patchee.core.Outcome;
import com.jointspaceforce.patchee.core.Step;

/**
 * Makes IC2's Quantum Chestplate climb as fast in hover mode as it does in normal
 * jetpack flight.
 *
 * <p>
 * IC2 clamps the hover climb to 0.1 blocks a tick while the jump key is held,
 * one sixth of the 0.6 ceiling the same method already uses when hover mode is
 * off. {@link com.jointspaceforce.patchee.asm.ChestplateFlightTransformer} rewrites
 * that one literal while the class loads; this feature owns the config switch
 * ({@code enableChestplateHoverLift}) and the boot line.
 *
 * <p>
 * IC2 loads on both sides and forwards the player's key state to the server in
 * its own packet ({@code IC2.network.initiateKeyUpdate} to
 * {@code Keyboard.processKeyUpdate}), so the server takes the same jump branch as
 * the client - one tick behind, and only after the key state changes. Put the same
 * jar on both sides so the two agree; a client without Patchee keeps IC2's stock
 * 0.1 climb and drifts out of step with a patched server.
 */
public final class ChestplateHoverLiftFeature implements Feature {

    public static final String ID = ChestplateFlight.LIFT_ID;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String configKey() {
        return ChestplateFlight.LIFT_KEY;
    }

    @Override
    public boolean defaultEnabled() {
        return ChestplateFlight.DEFAULT_ENABLED;
    }

    @Override
    public String description() {
        return "Feature: the IC2 Quantum Chestplate's hover mode climbs as fast as its normal jetpack. "
            + "true = while hovering and holding jump you rise at up to "
            + ChestplateFlight.ASCENT_CEILING
            + " blocks a tick, the same ceiling as normal jetpack flight, instead of IC2's "
            + ChestplateFlight.IC2_HOVER_JUMP_CLAMP
            + "; false = IC2's own hover speed. Nothing is ever made slower: sinking and no-input hover keep "
            + "their stock values. Applied while IC2's class loads, on both sides: IC2 sends your key state to the "
            + "server, one tick late and only after a key changes, so the same jar on both sides keeps them in step.";
    }

    @Override
    public String disabledMessage() {
        return "[ChestplateHoverLift] disabled in config — IC2's hover climb left as stock (0.1 per tick)";
    }

    @Override
    public String failureMessage() {
        return "[ChestplateHoverLift] failed — IC2's hover climb left as stock";
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

    /**
     * One loud boot line, and only when the transformer really rewrote the clamp
     * this boot — a class whose shape was refused must not report "armed".
     */
    private Step armedStep() {
        return ctx -> {
            if (!ChestplateFlight.liftPatched()) {
                ctx.log()
                    .info(
                        "[ChestplateHoverLift] IC2's Quantum Chestplate class was not patched this boot (IC2 absent, "
                            + "or its useJetpack has a shape this patch does not know — see the [ChestplateFlight] "
                            + "line above); IC2's hover climb is unchanged");
                return Outcome.SKIPPED;
            }
            ctx.log()
                .info(
                    "[ChestplateHoverLift] armed — holding jump in hover mode climbs at up to {} per tick instead of "
                        + "{}; applied on both sides (the server's key copy is one tick late)",
                    ChestplateFlight.ASCENT_CEILING,
                    ChestplateFlight.IC2_HOVER_JUMP_CLAMP);
            return Outcome.APPLIED;
        };
    }
}
