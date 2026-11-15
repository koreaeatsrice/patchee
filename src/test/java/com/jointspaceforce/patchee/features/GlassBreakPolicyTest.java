package com.jointspaceforce.patchee.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.jointspaceforce.patchee.features.GlassBreakPolicy.Verdict;

/**
 * The glass-cut rule, without a game.
 *
 * <p>
 * The rule exists because Blood Arsenal 1.5.12 cuts the player on any
 * {@code BlockGlass} (its {@code harvestEvent}, decompiled line {@code 171}) and
 * Chisel's Transparent Technical Block is {@code new BlockCarvableGlass()}
 * (Chisel {@code Features.java:3823}) — a metal mesh pretending to be glass. So
 * the cases pinned here are the ones that decide real gameplay: the technical
 * block is recognised by either of its names, and real glass still cuts a player
 * with no armour while armour nullifies it.
 */
class GlassBreakPolicyTest {

    @Test
    void recognisesTheTechnicalBlockByItsUnlocalizedName() {
        assertTrue(GlassBreakPolicy.isKnownNonGlass("tile.chisel.technical2", null));
        assertTrue(GlassBreakPolicy.isKnownNonGlass("tile.chisel.technical2", "some.other:name"));
    }

    @Test
    void recognisesTheTechnicalBlockByItsRegistryName() {
        assertTrue(GlassBreakPolicy.isKnownNonGlass(null, "chisel:technical2"));
        assertTrue(GlassBreakPolicy.isKnownNonGlass("tile.chisel.glass", "chisel:technical2"));
    }

    @Test
    void toleratesCaseAndSurroundingWhitespace() {
        assertTrue(GlassBreakPolicy.isKnownNonGlass("  TILE.Chisel.Technical2  ", null));
        assertTrue(GlassBreakPolicy.isKnownNonGlass(null, "\tCHISEL:Technical2 "));
    }

    @Test
    void anUnknownNullOrBlankNameIsNotTheTechnicalBlock() {
        assertFalse(GlassBreakPolicy.isKnownNonGlass("tile.chisel.glass", "chisel:glass"));
        assertFalse(GlassBreakPolicy.isKnownNonGlass("tile.glass", "minecraft:glass"));
        assertFalse(GlassBreakPolicy.isKnownNonGlass(null, null));
        assertFalse(GlassBreakPolicy.isKnownNonGlass("", "   "));
    }

    @Test
    void aBlockThatIsNotGlassIsAlwaysAllowed() {
        // Blood Arsenal's own instanceof would not fire either
        assertEquals(Verdict.ALLOW, GlassBreakPolicy.verdict(false, true, 0, 1));
        assertEquals(Verdict.ALLOW, GlassBreakPolicy.verdict(false, false, 4, 4));
    }

    @Test
    void theTechnicalBlockIsSuppressedWhateverThePlayerWears() {
        // the non-glass answer wins over the armour answer
        assertEquals(Verdict.SUPPRESS_NOT_GLASS, GlassBreakPolicy.verdict(true, true, 4, 4));
        assertEquals(Verdict.SUPPRESS_NOT_GLASS, GlassBreakPolicy.verdict(true, true, 0, 1));
    }

    @Test
    void realGlassCutsABarePlayer() {
        assertEquals(Verdict.ALLOW, GlassBreakPolicy.verdict(true, false, 0, 1));
    }

    @Test
    void armourSuppressesRealGlass() {
        assertEquals(Verdict.SUPPRESS_ARMOURED, GlassBreakPolicy.verdict(true, false, 1, 1));
        assertEquals(Verdict.SUPPRESS_ARMOURED, GlassBreakPolicy.verdict(true, false, 4, 1));
        assertEquals(Verdict.SUPPRESS_ARMOURED, GlassBreakPolicy.verdict(true, false, 3, 2));
    }

    @Test
    void notEnoughArmourLeavesTheCutInPlace() {
        assertEquals(Verdict.ALLOW, GlassBreakPolicy.verdict(true, false, 3, 4));
        assertEquals(Verdict.ALLOW, GlassBreakPolicy.verdict(true, false, 0, 2));
    }

    @Test
    void aThresholdOfZeroMeansNoOneIsProtected() {
        assertEquals(Verdict.ALLOW, GlassBreakPolicy.verdict(true, false, 4, 0));
    }

    @Test
    void aThresholdAboveFourIsClampedToAFullSet() {
        // 99 pieces can never be worn; clamping to 4 keeps the intent: a full set
        assertEquals(Verdict.SUPPRESS_ARMOURED, GlassBreakPolicy.verdict(true, false, 4, 99));
        assertEquals(Verdict.ALLOW, GlassBreakPolicy.verdict(true, false, 3, 99));
    }

    @Test
    void clampKeepsTheThresholdInsideZeroToFour() {
        assertEquals(0, GlassBreakPolicy.clampMinArmourPieces(-2));
        assertEquals(4, GlassBreakPolicy.clampMinArmourPieces(9));
        assertEquals(0, GlassBreakPolicy.clampMinArmourPieces(0));
        assertEquals(1, GlassBreakPolicy.clampMinArmourPieces(1));
        assertEquals(4, GlassBreakPolicy.clampMinArmourPieces(4));
    }
}
