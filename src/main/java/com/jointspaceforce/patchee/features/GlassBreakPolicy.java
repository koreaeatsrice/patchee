package com.jointspaceforce.patchee.features;

/**
 * The glass-cut rule, as plain strings and numbers, so it can be unit-tested
 * without a game.
 *
 * <p>
 * <b>The bug.</b> Blood Arsenal 1.5.12's
 * {@code com.arc.bloodarsenal.common.BloodArsenalEventHooks.harvestEvent}
 * (decompiled line {@code 171}) reads
 * {@code if (block != null && block instanceof BlockGlass)} and, on
 * {@code random.nextInt(3) == 2}, applies its {@code bleeding} potion for 8
 * seconds. Its living-tick handler then re-applies {@code Potion.blindness}
 * every tick while bleeding is active, and there is no armour check anywhere in
 * that path. So a player who mines any {@code BlockGlass} is blinded, wearing
 * full armour or not.
 *
 * <p>
 * <b>Why one of those glasses is not glass.</b> Chisel 2.17.34-GTNH's
 * {@code team.chisel.block.BlockCarvableGlass extends BlockGlass}
 * ({@code BlockCarvableGlass.java:113} compares the unlocalized name
 * {@code tile.chisel.technical2} to pick the transparent texture set), and the
 * Transparent Technical Block {@code chisel:technical2} is built as
 * {@code new BlockCarvableGlass()} in {@code team.chisel.Features.java}
 * ({@code 3823}). It is a metal mesh block, not glass, so cutting it must not
 * cut the player.
 *
 * <p>
 * <b>Why armour matters at all.</b> The owner's rule: a bare-handed player
 * cutting real glass still gets cut (that is the mod's flavour and it stays),
 * but a player wearing armour is protected. That is the whole decision, and it
 * depends on nothing but the block's class, the block's two names, how many
 * armour pieces the player wears and the configured minimum. Keeping it here —
 * no Minecraft objects, {@code java.util} only — means the brittle part is
 * tested without a game.
 */
public final class GlassBreakPolicy {

    /**
     * The unlocalized names that belong to a {@code BlockGlass} subclass but are
     * not glass. {@code tile.chisel.technical2} is the Transparent Technical
     * Block: {@code new BlockCarvableGlass()} at {@code Features.java:3823}, the
     * exact string {@code BlockCarvableGlass.java:113} compares. The single
     * source of truth for the runtime glue in {@link GlassBreak}.
     */
    public static final String[] NON_GLASS_UNLOCALIZED = { "tile.chisel.technical2" };

    /**
     * The registry names of the same blocks, for a pack that keeps the block but
     * renames its unlocalized name. {@code chisel:technical2} is how the block is
     * registered, so it is stable where the unlocalized name is not.
     */
    public static final String[] NON_GLASS_REGISTRY = { "chisel:technical2" };

    private GlassBreakPolicy() {}

    /**
     * Whether these two names say "this {@code BlockGlass} is not glass".
     *
     * <p>
     * Both names are compared trimmed and case-insensitively against
     * {@link #NON_GLASS_UNLOCALIZED} and {@link #NON_GLASS_REGISTRY}: a pack or a
     * mod may hand either name back in a different case, and a trailing space
     * must not make the rule miss. A null or blank name matches nothing, so an
     * unregistered block is never skipped by accident.
     *
     * @param unlocalizedName {@code Block#getUnlocalizedName()}, e.g. {@code tile.chisel.technical2}
     * @param registryName    {@code Block.blockRegistry.getNameForObject(block)}, e.g. {@code chisel:technical2}
     * @return true when either name is one of the known non-glass blocks
     */
    public static boolean isKnownNonGlass(String unlocalizedName, String registryName) {
        return matches(NON_GLASS_UNLOCALIZED, unlocalizedName) || matches(NON_GLASS_REGISTRY, registryName);
    }

    /**
     * The armour threshold as the rule uses it: 0 (never protected — every
     * player is cut) up to 4 (a full set). A number outside that range is a
     * config typo, so it is clamped rather than trusted: a negative value would
     * otherwise mean "always protected" and a value above 4 could never be met.
     *
     * @param value the configured value, e.g. {@code 1} for "any armour piece"
     * @return the value clamped into 0..4
     */
    public static int clampMinArmourPieces(int value) {
        if (value < 0) {
            return 0;
        }
        if (value > 4) {
            return 4;
        }
        return value;
    }

    /**
     * The decision for one broken block and one player.
     *
     * <p>
     * Order matters: a block that is not a {@code BlockGlass} is never touched
     * (Blood Arsenal's {@code instanceof} at line {@code 171} would not fire
     * either); a known non-glass block is suppressed whatever the player wears
     * (the Transparent Technical Block is a metal mesh, so it never counts as
     * glass); and real glass is suppressed only for a player wearing at least
     * the configured number of armour pieces, where 0 means "never protected".
     *
     * @param blockIsGlass    the block is a {@code BlockGlass}
     * @param knownNonGlass   one of the names in {@link #NON_GLASS_UNLOCALIZED} /
     *                        {@link #NON_GLASS_REGISTRY} matched
     * @param armourPieces    how many armour slots the player has filled, 0..4
     * @param minArmourPieces the configured threshold; clamped by
     *                        {@link #clampMinArmourPieces(int)}
     * @return ALLOW when Blood Arsenal's behaviour should stand
     */
    public static Verdict verdict(boolean blockIsGlass, boolean knownNonGlass, int armourPieces, int minArmourPieces) {
        if (!blockIsGlass) {
            return Verdict.ALLOW;
        }
        if (knownNonGlass) {
            return Verdict.SUPPRESS_NOT_GLASS;
        }
        int minimum = clampMinArmourPieces(minArmourPieces);
        if (minimum > 0 && armourPieces >= minimum) {
            return Verdict.SUPPRESS_ARMOURED;
        }
        return Verdict.ALLOW;
    }

    private static boolean matches(String[] known, String candidate) {
        if (candidate == null) {
            return false;
        }
        String trimmed = candidate.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        for (String entry : known) {
            if (entry.equalsIgnoreCase(trimmed)) {
                return true;
            }
        }
        return false;
    }

    /** What to do about the debuffs Blood Arsenal would apply. */
    public enum Verdict {

        /** Leave Blood Arsenal's behaviour alone: the player is cut. */
        ALLOW,

        /** Remove the debuffs: this {@code BlockGlass} is a known non-glass block. */
        SUPPRESS_NOT_GLASS,

        /** Remove the debuffs: real glass, but the player is wearing enough armour. */
        SUPPRESS_ARMOURED
    }
}
