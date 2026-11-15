package com.jointspaceforce.patchee.asm;

import net.minecraft.launchwrapper.IClassTransformer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.jointspaceforce.patchee.features.ChestplateFlight;
import com.jointspaceforce.patchee.features.ChestplateFlightPatch;
import com.jointspaceforce.patchee.features.ChestplateFlightSettings;

/**
 * Applies the ChestplateFlight patches to IC2's Quantum Chestplate as its class
 * loads.
 *
 * <p>
 * Three independent edits live in one method
 * ({@code useJetpack(EntityPlayer, boolean)}): the hover+jump climb clamp is
 * raised to the normal ascent ceiling, a shift+space branch holds altitude
 * instead of drifting up, and an early return keeps a player standing on the
 * ground from being treated as flying. See {@link ChestplateFlightPatch} for the
 * byte-level detail and the Hodgepodge interaction.
 *
 * <p>
 * The switches are read while the class loads, which happens before Forge's
 * config system exists, so {@link ChestplateFlightSettings} parses
 * {@code config/patchee.cfg} itself. IC2's class is loaded on both sides, so the
 * patches are applied on both sides. IC2 forwards the player's key state to the server in its own packet, so the two
 * keyboard-driven edits also reach the server, one tick late and only after a key changes.
 *
 * <p>
 * Every failure mode is soft and loud: a class that is not IC2's is skipped
 * immediately, an IC2 whose shape is not recognised is reported at ERROR with
 * the instruction dump and handed back untouched, and any unexpected
 * {@link Throwable} returns the original bytes. This runs inside the class
 * loader, where throwing would take the game down, so it never throws.
 */
public class ChestplateFlightTransformer implements IClassTransformer {

    private static final Logger LOG = LogManager.getLogger("patchee");

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        // Runs for every class the loader sees: the name check comes first.
        if (basicClass == null) {
            return null;
        }
        if (!ChestplateFlightPatch.TARGET_CLASS.equals(transformedName)
            && !ChestplateFlightPatch.TARGET_CLASS.equals(name)) {
            return basicClass;
        }
        try {
            return patch(transformedName != null ? transformedName : name, basicClass);
        } catch (Throwable unexpected) {
            try {
                LOG.error(
                    "[ChestplateFlight] unexpected failure while patching " + ChestplateFlightPatch.TARGET_CLASS
                        + " — IC2's Quantum Chestplate left exactly as it ships",
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
                "[ChestplateFlight] the master switch is off in {} — IC2's Quantum Chestplate left exactly as it ships",
                source);
            return original;
        }
        boolean lift = settings.liftEnabled();
        boolean hold = settings.holdEnabled();
        boolean guard = settings.guardEnabled();
        if (!lift && !hold && !guard) {
            LOG.info(
                "[ChestplateFlight] all three switches are off in {} — IC2's Quantum Chestplate left exactly as it "
                    + "ships",
                source);
            return original;
        }

        ChestplateFlightPatch.Result result = ChestplateFlightPatch.apply(original, lift, guard, hold);
        switch (result.state()) {
            case PATCHED:
            case ALREADY_PATCHED:
                // Publish the real outcome before the boot line, so a refusal can
                // never be reported as "armed".
                if (lift) {
                    ChestplateFlight.markLiftPatched();
                }
                if (hold) {
                    ChestplateFlight.markHoldPatched();
                }
                if (guard) {
                    ChestplateFlight.markGuardPatched();
                }
                if (lift) {
                    LOG.info(
                        "[ChestplateHoverLift] patched {} — holding jump in hover mode climbs at up to {} per tick "
                            + "(the normal jetpack ceiling; IC2's own hover clamp was {}). Switches from {}",
                        className,
                        ChestplateFlight.ASCENT_CEILING,
                        ChestplateFlight.IC2_HOVER_JUMP_CLAMP,
                        source);
                }
                if (hold) {
                    LOG.info(
                        "[ChestplateHoldAltitude] patched {} — in hover mode, sneak + jump holds height at {} instead "
                            + "of drifting up. Switches from {}",
                        className,
                        ChestplateFlight.HOLD_ALTITUDE_TARGET,
                        source);
                }
                if (guard) {
                    LOG.info(
                        "[ChestplateGroundGuard] patched {} — on the ground and not rising, the jetpack tick is "
                            + "skipped, so standing still is not counted as flying. Switches from {}",
                        className,
                        source);
                }
                return result.state() == ChestplateFlightPatch.State.PATCHED ? result.bytes() : original;
            default:
                LOG.error(
                    "[ChestplateFlight] {} does not have the shape this patch knows — IC2's Quantum Chestplate left "
                        + "exactly as it ships ({}). "
                        + "Expected one {} method, one 0.6 ascent ceiling and one hover+jump clamp site.",
                    className,
                    result.detail(),
                    ChestplateFlightPatch.METHOD_NAME + ChestplateFlightPatch.METHOD_DESC);
                LOG.error("[ChestplateFlight] observed shape: {}", ChestplateFlightPatch.dump(original));
                return original;
        }
    }
}
