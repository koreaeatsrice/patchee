package com.jointspaceforce.patchee.asm;

import net.minecraft.launchwrapper.IClassTransformer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.jointspaceforce.patchee.features.PollutionNoVisuals;
import com.jointspaceforce.patchee.features.PollutionNoVisualsPatch;
import com.jointspaceforce.patchee.features.PollutionNoVisualsSettings;

/**
 * Stops GregTech telling clients how polluted a chunk is, so players see no
 * pollution visuals while the server keeps the real numbers.
 *
 * <p>
 * GregTech sends one number per chunk to nearby clients in
 * {@code gregtech.api.net.GTPacketPollution}, and the client's
 * {@code PollutionRenderer} draws fog, a fog colour shift, smoke particles and
 * grass/leaves/water/flower tints from that number alone. Rewriting the
 * packet's {@code (chunk, int)} constructor to store {@code 0} makes every value
 * the server sends 0 — see {@link PollutionNoVisualsPatch} for the byte-level
 * detail. The real pollution is written to chunk storage before the packet is
 * built, so every server-side read stays exact.
 *
 * <p>
 * The switches are read while the class loads, which happens before Forge's
 * config system exists, so {@link PollutionNoVisualsSettings} parses
 * {@code config/patchee.cfg} itself — the settings are handed to
 * {@link #patch(byte[], PollutionNoVisualsSettings)} as a value, so a test never
 * reads a config file. Every failure mode is soft and loud: a
 * class that is not GregTech's packet is skipped immediately, a packet whose
 * shape is not recognised is reported at ERROR with the instruction dump and
 * handed back untouched, and any unexpected {@link Throwable} returns the
 * original bytes. This runs inside the class loader, where throwing would take
 * the game down, so it never throws.
 */
public class PollutionNoVisualsTransformer implements IClassTransformer {

    private static final Logger LOG = LogManager.getLogger("patchee");

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        // Runs for every class the loader sees: the name check comes first.
        if (basicClass == null) {
            return null;
        }
        if (!PollutionNoVisualsPatch.TARGET_CLASS.equals(transformedName)
            && !PollutionNoVisualsPatch.TARGET_CLASS.equals(name)) {
            return basicClass;
        }
        try {
            return patch(basicClass, PollutionNoVisualsSettings.read());
        } catch (Throwable unexpected) {
            try {
                LOG.error(
                    "[PollutionNoVisuals] unexpected failure while patching " + PollutionNoVisualsPatch.TARGET_CLASS
                        + " — pollution still sends its real value to clients",
                    unexpected);
            } catch (Throwable ignored) {
                // logging must not be the reason a class fails to load
            }
            return basicClass;
        }
    }

    /**
     * The patch itself, with the switches handed in as a value.
     *
     * <p>
     * Public so the unit tests can drive the whole decision without reading the
     * developer's own {@code config/patchee.cfg} (the {@link #transform} entry
     * point reads the file; this method never does). All logging stays here, so
     * a test that runs it prints exactly what a real boot would.
     *
     * @param original the class bytes as the loader handed them over
     * @param settings the switches, already read
     * @return the patched bytes, or {@code original} when nothing was changed
     */
    public byte[] patch(byte[] original, PollutionNoVisualsSettings settings) {
        String source = settings.fromFile() ? settings.path()
            : "the built-in defaults (" + settings.path() + " not read)";
        if (!PollutionNoVisuals.shouldPatch(settings.masterEnabled(), settings.enabled())) {
            // Publish the real reason: the boot line must not report a shape
            // mismatch on a class that was deliberately left alone.
            PollutionNoVisuals.markLoadTimeDisabled();
            if (!settings.masterEnabled()) {
                LOG.info(
                    "[PollutionNoVisuals] the master switch is off in {} — pollution still sends its real value to clients",
                    source);
            } else {
                LOG.info(
                    "[PollutionNoVisuals] switched off in {} — pollution still sends its real value to clients",
                    source);
            }
            return original;
        }

        PollutionNoVisualsPatch.Result result = PollutionNoVisualsPatch.apply(original);
        switch (result.state()) {
            case PATCHED:
                // Publish the real outcome before the log line, so the ARMED gate
                // reflects the constructor rewrite and not a bare class presence.
                PollutionNoVisuals.markPatchApplied();
                LOG.info(
                    "[PollutionNoVisuals] patched {} — every pollution value sent to clients is now 0 ({} constructor site; switches from {})",
                    PollutionNoVisualsPatch.TARGET_CLASS,
                    result.sites(),
                    source);
                return result.bytes();
            case ALREADY_PATCHED:
                // A second pass over an already-patched class still means the patch
                // is in place: the ARMED gate passes either way.
                PollutionNoVisuals.markPatchApplied();
                LOG.info(
                    "[PollutionNoVisuals] {} already carries this patch — nothing to do (switches from {})",
                    PollutionNoVisualsPatch.TARGET_CLASS,
                    source);
                return original;
            default:
                LOG.error(
                    "[PollutionNoVisuals] {} does not have the shape this patch knows — pollution still sends its real value to clients ({}). "
                        + "Expected a (chunk, int) constructor that stores its int argument into the pollution field; the patch replaces that load with the constant 0.",
                    PollutionNoVisualsPatch.TARGET_CLASS,
                    result.detail());
                LOG.error("[PollutionNoVisuals] observed shape: {}", PollutionNoVisualsPatch.dump(original));
                return original;
        }
    }
}
