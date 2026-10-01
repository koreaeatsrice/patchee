package com.jointspaceforce.patchee.features;

import java.util.Collections;
import java.util.List;

import com.jointspaceforce.patchee.core.Feature;
import com.jointspaceforce.patchee.core.Outcome;
import com.jointspaceforce.patchee.core.Step;
import com.jointspaceforce.patchee.core.Toggles;

/**
 * Overrides NEI's F7 light/mob-spawn overlay threshold per dimension, on the
 * client.
 *
 * <p>
 * NEI's overlay marks blocks whose light is below a hardcoded threshold
 * (comparison constant {@code 8} ⇒ an X at light 0–7). The owner wants the X
 * only in pitch darkness everywhere except the Nether, where NEI's own
 * behaviour stays. The value is a literal inside a private static method
 * ({@code codechicken.nei.WorldOverlayRenderer#getSpawnMode}) with no config
 * key, no server sync and no hook — so this is a <b>client-only</b> mixin, not
 * a pipeline step. See {@link com.jointspaceforce.patchee.mixin.WorldOverlayRendererMixin}.
 *
 * <p>
 * Consequence, stated plainly: only players who have Patchee installed on their
 * client get this. A player without it sees NEI's stock overlay. The server jar
 * is not involved.
 *
 * <p>
 * This feature still registers like every other one so it gets a config switch
 * ({@code enableLightOverlayTweak}), a banner field and a boot line; its only
 * step logs the effective thresholds. The mixin reads the live config snapshot
 * ({@link com.jointspaceforce.patchee.Config#toggles()}) on every F7 tick, so
 * editing the config and restarting is all it takes — and when the switch is
 * off the mixin returns NEI's own constant, byte-identical to stock.
 */
public final class LightOverlayFeature implements Feature {

    public static final String ID = "LightOverlay";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String configKey() {
        return "enableLightOverlayTweak";
    }

    @Override
    public boolean defaultEnabled() {
        return true;
    }

    @Override
    public String description() {
        return "Feature: make NEI's F7 light overlay match this rule. true = on a client that has Patchee "
            + "installed, the X is drawn only at light level lightOverlay.maxLightNormal (0 = pitch dark) in "
            + "every dimension except the Nether, and at lightOverlay.maxLightNether (7 = NEI's own behaviour) "
            + "in the Nether. false = NEI's overlay is left exactly as it ships. Client-side only: players "
            + "without Patchee never see a change.";
    }

    @Override
    public String disabledMessage() {
        return "[LightOverlay] disabled in config — NEI's F7 overlay left as stock";
    }

    @Override
    public String failureMessage() {
        return "[LightOverlay] failed — NEI's F7 overlay left as stock";
    }

    /** The mixin handles a missing NEI by itself, so there is no boot-time target gate here. */
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
     * One loud boot line with the effective thresholds. The mixin itself is
     * silent per tick, by design.
     */
    private Step armedStep() {
        return ctx -> {
            Toggles toggles = ctx.toggles();
            ctx.log()
                .info(
                    "[LightOverlay] armed — X at light <= {} in normal dimensions, <= {} in the Nether "
                        + "(client-side; only clients running Patchee see it)",
                    toggles.lightOverlayMaxNormal(),
                    toggles.lightOverlayMaxNether());
            return Outcome.APPLIED;
        };
    }
}
