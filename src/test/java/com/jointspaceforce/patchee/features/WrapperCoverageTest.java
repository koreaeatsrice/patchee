package com.jointspaceforce.patchee.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The block-wrapper research table and the boot wording it drives, pinned without
 * a game: which mods were inspected, how each one is probed on the classpath
 * (a class name that is only a string, so no adapter code and no dependency), and
 * what the log says for each installed wrapper.
 *
 * <p>
 * The table is the record of why no unwrapping code exists — every inspected
 * wrapper computes the break progress from the wrapped block itself. A new row is
 * one entry here plus one test, and the summary count comes from the table, so it
 * cannot drift.
 */
class WrapperCoverageTest {

    private static final String FORGE_MULTIPART = "codechicken.multipart.BlockMultipart";
    private static final String ARCHITECTURE_CRAFT = "gcewing.architecture.common.block.BlockShape";
    private static final String CARPENTERS_BLOCKS = "com.carpentersblocks.block.BlockCarpentersBlock";
    private static final String LITTLE_TILES = "com.creativemd.littletiles.common.blocks.BlockTile";

    @Test
    void tableHasTheFourInspectedWrappers() {
        List<WrapperCoverage.Wrapper> table = WrapperCoverage.table();

        assertEquals(4, table.size(), "four wrappers were inspected");
        assertEquals(
            Arrays.asList("Forge Multipart", "ArchitectureCraft", "Carpenter's Blocks", "LittleTiles"),
            Arrays.asList(
                table.get(0)
                    .mod(),
                table.get(1)
                    .mod(),
                table.get(2)
                    .mod(),
                table.get(3)
                    .mod()),
            "the table order is the research order");
        assertEquals(
            FORGE_MULTIPART,
            table.get(0)
                .probeClass());
        assertEquals(
            ARCHITECTURE_CRAFT,
            table.get(1)
                .probeClass());
        assertEquals(
            CARPENTERS_BLOCKS,
            table.get(2)
                .probeClass());
        assertEquals(
            LITTLE_TILES,
            table.get(3)
                .probeClass());
    }

    @Test
    void probeClassesAreDistinctAndBelongToWrappers() {
        Set<String> probes = new LinkedHashSet<String>();
        for (WrapperCoverage.Wrapper wrapper : WrapperCoverage.table()) {
            probes.add(wrapper.probeClass());
        }

        assertEquals(4, probes.size(), "each wrapper has its own probe class");
        for (String probe : probes) {
            assertFalse(
                probe.startsWith("twilightforest."),
                "a probe is a wrapper class, not a Twilight Forest one: " + probe);
        }
    }

    @Test
    void hammerVerdictsMatchWhatWasVerified() {
        for (WrapperCoverage.Wrapper wrapper : WrapperCoverage.table()) {
            WrapperCoverage.Hammer expected = "ArchitectureCraft".equals(wrapper.mod())
                ? WrapperCoverage.Hammer.ALREADY_EFFECTIVE
                : WrapperCoverage.Hammer.NOT_EXPRESSIBLE;
            assertEquals(expected, wrapper.hammer(), wrapper.mod() + " hammer verdict");
        }
    }

    @Test
    void presentKeepsTheTableOrderAndIgnoresUnknownClasses() {
        // Deliberately out of table order, with an unrelated class mixed in.
        Set<String> presentClasses = new LinkedHashSet<String>(
            Arrays.asList(LITTLE_TILES, "net.minecraft.block.Block", FORGE_MULTIPART));

        List<WrapperCoverage.Wrapper> present = WrapperCoverage.present(presentClasses);

        assertEquals(2, present.size());
        assertEquals(
            "Forge Multipart",
            present.get(0)
                .mod());
        assertEquals(
            "LittleTiles",
            present.get(1)
                .mod());
    }

    @Test
    void presentIsEmptyForNullOrUnknownInput() {
        assertTrue(
            WrapperCoverage.present(null)
                .isEmpty(),
            "no class names means nothing present");
        assertTrue(
            WrapperCoverage.present(Collections.singleton("com.example.NotAWrapper"))
                .isEmpty(),
            "an unknown class is not a wrapper");
    }

    @Test
    void linesWithNoWrapperInstalledSaysThereIsNothingToUnwrap() {
        List<String> lines = WrapperCoverage.lines("AuroraBreak", Collections.<String>emptySet());

        assertEquals(1, lines.size(), "one plain line when nothing is installed");
        String only = lines.get(0);
        assertTrue(only.contains("[AuroraBreak] wrapper coverage:"), only);
        assertTrue(only.contains("no block-wrapper mods installed"), only);
    }

    @Test
    void linesNameEachInstalledWrapperAndCount() {
        List<String> lines = WrapperCoverage.lines("AuroraBreak", Collections.singleton(FORGE_MULTIPART));

        assertEquals(2, lines.size(), "one wrapper line plus a count");
        String wrapperLine = lines.get(0);
        assertTrue(wrapperLine.contains("Forge Multipart"), wrapperLine);
        assertTrue(wrapperLine.contains("10-tick floor"), wrapperLine);
        assertTrue(wrapperLine.contains("TiC hammer 3x3 not enabled"), wrapperLine);

        String summary = lines.get(1);
        assertTrue(summary.contains("1 of 4 known block-wrapper mods present"), summary);
    }

    @Test
    void architectureCraftIsReportedAsHammerEffective() {
        List<String> lines = WrapperCoverage.lines("AuroraBreak", Collections.singleton(ARCHITECTURE_CRAFT));

        assertEquals(2, lines.size());
        assertTrue(
            lines.get(0)
                .contains("TiC hammer 3x3 works"),
            lines.get(0));
    }

    @Test
    void linesCarryNoLogFormatPlaceholders() {
        Set<String> all = new LinkedHashSet<String>(
            Arrays.asList(FORGE_MULTIPART, ARCHITECTURE_CRAFT, CARPENTERS_BLOCKS, LITTLE_TILES));

        String joined = String.join("\n", WrapperCoverage.lines("AuroraBreak", all));

        assertTrue(joined.contains("4 of 4 known block-wrapper mods present"), joined);
        assertFalse(joined.contains("{}"), "report() logs each line as a plain message: " + joined);
        assertFalse(joined.contains("%s"), "report() logs each line as a plain message: " + joined);
    }
}
