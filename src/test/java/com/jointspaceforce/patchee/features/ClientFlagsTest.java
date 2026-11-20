package com.jointspaceforce.patchee.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

/**
 * The two client render flags in {@code config/GregTech/Client.cfg}: quoted
 * Forge names, the older unquoted spellings, a missing key, and a same-named
 * key that belongs to another mod's section and must not be touched.
 */
class ClientFlagsTest {

    private static final Path FIXTURES = Paths.get("src", "test", "resources", "pollution");

    @Test
    void quotedRenderKeysAreTurnedOffInsideTheRenderSection() throws IOException {
        String before = fixture("Client.before.cfg");
        String after = fixture("Client.after.cfg");

        PollutionCfg.Edit edit = PollutionCfg.apply(before, PollutionCfg.CLIENT_KEYS);

        assertEquals(after, edit.text);
        assertEquals(2, edit.changed);
        assertTrue(edit.missing.isEmpty(), edit.missing.toString());
        assertTrue(edit.text.contains("B:\"Pollution Fog\"=false"));
        assertTrue(edit.text.contains("B:\"Pollution Dirt\"=false"));
    }

    @Test
    void legacyAliasIsRecognised() {
        String text = "render {\n    B:RenderPollutionFog_true=true\n}\n";

        PollutionCfg.Edit edit = PollutionCfg.apply(text, PollutionCfg.CLIENT_KEYS);

        assertEquals(1, edit.changed);
        assertTrue(edit.text.contains("B:RenderPollutionFog_true=false"), edit.text);
        assertEquals("true", edit.found.get("Pollution Fog"));
        assertEquals(1, edit.missing.size());
        assertEquals("Pollution Dirt", edit.missing.get(0));
    }

    @Test
    void missingDirtIsReportedWhileFogIsStillWritten() throws IOException {
        String before = fixture("Client.before.cfg").replace("    B:\"Pollution Dirt\"=true\n", "");

        PollutionCfg.Edit edit = PollutionCfg.apply(before, PollutionCfg.CLIENT_KEYS);

        assertEquals(1, edit.changed);
        assertTrue(edit.text.contains("B:\"Pollution Fog\"=false"), edit.text);
        assertEquals(1, edit.missing.size());
        assertEquals("Pollution Dirt", edit.missing.get(0));
        assertFalse(edit.text.contains("B:\"Pollution Dirt\""), edit.text);
    }

    @Test
    void sameNamedKeyInAnotherSectionIsNotTouched() {
        String text = "other {\n    B:\"Pollution Fog\"=true\n}\n\nrender {\n    B:\"Pollution Fog\"=true\n}\n";

        PollutionCfg.Edit edit = PollutionCfg.apply(text, PollutionCfg.CLIENT_KEYS);

        assertEquals(1, edit.changed);
        assertEquals(
            "other {\n    B:\"Pollution Fog\"=true\n}\n\nrender {\n    B:\"Pollution Fog\"=false\n}\n",
            edit.text);
    }

    private static String fixture(String name) throws IOException {
        return new String(Files.readAllBytes(FIXTURES.resolve(name)), StandardCharsets.UTF_8);
    }
}
