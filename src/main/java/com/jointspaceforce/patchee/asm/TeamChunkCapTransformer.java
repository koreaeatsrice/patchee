package com.jointspaceforce.patchee.asm;

import net.minecraft.launchwrapper.IClassTransformer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.jointspaceforce.patchee.features.TeamChunkCap;
import com.jointspaceforce.patchee.features.TeamChunkCapPatch;
import com.jointspaceforce.patchee.features.TeamChunkCapSettings;

/**
 * Clamps ServerUtilities' TEAM chunk limits as the class loads.
 *
 * <p>
 * ServerUtilities sums every member's own limit for the whole team, so a
 * four-player team gets four times the per-player numbers. The owner's rule is
 * one flat total per team: 1500 claimed chunks and 500 loaded chunks, with solo
 * players (whose sum is already the per-player number) untouched. The two
 * methods that compute those sums are
 * {@code serverutils.data.ServerUtilitiesTeamData.getMaxClaimChunks()} and
 * {@code getMaxChunkloaderChunks()}, so their return sites are rewritten in
 * place — see {@link TeamChunkCapPatch} for the byte-level detail and for why
 * this patch injects a call where the other Patchee patches do not.
 *
 * <p>
 * The values are read while the class loads, which happens before Forge's config
 * system exists, so {@link TeamChunkCapSettings} parses {@code config/patchee.cfg}
 * itself and seeds {@link TeamChunkCap}; Patchee's own pipeline publishes the
 * authoritative values a moment later. Every failure mode is soft and loud: a
 * class that is not ServerUtilities is skipped immediately, a ServerUtilities
 * whose shape is not recognised is reported at ERROR with the instruction dump
 * and handed back untouched, and any unexpected {@link Throwable} returns the
 * original bytes. This runs inside the class loader, where throwing would take
 * the game down, so it never throws.
 */
public class TeamChunkCapTransformer implements IClassTransformer {

    private static final Logger LOG = LogManager.getLogger("patchee");

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        // Runs for every class the loader sees: the name check comes first.
        if (basicClass == null) {
            return null;
        }
        if (!TeamChunkCapPatch.TARGET_CLASS.equals(transformedName) && !TeamChunkCapPatch.TARGET_CLASS.equals(name)) {
            return basicClass;
        }
        try {
            return patch(basicClass);
        } catch (Throwable unexpected) {
            try {
                LOG.error(
                    "[TeamChunkCap] unexpected failure while patching " + TeamChunkCapPatch.TARGET_CLASS
                        + " — ServerUtilities' team limits left exactly as they ship",
                    unexpected);
            } catch (Throwable ignored) {
                // logging must not be the reason a class fails to load
            }
            return basicClass;
        }
    }

    private byte[] patch(byte[] original) {
        TeamChunkCapSettings settings = TeamChunkCapSettings.read();
        String source = settings.fromFile() ? settings.path()
            : "the built-in defaults (" + settings.path() + " not read)";
        if (!settings.masterEnabled()) {
            LOG.info(
                "[TeamChunkCap] the master switch is off in {} — ServerUtilities' team limits left exactly as they ship",
                source);
            return original;
        }
        if (!settings.enabled()) {
            LOG.info(
                "[TeamChunkCap] switched off in {} — ServerUtilities' team limits left exactly as they ship",
                source);
            return original;
        }
        TeamChunkCap.seed(settings.masterEnabled(), settings.enabled(), settings.maxClaims(), settings.maxLoads());

        TeamChunkCapPatch.Result result = TeamChunkCapPatch.apply(original);
        switch (result.state()) {
            case PATCHED:
                // Publish the real outcome before the log line, so the ARMED gate
                // reflects the site patch and not a bare target-class presence.
                TeamChunkCap.markPatchApplied();
                LOG.info(
                    "[TeamChunkCap] patched {} — team limits clamped to {} claimed / {} loaded chunks ({} return sites; seed values from {})",
                    TeamChunkCapPatch.TARGET_CLASS,
                    capText(settings.maxClaims()),
                    capText(settings.maxLoads()),
                    result.sites(),
                    source);
                return result.bytes();
            case ALREADY_PATCHED:
                // A second pass over an already-patched class still means the patch
                // is in place: the ARMED gate passes either way.
                TeamChunkCap.markPatchApplied();
                LOG.info(
                    "[TeamChunkCap] {} already carries this patch — nothing to do (seed values from {})",
                    TeamChunkCapPatch.TARGET_CLASS,
                    source);
                return original;
            default:
                LOG.error(
                    "[TeamChunkCap] {} does not have the shape this patch knows — ServerUtilities left exactly as it ships ({}). "
                        + "Expected {} return sites, GETFIELD cachedMax...Chunks: I followed by IRETURN, in getMaxClaimChunks() and getMaxChunkloaderChunks().",
                    TeamChunkCapPatch.TARGET_CLASS,
                    result.detail(),
                    TeamChunkCapPatch.SITE_COUNT);
                LOG.error("[TeamChunkCap] observed shape: {}", TeamChunkCapPatch.dump(original));
                return original;
        }
    }

    private static String capText(int cap) {
        return cap == TeamChunkCap.CAP_OFF ? "no" : String.valueOf(cap);
    }
}
