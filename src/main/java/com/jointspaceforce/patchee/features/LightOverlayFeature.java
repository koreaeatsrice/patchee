package com.jointspaceforce.patchee.features;

import java.util.Collections;
import java.util.List;

import com.jointspaceforce.patchee.core.Feature;
import com.jointspaceforce.patchee.core.Outcome;
import com.jointspaceforce.patchee.core.Step;
import com.jointspaceforce.patchee.core.Toggles;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;

/**
 * Overrides NEI's F7 light/mob-spawn overlay threshold, on the client.
 *
 * <p>
 * NEI's overlay marks blocks whose light is below a hardcoded threshold
 * (comparison constant {@code 8} = an X at light 0-7). The owner wants the X
 * only in pitch darkness. The value is a literal inside a private static method
 * ({@code codechicken.nei.WorldOverlayRenderer#getSpawnMode}) with no config
 * key, no server sync and no hook, so the only way to change it is to change
 * that literal.
 *
 * <p>
 * That is done by {@link com.jointspaceforce.patchee.asm.LightOverlayTransformer},
 * a coremod that rewrites the two comparison constants while NEI's class loads —
 * the same two-operand change a person makes by hand with a hex editor, with no
 * call injected and no method added. This feature exists to own the config
 * switch ({@code enableLightOverlayTweak}) and the boot line; it contributes no
 * pipeline work of its own.
 *
 * <p>
 * Consequence, stated plainly: only players who have Patchee installed on their
 * client get this. A player without it sees NEI's stock overlay. The server jar
 * is not involved.
 */
public final class LightOverlayFeature implements Feature {

    public static final String ID = "LightOverlay";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String configKey() {
        return LightOverlaySettings.MASTER_KEY;
    }

    @Override
    public boolean defaultEnabled() {
        return LightOverlaySettings.DEFAULT_ENABLED;
    }

    @Override
    public String description() {
        return "Feature: make NEI's F7 light overlay mark only pitch-dark blocks (light level "
            + LightOverlaySettings.DEFAULT_MAX_LIGHT
            + "), instead of NEI's fixed levels. true = on a client that has Patchee installed the X is drawn only "
            + "at light level lightOverlay.maxLightNormal and below; false = NEI's overlay is left exactly as it "
            + "ships. Client-side only: players without Patchee never see a change.";
    }

    @Override
    public String disabledMessage() {
        return "[LightOverlay] disabled in config — NEI's F7 overlay left as stock";
    }

    @Override
    public String failureMessage() {
        return "[LightOverlay] failed — NEI's F7 overlay left as stock";
    }

    /** The class transformer handles a missing NEI by itself, so there is no boot-time target gate here. */
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
     * One loud boot line with the effective light level, on the client only —
     * the side the patch can act on. The transformer reports the patch itself.
     */
    private Step armedStep() {
        return ctx -> {
            if (FMLCommonHandler.instance()
                .getSide() != Side.CLIENT) {
                return Outcome.SKIPPED;
            }
            Toggles toggles = ctx.toggles();
            ctx.log()
                .info(
                    "[LightOverlay] armed — F7 X at light <= {} (every dimension). Applied while NEI's class loads; "
                        + "client-side, so only clients running Patchee see it",
                    LightOverlayThreshold.clampMaxLight(toggles.lightOverlayMaxNormal()));
            return Outcome.APPLIED;
        };
    }
}
