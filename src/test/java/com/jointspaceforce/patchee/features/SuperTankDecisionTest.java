package com.jointspaceforce.patchee.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The one rule the Super Tank fix adds: clear the four debuffs only when the
 * player carries a filled tank/chest, has at least one of the effects, and the
 * game that asked is either the local client or a client that runs Patchee.
 *
 * <p>
 * The server cannot see a stock client's screen, so a false/unknown handshake
 * must leave GregTech's own behaviour alone — that is the difference between
 * "the fix clears it" and "the effect looked stuck".
 */
class SuperTankDecisionTest {

    @Test
    void aPatcheeClientClearsTheDebuffs() {
        assertTrue(
            SuperTankDecision.shouldClear(false, true, true, true),
            "a server-side player whose client runs Patchee must have the debuffs cleared");
    }

    @Test
    void theLocalClientClearsTheDebuffs() {
        assertTrue(
            SuperTankDecision.shouldClear(true, false, true, true),
            "the player's own client clears its copy even before any handshake is known");
    }

    @Test
    void aStockClientKeepsGregtechBehaviour() {
        assertFalse(
            SuperTankDecision.shouldClear(false, false, true, true),
            "a client without Patchee must keep GregTech's own debuff and timer");
    }

    @Test
    void everyCombinationFollowsTheRule() {
        // Hand-written expected values, one row per combination of the four inputs.
        // Each row is {logicalClient, clientHasPatchee, carries, hasEffects, expected}.
        Object[][] rows = { { false, false, false, false, false }, { true, false, false, false, false },
            { false, true, false, false, false }, { true, true, false, false, false },
            { false, false, true, false, false }, { true, false, true, false, false },
            { false, true, true, false, false }, { true, true, true, false, false },
            { false, false, false, true, false }, { true, false, false, true, false },
            { false, true, false, true, false }, { true, true, false, true, false },
            { false, false, true, true, false }, { true, false, true, true, true }, { false, true, true, true, true },
            { true, true, true, true, true }, };
        for (Object[] row : rows) {
            boolean logicalClient = (Boolean) row[0];
            boolean clientHasPatchee = (Boolean) row[1];
            boolean carries = (Boolean) row[2];
            boolean hasEffects = (Boolean) row[3];
            boolean expected = (Boolean) row[4];
            assertEquals(
                expected,
                SuperTankDecision.shouldClear(logicalClient, clientHasPatchee, carries, hasEffects),
                "shouldClear(logicalClient=" + logicalClient
                    + ", clientHasPatchee="
                    + clientHasPatchee
                    + ", carries="
                    + carries
                    + ", hasEffects="
                    + hasEffects
                    + ")");
        }
    }

    @Test
    void carryingNothingIsNeverCleared() {
        for (int bits = 0; bits < 8; bits++) {
            boolean logicalClient = (bits & 1) != 0;
            boolean clientHasPatchee = (bits & 2) != 0;
            boolean hasEffects = (bits & 4) != 0;
            assertFalse(
                SuperTankDecision.shouldClear(logicalClient, clientHasPatchee, false, hasEffects),
                "a player carrying no qualifying tank/chest must never be touched");
        }
    }

    @Test
    void havingNoEffectsIsNeverCleared() {
        for (int bits = 0; bits < 8; bits++) {
            boolean logicalClient = (bits & 1) != 0;
            boolean clientHasPatchee = (bits & 2) != 0;
            boolean carries = (bits & 4) != 0;
            assertFalse(
                SuperTankDecision.shouldClear(logicalClient, clientHasPatchee, carries, false),
                "a player without any of the four effects must never be touched");
        }
    }

    @Test
    void aServerSidePlayerWithoutPatcheeIsNeverCleared() {
        for (int bits = 0; bits < 4; bits++) {
            boolean carries = (bits & 1) != 0;
            boolean hasEffects = (bits & 2) != 0;
            assertFalse(
                SuperTankDecision.shouldClear(false, false, carries, hasEffects),
                "an unknown or stock client must never be cleared on the server (carries=" + carries
                    + ", hasEffects="
                    + hasEffects
                    + ")");
        }
    }

    @Test
    void theLocalClientAlwaysClearsWhileCarryingAndDebuffed() {
        assertTrue(SuperTankDecision.shouldClear(true, false, true, true), "local client, unknown server handshake");
        assertTrue(SuperTankDecision.shouldClear(true, true, true, true), "local client, known Patchee client");
    }
}
