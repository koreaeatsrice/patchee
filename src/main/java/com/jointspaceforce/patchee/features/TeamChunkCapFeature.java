package com.jointspaceforce.patchee.features;

import java.util.Arrays;
import java.util.List;

import com.jointspaceforce.patchee.core.Feature;
import com.jointspaceforce.patchee.core.Outcome;
import com.jointspaceforce.patchee.core.Step;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;

/**
 * Reads a ServerUtilities TEAM's chunk limit from its members' per-rank
 * settings instead of the members' sum.
 *
 * <p>
 * ServerUtilities gives each player a claim limit and a loaded-chunk limit
 * (from their rank) and then <b>adds its members' limits up for the whole
 * team</b> ({@code ServerUtilitiesTeamData.getMaxClaimChunks} /
 * {@code getMaxChunkloaderChunks}). On this pack one player has 1500 claims and
 * 500 loaded chunks, so a four-player team is allowed 6000 claims and 2000
 * loaded chunks — four times the space to build in, for a team that took one
 * click to make. The owner's rule: a team's limit is its <b>best member's own
 * per-rank limit</b> — member limits are never added up, so a team of players
 * gets one player's total however many members it has, and a member whose rank
 * carries higher numbers (the admin rank) keeps them. A solo player is not
 * affected at all (their sum already is their own number).
 *
 * <p>
 * The numbers leave those two methods through four return sites and nowhere
 * else, so the rule is applied by
 * {@link com.jointspaceforce.patchee.asm.TeamChunkCapTransformer}, a coremod
 * that rewrites those sites while ServerUtilities' class loads (the same change
 * a person would make by hand, plus the one injected call the JVM's instruction
 * set cannot avoid — see {@link TeamChunkCapPatch}). Nothing on the client
 * needs changing: ServerUtilities computes the numbers the claim-map GUI shows
 * on the <b>server</b> and sends them in a packet
 * ({@code MessageClaimedChunksUpdate}), so every stock client displays the
 * resulting numbers by itself.
 *
 * <p>
 * This feature exists to own the config switch and the boot line, and to
 * publish the authoritative switches into the holder the injected hooks read;
 * it contributes one step of its own and no other pipeline work.
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
        return "Feature: a TEAM's chunk limit is its best member's own per-rank limit — member limits are never "
            + "added up. ServerUtilities normally sums every member's limit (so a 4-player team would otherwise get "
            + "6000 claims and 2000 loaded chunks); with this on, a team of same-rank players gets the single-player "
            + "total (the player rank's 1500 claims / 500 loaded) however many members it has, and a member whose "
            + "rank carries higher numbers — the admin rank — has those numbers apply; nothing is ever capped below "
            + "what a member's own rank grants. A solo player is untouched (their sum already is their own number). "
            + "Server-side only; clients need nothing installed. Restart the server after changing this.";
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
     * the missing-target decorator does not fire), but nothing was applied — the
     * boot line must say so at ERROR instead of claiming the feature is armed.
     */
    static ArmReport armReport() {
        return TeamChunkCap.patchApplied() ? ArmReport.ARMED : ArmReport.NOT_ARMED;
    }

    /**
     * One loud boot line, and the one place the authoritative config switches
     * reach the holder the class-load hook reads.
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
            TeamChunkCap.configure(true, true);
            if (armReport() != ArmReport.ARMED) {
                ctx.log()
                    .error(
                        "[TeamChunkCap] NOT armed — {} was not patched (its shape is not the one this patch knows), "
                            + "so team chunk limits are NOT applied; ServerUtilities' own per-member sums stand. "
                            + "See the [TeamChunkCap] shape ERROR above for the observed bytes.",
                        TeamChunkCapPatch.TARGET_CLASS);
                return Outcome.FAILED;
            }
            ctx.log()
                .info(
                    "[TeamChunkCap] armed — a team's limit is its best member's own per-rank limit "
                        + "(member limits are never added up, so a rank's own numbers — the admin rank's included — "
                        + "are never capped away; solo players are untouched; switches from config/patchee.cfg)");
            return Outcome.APPLIED;
        };
    }
}
