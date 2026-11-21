package com.jointspaceforce.patchee.asm;

import net.minecraft.launchwrapper.IClassTransformer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.jointspaceforce.patchee.features.HandshakeModListPatch;
import com.jointspaceforce.patchee.features.SuperTankFeature;

/**
 * Applies the Super Tank fix's handshake observation as FML's class loads.
 *
 * <p>
 * FML 1.7.10 reads the client's mod list once, during the handshake, and keeps
 * no copy of it afterwards. So the only way to know whether a joining client
 * runs Patchee is to look while that list is in hand: the entry of
 * {@code FMLHandshakeServerState$2.accept} is rewritten to call
 * {@link com.jointspaceforce.patchee.features.SuperTankHandshake}. See
 * {@link HandshakeModListPatch} for the byte-level detail.
 *
 * <p>
 * The whole point is that a stock client is left completely alone. If the Super
 * Tank fix is switched off, the class is not touched at all and every client is
 * treated as stock. Every failure mode is soft and loud: a class that is not
 * FML's handshake state is skipped immediately, a class whose shape is not
 * recognised is reported at ERROR and handed back untouched, and any unexpected
 * {@link Throwable} returns the original bytes. This runs inside the class
 * loader, where throwing would take the game down, so it never throws.
 */
public class HandshakeModListTransformer implements IClassTransformer {

    private static final Logger LOG = LogManager.getLogger("patchee");

    /** One INFO line for a switched-off fix, not one per class the loader sees. */
    private static volatile boolean offReported;

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        // Runs for every class the loader sees: the name check comes first.
        if (basicClass == null) {
            return null;
        }
        if (!HandshakeModListPatch.TARGET_CLASS.equals(transformedName)
            && !HandshakeModListPatch.TARGET_CLASS.equals(name)) {
            return basicClass;
        }
        try {
            return patch(basicClass);
        } catch (Throwable unexpected) {
            try {
                LOG.error(
                    "[SuperTankFix] unexpected failure while patching " + HandshakeModListPatch.TARGET_CLASS
                        + " — FML's handshake left exactly as it ships",
                    unexpected);
            } catch (Throwable ignored) {
                // logging must not be the reason a class fails to load
            }
            return basicClass;
        }
    }

    private byte[] patch(byte[] original) {
        if (!SuperTankFeature.isArmed()) {
            reportOff();
            return original;
        }

        HandshakeModListPatch.Result result = HandshakeModListPatch.apply(original);
        switch (result.state()) {
            case PATCHED:
                LOG.info(
                    "[SuperTankFix] patched FML's client mod-list handshake — only clients that run Patchee "
                        + "will have tank debuffs cleared");
                return result.bytes();
            case ALREADY_PATCHED:
                LOG.info("[SuperTankFix] FML's client mod-list handshake already carries this patch — nothing to do");
                return original;
            default:
                LOG.error(
                    "[SuperTankFix] {} does not have the shape this patch knows — FML's handshake left "
                        + "exactly as it ships (expected {} {})",
                    HandshakeModListPatch.TARGET_CLASS,
                    HandshakeModListPatch.METHOD,
                    HandshakeModListPatch.METHOD_DESC);
                return original;
        }
    }

    /** Tells the log once that the fix is off, so no handshake is worth patching. */
    private static void reportOff() {
        if (offReported) {
            return;
        }
        offReported = true;
        try {
            LOG.info(
                "[SuperTankFix] the fix is off in config — FML's client mod-list handshake left exactly as it ships, "
                    + "so every client is treated as stock");
        } catch (Throwable ignored) {
            // logging must not be the reason a class fails to load
        }
    }
}
