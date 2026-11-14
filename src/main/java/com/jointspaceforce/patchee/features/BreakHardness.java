package com.jointspaceforce.patchee.features;

/**
 * The hardness a break-speed floor is computed from, together with the one fact
 * the arithmetic needs: was that hardness read <b>at the block's position</b>?
 *
 * <p>
 * The floor turns a hardness into a break speed ({@link AuroraBreakFeature#floorSpeed}),
 * and vanilla — {@code Block.getPlayerRelativeBlockHardness} through
 * {@code ForgeHooks.blockStrength} — reads the hardness at the position being
 * mined. A block-wrapper mod breaks that assumption: it re-exposes a foreign
 * block at a position of its own, but reports the wrapped block to the break
 * event with no position to look at. Forge Multipart answers {@code (0,-1,0)}
 * for a microblock ({@code BlockMicroMaterial.scala:133-141}:
 * {@code player.getBreakSpeed(block, false, meta % 16, 0, -1, 0)}), because a
 * microblock has no block position of its own. Passing that coordinate to
 * {@code getBlockHardness} would read the
 * world at {@code 0,-1,0} — a different block, or none.
 *
 * <p>
 * So a hardness read at a real position ({@link #atPosition}) and the block's own
 * constant hardness ({@link #withoutPosition}) are different facts, and this type
 * keeps them apart instead of letting a caller notice the {@code -1} itself. The
 * block's own constant hardness is the honest substitute: it is what the block
 * answers wherever it is asked without a world, and it is exactly the number the
 * vanilla formula divides by.
 *
 * <p>
 * {@code 0} or a negative hardness means unbreakable or unknown — the floor does
 * nothing then ({@link #readable()} is false), because a floor computed from it
 * would be a guess.
 */
final class BreakHardness {

    private final boolean positioned;
    private final float value;

    private BreakHardness(boolean positioned, float value) {
        this.positioned = positioned;
        this.value = value;
    }

    /** A hardness really read at {@code x,y,z} in the world. */
    static BreakHardness atPosition(float worldHardness) {
        return new BreakHardness(true, worldHardness);
    }

    /** The block's own constant hardness, when the event carried no usable position. */
    static BreakHardness withoutPosition(float blockHardness) {
        return new BreakHardness(false, blockHardness);
    }

    /** Whether a break event's {@code y} is a real position ({@code -1} is a wrapper's "none"). */
    static boolean hasPosition(int y) {
        return y >= 0;
    }

    /** The hardness the floor arithmetic divides by. */
    float value() {
        return value;
    }

    /** Whether the hardness is usable at all — {@code 0} and below mean unbreakable or unknown. */
    boolean readable() {
        return value > 0.0F;
    }

    /** Whether the hardness was read at a world position rather than taken from the block. */
    boolean positioned() {
        return positioned;
    }
}
