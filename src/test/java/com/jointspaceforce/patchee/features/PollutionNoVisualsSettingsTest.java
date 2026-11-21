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
 * The master switch defaults to on and the feature switch defaults to off; a
 * missing file or key keeps those defaults, and the patch only runs when the
 * master switch and the feature switch are both on.
 */
class PollutionNoVisualsSettingsTest {

    @Test
    void theFeatureDefaultsToOffWhenThereIsNoFile() {
        PollutionNoVisualsSettings settings = PollutionNoVisualsSettings.parse("");
        assertTrue(settings.masterEnabled());
        assertFalse(settings.enabled());
        assertFalse(settings.fromFile());
    }

    @Test
    void nullConfigTextKeepsTheFeatureOff() {
        PollutionNoVisualsSettings settings = PollutionNoVisualsSettings.parse(null);
        assertTrue(settings.masterEnabled());
        assertFalse(settings.enabled());
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
    void aValueThatIsNotTrueOrFalseKeepsTheDefault() {
        PollutionNoVisualsSettings settings = PollutionNoVisualsSettings
            .parse("B:enabled=true\nB:enablePollutionNoVisuals=maybe\n");
        assertTrue(settings.masterEnabled());
        assertFalse(settings.enabled(), "a typo must not switch the feature on");

        PollutionNoVisualsSettings masterTypo = PollutionNoVisualsSettings
            .parse("B:enabled=yes\nB:enablePollutionNoVisuals=true\n");
        assertTrue(masterTypo.masterEnabled(), "a typo must not switch the master off");
        assertTrue(masterTypo.enabled());

        PollutionNoVisualsSettings typoWhenOn = PollutionNoVisualsSettings
            .parse("B:enabled=true\nB:enablePollutionNoVisuals=true\nB:enablePollutionNoVisuals=maybe\n");
        assertTrue(typoWhenOn.enabled(), "a typo must not switch the feature off either");
    }

    @Test
    void trueAndFalseAreHonouredInAnyCase() {
        PollutionNoVisualsSettings off = PollutionNoVisualsSettings
            .parse("B:enabled=True\nB:enablePollutionNoVisuals=FALSE\n");
        assertTrue(off.masterEnabled());
        assertFalse(off.enabled());

        PollutionNoVisualsSettings on = PollutionNoVisualsSettings
            .parse("B:enabled=TRUE\nB:enablePollutionNoVisuals=false\n");
        assertTrue(on.masterEnabled());
        assertFalse(on.enabled());
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
