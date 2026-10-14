package com.jointspaceforce.patchee.features;

/**
 * The team chunk-cap decision, plus the live cap values the injected hooks read.
 *
 * <p>
 * ServerUtilities gives every player a claim limit and a loaded-chunk limit
 * (the pack sets 1500 claims and 500 loaded chunks per player) and then sums
 * those per-player numbers over the whole team
 * ({@code ServerUtilitiesTeamData.getMaxClaimChunks} /
 * {@code getMaxChunkloaderChunks}), so a four-player team ends up with 6000
 * claims and 2000 loaded chunks. The owner's rule is that a TEAM is capped at
 * the single-player numbers — 1500 claims and 500 loaded chunks in total, no
 * matter how many members it has — while a solo player (whose sum already equals
 * the per-player value) is untouched.
 *
 * <p>
 * {@link #clamp(int, int)} is that rule and nothing else, so it is unit-testable
 * without a game. It deliberately passes through every value ServerUtilities
 * uses as a flag: {@code -1} (claimed chunks not active), {@code -2} (team not
 * valid) and {@code 0} (the mod's own "disabled" answer) all come back
 * unchanged, and a cap of {@code 0} means "Patchee does not cap this".
 *
 * <p>
 * The clamp itself is applied in
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

    /** The team claim cap Forge writes, e.g. {@code I:teamChunkCap.maxClaimChunks=1500}. */
    public static final String CLAIMS_KEY = "teamChunkCap.maxClaimChunks";

    /** The team loaded-chunk cap Forge writes, e.g. {@code I:teamChunkCap.maxLoadedChunks=500}. */
    public static final String LOADS_KEY = "teamChunkCap.maxLoadedChunks";

    /** Used when the config file or the key is not there yet. */
    public static final boolean DEFAULT_ENABLED = true;

    /** The single-player claim limit: the whole team gets at most this many claims. */
    public static final int DEFAULT_MAX_CLAIMS = 1500;

    /** The single-player loaded-chunk limit: the whole team gets at most this many. */
    public static final int DEFAULT_MAX_LOADS = 500;

    /** A cap of this value means "Patchee does not cap this one". */
    public static final int CAP_OFF = 0;

    private static volatile boolean master = true;
    private static volatile boolean enabled = DEFAULT_ENABLED;
    private static volatile int maxClaims = DEFAULT_MAX_CLAIMS;
    private static volatile int maxLoads = DEFAULT_MAX_LOADS;
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
     * The rule: {@code min(raw, cap)}, with every special value left alone.
     *
     * @param raw ServerUtilities' own number (or one of its sentinels)
     * @param cap Patchee's cap; {@link #CAP_OFF} (and anything negative) means no cap
     * @return {@code raw} when there is nothing to do, otherwise {@code cap}
     */
    public static int clamp(int raw, int cap) {
        return clampFires(raw, cap) ? cap : raw;
    }

    /** True only when the clamp actually changes the number — the sampled log's gate. */
    public static boolean clampFires(int raw, int cap) {
        return raw > 0 && cap > 0 && raw > cap;
    }

    /**
     * Seed the holder from {@code config/patchee.cfg} while ServerUtilities' class
     * loads. Never overwrites a value the real config already published.
     */
    public static void seed(boolean masterEnabled, boolean featureEnabled, int claimsCap, int loadsCap) {
        if (configured) {
            return;
        }
        apply(masterEnabled, featureEnabled, claimsCap, loadsCap);
    }

    /**
     * Publish the authoritative config values. Called by the feature's arm step
     * with the {@code Toggles} snapshot, so a feature (and a master switch) that
     * is off in the file is off here too.
     */
    public static void configure(boolean masterEnabled, boolean featureEnabled, int claimsCap, int loadsCap) {
        apply(masterEnabled, featureEnabled, claimsCap, loadsCap);
        configured = true;
    }

    private static void apply(boolean masterEnabled, boolean featureEnabled, int claimsCap, int loadsCap) {
        master = masterEnabled;
        enabled = featureEnabled;
        maxClaims = normalize(claimsCap, DEFAULT_MAX_CLAIMS);
        maxLoads = normalize(loadsCap, DEFAULT_MAX_LOADS);
    }

    /** A negative cap is nonsense (an unreadable value): fall back to the documented default. */
    private static int normalize(int cap, int fallback) {
        return cap < 0 ? fallback : cap;
    }

    /** Whether the clamp may run at all: the master switch and the feature's own switch. */
    public static boolean active() {
        return master && enabled;
    }

    public static boolean masterEnabled() {
        return master;
    }

    public static boolean featureEnabled() {
        return enabled;
    }

    /** The effective team claim cap; {@link #CAP_OFF} means Patchee does not cap claims. */
    public static int maxClaims() {
        return maxClaims;
    }

    /** The effective team loaded-chunk cap; {@link #CAP_OFF} means Patchee does not cap loads. */
    public static int maxLoads() {
        return maxLoads;
    }

    /** True once the real config has been published (the boot line's source note). */
    public static boolean configured() {
        return configured;
    }
}
