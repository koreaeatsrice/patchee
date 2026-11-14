package com.jointspaceforce.patchee.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The hardness a break-speed floor is computed from, with and without a position.
 *
 * <p>
 * A break event normally arrives with the block's coordinates, so the hardness is
 * read from the world at that spot. A block-wrapper mod breaks that: Forge
 * Multipart re-exposes a Twilight Forest microblock and reports it at the
 * synthetic coordinate {@code (0,-1,0)} because a microblock has no block
 * position of its own. Reading the world there would measure a different block,
 * so the block's own constant hardness is the right number to divide by — it is
 * what the block answers when asked without a world, and it is the same number
 * the vanilla formula uses. These tests pin that the two reads stay distinct and
 * that an unusable hardness is never floored.
 */
class BreakHardnessTest {

    private static final float EPS = 1.0e-4F;

    @Test
    void positionedUsesTheWorldHardness() {
        BreakHardness hardness = BreakHardness.atPosition(2.0F);

        assertEquals(2.0F, hardness.value(), EPS);
        assertTrue(hardness.readable(), "a positive hardness is usable");
        assertTrue(hardness.positioned(), "the hardness was read at a real position");
    }

    @Test
    void unknownPositionUsesTheBlocksOwnHardness() {
        BreakHardness hardness = BreakHardness.withoutPosition(2.0F);

        assertEquals(2.0F, hardness.value(), EPS);
        assertTrue(hardness.readable(), "the block's own hardness is usable");
        assertFalse(hardness.positioned(), "the block reported no position");
    }

    @Test
    void zeroAndNegativeAreUnreadable() {
        assertFalse(
            BreakHardness.atPosition(0.0F)
                .readable(),
            "hardness 0 means unbreakable");
        assertFalse(
            BreakHardness.withoutPosition(0.0F)
                .readable(),
            "hardness 0 means unbreakable");
        assertFalse(
            BreakHardness.withoutPosition(-1.0F)
                .readable(),
            "a negative hardness means unknown");
    }

    @Test
    void hasPositionOnlyForRealCoordinates() {
        assertTrue(BreakHardness.hasPosition(0), "y = 0 is the world floor, a real position");
        assertTrue(BreakHardness.hasPosition(64), "y = 64 is a real position");
        assertTrue(BreakHardness.hasPosition(255), "y = 255 is a real position");
        assertFalse(BreakHardness.hasPosition(-1), "-1 is a wrapper's \"no position\"");
        assertFalse(BreakHardness.hasPosition(-2), "another negative is still no position");
    }

    @Test
    void aWrapperWithoutAPositionStillBreaksAtTheSameFloor() {
        float positioned = AuroraBreakFeature.floorSpeed(
            BreakHardness.atPosition(2.0F)
                .value(),
            false,
            AuroraBreakFeature.FLOOR);
        float unpositioned = AuroraBreakFeature.floorSpeed(
            BreakHardness.withoutPosition(2.0F)
                .value(),
            false,
            AuroraBreakFeature.FLOOR);

        assertEquals(20.0F, unpositioned, EPS);
        assertEquals(positioned, unpositioned, EPS, "the wrapper breaks as fast as the full block");
    }
}
