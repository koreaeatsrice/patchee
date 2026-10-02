package com.jointspaceforce.patchee.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The early config reader: the F7 patch is applied while NEI's class loads,
 * before Forge's config exists, so it parses config/patchee.cfg itself. These
 * tests pin the format it accepts and the defaults it falls back to, with no
 * file system involved.
 */
class LightOverlaySettingsTest {

    @Test
    void readsTheFileForgeWrites() {
        String config = "# Configuration file\n" + "\n"
            + "general {\n"
            + "    B:enableDollyFix=true\n"
            + "    B:enableLightOverlayTweak=true\n"
            + "    I:lightOverlay.maxLightNormal=0\n"
            + "}\n";
        LightOverlaySettings settings = LightOverlaySettings.parse(config);
        assertTrue(settings.enabled());
        assertEquals(0, settings.maxLight());
    }

    @Test
    void readsTheSwitchBeingOff() {
        LightOverlaySettings settings = LightOverlaySettings
            .parse("general {\n    B:enableLightOverlayTweak=false\n    I:lightOverlay.maxLightNormal=0\n}");
        assertFalse(settings.enabled());
    }

    @Test
    void readsAChangedLightLevel() {
        LightOverlaySettings settings = LightOverlaySettings
            .parse("general {\n    B:enableLightOverlayTweak=true\n    I:lightOverlay.maxLightNormal=7\n}");
        assertEquals(7, settings.maxLight());
        assertEquals(
            LightOverlayThreshold.NEI_DEFAULT_CONSTANT,
            LightOverlayThreshold.constantFor(settings.maxLight()));
    }

    @Test
    void fallsBackToTheDefaultsWhenKeysAreMissing() {
        LightOverlaySettings settings = LightOverlaySettings.parse("general {\n    B:enableVeinConfig=true\n}");
        assertTrue(settings.enabled());
        assertEquals(LightOverlaySettings.DEFAULT_MAX_LIGHT, settings.maxLight());
    }

    @Test
    void ignoresCommentsBlanksAndUnreadableValues() {
        String config = "# comment about 0\n" + "   \n"
            + "general {\n"
            + "    B:enableLightOverlayTweak=true # trailing comment\n"
            + "    I:lightOverlay.maxLightNormal=not-a-number\n"
            + "}\n";
        LightOverlaySettings settings = LightOverlaySettings.parse(config);
        assertTrue(settings.enabled());
        assertEquals(LightOverlaySettings.DEFAULT_MAX_LIGHT, settings.maxLight());
    }

    @Test
    void acceptsKeysWithoutForgeTypePrefixes() {
        LightOverlaySettings settings = LightOverlaySettings
            .parse("enableLightOverlayTweak=false\nlightOverlay.maxLightNormal=3\n");
        assertFalse(settings.enabled());
        assertEquals(3, settings.maxLight());
    }

    @Test
    void nullTextIsTheDefaults() {
        LightOverlaySettings settings = LightOverlaySettings.parse(null);
        assertTrue(settings.enabled());
        assertEquals(LightOverlaySettings.DEFAULT_MAX_LIGHT, settings.maxLight());
    }
}
