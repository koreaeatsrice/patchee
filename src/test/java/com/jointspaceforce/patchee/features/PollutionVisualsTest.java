package com.jointspaceforce.patchee.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The pure half of the client-side pollution feature: the switch defaults, the
 * decision truth table and the field names the guard reflects on. No game
 * classes are touched.
 */
class PollutionVisualsTest {

    @Test
    void defaultsAndKeysAreStable() {
        assertTrue(PollutionVisuals.DEFAULT_ENABLED);
        assertEquals("PollutionVisuals", PollutionVisuals.ID);
        assertEquals("enablePollutionNoVisuals", PollutionVisuals.ENABLE_KEY);
        assertEquals(PollutionCfg.CLIENT_CFG, PollutionVisuals.CLIENT_CFG);
        assertEquals("GregTech/Client.cfg", PollutionVisuals.CLIENT_CFG);
    }

    @Test
    void everyDecisionRowIsRight() {
        PollutionVisuals.Decision run = PollutionVisuals.Decision.RUN;
        PollutionVisuals.Decision off = PollutionVisuals.Decision.DISABLED;
        PollutionVisuals.Decision server = PollutionVisuals.Decision.SERVER;

        assertSame(run, PollutionVisuals.decide(true, true, true));
        assertSame(server, PollutionVisuals.decide(true, true, false));
        assertSame(off, PollutionVisuals.decide(true, false, true));
        assertSame(off, PollutionVisuals.decide(true, false, false));
        assertSame(off, PollutionVisuals.decide(false, true, true));
        assertSame(off, PollutionVisuals.decide(false, true, false));
        assertSame(off, PollutionVisuals.decide(false, false, true));
        assertSame(off, PollutionVisuals.decide(false, false, false));
    }

    @Test
    void flagLabelsAreTheClientKeysInOrder() {
        List<String> names = new ArrayList<String>();
        for (PollutionCfg.Key key : PollutionCfg.CLIENT_KEYS) {
            names.add(key.name);
        }
        assertEquals(names, PollutionVisuals.FLAG_LABELS);
        assertEquals(Arrays.asList("Pollution Fog", "Pollution Dirt"), PollutionVisuals.FLAG_LABELS);
    }

    @Test
    void reflectionFieldListsLineUp() {
        assertEquals(PollutionVisuals.FLAG_LABELS.size(), PollutionVisuals.RENDER_FIELDS.size());
        assertEquals(PollutionVisuals.RENDER_FIELDS.size(), PollutionVisuals.PROXY_FIELDS.size());
        assertEquals(Arrays.asList("renderPollutionFog", "renderDirtParticles"), PollutionVisuals.RENDER_FIELDS);
        assertEquals(Arrays.asList("mRenderPollutionFog", "mRenderDirtParticles"), PollutionVisuals.PROXY_FIELDS);
        assertEquals(
            Arrays.asList("standardBlocks", "liquidBlocks", "doublePlants", "crossedSquares", "blockVine"),
            PollutionVisuals.MATCHER_FIELDS);
    }
}
