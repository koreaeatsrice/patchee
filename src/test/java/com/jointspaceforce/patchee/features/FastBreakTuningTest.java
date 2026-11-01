package com.jointspaceforce.patchee.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The block coverage and rate of the Twilight Forest fast-break rule, pure and
 * with no game: aurora must match the castle exactly (the same 0.1/tick floor),
 * aurora must be marked with the pickaxe harvest tool so the Tinkers' Construct
 * hammer's 3x3 pass includes it, and Weathered Deadrock
 * ({@code twilightforest.block.BlockTFDeadrock}) must be part of the same
 * class-matched list.
 */
class FastBreakTuningTest {

    @Test
    void auroraFloorEqualsTheCastleFloor() {
        // The owner's rule: aurora follows the castle pattern exactly.
        assertEquals(FinalCastleBreakFeature.FLOOR, AuroraBreakFeature.FLOOR);
        assertEquals(0.1F, AuroraBreakFeature.FLOOR, 1.0e-6F);
    }

    @Test
    void auroraIsMarkedWithThePickaxeHarvestTool() {
        // HarvestTool.isEffective(block, meta) is true when
        // "pickaxe".equals(block.getHarvestTool(meta)); any pickaxe qualifies.
        assertEquals("pickaxe", AuroraBreakFeature.PICKAXE);
        assertEquals(0, AuroraBreakFeature.PICKAXE_LEVEL);
    }

    @Test
    void theFourAuroraFieldsAreTheExpectedTfBlocksFields() {
        List<String> fields = Arrays.asList(AuroraBreakFeature.AURORA_FIELDS);
        assertEquals(4, fields.size());
        assertTrue(fields.contains("auroraBlock"));
        assertTrue(fields.contains("auroraPillar"));
        assertTrue(fields.contains("auroraSlab"));
        assertTrue(fields.contains("auroraDoubleSlab"));
    }

    @Test
    void weatheredDeadrockIsPartOfTheFastBreakList() {
        List<String> fields = Arrays.asList(FinalCastleBreakFeature.BLOCK_FIELDS);
        assertTrue(fields.contains("deadrock"), "Weathered Deadrock must be in the fast-break rule");
        // The three original blocks stay covered.
        assertTrue(fields.contains("castleBlock"));
        assertTrue(fields.contains("castleMagic"));
        assertTrue(fields.contains("thorns"));
    }

    @Test
    void weatheredDeadrockMatchesByClass() {
        // One block, three metadata variants; the class match covers all of them.
        Set<String> targets = Collections.singleton("twilightforest.block.BlockTFDeadrock");
        assertTrue(
            BlockClassMatcher.matchesClass(
                targets,
                "twilightforest.block.BlockTFDeadrock",
                Collections.singletonList("net.minecraft.block.Block")));
    }
}
