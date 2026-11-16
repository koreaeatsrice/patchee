package com.jointspaceforce.patchee.features;

import java.util.Collections;
import java.util.List;

import com.jointspaceforce.patchee.core.Feature;
import com.jointspaceforce.patchee.core.Outcome;
import com.jointspaceforce.patchee.core.Step;

/**
 * Makes IC2's Quantum Chestplate hover straight up and straight down at the same
 * speed - the speed it already flies sideways in hover mode.
 *
 * <p>
 * IC2 clamps the hover climb to 0.1 blocks a tick with jump held and the hover
 * sink to -0.1 with sneak held, while its own hover flight already moves the
 * player sideways at about 0.09 a tick. This patch rewrites the climb literal to
 * that sideways speed and injects a second store just after the stock sneak store
 * so the sink matches it, leaving the stock literal itself in place. Rising and
 * sinking then match each other and match the way you already travel.
 * {@link com.jointspaceforce.patchee.asm.ChestplateFlightTransformer} does it while
 * the class loads; this feature owns the config switch
 * ({@code enableChestplateHoverLift}) and the boot line.
 *
 * <p>
 * The climb becomes <b>slower</b> than the old 0.6 ceiling on purpose: that is the
 * whole point of the fix. The sideways speed is IC2's own, at full charge and
 * normal altitude: below 5% charge or near the build limit IC2 scales its own
 * sideways speed down while the vertical target stays fixed, so the match is
 * least exact there. Hover with no key held is untouched: stock IC2 sinks at 0.025,
 * and at 0.0 when Hodgepodge's own hover fix is on, which is its default.
 *
 * <p>
 * IC2 loads on both sides and forwards the player's key state to the server in
 * its own packet ({@code IC2.network.initiateKeyUpdate} to
 * {@code Keyboard.processKeyUpdate}), so the server takes the same branches as
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
        return "Feature: the IC2 Quantum Chestplate's hover mode rises and sinks at the same speed, the speed it "
            + "already flies sideways. true = while hovering, holding jump rises and holding sneak sinks at "
            + ChestplateFlight.HOVER_HORIZONTAL_SPEED
            + " blocks a tick (about 0.09), which is how fast hover flight already moves you sideways at full "
            + "charge and normal altitude (below 5% charge or near the build limit IC2 scales its own sideways "
            + "speed down while this vertical target stays fixed); with no key held stock IC2 still sinks at "
            + ChestplateFlight.HOVER_DEFAULT_TARGET
            + ", or at 0.0 when Hodgepodge's own hover fix is on (its default)"
            + ". IC2's own hover was "
            + ChestplateFlight.IC2_HOVER_JUMP_CLAMP
            + " up and "
            + ChestplateFlight.IC2_SNEAK_CLAMP
            + " down with sneak. This does make the climb slower than the old 0.6 ceiling, on purpose, so up and "
            + "down match sideways. false = IC2's own hover speed. This switch is IC2-named but it also drives "
            + "GraviSuite's Advanced Jetpack and Advanced Nano Chestplate: with it on, their hover rise and sink "
            + "are rewritten the same way (about "
            + GraviSuiteFlightPatch.HOVER_JUMP_LIFTED
            + " a tick); false leaves GraviSuite at its own 0.2 up and -0.2 down with sneak. Applied while the "
            + "class loads, on both sides: "
            + "both mods send your key state to the server, one tick late and only after a key changes, so the same "
            + "jar on both sides keeps them in step.";
    }

    @Override
    public String disabledMessage() {
        return "[ChestplateHoverLift] disabled in config — IC2's hover speed left as stock (0.1 up, -0.1 down with "
            + "sneak); it does not match the sideways hover speed";
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
                    "[ChestplateHoverLift] armed — in hover mode, jump rises and sneak sinks at the same {} per tick, "
                        + "which is how fast hover flight already moves you sideways (IC2's stock was {} up, {} down "
                        + "with sneak); applied on both sides (the server's key copy is one tick late)",
                    ChestplateFlight.HOVER_HORIZONTAL_SPEED,
                    ChestplateFlight.IC2_HOVER_JUMP_CLAMP,
                    ChestplateFlight.IC2_SNEAK_CLAMP);
            return Outcome.APPLIED;
        };
    }
}
