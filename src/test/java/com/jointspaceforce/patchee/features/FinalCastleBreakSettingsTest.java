package com.jointspaceforce.patchee.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The early config reader: the FinalCastleBreak patch is applied while Twilight
 * Forest's classes load, before Forge's config exists, so it parses
 * config/patchee.cfg itself. These tests pin the format it accepts and the
 * defaults it falls back to, with no file system involved.
 */
class FinalCastleBreakSettingsTest {

    @Test
    void readsTheFileForgeWrites() {
        String config = "# Configuration file\n" + "\n"
            + "general {\n"
            + "    B:enabled=true\n"
            + "    B:enableFinalCastleBreak=true\n"
            + "}\n";
        FinalCastleBreakSettings settings = FinalCastleBreakSettings.parse(config);
        assertTrue(settings.masterEnabled());
        assertTrue(settings.enabled());
    }

    @Test
    void readsTheSwitchBeingOff() {
        FinalCastleBreakSettings settings = FinalCastleBreakSettings
            .parse("general {\n    B:enableFinalCastleBreak=false\n}\n");
        assertTrue(settings.masterEnabled());
        assertFalse(settings.enabled());
    }

    @Test
    void readsTheMasterSwitchBeingOff() {
        FinalCastleBreakSettings settings = FinalCastleBreakSettings
            .parse("general {\n    B:enabled=false\n    B:enableFinalCastleBreak=true\n}\n");
        assertFalse(settings.masterEnabled());
        assertTrue(settings.enabled());
    }

    @Test
    void fallsBackToTheDefaultsWhenKeysAreMissing() {
        FinalCastleBreakSettings settings = FinalCastleBreakSettings
            .parse("general {\n    B:enableAuroraBreak=true\n}\n");
        assertTrue(settings.masterEnabled());
        assertTrue(settings.enabled());
    }

    @Test
    void ignoresCommentsBlanksAndUnknownKeys() {
        String config = "# comment about true\n" + "   \n"
            + "general {\n"
            + "    B:enableFinalCastleBreak=true # trailing comment\n"
            + "    B:someOtherFeature=false\n"
            + "}\n"
            + "leading text without an equals sign\n";
        FinalCastleBreakSettings settings = FinalCastleBreakSettings.parse(config);
        assertTrue(settings.masterEnabled());
        assertTrue(settings.enabled());
    }

    @Test
    void garbageValuesFallBackToTheDefault() {
        // Boolean.parseBoolean only accepts "true" (case-insensitive); anything
        // else is false, but a missing/junk key must not lose the default. A junk
        // value for the feature switch is read as false (Forge's own behaviour).
        FinalCastleBreakSettings settings = FinalCastleBreakSettings.parse("enableFinalCastleBreak=not-a-boolean\n");
        assertFalse(settings.enabled());
    }

    @Test
    void acceptsKeysWithoutForgeTypePrefixes() {
        FinalCastleBreakSettings settings = FinalCastleBreakSettings
            .parse("enableFinalCastleBreak=false\nenabled=false\n");
        assertFalse(settings.masterEnabled());
        assertFalse(settings.enabled());
    }

    @Test
    void nullTextIsTheDefaults() {
        FinalCastleBreakSettings settings = FinalCastleBreakSettings.parse(null);
        assertTrue(settings.masterEnabled());
        assertTrue(settings.enabled());
    }

    @Test
    void readOfAMissingFileIsTheDefaultsAndNotFromFile() {
        // A path that cannot exist, so the test does not depend on whether a
        // config/patchee.cfg sits in the developer's working directory. The
        // package-private read(File) overload is the seam that makes this safe
        // under parallel tests (no global user.dir change).
        File missing = new File(
            System.getProperty("java.io.tmpdir"),
            "patchee-absent-" + UUID.randomUUID() + "/config/patchee.cfg");
        FinalCastleBreakSettings settings = FinalCastleBreakSettings.read(missing);
        assertTrue(settings.masterEnabled());
        assertEquals(FinalCastleBreak.DEFAULT_ENABLED, settings.enabled());
        assertFalse(settings.fromFile());
    }

    @Test
    void readOfARealFileIsFromFile(@TempDir Path dir) throws Exception {
        File cfg = dir.resolve("patchee.cfg")
            .toFile();
        Files
            .write(cfg.toPath(), "general {\n    B:enableFinalCastleBreak=false\n}\n".getBytes(StandardCharsets.UTF_8));
        FinalCastleBreakSettings settings = FinalCastleBreakSettings.read(cfg);
        assertTrue(settings.fromFile());
        assertTrue(settings.masterEnabled());
        assertFalse(settings.enabled());
    }
}
