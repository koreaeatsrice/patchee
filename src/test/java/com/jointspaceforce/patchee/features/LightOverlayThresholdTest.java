package com.jointspaceforce.patchee.features;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The pure F7 threshold decision, no game required.
 *
 * <p>
 * The owner speaks in the light level the X appears at ({@code N}); NEI's
 * comparison constant is {@code N + 1} because it marks light strictly below
 * the threshold. These tests pin that conversion, the per-dimension split and
 * the off switch (which must reproduce NEI's own {@code 8}).
 */
class LightOverlayThresholdTest {

    private static final int NORMAL = 0;
    private static final int NETHER = -1;
    private static final int NEI_DEFAULT = 8;

    @Test
    void normalDimensionDrawsXOnlyAtLightZero() {
        assertEquals(1, LightOverlayThreshold.constant(true, NORMAL, 0, 7));
    }

    @Test
    void netherKeepsNeiBehaviourAtSeven() {
        assertEquals(NEI_DEFAULT, LightOverlayThreshold.constant(true, NETHER, 0, 7));
    }

    @Test
    void disabledAlwaysReproducesNei() {
        assertEquals(NEI_DEFAULT, LightOverlayThreshold.constant(false, NORMAL, 0, 7));
        assertEquals(NEI_DEFAULT, LightOverlayThreshold.constant(false, NETHER, 0, 7));
        // even nonsense config values must not leak through when the tweak is off
        assertEquals(NEI_DEFAULT, LightOverlayThreshold.constant(false, NORMAL, 15, 15));
    }

    @Test
    void moddedDimensionsBehaveLikeNormal() {
        assertEquals(1, LightOverlayThreshold.constant(true, 7, 0, 7));
        assertEquals(1, LightOverlayThreshold.constant(true, 95, 0, 7));
        // the Nether value must not leak into other dimensions
        assertEquals(1, LightOverlayThreshold.constant(true, 0, 0, 15));
    }

    @Test
    void netherIgnoresTheNormalValue() {
        assertEquals(4, LightOverlayThreshold.constant(true, NETHER, 0, 3));
        assertEquals(1, LightOverlayThreshold.constant(true, NETHER, 15, 0));
    }

    @Test
    void conversionIsOwnerNumberPlusOne() {
        assertEquals(2, LightOverlayThreshold.constant(true, NORMAL, 1, 7));
        assertEquals(9, LightOverlayThreshold.constant(true, NORMAL, 8, 7));
    }
}
