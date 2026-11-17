package com.jointspaceforce.patchee.features;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The fail-soft file writer: a second boot touches nothing, a missing file stays
 * missing, an unreadable target reports instead of throwing, and a real write
 * keeps the original bytes in a timestamped {@code .bak} beside the file.
 */
class PollutionCfgWriterTest {

    private static final Path FIXTURES = Paths.get("src", "test", "resources", "pollution");

    private static final String STAMP = "20260101T000000Z";

    @Test
    void alreadyCorrectFileIsNotRewrittenAndNoBackupIsMade() throws IOException {
        Path dir = Files.createTempDirectory("patchee-pollution-correct");
        Path cfg = dir.resolve("Pollution.cfg");
        byte[] correct = Files.readAllBytes(FIXTURES.resolve("Pollution.after.cfg"));
        Files.write(cfg, correct);

        PollutionCfgWriter.Result result = PollutionCfgWriter.rewrite(cfg.toFile(), PollutionCfg.SERVER_KEYS, STAMP);

        assertEquals(PollutionCfgWriter.Status.ALREADY_CORRECT, result.status);
        assertArrayEquals(correct, Files.readAllBytes(cfg));
        assertEquals(0, backups(dir).size(), "no backup may be made when nothing changed");
        assertTrue(result.changedKeys.isEmpty());
        assertEquals("", result.backupPath);
    }

    @Test
    void missingKeyOnlyMakesNoWrite() throws IOException {
        Path dir = Files.createTempDirectory("patchee-pollution-missing-key");
        Path cfg = dir.resolve("Pollution.cfg");
        String after = new String(Files.readAllBytes(FIXTURES.resolve("Pollution.after.cfg")), StandardCharsets.UTF_8)
            .replace("    I:pollutionPoisonLimit=2147483647\n", "");
        byte[] original = after.getBytes(StandardCharsets.UTF_8);
        Files.write(cfg, original);

        PollutionCfgWriter.Result result = PollutionCfgWriter.rewrite(cfg.toFile(), PollutionCfg.SERVER_KEYS, STAMP);

        assertEquals(PollutionCfgWriter.Status.ALREADY_CORRECT, result.status);
        assertTrue(result.changedKeys.isEmpty(), result.changedKeys.toString());
        assertTrue(result.missingKeys.contains("pollutionPoisonLimit"), result.missingKeys.toString());
        assertEquals("", result.backupPath);
        assertArrayEquals(original, Files.readAllBytes(cfg));
        assertEquals(0, backups(dir).size(), "no backup may be made when no value needed changing");
    }

    @Test
    void missingFileIsLeftAlone() throws IOException {
        Path dir = Files.createTempDirectory("patchee-pollution-missing");
        Path cfg = dir.resolve("Pollution.cfg");

        PollutionCfgWriter.Result result = PollutionCfgWriter.rewrite(cfg.toFile(), PollutionCfg.SERVER_KEYS, STAMP);

        assertEquals(PollutionCfgWriter.Status.MISSING_FILE, result.status);
        assertFalse(Files.exists(cfg), "a missing file must not be created");
        assertEquals(0, backups(dir).size());
    }

    @Test
    void ioFailureFailsSoft() throws IOException {
        Path dir = Files.createTempDirectory("patchee-pollution-dir");
        Path notAFile = Files.createDirectory(dir.resolve("Pollution.cfg"));

        PollutionCfgWriter.Result result = PollutionCfgWriter
            .rewrite(notAFile.toFile(), PollutionCfg.SERVER_KEYS, STAMP);

        assertEquals(PollutionCfgWriter.Status.FAILED, result.status);
        assertFalse(result.message.isEmpty());
        assertTrue(Files.isDirectory(notAFile), "the target must be left exactly as it was");
    }

    @Test
    void writeBacksUpFirstAndReplacesTheFile() throws IOException {
        Path dir = Files.createTempDirectory("patchee-pollution-write");
        Path cfg = dir.resolve("Pollution.cfg");
        byte[] original = Files.readAllBytes(FIXTURES.resolve("Pollution.before.cfg"));
        byte[] expected = Files.readAllBytes(FIXTURES.resolve("Pollution.after.cfg"));
        Files.write(cfg, original);

        PollutionCfgWriter.Result result = PollutionCfgWriter.rewrite(cfg.toFile(), PollutionCfg.SERVER_KEYS, STAMP);

        assertEquals(PollutionCfgWriter.Status.WRITTEN, result.status);
        assertEquals(5, result.changedKeys.size());
        assertTrue(result.missingKeys.isEmpty());
        Path backup = dir.resolve("Pollution.cfg." + STAMP + ".bak");
        assertEquals(backup, Paths.get(result.backupPath));

        assertArrayEquals(original, Files.readAllBytes(backup));
        assertArrayEquals(expected, Files.readAllBytes(cfg));
        assertFalse(Files.exists(dir.resolve("Pollution.cfg.tmp")), "the temp file must be gone");
    }

    @Test
    void secondBootIsIdempotent() throws IOException {
        Path dir = Files.createTempDirectory("patchee-pollution-second");
        Path cfg = dir.resolve("Pollution.cfg");
        Files.write(cfg, Files.readAllBytes(FIXTURES.resolve("Pollution.before.cfg")));

        assertEquals(
            PollutionCfgWriter.Status.WRITTEN,
            PollutionCfgWriter.rewrite(cfg.toFile(), PollutionCfg.SERVER_KEYS, STAMP).status);

        PollutionCfgWriter.Result second = PollutionCfgWriter.rewrite(cfg.toFile(), PollutionCfg.SERVER_KEYS, STAMP);
        assertEquals(PollutionCfgWriter.Status.ALREADY_CORRECT, second.status);
        assertEquals(1, backups(dir).size(), "the first backup stays, the second boot adds none");
    }

    /** The {@code .bak} files in a directory. */
    private static List<Path> backups(Path dir) {
        List<Path> found = new ArrayList<Path>();
        File[] files = dir.toFile()
            .listFiles();
        if (files == null) {
            return found;
        }
        for (File file : files) {
            if (file.getName()
                .endsWith(".bak")) {
                found.add(file.toPath());
            }
        }
        return found;
    }
}
