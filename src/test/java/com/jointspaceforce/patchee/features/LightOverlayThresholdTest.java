package com.jointspaceforce.patchee.features;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The pure F7 threshold decision, no game required.
 *
 * <p>
 * The owner speaks in the light level the X appears at ({@code N}); NEI's
 * comparison constant is {@code N + 1} because it marks light strictly below
 * the threshold. These tests pin that conversion and the clamp that keeps a
 * typo in the config from producing a nonsense constant.
 */
class LightOverlayThresholdTest {

    @Test
    void ownerNumberPlusOneIsTheComparisonConstant() {
        assertEquals(1, LightOverlayThreshold.constantFor(0));
        assertEquals(2, LightOverlayThreshold.constantFor(1));
        assertEquals(8, LightOverlayThreshold.constantFor(7));
    }

    @Test
    void sevenReproducesNeisOwnBehaviour() {
        assertEquals(LightOverlayThreshold.NEI_DEFAULT_CONSTANT, LightOverlayThreshold.constantFor(7));
    }

    @Test
    void lowValuesClampToZero() {
        assertEquals(0, LightOverlayThreshold.clampMaxLight(-1));
        assertEquals(0, LightOverlayThreshold.clampMaxLight(Integer.MIN_VALUE));
        assertEquals(1, LightOverlayThreshold.constantFor(-1));
    }

    @Test
    void highValuesClampToMinecraftsLightRange() {
        assertEquals(15, LightOverlayThreshold.clampMaxLight(16));
        assertEquals(15, LightOverlayThreshold.clampMaxLight(Integer.MAX_VALUE));
        assertEquals(16, LightOverlayThreshold.constantFor(99));
    }

    @Test
    void everyConstantFitsInABipushOperand() {
        // The patch writes the constant into a bipush instruction: it must stay
        // a valid signed byte for every possible config value.
        for (int level = Integer.MIN_VALUE; level < Integer.MAX_VALUE; level++) {
            int constant = LightOverlayThreshold.constantFor(level);
            assertEquals(constant, (byte) constant);
            if (level > LightOverlayThreshold.MAX_LIGHT) {
                break;
            }
        }
    }
}
