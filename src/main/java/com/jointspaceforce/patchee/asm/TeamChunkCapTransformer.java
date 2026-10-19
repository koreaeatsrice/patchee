package com.jointspaceforce.patchee.asm;

import net.minecraft.launchwrapper.IClassTransformer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.jointspaceforce.patchee.features.TeamChunkCap;
import com.jointspaceforce.patchee.features.TeamChunkCapPatch;
import com.jointspaceforce.patchee.features.TeamChunkCapSettings;

/**
 * Applies the team chunk-limit rule as ServerUtilities' class loads.
 *
 * <p>
 * ServerUtilities sums every member's own limit for the whole team, so a
 * four-player team gets four times the per-player numbers. The owner's rule: a
 * team's limit is its best member's own per-rank limit — member limits are
 * never added up, so a team of same-rank players gets one player's total and a
 * member whose rank carries higher numbers (the admin rank) keeps them. The two
 * methods that compute those sums are
 * {@code serverutils.data.ServerUtilitiesTeamData.getMaxClaimChunks()} and
 * {@code getMaxChunkloaderChunks()}, so their return sites are rewritten in
 * place — see {@link TeamChunkCapPatch} for the byte-level detail and for why
 * this patch injects a call where the other Patchee patches do not.
 *
 * <p>
 * The switches are read while the class loads, which happens before Forge's
 * config system exists, so {@link TeamChunkCapSettings} parses
 * {@code config/patchee.cfg} itself and seeds {@link TeamChunkCap}; Patchee's
 * own pipeline publishes the authoritative values a moment later. Every failure
 * mode is soft and loud: a class that is not ServerUtilities is skipped
 * immediately, a ServerUtilities whose shape is not recognised is reported at
 * ERROR with the instruction dump and handed back untouched, and any unexpected
 * {@link Throwable} returns the original bytes. This runs inside the class
 * loader, where throwing would take the game down, so it never throws.
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
        TeamChunkCap.seed(settings.masterEnabled(), settings.enabled());

        TeamChunkCapPatch.Result result = TeamChunkCapPatch.apply(original);
        switch (result.state()) {
            case PATCHED:
                // Publish the real outcome before the log line, so the ARMED gate
                // reflects the site patch and not a bare target-class presence.
                TeamChunkCap.markPatchApplied();
                LOG.info(
                    "[TeamChunkCap] patched {} — team limits will be read from the members' per-rank settings ({} return sites; switches from {})",
                    TeamChunkCapPatch.TARGET_CLASS,
                    result.sites(),
                    source);
                return result.bytes();
            case ALREADY_PATCHED:
                // A second pass over an already-patched class still means the patch
                // is in place: the ARMED gate passes either way.
                TeamChunkCap.markPatchApplied();
                LOG.info(
                    "[TeamChunkCap] {} already carries this patch — nothing to do (switches from {})",
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
}
