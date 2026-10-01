package com.jointspaceforce.patchee.features;

/**
 * The pure decision behind the F7 light-overlay tweak: which light level still
 * gets an X in the current dimension.
 *
 * <p>
 * NEI draws an X where a block's light value is <b>below</b> a threshold, and
 * its threshold is the hardcoded comparison constant {@code 8} (block light
 * {@code >= 8} and sky light {@code >= 8} both mean "no marker"). The owner
 * speaks in the level the X should appear <b>at</b>, so this class exposes the
 * owner's number {@code N} and converts it with {@code N + 1}: N = 0 ⇒ 1
 * (X only in pitch dark), N = 7 ⇒ 8 (NEI's own default).
 *
 * <p>
 * This class has no Minecraft or Patchee dependencies on purpose, so the
 * decision can be unit-tested without a running game.
 */
public final class LightOverlayThreshold {

    /** The Nether's dimension id. */
    public static final int NETHER_DIMENSION = -1;

    /** NEI's stock comparison constant, used whenever the tweak is off. */
    public static final int NEI_DEFAULT_CONSTANT = 8;

    private LightOverlayThreshold() {}

    /**
     * The comparison constant the F7 overlay should use right now.
     *
     * @param tweakActive    true only when the master switch and this feature's
     *                       own switch are both on; false reproduces NEI exactly.
     * @param dimensionId    the dimension the overlay is drawing (client's own world).
     * @param maxLightNormal the owner's max light for every dimension but the Nether.
     * @param maxLightNether the owner's max light for the Nether.
     * @return the value to compare light against — {@code maxLight + 1}, or
     *         {@link #NEI_DEFAULT_CONSTANT} when the tweak is off.
     */
    public static int constant(boolean tweakActive, int dimensionId, int maxLightNormal, int maxLightNether) {
        if (!tweakActive) {
            return NEI_DEFAULT_CONSTANT;
        }
        int maxLight = dimensionId == NETHER_DIMENSION ? maxLightNether : maxLightNormal;
        return maxLight + 1;
    }
}
