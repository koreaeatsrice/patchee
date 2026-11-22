package com.jointspaceforce.patchee.features;

import java.util.Collections;
import java.util.List;

import com.jointspaceforce.patchee.core.Feature;
import com.jointspaceforce.patchee.core.Outcome;
import com.jointspaceforce.patchee.core.Step;

/**
 * Puts IC2's Quantum Chestplate hover on the top rung of the vertical flight
 * ladder.
 *
 * <p>
 * IC2 clamps the hover climb to {@value ChestplateFlight#IC2_HOVER_JUMP_CLAMP}
 * blocks a tick with jump held and the hover sink to
 * {@value ChestplateFlight#IC2_SNEAK_CLAMP} with sneak held. This patch rewrites
 * the climb literal to {@link FlightTiers#QUANTUM_HOVER_ASCENT} and injects a
 * second store just after the stock sneak store so the sink matches it, leaving
 * the stock literal itself in place. Rising and sinking then match each other at
 * {@value FlightTiers#QUANTUM_HOVER_ASCENT}: 0.4 up and 0.4 down, exactly twice
 * the GraviSuite Advanced Nano's stock
 * {@value GraviSuiteFlightPatch#HOVER_JUMP_CLAMP}, so the ladder ascends and the
 * Quantum sits above the Advanced Nano.
 * {@link com.jointspaceforce.patchee.asm.ChestplateFlightTransformer} does it while
 * the class loads; this feature owns the config switch
 * ({@code enableChestplateHoverLift}) and the boot line.
 *
 * <p>
 * The sideways speed is not touched: IC2 keeps its own hover horizontal speed,
 * which is a different number on a different axis and does not set the vertical
 * target. Hover with no key held is also untouched: stock IC2 sinks at
 * {@value ChestplateFlight#HOVER_DEFAULT_TARGET}, and at 0.0 when Hodgepodge's
 * own hover fix is on, which is its default.
 *
 * <p>
 * IC2 loads on both sides and forwards the player's key state to the server in
 * its own packet ({@code IC2.network.initiateKeyUpdate} to
 * {@code Keyboard.processKeyUpdate}), so the server takes the same branches as
 * the client - one tick behind, and only after the key state changes. Put the same
 * jar on both sides so the two agree; a client without Patchee keeps IC2's stock
 * 0.1 up and -0.1 down and drifts out of step with a patched server.
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
        return "Feature: the IC2 Quantum Chestplate's hover mode rises and sinks at "
            + ChestplateFlight.hoverAscentTarget()
            + " blocks a tick, twice the GraviSuite Advanced Nano's stock "
            + GraviSuiteFlightPatch.HOVER_JUMP_CLAMP
            + ". true = while hovering, holding jump rises and holding sneak sinks at that speed; IC2's own "
            + "hover was "
            + ChestplateFlight.IC2_HOVER_JUMP_CLAMP
            + " up and "
            + ChestplateFlight.IC2_SNEAK_CLAMP
            + " down with sneak, and with no key held stock IC2 still sinks at "
            + ChestplateFlight.HOVER_DEFAULT_TARGET
            + " (0.0 when Hodgepodge's own hover fix is on, its default). The GraviSuite Advanced Nano keeps "
            + "its own stock "
            + GraviSuiteFlightPatch.HOVER_JUMP_CLAMP
            + " up and "
            + GraviSuiteFlightPatch.SNEAK_CLAMP
            + " down with sneak either way — this switch no longer changes its vertical speed; its shift+space "
            + "hold and ground guard have their own switches. false = IC2's own hover speed ("
            + ChestplateFlight.IC2_HOVER_JUMP_CLAMP
            + " up, "
            + ChestplateFlight.IC2_SNEAK_CLAMP
            + " down with sneak). Applied while the class loads, on both sides: "
            + "both mods send your key state to the server, one tick late and only after a key changes, so the same "
            + "jar on both sides keeps them in step.";
    }

    @Override
    public String disabledMessage() {
        return "[ChestplateHoverLift] disabled in config — IC2's hover speed left as stock ("
            + ChestplateFlight.IC2_HOVER_JUMP_CLAMP
            + " up, "
            + ChestplateFlight.IC2_SNEAK_CLAMP
            + " down with sneak)";
    }

    @Override
    public String failureMessage() {
        return "[ChestplateHoverLift] failed — IC2's hover speed left as stock";
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
                    "[ChestplateHoverLift] armed — in hover mode, jump rises and sneak sinks at {} per tick, twice "
                        + "the Advanced Nano's stock {}, so the Quantum sits above it on the ladder (IC2's stock "
                        + "was {} up, {} down with sneak); applied on both sides (the server's key copy is one tick "
                        + "late)",
                    ChestplateFlight.hoverAscentTarget(),
                    GraviSuiteFlightPatch.HOVER_JUMP_CLAMP,
                    ChestplateFlight.IC2_HOVER_JUMP_CLAMP,
                    ChestplateFlight.IC2_SNEAK_CLAMP);
            return Outcome.APPLIED;
        };
    }
}
