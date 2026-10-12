package com.jointspaceforce.patchee.features;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The pure break-floor arithmetic both Twilight break-speed features share, no
 * game required.
 *
 * <p>
 * The vanilla result is {@code speed / hardness / divisor}, so setting the speed
 * to {@code floor * hardness * divisor} makes the player break the block at
 * exactly {@code floor} progress per tick — 0.1 (10 ticks) for the aurora blocks
 * and 0.2 (5 ticks) for arctic fur. These tests pin that arithmetic and the tick
 * counts the feature promises.
 */
class AuroraBreakFloorTest {

    private static final float EPS = 1.0e-4F;

    /** Mirrors {@code ForgeHooks.blockStrength}'s own division. */
    private static double progress(float speed, float hardness, boolean canHarvest) {
        float divisor = canHarvest ? AuroraBreakFeature.HARVESTED_DIVISOR : AuroraBreakFeature.UNHARVESTED_DIVISOR;
        return (double) speed / hardness / divisor;
    }

    /** The block breaks when {@code progressPerTick * (elapsedTicks + 1)} reaches 1. */
    private static int ticks(double progressPerTick) {
        return (int) Math.ceil(1.0 / progressPerTick - 1.0e-9);
    }

    @Test
    void modernAuroraRateIsTenTicks() {
        float speed = AuroraBreakFeature.floorSpeed(2.0F, false, AuroraBreakFeature.FLOOR);
        assertEquals(20.0F, speed, EPS);
        assertEquals(10, ticks(progress(speed, 2.0F, false)));
    }

    @Test
    void modernArcticFurRateIsFiveTicks() {
        // BlockTFCompressed(ARCTIC_FUR) is hardness 0.8F.
        float speed = AuroraBreakFeature.floorSpeed(0.8F, false, ArcticFurBreakFeature.FLOOR);
        assertEquals(16.0F, speed, 1.0e-2F);
        assertEquals(5, ticks(progress(speed, 0.8F, false)));
    }

    @Test
    void canHarvestUsesTheThirtyDivisor() {
        float speed = AuroraBreakFeature.floorSpeed(2.0F, true, AuroraBreakFeature.FLOOR);
        assertEquals(6.0F, speed, EPS);
        assertEquals(10, ticks(progress(speed, 2.0F, true)));
    }

    @Test
    void theFloorIsExactForAnyHardness() {
        for (float hardness = 0.5F; hardness <= 10.0F; hardness += 0.5F) {
            assertEquals(
                AuroraBreakFeature.FLOOR,
                progress(AuroraBreakFeature.floorSpeed(hardness, false, AuroraBreakFeature.FLOOR), hardness, false),
                EPS);
            assertEquals(
                ArcticFurBreakFeature.FLOOR,
                progress(AuroraBreakFeature.floorSpeed(hardness, false, ArcticFurBreakFeature.FLOOR), hardness, false),
                EPS);
        }
    }
}
