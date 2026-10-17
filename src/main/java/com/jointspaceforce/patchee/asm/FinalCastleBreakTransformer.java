package com.jointspaceforce.patchee.asm;

import net.minecraft.launchwrapper.IClassTransformer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.jointspaceforce.patchee.features.FinalCastleBreak;
import com.jointspaceforce.patchee.features.FinalCastleBreakPatch;
import com.jointspaceforce.patchee.features.FinalCastleBreakSettings;

/**
 * Rewrites Twilight Forest's castle bricks and thorn wall as the classes load.
 *
 * <p>
 * For a player who has earned the Glacier progression, the castle bricks must
 * stop draining the pickaxe and the thorn wall must be removable. Both live in
 * one method each — {@code BlockTFCastleBlock.harvestBlock} calls
 * {@code Item.onBlockDestroyed} in a 16-iteration loop, and
 * {@code BlockTFThorns.removedByPlayer} grows the block back and bursts more
 * thorns — so {@link FinalCastleBreakPatch} redirects those call sites to
 * {@link com.jointspaceforce.patchee.features.FinalCastleBreakHooks}. See that
 * class for why a call is injected where the other Patchee patches do not.
 *
 * <p>
 * The switches are read while the class loads, which happens before Forge's
 * config system exists, so {@link FinalCastleBreakSettings} parses
 * {@code config/patchee.cfg} itself and seeds {@link FinalCastleBreak}; Patchee's
 * own pipeline publishes the authoritative values a moment later. Every failure
 * mode is soft and loud: a class that is not Twilight Forest is skipped
 * immediately, a Twilight Forest whose shape is not recognised is reported at
 * ERROR with the instruction dump and handed back untouched, and any unexpected
 * {@link Throwable} returns the original bytes. This runs inside the class
 * loader, where throwing would take the game down, so it never throws.
 */
public class FinalCastleBreakTransformer implements IClassTransformer {

    private static final Logger LOG = LogManager.getLogger("patchee");

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        // Runs for every class the loader sees: the name check comes first.
        if (basicClass == null) {
            return null;
        }
        if (!isTarget(transformedName) && !isTarget(name)) {
            return basicClass;
        }
        try {
            return patch(transformedName != null ? transformedName : name, basicClass);
        } catch (Throwable unexpected) {
            try {
                LOG.error(
                    "[FinalCastleBreak] unexpected failure while patching " + transformedName
                        + " — Twilight Forest's castle left exactly as it ships",
                    unexpected);
            } catch (Throwable ignored) {
                // logging must not be the reason a class fails to load
            }
            return basicClass;
        }
    }

    private static boolean isTarget(String className) {
        return FinalCastleBreakPatch.CASTLE_CLASS.equals(className)
            || FinalCastleBreakPatch.THORNS_CLASS.equals(className);
    }

    private byte[] patch(String className, byte[] original) {
        FinalCastleBreakSettings settings = FinalCastleBreakSettings.current();
        String source = settings.fromFile() ? settings.path()
            : "the built-in defaults (" + settings.path() + " not read)";
        if (!settings.masterEnabled()) {
            LOG.info(
                "[FinalCastleBreak] the master switch is off in {} — Twilight Forest's castle left exactly as it ships",
                source);
            return original;
        }
        if (!settings.enabled()) {
            LOG.info(
                "[FinalCastleBreak] switched off in {} — Twilight Forest's castle left exactly as it ships",
                source);
            return original;
        }
        FinalCastleBreak.seed(settings.masterEnabled(), settings.enabled());

        FinalCastleBreakPatch.Result result = FinalCastleBreakPatch.apply(original);
        switch (result.state()) {
            case PATCHED:
                // Publish the real outcome before the log line, so the ARMED gate
                // reflects the site patch and not a bare target-class presence.
                // Per class: the castle and thorn half are tracked separately, so
                // one patched half can never stand in for the other.
                FinalCastleBreak.markPatched(className);
                LOG.info(
                    "[FinalCastleBreak] patched {} — castle bricks and the thorn wall obey the Glacier gate "
                        + "({} site(s); switches from {})",
                    className,
                    result.sites(),
                    source);
                return result.bytes();
            case ALREADY_PATCHED:
                // A second pass over an already-patched class still means the patch
                // is in place for that class: the ARMED gate passes for that half
                // either way.
                FinalCastleBreak.markPatched(className);
                LOG.info(
                    "[FinalCastleBreak] {} already carries this patch — nothing to do (switches from {})",
                    className,
                    source);
                return original;
            default:
                LOG.error(
                    "[FinalCastleBreak] {} does not have the shape this patch knows — Twilight Forest left exactly as it ships ({}). "
                        + "Expected {} site(s) in {}, or {} site(s) in {}.",
                    className,
                    result.detail(),
                    FinalCastleBreakPatch.CASTLE_SITE_COUNT,
                    FinalCastleBreakPatch.CASTLE_CLASS,
                    FinalCastleBreakPatch.THORNS_SITE_COUNT,
                    FinalCastleBreakPatch.THORNS_CLASS);
                LOG.error("[FinalCastleBreak] observed shape: {}", FinalCastleBreakPatch.dump(original));
                return original;
        }
    }
}
