package com.jointspaceforce.patchee.features;

/**
 * The F7 light-overlay byte patch: NEI's two hardcoded comparison constants,
 * rewritten in place.
 *
 * <p>
 * Inside {@code codechicken.nei.WorldOverlayRenderer.getSpawnMode} NEI compares
 * light against a hardcoded literal {@code 8} twice:
 *
 * <ul>
 * <li>{@code bipush 8} followed by {@code if_icmpge} — "block light is 8 or
 * more, so no X";</li>
 * <li>{@code bipush 8} followed by {@code if_icmplt} — the sky-light check that
 * picks the colour.</li>
 * </ul>
 *
 * <p>
 * This class replaces the operand of those two {@code bipush} instructions and
 * touches nothing else: no call is injected, no method is added, no branch
 * offset moves. The result differs from NEI's own class bytes by exactly two
 * operands, so the class still verifies — it is the same change a person makes
 * by hand with a hex editor, and the same change patchee's class transformer
 * applies while NEI loads.
 *
 * <p>
 * It is deliberately suspicious: both patterns must occur exactly once, an
 * already-patched class is recognised and reported as such, and any other shape
 * leaves the bytes completely untouched. Pure byte arrays in, byte arrays out:
 * no game needed to test it.
 */
public final class LightOverlayPatch {

    /** {@code bipush 8; if_icmpge} — NEI's block-light gate. */
    static final byte[] BLOCK_LIGHT_SITE = { (byte) 0x10, (byte) 0x08, (byte) 0xA2 };

    /** {@code bipush 8; if_icmplt} — NEI's sky-light colour check. */
    static final byte[] SKY_LIGHT_SITE = { (byte) 0x10, (byte) 0x08, (byte) 0xA1 };

    /** How many sites a healthy NEI class has. */
    public static final int SITE_COUNT = 2;

    /** What {@link #apply} found and did. */
    public enum State {

        /** Both sites rewritten; {@link Result#bytes()} is the patched class. */
        PATCHED,

        /** The class already carries this exact patch; bytes are unchanged. */
        ALREADY_PATCHED,

        /** NEI's class is not the shape this patch knows; bytes are unchanged. */
        UNEXPECTED_SHAPE
    }

    /** The outcome of one {@link #apply} call. */
    public static final class Result {

        private final State state;
        private final byte[] bytes;

        Result(State state, byte[] bytes) {
            this.state = state;
            this.bytes = bytes;
        }

        public State state() {
            return state;
        }

        /** The class bytes to hand back to the loader (never null). */
        public byte[] bytes() {
            return bytes;
        }
    }

    private LightOverlayPatch() {}

    /**
     * Rewrite NEI's two comparison constants to {@code constant}, or leave the
     * class alone and say why.
     *
     * @param original NEI's class bytes as the loader sees them
     * @param constant the comparison constant NEI should use
     */
    public static Result apply(byte[] original, int constant) {
        if (original == null) {
            return new Result(State.UNEXPECTED_SHAPE, new byte[0]);
        }
        byte operand = (byte) constant;
        int block = indexOf(original, BLOCK_LIGHT_SITE, 0);
        int sky = indexOf(original, SKY_LIGHT_SITE, 0);
        boolean oneOfEach = block >= 0 && sky >= 0
            && indexOf(original, BLOCK_LIGHT_SITE, block + 1) < 0
            && indexOf(original, SKY_LIGHT_SITE, sky + 1) < 0;
        if (oneOfEach) {
            byte[] patched = original.clone();
            patched[block + 1] = operand;
            patched[sky + 1] = operand;
            return new Result(State.PATCHED, patched);
        }
        byte[] patchedBlock = { (byte) 0x10, operand, (byte) 0xA2 };
        byte[] patchedSky = { (byte) 0x10, operand, (byte) 0xA1 };
        boolean alreadyPatched = indexOf(original, patchedBlock, 0) >= 0 && indexOf(original, patchedSky, 0) >= 0;
        return new Result(alreadyPatched ? State.ALREADY_PATCHED : State.UNEXPECTED_SHAPE, original);
    }

    /** The first occurrence of {@code needle} in {@code haystack} at or after {@code from}, or {@code -1}. */
    static int indexOf(byte[] haystack, byte[] needle, int from) {
        if (haystack == null || needle.length == 0) {
            return -1;
        }
        outer: for (int i = Math.max(from, 0); i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
