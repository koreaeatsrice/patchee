package com.jointspaceforce.patchee.features;

import java.util.Arrays;
import java.util.List;

import com.jointspaceforce.patchee.core.Feature;
import com.jointspaceforce.patchee.core.Outcome;
import com.jointspaceforce.patchee.core.Step;
import com.jointspaceforce.patchee.core.Toggles;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;

/**
 * Caps ServerUtilities' TEAM chunk limits at the single-player numbers.
 *
 * <p>
 * ServerUtilities gives each player a claim limit and a loaded-chunk limit and
 * then <b>adds its members' limits up for the whole team</b>
 * ({@code ServerUtilitiesTeamData.getMaxClaimChunks} /
 * {@code getMaxChunkloaderChunks}). On this pack one player has 1500 claims and
 * 500 loaded chunks, so a four-player team is allowed 6000 claims and 2000
 * loaded chunks — four times the space to build in, for a team that took one
 * click to make. The owner's rule: a TEAM gets the single-player total, however
 * many members it has, and a solo player (whose sum already <i>is</i> the
 * per-player number) is not affected at all.
 *
 * <p>
 * The numbers leave those two methods through four return sites and nowhere
 * else, so the rule is applied by
 * {@link com.jointspaceforce.patchee.asm.TeamChunkCapTransformer}, a coremod
 * that rewrites those sites while ServerUtilities' class loads (the same change
 * a person would make by hand, plus the one injected clamp call the JVM's
 * instruction set cannot avoid — see {@link TeamChunkCapPatch}). Nothing on the
 * client needs changing: ServerUtilities computes the numbers the claim-map GUI
 * shows on the <b>server</b> and sends them in a packet
 * ({@code MessageClaimedChunksUpdate}), so every stock client displays the
 * capped numbers by itself.
 *
 * <p>
 * This feature exists to own the config switch, the two cap values and the boot
 * line, and to publish the authoritative values into the holder the injected
 * hooks read; it contributes one step of its own and no other pipeline work.
 */
public final class TeamChunkCapFeature implements Feature {

    public static final String ID = TeamChunkCap.ID;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String configKey() {
        return TeamChunkCap.ENABLE_KEY;
    }

    @Override
    public boolean defaultEnabled() {
        return TeamChunkCap.DEFAULT_ENABLED;
    }

    @Override
    public String description() {
        return "Feature: cap a TEAM's chunk limits at the single-player numbers — " + TeamChunkCap.DEFAULT_MAX_CLAIMS
            + " claimed chunks and "
            + TeamChunkCap.DEFAULT_MAX_LOADS
            + " loaded chunks in total, no matter how many members it has. ServerUtilities adds every member's "
            + "limit up (so a 4-player team would otherwise get 6000 / 2000); with this on, every team gets the same "
            + "total as one player, and a solo player is untouched (their sum is already the per-player number). "
            + "0 in teamChunkCap.maxClaimChunks or teamChunkCap.maxLoadedChunks means Patchee does not cap that one. "
            + "Server-side only; clients need nothing installed. Restart the server after changing any of it.";
    }

    @Override
    public String disabledMessage() {
        return "[TeamChunkCap] disabled in config — ServerUtilities' team limits left exactly as they ship";
    }

    @Override
    public String failureMessage() {
        return "[TeamChunkCap] failed — ServerUtilities' team limits left exactly as they ship";
    }

    /** The class whose two limit methods the transformer rewrites. */
    @Override
    public String targetClass() {
        return TeamChunkCapPatch.TARGET_CLASS;
    }

    @Override
    public String missingTargetMessage() {
        return "[TeamChunkCap] ServerUtilities is not installed — team chunk limits skipped";
    }

    @Override
    public List<Step> steps() {
        return Arrays.asList(configureAndArm());
    }

    /** Which boot line the arm step writes; split out so it is unit-testable. */
    enum ArmReport {

        /** The target's return sites were really patched this boot: the armed line goes out. */
        ARMED,

        /** The target was present but its shape was not patched: the ERROR line goes out. */
        NOT_ARMED
    }

    /**
     * The armed-line policy, kept pure so a unit test can pin it without a game:
     * the armed line is written only after
     * {@link com.jointspaceforce.patchee.asm.TeamChunkCapTransformer} really
     * patched the target's return sites. On a ServerUtilities build whose shape
     * the patch does not recognise the target class is still <i>present</i> (so
     * the missing-target decorator does not fire), but nothing was clamped — the
     * boot line must say so at ERROR instead of claiming the feature is armed.
     */
    static ArmReport armReport() {
        return TeamChunkCap.patchApplied() ? ArmReport.ARMED : ArmReport.NOT_ARMED;
    }

    /**
     * One loud boot line with the effective caps, and the one place the
     * authoritative config values reach the holder the class-load hook reads.
     *
     * <p>
     * Server-side only: the two methods are the server's own limit lookup, and a
     * client copy of Patchee would otherwise report a limit that only its own
     * single-player world would ever ask about.
     *
     * <p>
     * The armed line is gated on {@link TeamChunkCap#patchApplied()}: a target
     * class that was merely <i>present</i> is not proof, because the transformer
     * hands an unrecognised shape back untested and logs its own ERROR. In that
     * case this step logs a plain ERROR that the feature is NOT armed, and writes
     * no armed line.
     */
    private Step configureAndArm() {
        return ctx -> {
            if (FMLCommonHandler.instance()
                .getSide() != Side.SERVER) {
                return Outcome.SKIPPED;
            }
            Toggles toggles = ctx.toggles();
            int claims = toggles.teamChunkCapMaxClaims();
            int loads = toggles.teamChunkCapMaxLoaded();
            TeamChunkCap.configure(true, true, claims, loads);
            if (armReport() != ArmReport.ARMED) {
                ctx.log()
                    .error(
                        "[TeamChunkCap] NOT armed — {} was not patched (its shape is not the one this patch knows), "
                            + "so team chunk limits are NOT capped; ServerUtilities' own per-member sums stand. "
                            + "See the [TeamChunkCap] shape ERROR above for the observed bytes.",
                        TeamChunkCapPatch.TARGET_CLASS);
                return Outcome.FAILED;
            }
            ctx.log()
                .info(
                    "[TeamChunkCap] armed — every team is capped at {} claimed / {} loaded chunks in total "
                        + "(0 = that one is not capped; single-player teams already have exactly these numbers, so "
                        + "nothing changes for them; values from config/patchee.cfg)",
                    claims == TeamChunkCap.CAP_OFF ? "no" : String.valueOf(claims),
                    loads == TeamChunkCap.CAP_OFF ? "no" : String.valueOf(loads));
            return Outcome.APPLIED;
        };
    }
}
