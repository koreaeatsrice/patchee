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

    @Test
    void keepsAByteThatIsNotValidUtf8() throws IOException {
        Path dir = Files.createTempDirectory("patchee-pollution-latin1");
        Path cfg = dir.resolve("Pollution.cfg");
        byte[] original = latin1Fixture();
        Files.write(cfg, original);

        PollutionCfgWriter.Result result = PollutionCfgWriter.rewrite(cfg.toFile(), PollutionCfg.SERVER_KEYS, STAMP);

        assertEquals(PollutionCfgWriter.Status.WRITTEN, result.status);
        assertEquals(5, result.changedKeys.size());
        byte[] rewritten = Files.readAllBytes(cfg);
        assertTrue(indexOf(rewritten, new byte[] { (byte) 0xE9 }, 0) >= 0, "the 0xE9 byte must survive the rewrite");
        assertEquals(
            -1,
            indexOf(rewritten, new byte[] { (byte) 0xEF, (byte) 0xBF, (byte) 0xBD }, 0),
            "the 0xE9 byte must not be replaced by the UTF-8 replacement character");
    }

    @Test
    void failsSoftWhenTheBackupPathCannotBeWritten() throws IOException {
        Path dir = Files.createTempDirectory("patchee-pollution-backup-blocked");
        Path cfg = dir.resolve("Pollution.cfg");
        byte[] original = Files.readAllBytes(FIXTURES.resolve("Pollution.before.cfg"));
        Files.write(cfg, original);
        // A non-empty directory where the .bak must go, so Files.copy cannot replace it.
        Path blocked = Files.createDirectory(dir.resolve("Pollution.cfg." + STAMP + ".bak"));
        Files.write(blocked.resolve("keep"), new byte[] { 1 });

        PollutionCfgWriter.Result result = PollutionCfgWriter.rewrite(cfg.toFile(), PollutionCfg.SERVER_KEYS, STAMP);

        assertEquals(PollutionCfgWriter.Status.FAILED, result.status);
        assertFalse(result.message.isEmpty());
        assertEquals("", result.backupPath);
        assertArrayEquals(original, Files.readAllBytes(cfg), "the original file must be left untouched");
    }

    /** A Pollution.cfg with the five keys wrong and a comment holding a lone 0xE9 byte. */
    private static byte[] latin1Fixture() {
        String head = "# Gr";
        String tail = "sse und Kommentar\n" + "pollution {\n"
            + "    B:\"Activate Pollution\"=false\n"
            + "    I:pollutionSmogLimit=550000\n"
            + "    I:pollutionPoisonLimit=550000\n"
            + "    I:pollutionVegetationLimit=550000\n"
            + "    I:pollutionSourRainLimit=550000\n"
            + "}\n";
        byte[] before = head.getBytes(StandardCharsets.ISO_8859_1);
        byte[] after = tail.getBytes(StandardCharsets.ISO_8859_1);
        byte[] all = new byte[before.length + 1 + after.length];
        System.arraycopy(before, 0, all, 0, before.length);
        all[before.length] = (byte) 0xE9;
        System.arraycopy(after, 0, all, before.length + 1, after.length);
        return all;
    }

    /** The first index of {@code needle} in {@code haystack} at or after {@code from}, or {@code -1}. */
    private static int indexOf(byte[] haystack, byte[] needle, int from) {
        for (int i = from; i <= haystack.length - needle.length; i++) {
            boolean hit = true;
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    hit = false;
                    break;
                }
            }
            if (hit) {
                return i;
            }
        }
        return -1;
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
