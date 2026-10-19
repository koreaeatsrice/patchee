package com.jointspaceforce.patchee.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The team limit rule and the early config reader, without a game.
 *
 * <p>
 * The owner's rule has edges that matter and are pinned here: a team whose
 * members' limits sum past the best member's own number is answered that best
 * member's number, a member whose rank carries higher numbers (the admin rank)
 * is never capped away, a solo team (whose sum already equals its own setting)
 * is not touched, and every value ServerUtilities uses as a flag — -1, -2 and 0
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
    void answersAMultiMemberTeamWithItsBestMember() {
        // four players worth 1500 each sum to 6000; the team gets the best member's 1500
        assertEquals(1500, TeamChunkCap.teamLimit(6000, 1500));
        assertEquals(500, TeamChunkCap.teamLimit(2000, 500));
        assertTrue(TeamChunkCap.limitFires(6000, 1500));
    }

    @Test
    void neverCapsARankWithHigherNumbersAway() {
        // an admin member worth 30000 keeps the team at 30000, not at 1500
        assertEquals(30000, TeamChunkCap.teamLimit(31500, 30000));
        assertEquals(30000, TeamChunkCap.teamLimit(30000, 30000));
        assertFalse(TeamChunkCap.limitFires(30000, 30000));
        assertEquals(750, TeamChunkCap.teamLimit(750, 750));
    }

    @Test
    void leavesASoloTeamUntouched() {
        assertEquals(1500, TeamChunkCap.teamLimit(1500, 1500));
        assertEquals(500, TeamChunkCap.teamLimit(500, 500));
        assertFalse(TeamChunkCap.limitFires(1500, 1500));
    }

    @Test
    void leavesServerUtilitiesFlagsUntouched() {
        // -1: claimed chunks not active; -2: team not valid; 0: the mod's own "disabled"
        assertEquals(-1, TeamChunkCap.teamLimit(-1, 1500));
        assertEquals(-2, TeamChunkCap.teamLimit(-2, 1500));
        assertEquals(0, TeamChunkCap.teamLimit(0, 1500));
        assertFalse(TeamChunkCap.limitFires(-1, 1500));
    }

    @Test
    void anUnreadableBestMemberMeansNoChange() {
        // 0 = "the members could not be read": ServerUtilities' own number stands
        assertEquals(6000, TeamChunkCap.teamLimit(6000, 0));
        assertFalse(TeamChunkCap.limitFires(6000, 0));
    }

    @Test
    void seedNeverOverwritesThePublishedConfig() {
        // the real config always wins: a seed may only fill in before it is published
        TeamChunkCap.configure(true, true);
        TeamChunkCap.seed(false, false);
        assertTrue(TeamChunkCap.active());
    }

    @Test
    void theMasterSwitchAndTheFeatureSwitchBothGate() {
        TeamChunkCap.configure(false, true);
        assertFalse(TeamChunkCap.active());
        TeamChunkCap.configure(true, false);
        assertFalse(TeamChunkCap.active());
        TeamChunkCap.configure(true, true);
        assertTrue(TeamChunkCap.active());
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
    }

    @Test
    void retiredCapKeysFromOlderFilesChangeNothing() {
        // old files still carry the v1.6.0 cap keys; they are ignored
        TeamChunkCapSettings settings = TeamChunkCapSettings
            .parse("general {\n    I:teamChunkCap.maxClaimChunks=0\n    I:teamChunkCap.maxLoadedChunks=200\n}\n");
        assertTrue(settings.masterEnabled());
        assertTrue(settings.enabled());
    }

    @Test
    void readsTheOffSwitches() {
        String file = "general {\n" + "    B:enabled=false\n" + "    B:enableTeamChunkCap=false\n" + "}\n";
        TeamChunkCapSettings settings = TeamChunkCapSettings.parse(file);
        assertFalse(settings.masterEnabled());
        assertFalse(settings.enabled());
    }

    @Test
    void fallsBackToDefaultsForAMissingOrUnreadableFile() {
        TeamChunkCapSettings missing = TeamChunkCapSettings.parse(null);
        assertTrue(missing.masterEnabled());
        assertTrue(missing.enabled());

        // Unknown keys, comments and junk values change nothing.
        String junk = "# note\nsomeOtherKey=3\n     I:teamChunkCap.maxClaimChunks=not-a-number\nleading text\n";
        TeamChunkCapSettings parsed = TeamChunkCapSettings.parse(junk);
        assertTrue(parsed.masterEnabled());
        assertTrue(parsed.enabled());
    }
}
