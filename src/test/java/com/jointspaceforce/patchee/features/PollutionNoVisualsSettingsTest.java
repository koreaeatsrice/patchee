package com.jointspaceforce.patchee.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The two switches the PollutionNoVisuals transformer reads at class-load time,
 * before Forge's config exists, straight out of {@code config/patchee.cfg}; and
 * the pure decision both the transformer and the test share.
 *
 * <p>
 * Both default to on, a missing file or key keeps the default, and the patch
 * only runs when the master switch and the feature switch are both on.
 */
class PollutionNoVisualsSettingsTest {

    @Test
    void defaultsAreOnWhenThereIsNoFile() {
        PollutionNoVisualsSettings settings = PollutionNoVisualsSettings.parse("");
        assertTrue(settings.masterEnabled());
        assertTrue(settings.enabled());
        assertFalse(settings.fromFile());
    }

    @Test
    void nullConfigTextKeepsTheDefaults() {
        PollutionNoVisualsSettings settings = PollutionNoVisualsSettings.parse(null);
        assertTrue(settings.masterEnabled());
        assertTrue(settings.enabled());
    }

    @Test
    void readsTheKeysForgeWrites() {
        PollutionNoVisualsSettings settings = PollutionNoVisualsSettings
            .parse("general {\n" + "    B:enabled=true\n" + "    B:enablePollutionNoVisuals=false\n" + "}\n");
        assertTrue(settings.masterEnabled());
        assertFalse(settings.enabled());
    }

    @Test
    void theMasterSwitchTurnsEverythingOff() {
        PollutionNoVisualsSettings settings = PollutionNoVisualsSettings
            .parse("B:enabled=false\nB:enablePollutionNoVisuals=true\n");
        assertFalse(settings.masterEnabled());
        assertTrue(settings.enabled());
        assertFalse(PollutionNoVisuals.shouldPatch(settings.masterEnabled(), settings.enabled()));
    }

    @Test
    void commentsAndUnknownKeysAreIgnored() {
        PollutionNoVisualsSettings settings = PollutionNoVisualsSettings.parse(
            "# a comment with fake=keys\n" + "enablePollutionNoVisuals=false   # inline comment\n"
                + "I:someOtherKey=7\n");
        assertTrue(settings.masterEnabled());
        assertFalse(settings.enabled());
    }

    @Test
    void shouldPatchIsThePlainAndOfBothSwitches() {
        assertTrue(PollutionNoVisuals.shouldPatch(true, true));
        assertFalse(PollutionNoVisuals.shouldPatch(true, false));
        assertFalse(PollutionNoVisuals.shouldPatch(false, true));
        assertFalse(PollutionNoVisuals.shouldPatch(false, false));
    }

    @Test
    void theFeatureDescriptorPointsAtTheSameSwitch() {
        PollutionNoVisualsFeature feature = new PollutionNoVisualsFeature();
        assertEquals(PollutionNoVisuals.ID, feature.id());
        assertEquals(PollutionNoVisuals.ENABLE_KEY, feature.configKey());
        assertEquals(PollutionNoVisuals.DEFAULT_ENABLED, feature.defaultEnabled());
        assertEquals(PollutionNoVisualsPatch.TARGET_CLASS, feature.targetClass());
    }
}
