package com.jointspaceforce.patchee.features;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

/**
 * The F7 byte patch itself, against synthetic class bytes.
 *
 * <p>
 * The patch may only ever rewrite the operand of the two {@code bipush 8}
 * instructions that NEI's {@code getSpawnMode} uses for its light comparisons.
 * These tests pin that: exactly two operands change, everything else is
 * byte-identical, a patch that cannot be applied leaves the class untouched, and
 * running it twice does nothing the second time.
 */
class LightOverlayPatchTest {

    /** A fake class body containing the two NEI patterns, surrounded by noise. */
    private static byte[] neiLikeClass() {
        byte[] filler = { (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, (byte) 0x2A, (byte) 0x59 };
        byte[] bytes = new byte[filler.length + 3 + filler.length + 3];
        System.arraycopy(filler, 0, bytes, 0, filler.length);
        System.arraycopy(LightOverlayPatch.BLOCK_LIGHT_SITE, 0, bytes, filler.length, 3);
        System.arraycopy(filler, 0, bytes, filler.length + 3, filler.length);
        System.arraycopy(LightOverlayPatch.SKY_LIGHT_SITE, 0, bytes, filler.length + 3 + filler.length, 3);
        return bytes;
    }

    @Test
    void rewritesExactlyTheTwoLightOperands() {
        byte[] original = neiLikeClass();
        LightOverlayPatch.Result result = LightOverlayPatch.apply(original, 1);

        assertEquals(LightOverlayPatch.State.PATCHED, result.state());
        assertNotSame(original, result.bytes());

        byte[] expected = original.clone();
        int block = LightOverlayPatch.indexOf(original, LightOverlayPatch.BLOCK_LIGHT_SITE, 0);
        int sky = LightOverlayPatch.indexOf(original, LightOverlayPatch.SKY_LIGHT_SITE, 0);
        expected[block + 1] = 1;
        expected[sky + 1] = 1;
        assertArrayEquals(expected, result.bytes());

        // the input must never be modified in place
        assertArrayEquals(neiLikeClass(), original);
        // and the difference must be exactly two bytes
        int changed = 0;
        for (int i = 0; i < original.length; i++) {
            if (original[i] != result.bytes()[i]) {
                changed++;
            }
        }
        assertEquals(LightOverlayPatch.SITE_COUNT, changed);
    }

    @Test
    void honoursTheConfiguredLevel() {
        // 4 is an arbitrary non-default level: both operands must become 4.
        byte[] result = LightOverlayPatch.apply(neiLikeClass(), 4)
            .bytes();
        assertTrue(LightOverlayPatch.indexOf(result, new byte[] { (byte) 0x10, (byte) 0x04, (byte) 0xA2 }, 0) >= 0);
        assertTrue(LightOverlayPatch.indexOf(result, new byte[] { (byte) 0x10, (byte) 0x04, (byte) 0xA1 }, 0) >= 0);
        // and NEI's own constants are gone
        assertEquals(-1, LightOverlayPatch.indexOf(result, LightOverlayPatch.BLOCK_LIGHT_SITE, 0));
        assertEquals(-1, LightOverlayPatch.indexOf(result, LightOverlayPatch.SKY_LIGHT_SITE, 0));
    }

    @Test
    void runningItTwiceChangesNothing() {
        byte[] once = LightOverlayPatch.apply(neiLikeClass(), 1)
            .bytes();
        LightOverlayPatch.Result twice = LightOverlayPatch.apply(once, 1);
        assertEquals(LightOverlayPatch.State.ALREADY_PATCHED, twice.state());
        assertSame(once, twice.bytes());
    }

    @Test
    void leavesAClassWithoutBothPatternsUntouched() {
        byte[] missingSky = Arrays.copyOf(neiLikeClass(), 12);
        LightOverlayPatch.Result result = LightOverlayPatch.apply(missingSky, 1);
        assertEquals(LightOverlayPatch.State.UNEXPECTED_SHAPE, result.state());
        assertSame(missingSky, result.bytes());
    }

    @Test
    void leavesAClassWithRepeatedPatternsUntouched() {
        byte[] original = neiLikeClass();
        byte[] twice = new byte[original.length + 3];
        System.arraycopy(original, 0, twice, 0, original.length);
        System.arraycopy(LightOverlayPatch.SKY_LIGHT_SITE, 0, twice, original.length, 3);
        LightOverlayPatch.Result result = LightOverlayPatch.apply(twice, 1);
        assertEquals(LightOverlayPatch.State.UNEXPECTED_SHAPE, result.state());
        assertSame(twice, result.bytes());
    }

    @Test
    void aNullClassIsNotAPatch() {
        assertEquals(
            LightOverlayPatch.State.UNEXPECTED_SHAPE,
            LightOverlayPatch.apply(null, 1)
                .state());
    }
}
