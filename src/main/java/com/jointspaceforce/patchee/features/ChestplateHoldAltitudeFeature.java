package com.jointspaceforce.patchee.features;

import java.util.Collections;
import java.util.List;

import com.jointspaceforce.patchee.core.Feature;
import com.jointspaceforce.patchee.core.Outcome;
import com.jointspaceforce.patchee.core.Step;

/**
 * Makes shift + space hold altitude in the IC2 Quantum Chestplate's hover mode.
 *
 * <p>
 * IC2 tests the sneak key first, then the jump key, so holding both takes the
 * jump branch and the player climbs. {@link com.jointspaceforce.patchee.asm.ChestplateFlightTransformer}
 * inserts one branch inside the jump-taken block, after the jump-only store:
 * when sneak is also held the vertical target is overwritten with 0.0, so the
 * player stops climbing. IC2's own clamp still restores a larger previous motion,
 * so a climb already in progress is damped to a stop over a few ticks, not cut dead. Each key on its own keeps its
 * stock meaning.
 *
 * <p>
 * This feature owns the config switch ({@code enableChestplateHoldAltitude}) and
 * the boot line. Like the other two, it is applied while IC2's class loads on both sides; IC2 forwards the key state to
 * the server in its own packet, one tick late and only after a key changes.
 */
public final class ChestplateHoldAltitudeFeature implements Feature {

    public static final String ID = ChestplateFlight.HOLD_ID;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String configKey() {
        return ChestplateFlight.HOLD_KEY;
    }

    @Override
    public boolean defaultEnabled() {
        return ChestplateFlight.DEFAULT_ENABLED;
    }

    @Override
    public String description() {
        return "Feature: in the IC2 Quantum Chestplate's hover mode, holding sneak and jump together holds your "
            + "height instead of drifting up. true = shift + space keeps you level ("
            + ChestplateFlight.HOLD_ALTITUDE_TARGET
            + " vertical); false = IC2's own behaviour, where the jump key wins and you climb. Each key on its "
            + "own is unchanged: shift alone still sinks, jump alone still climbs. Applied while IC2's class "
            + "loads, on both sides (IC2 sends your key state to the server; its copy is one tick late). A climb "
            + "already in progress settles to level over a few ticks, because IC2 keeps the larger previous motion.";
    }

    @Override
    public String disabledMessage() {
        return "[ChestplateHoldAltitude] disabled in config — IC2's own shift+space behaviour kept (drifts up)";
    }

    @Override
    public String failureMessage() {
        return "[ChestplateHoldAltitude] failed — IC2's own shift+space behaviour kept";
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

    /** One loud boot line, and only when the branch was really injected this boot. */
    private Step armedStep() {
        return ctx -> {
            if (!ChestplateFlight.holdPatched()) {
                ctx.log()
                    .info(
                        "[ChestplateHoldAltitude] IC2's Quantum Chestplate class was not patched this boot (IC2 "
                            + "absent, or its useJetpack has a shape this patch does not know — see the "
                            + "[ChestplateFlight] line above); IC2's shift+space behaviour is unchanged");
                return Outcome.SKIPPED;
            }
            ctx.log()
                .info(
                    "[ChestplateHoldAltitude] armed — in hover mode, shift + space holds your height at {} "
                        + "instead of drifting up",
                    ChestplateFlight.HOLD_ALTITUDE_TARGET);
            return Outcome.APPLIED;
        };
    }
}
