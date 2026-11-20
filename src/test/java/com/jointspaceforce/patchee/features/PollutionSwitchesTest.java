package com.jointspaceforce.patchee.features;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.junit.jupiter.api.Test;

/**
 * The two switches the coremod reads at load time, before Forge's config
 * exists, straight out of {@code config/patchee.cfg}. Both default to on, and a
 * missing file or key keeps the default.
 */
class PollutionSwitchesTest {

    @Test
    void missingTextUsesDefaults() {
        PollutionSwitches switches = PollutionSwitches.parse("");

        assertTrue(switches.masterEnabled());
        assertTrue(switches.featureEnabled());
        assertFalse(switches.fromFile());
    }

    @Test
    void nullTextUsesDefaults() {
        PollutionSwitches switches = PollutionSwitches.parse(null);

        assertTrue(switches.masterEnabled());
        assertTrue(switches.featureEnabled());
    }

    @Test
    void masterOff() {
        PollutionSwitches switches = PollutionSwitches
            .parse("general {\n" + "    B:enabled=false\n" + "    B:enablePollutionNoNegatives=true\n" + "}\n");

        assertFalse(switches.masterEnabled());
        assertTrue(switches.featureEnabled());
    }

    @Test
    void featureOff() {
        PollutionSwitches switches = PollutionSwitches
            .parse("general {\n" + "    B:enabled=true\n" + "    B:enablePollutionNoNegatives=false\n" + "}\n");

        assertTrue(switches.masterEnabled());
        assertFalse(switches.featureEnabled());
    }

    @Test
    void commentsAndUnknownKeysIgnored() {
        PollutionSwitches switches = PollutionSwitches.parse(
            "# enabled=false would be a comment\n" + "B:enabled=true\n"
                + "B:somethingElse=false\n"
                + "   # B:enablePollutionNoNegatives=false\n"
                + "B:enablePollutionNoNegatives=true\n");

        assertTrue(switches.masterEnabled());
        assertTrue(switches.featureEnabled());
    }

    @Test
    void missingFileUsesDefaults() {
        PollutionSwitches switches = PollutionSwitches.read(new File("does/not/exist/patchee.cfg"));

        assertTrue(switches.masterEnabled());
        assertTrue(switches.featureEnabled());
        assertFalse(switches.fromFile());
    }
}
