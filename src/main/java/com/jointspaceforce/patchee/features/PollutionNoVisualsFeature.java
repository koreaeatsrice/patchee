package com.jointspaceforce.patchee.features;

import java.util.Arrays;
import java.util.List;

import com.jointspaceforce.patchee.core.Feature;
import com.jointspaceforce.patchee.core.Outcome;
import com.jointspaceforce.patchee.core.Step;

/**
 * Keeps GregTech pollution fully on, but makes players see none of it.
 *
 * <p>
 * Pollution itself is untouched: production, accumulation, decay, chunk
 * storage, damage, sour rain, vegetation checks, scanners, WAILA and every
 * machine that reads pollution all keep working on their exact numbers. What
 * changes is only what a player's screen is told: GregTech sends one number per
 * chunk to each nearby client in {@code gregtech.api.net.GTPacketPollution}, and
 * the client's {@code PollutionRenderer} draws the smog, the fog, the fog colour
 * shift, the smoke particles and the grass/leaves/water/flower tints from that
 * one number and nothing else. {@link PollutionNoVisualsPatch} rewrites that
 * packet's constructor to store {@code 0} instead of the value it was given, so
 * every number the server sends is 0 and the client has nothing to draw with.
 *
 * <p>
 * It needs GregTech's own class, so the coremod transformer
 * ({@link com.jointspaceforce.patchee.asm.PollutionNoVisualsTransformer}) does
 * the work while {@code GTPacketPollution} loads, and this feature owns the
 * config switch and the boot line. Nothing on a client needs changing: the
 * packet is built the same way on a stock client when it decodes one, so a
 * client with no Patchee sees the same empty visuals.
 */
public final class PollutionNoVisualsFeature implements Feature {

    public static final String ID = PollutionNoVisuals.ID;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String configKey() {
        return PollutionNoVisuals.ENABLE_KEY;
    }

    @Override
    public boolean defaultEnabled() {
        return PollutionNoVisuals.DEFAULT_ENABLED;
    }

    @Override
    public String description() {
        return "Feature: players see no pollution smog, no fog colour shift, no smoke particles and no "
            + "grass/leaf/water/flower tints, because the one number GregTech sends each client about a chunk is "
            + "rewritten to 0 as the packet is built. Pollution production, accumulation, decay, chunk storage, "
            + "damage, sour rain, vegetation checks, scanners, WAILA and every machine reading pollution are all "
            + "server-side and stay exactly as they were. This does not edit Pollution.cfg, does not lower or "
            + "remove pollution and does not touch the game world. Tradeoff: anything a CLIENT reads from that "
            + "number now reads 0 — a client-side pollution readout or debug display built on GregTech's map shows "
            + "nothing, while every server-side read is exact. Server-side only; clients need nothing installed. "
            + "Restart the server after changing this.";
    }

    @Override
    public String disabledMessage() {
        return "[PollutionNoVisuals] disabled in config — pollution still sends its real value to clients";
    }

    @Override
    public String failureMessage() {
        return "[PollutionNoVisuals] failed — pollution still sends its real value to clients";
    }

    /** The class whose constructor the transformer rewrites. */
    @Override
    public String targetClass() {
        return PollutionNoVisualsPatch.TARGET_CLASS;
    }

    @Override
    public String missingTargetMessage() {
        return "[PollutionNoVisuals] GregTech is not installed — pollution visuals left as they are";
    }

    @Override
    public List<Step> steps() {
        return Arrays.asList(configureAndArm());
    }

    /**
     * One loud boot line, gated on the transformer really having rewritten the
     * constructor.
     *
     * <p>
     * There is no side gate on purpose: the same rewrite is valid on a client
     * (the client builds the packet when it decodes one), though the point of the
     * feature is a server.
     *
     * <p>
     * The armed line is gated on {@link PollutionNoVisuals#patchApplied()}: a
     * target class that was merely <i>present</i> is not proof, because the
     * transformer hands an unrecognised shape back untouched and logs its own
     * ERROR. In that case this step logs a plain ERROR that the feature is NOT
     * armed and writes no armed line.
     */
    private Step configureAndArm() {
        return ctx -> {
            if (!PollutionNoVisuals.patchApplied()) {
                ctx.log()
                    .error(
                        "[PollutionNoVisuals] NOT armed — {} was not patched (its shape is not the one this patch knows), "
                            + "so clients still receive the real pollution value. "
                            + "See the [PollutionNoVisuals] shape ERROR above for the observed bytes.",
                        PollutionNoVisualsPatch.TARGET_CLASS);
                return Outcome.FAILED;
            }
            ctx.log()
                .info(
                    "[PollutionNoVisuals] armed — every pollution value GregTech sends to clients is now 0, so players "
                        + "see no pollution smog, fog, particles or grass/leaf/water/flower tints; the server keeps the "
                        + "real pollution numbers for every damage, sour-rain, vegetation, scanner and WAILA check");
            return Outcome.APPLIED;
        };
    }
}
