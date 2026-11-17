package com.jointspaceforce.patchee.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * The line-preserving edit of {@code config/GregTech/Pollution.cfg}.
 *
 * <p>
 * The fixture pair is a realistic Forge file straight from GregTech: comments,
 * a {@code pollution} section and eight keys, five of them Patchee's. The after
 * fixture must differ from the before fixture on exactly those five lines -
 * nothing reordered, nothing re-commented, nothing dropped.
 */
class PollutionCfgTest {

    private static final Path FIXTURES = Paths.get("src", "test", "resources", "pollution");

    @Test
    void onlyFiveLinesChange() throws IOException {
        String before = fixture("Pollution.before.cfg");
        String after = fixture("Pollution.after.cfg");

        PollutionCfg.Edit edit = PollutionCfg.apply(before, PollutionCfg.SERVER_KEYS);

        assertEquals(after, edit.text);
        assertEquals(5, edit.changed);
        assertTrue(edit.missing.isEmpty(), edit.missing.toString());

        List<String> changedLines = changedLines(before, after);
        assertEquals(5, changedLines.size(), changedLines.toString());
        for (String line : changedLines) {
            String trimmed = line.trim();
            assertTrue(
                trimmed.startsWith("B:\"Activate Pollution\"=") || trimmed.startsWith("I:pollutionSmogLimit=")
                    || trimmed.startsWith("I:pollutionPoisonLimit=")
                    || trimmed.startsWith("I:pollutionVegetationLimit=")
                    || trimmed.startsWith("I:pollutionSourRainLimit="),
                "only the five target key lines may change, but this one did: " + line);
        }
    }

    @Test
    void applyIsIdempotent() throws IOException {
        String after = fixture("Pollution.after.cfg");

        PollutionCfg.Edit edit = PollutionCfg.apply(after, PollutionCfg.SERVER_KEYS);

        assertEquals(after, edit.text);
        assertEquals(0, edit.changed);
        assertFalse(edit.needsWrite());
        assertTrue(edit.alreadyCorrect());
    }

    @Test
    void missingKeyIsReported() throws IOException {
        String before = fixture("Pollution.before.cfg").replace("    I:pollutionPoisonLimit=750000\n", "");

        PollutionCfg.Edit edit = PollutionCfg.apply(before, PollutionCfg.SERVER_KEYS);

        assertEquals(4, edit.changed);
        assertEquals(1, edit.missing.size());
        assertEquals("pollutionPoisonLimit", edit.missing.get(0));
        assertFalse(edit.alreadyCorrect());
        assertTrue(edit.needsWrite());
    }

    @Test
    void alreadyCorrectFileNeedsNoWrite() throws IOException {
        PollutionCfg.Edit edit = PollutionCfg.apply(fixture("Pollution.after.cfg"), PollutionCfg.SERVER_KEYS);

        assertTrue(edit.alreadyCorrect());
        assertFalse(edit.needsWrite());
        assertEquals(0, edit.changed);
    }

    @Test
    void inlineCommentPreserved() {
        String text = "pollution {\n    I:pollutionSmogLimit=1  # keep me, please\n}\n";

        PollutionCfg.Edit edit = PollutionCfg.apply(text, PollutionCfg.SERVER_KEYS);

        assertTrue(edit.text.contains("    I:pollutionSmogLimit=2147483647  # keep me, please\n"), edit.text);
        assertEquals(1, edit.changed);
        assertEquals(4, edit.missing.size());
    }

    @Test
    void commentThatLooksLikeAKeyIsNotChanged() {
        String text = "# Configuration file\n" + "pollution {\n"
            + "    # Activate Pollution\n"
            + "    B:\"Activate Pollution\"=false\n"
            + "    # pollutionSmogLimit=550000  # old value\n"
            + "    I:pollutionSmogLimit=550000\n"
            + "    I:pollutionPoisonLimit=750000\n"
            + "    I:pollutionVegetationLimit=1000000\n"
            + "    I:pollutionSourRainLimit=2000000\n"
            + "}\n"
            + "render {\n"
            + "    # pollution {\n"
            + "    # B:\"Pollution Fog\"=true\n"
            + "    B:\"Pollution Fog\"=true\n"
            + "    # B:\"Pollution Dirt\"=true\n"
            + "    B:\"Pollution Dirt\"=true\n"
            + "    B:someOtherFlag=true\n"
            + "}\n";

        List<PollutionCfg.Key> keys = new ArrayList<PollutionCfg.Key>(PollutionCfg.SERVER_KEYS);
        keys.addAll(PollutionCfg.CLIENT_KEYS);
        PollutionCfg.Edit edit = PollutionCfg.apply(text, keys);

        String expected = "# Configuration file\n" + "pollution {\n"
            + "    # Activate Pollution\n"
            + "    B:\"Activate Pollution\"=true\n"
            + "    # pollutionSmogLimit=550000  # old value\n"
            + "    I:pollutionSmogLimit=2147483647\n"
            + "    I:pollutionPoisonLimit=2147483647\n"
            + "    I:pollutionVegetationLimit=2147483647\n"
            + "    I:pollutionSourRainLimit=2147483647\n"
            + "}\n"
            + "render {\n"
            + "    # pollution {\n"
            + "    # B:\"Pollution Fog\"=true\n"
            + "    B:\"Pollution Fog\"=false\n"
            + "    # B:\"Pollution Dirt\"=true\n"
            + "    B:\"Pollution Dirt\"=false\n"
            + "    B:someOtherFlag=true\n"
            + "}\n";

        assertEquals(expected, edit.text);
        assertEquals(7, edit.changed);
        assertTrue(edit.missing.isEmpty(), edit.missing.toString());
        for (String comment : new String[] { "    # pollutionSmogLimit=550000  # old value\n", "    # pollution {\n",
            "    # B:\"Pollution Fog\"=true\n", "    # B:\"Pollution Dirt\"=true\n" }) {
            assertTrue(edit.text.contains(comment), "comment must stay byte-identical: " + comment);
        }
    }

    @Test
    void valuesReportsWhatTheFileSaid() throws IOException {
        Map<String, String> values = PollutionCfg.values(fixture("Pollution.before.cfg"), PollutionCfg.SERVER_KEYS);

        assertEquals("false", values.get("Activate Pollution"));
        assertEquals("550000", values.get("pollutionSmogLimit"));
        assertEquals("750000", values.get("pollutionPoisonLimit"));
        assertEquals("1000000", values.get("pollutionVegetationLimit"));
        assertEquals("2000000", values.get("pollutionSourRainLimit"));
        assertEquals(5, values.size());
    }

    /** Leaves the real apply() output on disk, so the acceptance diff is the code's own work. */
    @Test
    void writesRewrittenFixtureForDiffEvidence() throws IOException {
        String before = fixture("Pollution.before.cfg");
        PollutionCfg.Edit edit = PollutionCfg.apply(before, PollutionCfg.SERVER_KEYS);

        Path out = Paths.get("build", "pollution-fixture");
        Files.createDirectories(out);
        Files.write(out.resolve("before.cfg"), before.getBytes(StandardCharsets.UTF_8));
        Files.write(out.resolve("after.cfg"), edit.text.getBytes(StandardCharsets.UTF_8));

        assertTrue(Files.isRegularFile(out.resolve("after.cfg")));
        assertEquals(fixture("Pollution.after.cfg"), edit.text);
    }

    private static String fixture(String name) throws IOException {
        return new String(Files.readAllBytes(FIXTURES.resolve(name)), StandardCharsets.UTF_8);
    }

    /** Lines that differ at the same index; the two files must have the same line count. */
    private static List<String> changedLines(String before, String after) {
        String[] beforeLines = before.split("\n", -1);
        String[] afterLines = after.split("\n", -1);
        assertEquals(beforeLines.length, afterLines.length, "the rewrite may not add or drop lines");
        List<String> changed = new ArrayList<String>();
        for (int i = 0; i < beforeLines.length; i++) {
            if (!beforeLines[i].equals(afterLines[i])) {
                changed.add(afterLines[i]);
            }
        }
        return changed;
    }
}
