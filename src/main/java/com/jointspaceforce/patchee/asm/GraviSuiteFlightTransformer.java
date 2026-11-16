package com.jointspaceforce.patchee.asm;

import net.minecraft.launchwrapper.IClassTransformer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.jointspaceforce.patchee.features.ChestplateFlightSettings;
import com.jointspaceforce.patchee.features.GraviSuiteFlightPatch;

/**
 * Applies the same three flight edits to GraviSuite's Advanced Jetpack as its
 * class loads. The Advanced Nano Chestplate inherits that code path, so one
 * class is all that has to change; {@code ItemGraviChestPlate} is a different
 * method and is deliberately left alone.
 *
 * <p>
 * The edits live in {@code ItemAdvancedJetPack.useJetpack(EntityPlayer,
 * boolean)}: the two hover key clamps are rewritten so jump rises and sneak
 * sinks at GraviSuite's own hover sideways speed, a shift+space branch holds
 * altitude instead of sinking, and an early return keeps a player standing on
 * the ground from being treated as flying. See {@link GraviSuiteFlightPatch} for
 * the byte-level detail.
 *
 * <p>
 * The two keyboard-driven edits are applied on <b>both</b> sides, because
 * GraviSuite forwards the player's key state to the server in its own packet
 * ({@code PacketKeyboardUpdate}), one tick late and only after a key change; the
 * ground guard runs on both sides as well.
 *
 * <p>
 * The switches are the existing ChestplateFlight ones, read while the class
 * loads (before Forge's config system exists) by
 * {@link ChestplateFlightSettings}. No new config keys: turning the IC2 tweaks
 * off turns these off too.
 *
 * <p>
 * Every failure mode is soft and loud: a class that is not GraviSuite's is
 * skipped immediately, a GraviSuite whose shape is not recognised is reported
 * at ERROR with the instruction dump and handed back untouched, and any
 * unexpected {@link Throwable} returns the original bytes. This runs inside the
 * class loader, where throwing would take the game down, so it never throws.
 */
public class GraviSuiteFlightTransformer implements IClassTransformer {

    private static final Logger LOG = LogManager.getLogger("patchee");

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        // Runs for every class the loader sees: the name check comes first.
        if (basicClass == null) {
            return null;
        }
        if (!GraviSuiteFlightPatch.TARGET_CLASS.equals(transformedName)
            && !GraviSuiteFlightPatch.TARGET_CLASS.equals(name)) {
            return basicClass;
        }
        try {
            return patch(transformedName != null ? transformedName : name, basicClass);
        } catch (Throwable unexpected) {
            try {
                LOG.error(
                    "[GraviSuiteFlight] unexpected failure while patching " + GraviSuiteFlightPatch.TARGET_CLASS
                        + " — GraviSuite's jetpack left exactly as it ships",
                    unexpected);
            } catch (Throwable ignored) {
                // logging must not be the reason a class fails to load
            }
            return basicClass;
        }
    }

    private byte[] patch(String className, byte[] original) {
        ChestplateFlightSettings settings = ChestplateFlightSettings.current();
        String source = settings.fromFile() ? settings.path()
            : "the built-in defaults (" + settings.path() + " not read)";
        if (!settings.masterEnabled()) {
            LOG.info(
                "[GraviSuiteFlight] the master switch is off in {} — GraviSuite's jetpack left exactly as it ships",
                source);
            return original;
        }
        boolean lift = settings.liftEnabled();
        boolean hold = settings.holdEnabled();
        boolean guard = settings.guardEnabled();
        if (!lift && !hold && !guard) {
            LOG.info(
                "[GraviSuiteFlight] all three switches are off in {} — GraviSuite's jetpack left exactly as it ships",
                source);
            return original;
        }

        GraviSuiteFlightPatch.Result result = GraviSuiteFlightPatch.apply(original, lift, guard, hold);
        switch (result.state()) {
            case PATCHED:
            case PARTIAL:
                // One line per edit this pass made, so a log reader can see which
                // switches fired without reading the detail string.
                if (result.liftApplied()) {
                    LOG.info(
                        "[GraviSuiteFlight] {} — in hover mode, jump rises at {} and sneak sinks at {} per tick — "
                            + "the same speed GraviSuite's own hover flight moves the player sideways (stock "
                            + "GraviSuite was {} up / {} down with sneak, and the previous Patchee release climbed "
                            + "at 0.6; no key still sinks at the stock hover fall speed). Switches from {}",
                        className,
                        GraviSuiteFlightPatch.HOVER_JUMP_LIFTED,
                        GraviSuiteFlightPatch.SNEAK_LIFTED,
                        GraviSuiteFlightPatch.HOVER_JUMP_CLAMP,
                        GraviSuiteFlightPatch.SNEAK_CLAMP,
                        source);
                }
                if (result.holdApplied()) {
                    LOG.info(
                        "[GraviSuiteFlight] {} — in hover mode, sneak + jump holds height at {} instead of sinking. "
                            + "Switches from {}",
                        className,
                        GraviSuiteFlightPatch.HOLD_ALTITUDE_TARGET,
                        source);
                }
                if (result.guardApplied()) {
                    LOG.info(
                        "[GraviSuiteFlight] {} — on the ground and not rising, the jetpack tick is skipped, so "
                            + "standing still is not counted as flying. Switches from {}",
                        className,
                        source);
                }
                if (result.state() == GraviSuiteFlightPatch.State.PARTIAL) {
                    LOG.warn(
                        "[GraviSuiteFlight] {} — rise, sink and shift+space were skipped together: {} (another mixin may already handle shift+space, and raising the rise alone would put the asymmetry back)",
                        className,
                        result.detail());
                }
                return result.bytes();
            case ALREADY_PATCHED:
                if (result.skipped()) {
                    LOG.warn("[GraviSuiteFlight] {} — nothing applied: {}", className, result.detail());
                } else if (result.detail() != null && result.detail()
                    .length() > 0) {
                        LOG.info(
                            "[GraviSuiteFlight] {} already carries every requested edit — bytes unchanged ({})",
                            className,
                            result.detail());
                    } else {
                        LOG.info(
                            "[GraviSuiteFlight] {} already carries every requested edit — bytes unchanged",
                            className);
                    }
                return original;
            default:
                LOG.error(
                    "[GraviSuiteFlight] {} does not have the shape this patch knows — GraviSuite's jetpack left "
                        + "exactly as it ships ({}). "
                        + "Expected one {} method, one 0.6 ascent ceiling and one hover clamp site (jump {} or {}, "
                        + "next to one sneak {}).",
                    className,
                    result.detail(),
                    GraviSuiteFlightPatch.METHOD_NAME + GraviSuiteFlightPatch.METHOD_DESC,
                    GraviSuiteFlightPatch.HOVER_JUMP_CLAMP,
                    GraviSuiteFlightPatch.HOVER_JUMP_LIFTED,
                    GraviSuiteFlightPatch.SNEAK_CLAMP);
                LOG.error("[GraviSuiteFlight] observed shape: {}", GraviSuiteFlightPatch.dump(original));
                return original;
        }
    }
}
