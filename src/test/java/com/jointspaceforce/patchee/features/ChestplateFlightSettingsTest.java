package com.jointspaceforce.patchee.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The three switches the ChestplateFlight transformer reads at class-load time,
 * before Forge's config exists, straight out of {@code config/patchee.cfg}.
 *
 * <p>
 * All three default to on, a missing file or key keeps the default, and an
 * unreadable boolean reads as off — the safe direction.
 */
class ChestplateFlightSettingsTest {

    @Test
    void defaultsAreOnWhenThereIsNoFile() {
        ChestplateFlightSettings settings = ChestplateFlightSettings.parse("");
        assertTrue(settings.masterEnabled());
        assertTrue(settings.liftEnabled());
        assertTrue(settings.holdEnabled());
        assertTrue(settings.guardEnabled());
        assertFalse(settings.fromFile());
    }

    @Test
    void nullConfigTextKeepsTheDefaults() {
        ChestplateFlightSettings settings = ChestplateFlightSettings.parse(null);
        assertTrue(settings.liftEnabled());
        assertTrue(settings.holdEnabled());
        assertTrue(settings.guardEnabled());
    }

    @Test
    void readsTheKeysForgeWrites() {
        ChestplateFlightSettings settings = ChestplateFlightSettings.parse(
            "general {\n" + "    B:enabled=true\n"
                + "    B:enableChestplateHoverLift=false\n"
                + "    B:enableChestplateHoldAltitude=true\n"
                + "    B:enableChestplateGroundGuard=true\n"
                + "}\n");
        assertTrue(settings.masterEnabled());
        assertFalse(settings.liftEnabled());
        assertTrue(settings.holdEnabled());
        assertTrue(settings.guardEnabled());
    }

    @Test
    void theThreeSwitchesAreReadSeparately() {
        ChestplateFlightSettings liftOff = ChestplateFlightSettings.parse("B:enableChestplateHoverLift=false");
        assertFalse(liftOff.liftEnabled());
        assertTrue(liftOff.holdEnabled(), "turning one off must not turn another off");
        assertTrue(liftOff.guardEnabled(), "turning one off must not turn another off");

        ChestplateFlightSettings holdOff = ChestplateFlightSettings.parse("B:enableChestplateHoldAltitude=false");
        assertTrue(holdOff.liftEnabled());
        assertFalse(holdOff.holdEnabled());
        assertTrue(holdOff.guardEnabled());

        ChestplateFlightSettings guardOff = ChestplateFlightSettings.parse("B:enableChestplateGroundGuard=false");
        assertTrue(guardOff.liftEnabled());
        assertTrue(guardOff.holdEnabled());
        assertFalse(guardOff.guardEnabled());
    }

    @Test
    void theMasterSwitchCoversAllThree() {
        ChestplateFlightSettings settings = ChestplateFlightSettings.parse(
            "B:enabled=false\nB:enableChestplateHoverLift=true\nB:enableChestplateHoldAltitude=true\n"
                + "B:enableChestplateGroundGuard=true\n");
        assertFalse(settings.masterEnabled());
        assertTrue(settings.liftEnabled());
        assertTrue(settings.holdEnabled());
        assertTrue(settings.guardEnabled());
    }

    @Test
    void anUnreadableBooleanIsOff() {
        ChestplateFlightSettings settings = ChestplateFlightSettings.parse("B:enableChestplateHoverLift=yes please");
        assertFalse(settings.liftEnabled());
    }

    @Test
    void commentsAndUnknownKeysAreIgnored() {
        ChestplateFlightSettings settings = ChestplateFlightSettings.parse(
            "# B:enableChestplateHoverLift=false\n" + "B:enableChestplateHoldAltitude=true # keep the hold\n"
                + "B:somethingElse=false\n");
        assertTrue(settings.liftEnabled());
        assertTrue(settings.holdEnabled());
        assertTrue(settings.guardEnabled());
    }

    @Test
    void readsARealFile(@TempDir Path dir) throws IOException {
        File file = dir.resolve("patchee.cfg")
            .toFile();
        Files.write(
            file.toPath(),
            ("B:enableChestplateHoverLift=false\nB:enableChestplateHoldAltitude=false\n"
                + "B:enableChestplateGroundGuard=true\n").getBytes(StandardCharsets.UTF_8));
        ChestplateFlightSettings settings = ChestplateFlightSettings.read(file);
        assertTrue(settings.fromFile());
        assertFalse(settings.liftEnabled());
        assertFalse(settings.holdEnabled());
        assertTrue(settings.guardEnabled());
        assertEquals(file.getPath(), settings.path());
    }

    @Test
    void aMissingFileFallsBackToTheDefaults(@TempDir Path dir) {
        ChestplateFlightSettings settings = ChestplateFlightSettings.read(
            dir.resolve("nope.cfg")
                .toFile());
        assertFalse(settings.fromFile());
        assertTrue(settings.liftEnabled());
        assertTrue(settings.holdEnabled());
        assertTrue(settings.guardEnabled());
    }

    @Test
    void theKeysAreTheOnesTheConfigUses() {
        assertEquals("enableChestplateHoverLift", ChestplateFlight.LIFT_KEY);
        assertEquals("enableChestplateHoldAltitude", ChestplateFlight.HOLD_KEY);
        assertEquals("enableChestplateGroundGuard", ChestplateFlight.GUARD_KEY);

        assertEquals("ChestplateHoverLift", new ChestplateHoverLiftFeature().id());
        assertEquals("ChestplateHoldAltitude", new ChestplateHoldAltitudeFeature().id());
        assertEquals("ChestplateGroundGuard", new ChestplateGroundGuardFeature().id());

        assertEquals(ChestplateFlight.LIFT_KEY, new ChestplateHoverLiftFeature().configKey());
        assertEquals(ChestplateFlight.HOLD_KEY, new ChestplateHoldAltitudeFeature().configKey());
        assertEquals(ChestplateFlight.GUARD_KEY, new ChestplateGroundGuardFeature().configKey());

        assertTrue(new ChestplateHoverLiftFeature().defaultEnabled());
        assertTrue(new ChestplateHoldAltitudeFeature().defaultEnabled());
        assertTrue(new ChestplateGroundGuardFeature().defaultEnabled());
    }
}
