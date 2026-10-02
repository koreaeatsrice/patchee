package com.jointspaceforce.patchee.asm;

import net.minecraft.launchwrapper.IClassTransformer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.jointspaceforce.patchee.features.LightOverlayPatch;
import com.jointspaceforce.patchee.features.LightOverlaySettings;
import com.jointspaceforce.patchee.features.LightOverlayThreshold;

/**
 * Applies the F7 light-overlay patch to NEI's class as it loads.
 *
 * <p>
 * What it does is exactly what a hand patch of {@code NotEnoughItems-*.jar}
 * does: the two hardcoded comparison constants inside
 * {@code codechicken.nei.WorldOverlayRenderer.getSpawnMode} are rewritten to the
 * configured light level + 1. Nothing is injected into the method and no method
 * is added, so the class's stack map frames stay valid and NEI's own code keeps
 * running unchanged — see {@link LightOverlayPatch} for the byte-level detail.
 *
 * <p>
 * Every failure mode is soft and loud: a class that is not NEI is skipped
 * immediately, a NEI whose shape is not recognised is reported at ERROR and
 * handed back untouched, and any unexpected {@link Throwable} returns the
 * original bytes. This runs inside the class loader, where throwing would take
 * the game down, so it never throws.
 */
public class LightOverlayTransformer implements IClassTransformer {

    private static final String TARGET_CLASS = "codechicken.nei.WorldOverlayRenderer";

    private static final Logger LOG = LogManager.getLogger("patchee");

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        // Runs for every class the loader sees: the name check comes first.
        if (basicClass == null) {
            return null;
        }
        if (!TARGET_CLASS.equals(transformedName) && !TARGET_CLASS.equals(name)) {
            return basicClass;
        }
        try {
            return patch(basicClass);
        } catch (Throwable unexpected) {
            try {
                LOG.error(
                    "[LightOverlay] unexpected failure while patching " + TARGET_CLASS
                        + " — NEI's F7 overlay left exactly as it ships",
                    unexpected);
            } catch (Throwable ignored) {
                // logging must not be the reason a class fails to load
            }
            return basicClass;
        }
    }

    private byte[] patch(byte[] original) {
        LightOverlaySettings settings = LightOverlaySettings.read();
        String source = settings.fromFile() ? settings.path()
            : "the built-in defaults (" + settings.path() + " not read)";
        if (!settings.enabled()) {
            LOG.info("[LightOverlay] switched off in {} — NEI's F7 overlay left exactly as it ships", source);
            return original;
        }
        int constant = LightOverlayThreshold.constantFor(settings.maxLight());
        if (constant == LightOverlayThreshold.NEI_DEFAULT_CONSTANT) {
            LOG.info(
                "[LightOverlay] light level {} is NEI's own — {} left exactly as it ships",
                settings.maxLight(),
                TARGET_CLASS);
            return original;
        }
        LightOverlayPatch.Result result = LightOverlayPatch.apply(original, constant);
        switch (result.state()) {
            case PATCHED:
                LOG.info(
                    "[LightOverlay] patched {} — F7 X at light level {} ({} comparison sites, {} bytes changed; values from {})",
                    TARGET_CLASS,
                    LightOverlayThreshold.clampMaxLight(settings.maxLight()),
                    LightOverlayPatch.SITE_COUNT,
                    LightOverlayPatch.SITE_COUNT,
                    source);
                return result.bytes();
            case ALREADY_PATCHED:
                LOG.info(
                    "[LightOverlay] {} already carries this patch — nothing to do (values from {})",
                    TARGET_CLASS,
                    source);
                return original;
            default:
                LOG.error(
                    "[LightOverlay] {} does not have the NEI shape this patch knows — NEI left exactly as it ships. "
                        + "Expected exactly one of each comparison pattern; the file is most likely a different NEI version.",
                    TARGET_CLASS);
                return original;
        }
    }
}
