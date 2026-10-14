package com.jointspaceforce.patchee.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The team cap rule and the early config reader, without a game.
 *
 * <p>
 * The owner's rule has three edges that matter and are pinned here: a team over
 * the cap is clamped, a solo team (whose sum already equals the per-player
 * number) is not, and every value ServerUtilities uses as a flag — -1, -2 and 0
 * — passes straight through.
 */
class TeamChunkCapTest {

    /**
     * The ARMED line is a claim, so it must be gated on the transformer really
     * patching the target — a class that is merely <i>present</i> is not proof.
     * No sites patched means the arm step picks its ERROR report and writes no
     * armed line; a real patch flips the gate to the normal armed line.
     */
    @Test
    void theArmedLineIsGatedOnARealPatch() {
        // no sites patched this boot: the log line policy must pick NOT_ARMED (ERROR)
        TeamChunkCap.patchApplied = false;
        assertFalse(TeamChunkCap.patchApplied());
        assertEquals(TeamChunkCapFeature.ArmReport.NOT_ARMED, TeamChunkCapFeature.armReport());

        // the transformer published a successful site patch: the armed line is written
        TeamChunkCap.markPatchApplied();
        assertTrue(TeamChunkCap.patchApplied());
        assertEquals(TeamChunkCapFeature.ArmReport.ARMED, TeamChunkCapFeature.armReport());
    }

    @Test
    void clampsATeamThatIsOverTheCap() {
        assertEquals(1500, TeamChunkCap.clamp(6000, 1500));
        assertEquals(500, TeamChunkCap.clamp(2000, 500));
    }

    @Test
    void leavesASoloTeamUntouched() {
        assertEquals(1500, TeamChunkCap.clamp(1500, 1500));
        assertEquals(500, TeamChunkCap.clamp(500, 500));
        assertFalse(TeamChunkCap.clampFires(1500, 1500));
    }

    @Test
    void leavesServerUtilitiesFlagsUntouched() {
        // -1: claimed chunks not active; -2: team not valid; 0: the mod's own "disabled"
        assertEquals(-1, TeamChunkCap.clamp(-1, 1500));
        assertEquals(-2, TeamChunkCap.clamp(-2, 1500));
        assertEquals(0, TeamChunkCap.clamp(0, 1500));
    }

    @Test
    void aCapOfZeroMeansNoCap() {
        assertEquals(6000, TeamChunkCap.clamp(6000, TeamChunkCap.CAP_OFF));
        assertFalse(TeamChunkCap.clampFires(6000, TeamChunkCap.CAP_OFF));
    }

    @Test
    void seedNeverOverwritesThePublishedConfig() {
        // the real config always wins: a seed may only fill in before it is published
        TeamChunkCap.configure(true, true, 1500, 500);
        TeamChunkCap.seed(false, false, 33, 44);
        assertEquals(1500, TeamChunkCap.maxClaims());
        assertEquals(500, TeamChunkCap.maxLoads());
        assertTrue(TeamChunkCap.active());
    }

    @Test
    void aSeedCarriesTheFileValuesBeforeTheConfigIsPublished() {
        // a fresh boot: the class-load seed is all the hook has until the pipeline runs
        TeamChunkCap.configure(true, true, TeamChunkCap.DEFAULT_MAX_CLAIMS, TeamChunkCap.DEFAULT_MAX_LOADS);
        TeamChunkCap.seed(true, true, 11, 22);
        TeamChunkCapSettings file = TeamChunkCapSettings
            .parse("general {\n    I:teamChunkCap.maxClaimChunks=11\n    I:teamChunkCap.maxLoadedChunks=22\n}\n");
        assertEquals(11, file.maxClaims());
        assertEquals(22, file.maxLoads());
        // and the published values stay the ones the boot line reports
        assertEquals(TeamChunkCap.DEFAULT_MAX_CLAIMS, TeamChunkCap.maxClaims());
    }

    @Test
    void aMasterSwitchOffMeansNothingClamps() {
        TeamChunkCap.configure(false, true, 1500, 500);
        assertFalse(TeamChunkCap.active());
        TeamChunkCap.configure(true, false, 1500, 500);
        assertFalse(TeamChunkCap.active());
        TeamChunkCap.configure(true, true, 1500, 500);
        assertTrue(TeamChunkCap.active());
    }

    @Test
    void aNegativeCapFallsBackToTheDocumentedDefault() {
        TeamChunkCap.configure(true, true, -5, -7);
        assertEquals(TeamChunkCap.DEFAULT_MAX_CLAIMS, TeamChunkCap.maxClaims());
        assertEquals(TeamChunkCap.DEFAULT_MAX_LOADS, TeamChunkCap.maxLoads());
    }

    @Test
    void parsesTheFileForgeWrites() {
        String file = "# Configuration file\n\n" + "general {\n"
            + "    B:enabled=true\n"
            + "    B:enableTeamChunkCap=true\n"
            + "    I:teamChunkCap.maxClaimChunks=1500\n"
            + "    I:teamChunkCap.maxLoadedChunks=500\n"
            + "}\n";
        TeamChunkCapSettings settings = TeamChunkCapSettings.parse(file);
        assertTrue(settings.masterEnabled());
        assertTrue(settings.enabled());
        assertEquals(1500, settings.maxClaims());
        assertEquals(500, settings.maxLoads());
    }

    @Test
    void readsTheOwnerSValuesAndTheOffSwitch() {
        String file = "general {\n" + "    B:enabled=false\n"
            + "    B:enableTeamChunkCap=false\n"
            + "    I:teamChunkCap.maxClaimChunks=0\n"
            + "    I:teamChunkCap.maxLoadedChunks=200\n"
            + "}\n";
        TeamChunkCapSettings settings = TeamChunkCapSettings.parse(file);
        assertFalse(settings.masterEnabled());
        assertFalse(settings.enabled());
        assertEquals(0, settings.maxClaims());
        assertEquals(200, settings.maxLoads());
    }

    @Test
    void fallsBackToDefaultsForAMissingOrUnreadableFile() {
        TeamChunkCapSettings missing = TeamChunkCapSettings.parse(null);
        assertTrue(missing.masterEnabled());
        assertTrue(missing.enabled());
        assertEquals(TeamChunkCap.DEFAULT_MAX_CLAIMS, missing.maxClaims());
        assertEquals(TeamChunkCap.DEFAULT_MAX_LOADS, missing.maxLoads());

        // Unknown keys, comments and junk values change nothing.
        String junk = "# note\nsomeOtherKey=3\n     I:teamChunkCap.maxClaimChunks=not-a-number\nleading text\n";
        TeamChunkCapSettings parsed = TeamChunkCapSettings.parse(junk);
        assertEquals(TeamChunkCap.DEFAULT_MAX_CLAIMS, parsed.maxClaims());
        assertEquals(TeamChunkCap.DEFAULT_MAX_LOADS, parsed.maxLoads());
    }
}
