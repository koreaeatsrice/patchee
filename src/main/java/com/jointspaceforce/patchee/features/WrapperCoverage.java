package com.jointspaceforce.patchee.features;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.jointspaceforce.patchee.core.PatchContext;

/**
 * The block-wrapper mods a Twilight Forest block can be hidden inside, written
 * down as data so the boot log says what Patchee knows about each one — and,
 * just as important, so the reason no adapter code exists is recorded where the
 * next reader will find it.
 *
 * <p>
 * <b>What was inspected.</b> Every 1.7.10 mod that re-exposes a foreign block
 * computes the break progress by calling the <i>wrapped</i> block's own break
 * speed. So Forge's {@code PlayerEvent.BreakSpeed} already carries the Twilight
 * Forest block as {@code event.block} and no unwrapping is needed:
 * <ul>
 * <li>ForgeMultipart — the entry point is {@code BlockMultipart.scala:280},
 * {@code getPlayerRelativeBlockHardness}, which returns
 * {@code tile.partList(index).getStrength(mop, player) / 30f}; the actual
 * synthetic-coordinate call is {@code BlockMicroMaterial.scala:133-141}
 * ({@code getStrength} -> {@code block.getBlockHardness(null, 0, 0, 0)} then
 * {@code player.getBreakSpeed(block, false, meta % 16, 0, -1, 0)}) — the
 * wrapped block, at a synthetic {@code (0,-1,0)}. FMP itself reads the wrapped
 * block's hardness with a null world, exactly what
 * {@link BreakHardness#withoutPosition} does.</li>
 * <li>ArchitectureCraft — {@code BlockShape.java:250-265} reaches
 * {@code BlockCompatUtils.java:109-112}, whose {@code getPlayerBreakSpeed} calls
 * {@code player.getBreakSpeed(block, false, meta, pos.x, pos.y, pos.z)} on the
 * source block.</li>
 * <li>Carpenter's Blocks — {@code BlockCoverable.java:1173-1200} calls
 * {@code ForgeHooks.blockStrength(coverBlock, ...)} for the cover.</li>
 * <li>LittleTiles — {@code BlockTile.java:155-163} delegates to the source block.</li>
 * </ul>
 * Read the other way round, that is Forge's documented contract:
 * {@code Block.getPlayerRelativeBlockHardness} -> {@code ForgeHooks.blockStrength}
 * -> {@code PlayerEvent.BreakSpeed}. This feature only exploits it. The single
 * real defect on our side was the {@code event.y < 0} bail-out: Forge Multipart
 * reports {@code (0,-1,0)}, so microblocks were silently skipped. That is fixed in
 * {@link BreakHardness} — a reported block without a position is floored using the
 * block's own hardness.
 *
 * <p>
 * <b>What the TiC hammer can reach.</b> The Tinkers' Construct hammer's 3x3 pass
 * tests {@code HarvestTool.isEffective} on the block it is given, which is the
 * <i>wrapper</i> block, not the wrapped one. Marking a wrapper pickaxe-harvestable
 * therefore enables the hammer for every material it can cover, and a wrapper with
 * a custom Material cannot be marked without breaking area-mining for the tiles it
 * holds. Hence the per-wrapper verdict in {@link #table()}.
 *
 * <p>
 * <b>Not wrappers</b> (checked and excluded): Chisel, ChiselTones, Railcraft and
 * BiblioCraft register their own blocks; StorageDrawers and MalisisDoors store a
 * foreign material or block <i>for rendering only</i> and never report it to a
 * break event.
 */
public final class WrapperCoverage {

    /** How a TiC hammer 3x3 area-mine sits with this wrapper. */
    enum Hammer {

        /** The wrapper's own Material is on the hammer's list: the 3x3 already works. */
        ALREADY_EFFECTIVE,

        /** Marking the wrapper would over-reach (or cannot be done): the 3x3 stays off. */
        NOT_EXPRESSIBLE
    }

    /** One known block-wrapper mod: how to probe it, what it hides, and the hammer verdict. */
    public static final class Wrapper {

        final String mod;
        final String probeClass;
        final String shape;
        final Hammer hammer;
        final String note;

        Wrapper(String mod, String probeClass, String shape, Hammer hammer, String note) {
            this.mod = mod;
            this.probeClass = probeClass;
            this.shape = shape;
            this.hammer = hammer;
            this.note = note;
        }

        /** The mod's display name, as the log prints it. */
        public String mod() {
            return mod;
        }

        /** The class whose presence proves the mod is installed. */
        public String probeClass() {
            return probeClass;
        }

        /** What the wrapper hides, in one word ("microblocks", "shapes", ...). */
        public String shape() {
            return shape;
        }

        /** Whether the TiC hammer 3x3 already reaches this wrapper. */
        public Hammer hammer() {
            return hammer;
        }

        /** Why the hammer verdict is what it is. */
        public String note() {
            return note;
        }
    }

    private WrapperCoverage() {}

    /**
     * The known block-wrapper mods, in a fixed order — the research artifact as
     * code, so a new wrapper is one row and one test, not a new if-chain.
     */
    static List<Wrapper> table() {
        List<Wrapper> rows = new ArrayList<Wrapper>();
        rows.add(
            new Wrapper(
                "Forge Multipart",
                "codechicken.multipart.BlockMultipart",
                "microblocks",
                Hammer.NOT_EXPRESSIBLE,
                "the multipart block is its own Material instance and carries no per-material harvest tool, so the "
                    + "TiC hammer 3x3 cannot be enabled for one microblock material"));
        rows.add(
            new Wrapper(
                "ArchitectureCraft",
                "gcewing.architecture.common.block.BlockShape",
                "shapes",
                Hammer.ALREADY_EFFECTIVE,
                "the wrapper block is Material.rock, so the TiC hammer 3x3 already accepts it"));
        rows.add(
            new Wrapper(
                "Carpenter's Blocks",
                "com.carpentersblocks.block.BlockCarpentersBlock",
                "covered blocks",
                Hammer.NOT_EXPRESSIBLE,
                "the wrapper block is Material.wood and marking it would enable the hammer for every cover "
                    + "material, not only the Twilight Forest ones"));
        rows.add(
            new Wrapper(
                "LittleTiles",
                "com.creativemd.littletiles.common.blocks.BlockTile",
                "mixed tiles",
                Hammer.NOT_EXPRESSIBLE,
                "the wrapper block is a custom Material with no harvest tool; area-mining it would break every "
                    + "tile in the block"));
        return Collections.unmodifiableList(rows);
    }

    /**
     * The wrappers really installed, in {@link #table()} order. Pure: the caller
     * supplies the class names it found, so this is unit-tested without a game.
     */
    static List<Wrapper> present(Collection<String> presentClasses) {
        List<Wrapper> found = new ArrayList<Wrapper>();
        if (presentClasses == null) {
            return found;
        }
        for (Wrapper wrapper : table()) {
            if (presentClasses.contains(wrapper.probeClass)) {
                found.add(wrapper);
            }
        }
        return found;
    }

    /**
     * The boot lines for {@code featureId}: nothing installed gives one plain line,
     * otherwise one line per installed wrapper plus a count. Pure, so the wording is
     * pinned by a test rather than by a live pack.
     */
    static List<String> lines(String featureId, Collection<String> presentClasses) {
        List<String> lines = new ArrayList<String>();
        List<Wrapper> found = present(presentClasses);
        if (found.isEmpty()) {
            lines.add("[" + featureId + "] wrapper coverage: no block-wrapper mods installed — nothing to unwrap");
            return lines;
        }
        for (Wrapper wrapper : found) {
            lines.add(
                "[" + featureId
                    + "] wrapper coverage: "
                    + wrapper.mod
                    + " "
                    + wrapper.shape
                    + " — 10-tick floor on the wrapped block; "
                    + (wrapper.hammer == Hammer.ALREADY_EFFECTIVE ? "TiC hammer 3x3 works"
                        : "TiC hammer 3x3 not enabled")
                    + " ("
                    + wrapper.note
                    + ")");
        }
        lines.add(
            "[" + featureId
                + "] wrapper coverage: "
                + found.size()
                + " of "
                + table().size()
                + " known block-wrapper mods present — wrapped aurora/castle blocks break at the same floor as "
                + "the native blocks");
        return lines;
    }

    /**
     * Probes each known wrapper class on this classpath and logs {@link #lines}.
     * Informational only: a wrapper that is absent, half-loaded or renamed must
     * never fail a feature's arm step, so anything thrown here is swallowed into
     * one WARN.
     */
    static void report(PatchContext ctx, String featureId) {
        try {
            Set<String> presentClasses = new LinkedHashSet<String>();
            for (Wrapper wrapper : table()) {
                if (ctx.reflect()
                    .find(wrapper.probeClass) != null) {
                    presentClasses.add(wrapper.probeClass);
                }
            }
            for (String line : lines(featureId, presentClasses)) {
                ctx.log()
                    .info(line);
            }
        } catch (Throwable failure) {
            ctx.log()
                .warn(
                    "[" + featureId + "] wrapper coverage probe failed — the break-speed floor is unaffected",
                    failure);
        }
    }
}
