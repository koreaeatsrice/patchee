package com.jointspaceforce.patchee.features;

/**
 * The pure decision behind the F7 light-overlay tweak: which light level still
 * gets an X.
 *
 * <p>
 * NEI draws an X where a block's light value is <b>below</b> a comparison
 * constant, and its constant is the hardcoded {@code 8} (light 0-7 get an X).
 * The owner speaks in the level the X should appear <b>at</b>, so this class
 * exposes the owner's number {@code N} and converts it with {@code N + 1}:
 * N = 0 &rArr; 1 (X only in pitch dark), N = 7 &rArr; 8 (NEI's own default).
 *
 * <p>
 * Values are clamped to the light range Minecraft actually has (0-15), so a
 * typo in the config can never produce a constant that marks nothing at all or
 * marks every block.
 *
 * <p>
 * This class has no Minecraft or Patchee dependencies on purpose, so the
 * decision can be unit-tested without a running game.
 */
public final class LightOverlayThreshold {

    /** NEI's stock comparison constant. */
    public static final int NEI_DEFAULT_CONSTANT = 8;

    /** The lowest light level Minecraft has. */
    public static final int MIN_LIGHT = 0;

    /** The highest light level Minecraft has. */
    public static final int MAX_LIGHT = 15;

    private LightOverlayThreshold() {}

    /** {@code level} forced into the range Minecraft can actually report. */
    public static int clampMaxLight(int level) {
        if (level < MIN_LIGHT) {
            return MIN_LIGHT;
        }
        if (level > MAX_LIGHT) {
            return MAX_LIGHT;
        }
        return level;
    }

    /**
     * The comparison constant NEI should use for the owner's light level {@code N}.
     *
     * @param maxLight the highest light level that should still get an X
     * @return {@code N + 1}, clamped to the light range
     */
    public static int constantFor(int maxLight) {
        return clampMaxLight(maxLight) + 1;
    }
}
