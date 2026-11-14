package com.jointspaceforce.patchee.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The place a floor notice names, no game needed.
 *
 * <p>
 * A covered block normally arrives with its coordinates, so the once-per-player
 * proof line can point at the block that was floored. A wrapper that reports the
 * wrapped block with no position (Forge Multipart microblocks at {@code (0,-1,0)})
 * must not print {@code "0,-1,0"} as if that were the block's home — the line says
 * plainly that the block had no position instead.
 */
class FloorNoticesTest {

    @Test
    void realCoordinatesArePrinted() {
        assertEquals("3,64,-7", FloorNotices.site(3, 64, -7));
    }

    @Test
    void aWrapperWithNoPositionSaysSo() {
        String site = FloorNotices.site(0, -1, 0);

        assertTrue(site.contains("wrapped block"), site);
        assertFalse(site.contains("-1"), "the synthetic y is not printed as a coordinate: " + site);
    }
}
