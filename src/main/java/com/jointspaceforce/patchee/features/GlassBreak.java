package com.jointspaceforce.patchee.features;

import net.minecraft.block.Block;
import net.minecraft.block.BlockGlass;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import com.jointspaceforce.patchee.features.GlassBreakPolicy.Verdict;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * The switches and the live glue for the glass-cut fix.
 *
 * <p>
 * <b>The bug.</b> Blood Arsenal 1.5.12's
 * {@code BloodArsenalEventHooks.harvestEvent} applies its {@code bleeding}
 * potion to whoever harvests any {@code BlockGlass} (decompiled line {@code 171}:
 * {@code block instanceof BlockGlass}), and its living-tick handler adds
 * {@code Potion.blindness} every tick while bleeding is active. There is no
 * armour check and no exception for a glass that is not really glass.
 *
 * <p>
 * <b>What this class does.</b> {@link #isKnownNonGlass(Block)} answers whether a
 * {@code BlockGlass} subclass is really a different block, and
 * {@link #suppresses(Block, EntityPlayer)} combines that with the player's armour
 * into one yes/no, so
 * {@link GlassBreakFeature.Handler} can clear the two potions inside the same
 * {@code HarvestDropsEvent} dispatch. Both read the numbers published by
 * {@link #configure(boolean, boolean, int, int)}, which the feature's arm step
 * writes from the config snapshot.
 *
 * <p>
 * Everything here is null-safe and never throws: it runs inside another mod's
 * event dispatch, and a broken check must not break a player's harvest.
 */
public final class GlassBreak {

    /** Log id and config lookup key, e.g. {@code [GlassBreak]} in the boot banner. */
    public static final String ID = "GlassBreak";

    /** The feature switch Forge writes, e.g. {@code B:enableGlassBreakFix=true}. */
    public static final String ENABLE_KEY = "enableGlassBreakFix";

    /** Used when the config file or the key is not there yet. */
    public static final boolean DEFAULT_ENABLED = true;

    /**
     * Advanced key: how many armour slots must be filled for a player to count as
     * protected against being cut by real glass. 0 = never protected.
     */
    public static final String MIN_ARMOUR_KEY = "glassBreak.minArmourPieces";

    /** The default: one armour piece anywhere (a helmet alone) is enough. */
    public static final int DEFAULT_MIN_ARMOUR_PIECES = 1;

    /**
     * Blood Arsenal's event-hooks class, the owner of the culpable method. A plain
     * string on purpose: Patchee never links against Blood Arsenal.
     */
    public static final String CULPRIT_CLASS = "com.arc.bloodarsenal.common.BloodArsenalEventHooks";

    /** The class that holds the mod's bleeding potion. See {@link #BLEEDING_FIELD}. */
    public static final String POTION_OWNER_CLASS = "com.arc.bloodarsenal.common.BloodArsenal";

    /**
     * The owner class names to try, in order.
     *
     * <p>
     * The real 1.5.12 jar keeps the class in the {@code common} package
     * ({@code com.arc.bloodarsenal.common.BloodArsenal}, which declares
     * {@code public static Potion bleeding}). The shortened name is only a
     * fallback for a repack that moved the class up one package.
     */
    public static final String[] POTION_OWNER_CLASSES = { POTION_OWNER_CLASS, "com.arc.bloodarsenal.BloodArsenal" };

    /** The static {@code Potion} field naming the mod's own bleeding effect. */
    public static final String BLEEDING_FIELD = "bleeding";

    private static volatile boolean enabled = DEFAULT_ENABLED;
    private static volatile boolean active = false;
    private static volatile int minArmourPieces = DEFAULT_MIN_ARMOUR_PIECES;
    private static volatile int bleedingId = -1;

    private GlassBreak() {}

    /**
     * Publish the authoritative config values. Called by the feature's arm step.
     *
     * <p>
     * The feature is {@link #active()} only when the master switch and its own
     * switch are both on <i>and</i> Blood Arsenal's bleeding potion was really
     * read: without a potion id there is nothing to remove, so claiming to be
     * armed would be a lie.
     *
     * @param masterEnabled   the mod's master switch
     * @param featureEnabled  this feature's own switch
     * @param minArmourPieces how many filled armour slots count as protected (clamped 0..4)
     * @param bleedingId      Blood Arsenal's live bleeding potion id, or a negative value
     */
    public static void configure(boolean masterEnabled, boolean featureEnabled, int minArmourPieces, int bleedingId) {
        enabled = masterEnabled && featureEnabled;
        GlassBreak.minArmourPieces = GlassBreakPolicy.clampMinArmourPieces(minArmourPieces);
        GlassBreak.bleedingId = bleedingId;
        active = enabled && bleedingId >= 0;
    }

    /** Whether the fix may run at all. */
    public static boolean active() {
        return active;
    }

    /** The clamped armour threshold in use. */
    public static int minArmourPieces() {
        return minArmourPieces;
    }

    /** Blood Arsenal's bleeding potion id, or {@code -1} when it was never read. */
    public static int bleedingId() {
        return bleedingId;
    }

    /**
     * Whether a block is a known {@code BlockGlass} that is not glass.
     *
     * <p>
     * Both names are read here and handed to {@link GlassBreakPolicy#isKnownNonGlass}
     * so the matching itself is testable without a game. A block whose registry
     * entry is missing (or a registry that is not up yet) yields a null name,
     * which matches nothing.
     *
     * @param block the broken block, may be null
     * @return true when the block is the Transparent Technical Block or another known one
     */
    public static boolean isKnownNonGlass(Block block) {
        if (block == null) {
            return false;
        }
        try {
            String unlocalized = block.getUnlocalizedName();
            String registry = Block.blockRegistry == null ? null : Block.blockRegistry.getNameForObject(block);
            return GlassBreakPolicy.isKnownNonGlass(unlocalized, registry);
        } catch (Exception | LinkageError broken) {
            return false;
        }
    }

    /**
     * The whole decision for one broken block and one player: should Blood
     * Arsenal's bleeding and blindness be taken away straight after it applied
     * them?
     *
     * <p>
     * The block must be a {@code BlockGlass}; the armour count is the number of
     * non-null {@code getCurrentArmor(0..3)} slots; then
     * {@link GlassBreakPolicy#verdict} makes the call. Null-safe throughout.
     *
     * @param block  the broken block, may be null
     * @param player the harvesting player, may be null
     * @return true when the two potions should be removed
     */
    public static boolean suppresses(Block block, EntityPlayer player) {
        if (block == null || player == null) {
            return false;
        }
        try {
            if (!(block instanceof BlockGlass)) {
                return false;
            }
            return GlassBreakPolicy.verdict(true, isKnownNonGlass(block), armourPieces(player), minArmourPieces)
                != Verdict.ALLOW;
        } catch (Exception | LinkageError broken) {
            return false;
        }
    }

    /** How many of the four armour slots hold something. */
    private static int armourPieces(EntityPlayer player) {
        int pieces = 0;
        for (int slot = 0; slot < 4; slot++) {
            ItemStack armour = player.getCurrentArmor(slot);
            if (armour != null) {
                pieces++;
            }
        }
        return pieces;
    }

    /**
     * One-line proof that the rule reads the blocks the way the fix assumes,
     * logged by {@code SelfTest} when {@code runSelfTest=true}.
     *
     * <p>
     * It checks the three things that can silently go wrong: vanilla glass must
     * be a {@code BlockGlass} and must not be treated as a non-glass block; the
     * rule must let a bare player be cut and must suppress a player wearing the
     * configured number of pieces on real glass (with the threshold at 0 nothing
     * can protect, so it proves the rule leaves everyone alone instead); and,
     * when Chisel is installed, {@code chisel:technical2} must be a
     * {@code BlockGlass} that the rule names as non-glass — the one thing that
     * would make the bug's headline case fail. Everything is caught: a broken
     * environment reports FAIL instead of throwing inside the server start.
     *
     * @return a line starting {@code PASS}, {@code FAIL} or {@code SKIP}
     */
    public static String selfCheck() {
        try {
            if (!(Blocks.glass instanceof BlockGlass)) {
                return "FAIL vanilla minecraft:glass is not a BlockGlass";
            }
            if (isKnownNonGlass(Blocks.glass)) {
                return "FAIL vanilla minecraft:glass is treated as a known non-glass block";
            }

            int minimum = minArmourPieces;
            String armourNote;
            if (minimum == 0) {
                if (GlassBreakPolicy.verdict(true, false, 4, 0) != Verdict.ALLOW) {
                    return "FAIL the armour rule is off (0 pieces) but a fully armoured player is still suppressed";
                }
                armourNote = "armour rule off";
            } else {
                if (GlassBreakPolicy.verdict(true, false, 0, minimum) != Verdict.ALLOW) {
                    return "FAIL the rule suppresses a bare player on real glass (it must let them be cut)";
                }
                if (GlassBreakPolicy.verdict(true, false, minimum, minimum) != Verdict.SUPPRESS_ARMOURED) {
                    return "FAIL the rule does not suppress a player wearing " + minimum
                        + " armour piece(s) on real glass";
                }
                armourNote = "armour rule ok (" + minimum + " piece(s))";
            }

            Block technical;
            try {
                technical = GameRegistry.findBlock("chisel", "technical2");
            } catch (Exception | LinkageError chiselAbsent) {
                technical = null;
            }
            if (technical == null) {
                return "PASS vanilla glass ok, " + armourNote + "; chisel:technical2 not found - Chisel not installed";
            }
            if (!(technical instanceof BlockGlass)) {
                return "FAIL chisel:technical2 resolved but is not a BlockGlass, so the bug's cause has changed";
            }
            if (!isKnownNonGlass(technical)) {
                return "FAIL chisel:technical2 is a BlockGlass but is not known as non-glass";
            }
            return "PASS vanilla glass ok, " + armourNote
                + ", chisel:technical2 is a BlockGlass and known as non-glass";
        } catch (Throwable broken) {
            return "FAIL glass-break self-check crashed: " + broken;
        }
    }
}
