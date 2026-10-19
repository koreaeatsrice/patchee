package com.jointspaceforce.patchee.features;

/**
 * The team chunk-limit rule, plus the switches the injected hooks read.
 *
 * <p>
 * ServerUtilities gives every player a claim limit and a loaded-chunk limit
 * from their rank (this pack ships 1500 claims and 500 loaded chunks on the
 * player rank, and a rank may carry its own numbers — the admin rank does) and
 * then sums those per-player numbers over the whole team
 * ({@code ServerUtilitiesTeamData.getMaxClaimChunks} /
 * {@code getMaxChunkloaderChunks}), so a four-player team ends up with four
 * times one player's numbers. The owner's rule: a TEAM's limit is read from its
 * members' per-rank settings — the best member's own limit becomes the team
 * max, and member limits are never added up. A solo player is untouched (their
 * sum already equals their own setting), a team of same-rank players gets one
 * player's total however many members it has, and a member whose rank carries
 * higher numbers (the admin rank) never has them capped away.
 *
 * <p>
 * {@link #teamLimit(int, int)} is that rule and nothing else, so it is
 * unit-testable without a game. It deliberately passes through every value
 * ServerUtilities uses as a flag: {@code -1} (claimed chunks not active),
 * {@code -2} (team not valid) and {@code 0} (the mod's own "disabled" answer)
 * all come back unchanged.
 *
 * <p>
 * The rule is applied in
 * {@link com.jointspaceforce.patchee.features.TeamChunkCapPatch}'s rewritten
 * bytecode, which calls {@link TeamChunkCapHooks} — a class that cannot reach
 * Forge (it runs inside the class loader) and therefore reads its switches from
 * the small static holder below. That holder is written twice: seeded from
 * {@code config/patchee.cfg} while ServerUtilities' class loads (Forge's config
 * system does not exist yet at that point), then replaced by the authoritative
 * {@code Toggles} snapshot when Patchee's own pipeline runs
 * ({@link TeamChunkCapFeature}'s arm step). The hooks read it on every call, so
 * the values that reach the hot path are always the ones the boot published.
 */
public final class TeamChunkCap {

    /** Log id and lookup key, e.g. {@code [TeamChunkCap]} in the boot banner. */
    public static final String ID = "TeamChunkCap";

    /** The feature switch Forge writes, e.g. {@code B:enableTeamChunkCap=true}. */
    public static final String ENABLE_KEY = "enableTeamChunkCap";

    /**
     * ServerUtilities' own claim-limit permission node — the per-rank setting its
     * sum method reads from each member. A plain string on purpose: Patchee never
     * links against ServerUtilities.
     */
    public static final String CLAIMS_NODE = "serverutilities.claims.max_chunks";

    /** ServerUtilities' own loaded-chunk permission node. See {@link #CLAIMS_NODE}. */
    public static final String LOADS_NODE = "serverutilities.chunkloader.max_chunks";

    /** Used when the config file or the key is not there yet. */
    public static final boolean DEFAULT_ENABLED = true;

    private static volatile boolean master = true;
    private static volatile boolean enabled = DEFAULT_ENABLED;
    private static volatile boolean configured = false;

    /**
     * Whether ServerUtilities' return sites were really rewritten this boot.
     *
     * <p>
     * Written only by {@link com.jointspaceforce.patchee.asm.TeamChunkCapTransformer}
     * once its site patch has succeeded (or found the class already patched), and
     * read by {@link TeamChunkCapFeature} before it claims the feature is armed.
     * A pure target-class <i>presence</i> probe is not enough: a ServerUtilities
     * build whose shape the patch does not recognise is handed back untouched, so
     * the boot line must not say "armed" on it.
     */
    static volatile boolean patchApplied = false;

    private TeamChunkCap() {}

    /**
     * Publish that the transformer's return-site patch is in place. Called by
     * {@link com.jointspaceforce.patchee.asm.TeamChunkCapTransformer} the moment
     * the target's sites are patched (or found already patched). Never un-set:
     * the patch only ever goes from "not applied" to "applied" during a boot.
     */
    public static void markPatchApplied() {
        patchApplied = true;
    }

    /** True when the target class was really patched this boot (the ARMED gate). */
    public static boolean patchApplied() {
        return patchApplied;
    }

    /**
     * The rule: the team keeps {@code raw} unless the best member's own limit is
     * lower, in which case the team gets the best member's number.
     *
     * @param raw        ServerUtilities' own number (the per-member sum, or one of its sentinels)
     * @param bestMember the highest per-rank limit among the team's members, or {@code 0} if unreadable
     * @return {@code raw} when there is nothing to do, otherwise {@code bestMember}
     */
    public static int teamLimit(int raw, int bestMember) {
        return limitFires(raw, bestMember) ? bestMember : raw;
    }

    /** True only when the rule actually changes the number — the sampled log's gate. */
    public static boolean limitFires(int raw, int bestMember) {
        return raw > 0 && bestMember > 0 && bestMember < raw;
    }

    /**
     * Seed the holder from {@code config/patchee.cfg} while ServerUtilities' class
     * loads. Never overwrites a value the real config already published.
     */
    public static void seed(boolean masterEnabled, boolean featureEnabled) {
        if (configured) {
            return;
        }
        apply(masterEnabled, featureEnabled);
    }

    /**
     * Publish the authoritative config values. Called by the feature's arm step
     * with the {@code Toggles} snapshot, so a feature (and a master switch) that
     * is off in the file is off here too.
     */
    public static void configure(boolean masterEnabled, boolean featureEnabled) {
        apply(masterEnabled, featureEnabled);
        configured = true;
    }

    private static void apply(boolean masterEnabled, boolean featureEnabled) {
        master = masterEnabled;
        enabled = featureEnabled;
    }

    /** Whether the rule may run at all: the master switch and the feature's own switch. */
    public static boolean active() {
        return master && enabled;
    }

    public static boolean masterEnabled() {
        return master;
    }

    public static boolean featureEnabled() {
        return enabled;
    }

    /** True once the real config has been published (the boot line's source note). */
    public static boolean configured() {
        return configured;
    }
}
