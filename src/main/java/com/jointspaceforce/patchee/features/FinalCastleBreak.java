package com.jointspaceforce.patchee.features;

import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.stats.Achievement;
import net.minecraft.stats.StatFileWriter;
import net.minecraft.stats.StatisticsFile;

/**
 * The FinalCastleBreak decision: the live switches the injected hooks read, the
 * Glacier gate, and the achievement field the feature's resolve step publishes.
 *
 * <p>
 * Two Twilight Forest blocks gate the last stretch of the Final Castle behind a
 * chore, not behind progress:
 *
 * <ul>
 * <li>{@code BlockTFCastleBlock} (the castle bricks) deals the player's pickaxe
 * <b>16 times</b> the normal durability damage in its {@code harvestBlock}
 * ({@code item.onBlockDestroyed(...)} called inside a {@code for (i &lt; 16)}
 * loop), so mining the castle wears a pickaxe down sixteen times as fast;</li>
 * <li>{@code BlockTFThorns} (the thorn wall) cannot be removed at all by a
 * survival player: its {@code removedByPlayer} grows the block straight back and
 * then bursts <i>more</i> thorns in random directions, and it drops nothing.</li>
 * </ul>
 *
 * <p>
 * The owner's rule is that a player who has earned the Twilight Forest Glacier
 * progression (the Snow Queen achievement, {@code twilightProgressGlacier} — the
 * same gate {@link AuroraBreakFeature} uses) has beaten the castle already and
 * should be able to take it apart by hand: the castle bricks do the pickaxe no
 * extra damage, and a thorn block that is broken stays broken.
 *
 * <p>
 * The two behaviours are changed by
 * {@link com.jointspaceforce.patchee.asm.FinalCastleBreakTransformer}, a coremod
 * that rewrites the two methods' call sites while the classes load, which is what
 * {@link FinalCastleBreakHooks} exists to serve. Those hooks run inside the class
 * loader, before Forge's config exists, so they read their switches from the
 * small static holder below rather than from Forge. The holder is written twice:
 * seeded from {@code config/patchee.cfg} while the Twilight Forest class loads,
 * then replaced by the authoritative {@code Toggles} snapshot when Patchee's own
 * pipeline runs ({@link FinalCastleBreakFeature}'s arm step).
 *
 * <p>
 * The Glacier gate itself has two sides, exactly the pack's own idiom
 * ({@code TFBiomeBase.doesPlayerHaveRequiredAchievement}):
 * {@code EntityPlayerMP.func_147099_x()} on the server and
 * {@code EntityClientPlayerMP.getStatFileWriter()} on the client. A player that
 * is neither — a Forge {@code FakePlayer} on the logical server, say — is
 * answered {@code false} before the client branch is reached, so the
 * {@code @SideOnly(Side.CLIENT)} stats class is never touched on a dedicated
 * server. When the achievement could not be read at all ({@code null}) the
 * answer is also {@code false}, so the blocks keep their stock 1.7.10 behaviour.
 */
public final class FinalCastleBreak {

    /** Log id and lookup key, e.g. {@code [FinalCastleBreak]} in the boot banner. */
    public static final String ID = "FinalCastleBreak";

    /** The feature switch Forge writes, e.g. {@code B:enableFinalCastleBreak=true}. */
    public static final String ENABLE_KEY = "enableFinalCastleBreak";

    /** Used when the config file or the key is not there yet. */
    public static final boolean DEFAULT_ENABLED = true;

    private static volatile boolean master = true;
    private static volatile boolean enabled = DEFAULT_ENABLED;
    private static volatile boolean configured = false;

    /**
     * The achievement the hooks gate on, published by
     * {@link FinalCastleBreakFeature}'s resolve step. {@code null} means the gate
     * cannot be read and the hooks fall back to stock behaviour (fail safe toward
     * vanilla).
     */
    private static volatile Achievement glacier;

    /**
     * Whether each Twilight Forest target's call sites were really rewritten this
     * boot, tracked <b>per class</b>.
     *
     * <p>
     * Written only by
     * {@link com.jointspaceforce.patchee.asm.FinalCastleBreakTransformer} once a
     * target's site patch has succeeded (or found that class already patched),
     * and read by {@link FinalCastleBreakFeature} before it claims the feature is
     * armed. A bare target-class <i>presence</i> probe is not enough: a Twilight
     * Forest build whose shape the patch does not recognise is handed back
     * untouched, so the boot line must not say "armed" on it. The two halves move
     * independently — a boot where the castle bricks patched but the thorn wall
     * shape was refused must NOT report "armed" — so the flag is kept per class
     * and {@link #patchApplied()} is true only when both are in place.
     */
    static volatile boolean patchedCastle = false;
    static volatile boolean patchedThorns = false;

    private FinalCastleBreak() {}

    /**
     * Publish that the transformer's call-site patch is in place for one target
     * class. Called by
     * {@link com.jointspaceforce.patchee.asm.FinalCastleBreakTransformer} the
     * moment that class' sites are patched (or found already patched); the other
     * class' flag is left untouched. Never un-set: a flag only ever goes from
     * "not patched" to "patched" during a boot.
     *
     * @param className the transformed class name, i.e. one of
     *                  {@link FinalCastleBreakPatch#CASTLE_CLASS} /
     *                  {@link FinalCastleBreakPatch#THORNS_CLASS}
     */
    public static void markPatched(String className) {
        if (FinalCastleBreakPatch.CASTLE_CLASS.equals(className)) {
            patchedCastle = true;
        } else if (FinalCastleBreakPatch.THORNS_CLASS.equals(className)) {
            patchedThorns = true;
        }
    }

    /** True when the castle-brick class' sites were really patched this boot. */
    public static boolean patchedCastle() {
        return patchedCastle;
    }

    /** True when the thorn-wall class' sites were really patched this boot. */
    public static boolean patchedThorns() {
        return patchedThorns;
    }

    /**
     * True when <i>both</i> targets were really patched this boot (the ARMED
     * gate): a boot where only one half was rewritten must not claim the feature.
     */
    public static boolean patchApplied() {
        return patchedCastle && patchedThorns;
    }

    /**
     * Seed the holder from {@code config/patchee.cfg} while the Twilight Forest
     * class loads. Never overwrites a value the real config already published.
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

    /**
     * Whether the hooks may bypass stock Twilight Forest behaviour right now.
     *
     * <p>
     * The class-load hooks run before Forge's config exists, so the seed from
     * {@code config/patchee.cfg} decides only whether the <i>patch</i> is
     * installed — it must never arm the hooks on its own. The hooks are live only
     * after Patchee's own pipeline has confirmed the switches through
     * {@link #configure(boolean, boolean)} ({@code configured}), and only when
     * both the master switch and the feature switch are on. A boot where the
     * pipeline never confirms therefore leaves the hooks on the stock Twilight
     * Forest path (fail safe toward stock), even though the seed may say
     * {@code true}.
     */
    public static boolean active() {
        return configured && master && enabled;
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

    /**
     * Test seam: return the holder to the state a fresh boot has, so a unit test
     * does not depend on the order the other tests ran in. Package-private and
     * called only by the tests; the game never calls it.
     */
    static void resetForTest() {
        master = true;
        enabled = DEFAULT_ENABLED;
        configured = false;
        glacier = null;
        patchedCastle = false;
        patchedThorns = false;
    }

    /** Publish the Glacier achievement the gate reads; called once by the resolve step. */
    public static void publishGlacier(Achievement achievement) {
        glacier = achievement;
    }

    /** The published Glacier achievement, or {@code null} when it could not be read. */
    public static Achievement glacier() {
        return glacier;
    }

    /**
     * The Glacier progression, read from the side-appropriate stats object — the
     * pack's own two-sided idiom: {@code EntityPlayerMP.func_147099_x()} on the
     * server, {@code EntityClientPlayerMP.getStatFileWriter()} on the client. A
     * non-MP player on the logical server (a Forge {@code FakePlayer}, say) is
     * answered {@code false} before the client branch, because touching the
     * {@code @SideOnly(Side.CLIENT)} class there would throw on a dedicated
     * server. A gate that cannot be read must not grant the bypass, so anything
     * else — including a null achievement — is false.
     */
    public static boolean earnedGlacier(EntityPlayer player) {
        Achievement achievement = glacier;
        if (achievement == null || player == null) {
            return false;
        }
        if (player instanceof EntityPlayerMP) {
            StatisticsFile stats = ((EntityPlayerMP) player).func_147099_x();
            return stats != null && stats.hasAchievementUnlocked(achievement);
        }
        if (player.worldObj != null && !player.worldObj.isRemote) {
            // A non-MP player on the logical server: nothing to read, and the
            // client class must stay untouched.
            return false;
        }
        if (player instanceof EntityClientPlayerMP) {
            StatFileWriter stats = ((EntityClientPlayerMP) player).getStatFileWriter();
            return stats != null && stats.hasAchievementUnlocked(achievement);
        }
        return false;
    }
}
