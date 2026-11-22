package com.jointspaceforce.patchee.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The flight ladder, pinned as an ordered ladder rather than as loose numbers.
 *
 * <p>
 * Two hover speeds, lowest first: GraviSuite's Advanced Nano at its own stock
 * 0.2, then IC2's Quantum at 0.4. The Quantum rung is written as a product of the
 * Advanced Nano rung, so it cannot drift: if the bottom moves, the top follows.
 *
 * <p>
 * Horizontal speeds are a separate axis and stay exactly where each mod put them.
 */
class FlightTiersTest {

    @Test
    void theAdvancedNanoRungIsGravSuitesStockTwoTenths() {
        assertEquals(0.2f, FlightTiers.ADVANCED_NANO_HOVER_ASCENT);
        assertEquals(0.2d, FlightTiers.ADVANCED_NANO_HOVER_ASCENT_DOUBLE);
        assertEquals(0.2d, GraviSuiteFlightPatch.HOVER_JUMP_CLAMP, "GraviSuite's own stock climb");
        assertEquals(0.2d, GraviSuiteFlightPatch.HOVER_JUMP_LIFTED, "the Advanced Nano stays stock");
        assertFalse(GraviSuiteFlightPatch.VERTICAL_LIFT, "no vertical rewrite ships for the Advanced Nano");
    }

    @Test
    void theQuantumRungIsExactlyTwiceTheAdvancedNanoRung() {
        assertEquals(0.4f, FlightTiers.QUANTUM_HOVER_ASCENT);
        assertEquals(
            0.4f,
            2.0f * FlightTiers.ADVANCED_NANO_HOVER_ASCENT,
            "the Quantum rung must be a product of the rung below it");
        assertEquals(
            FlightTiers.QUANTUM_HOVER_ASCENT,
            2.0f * FlightTiers.ADVANCED_NANO_HOVER_ASCENT,
            "the Quantum rung must be written as a multiple, not as a free literal");
    }

    @Test
    void theLadderAscends() {
        assertTrue(
            FlightTiers.QUANTUM_HOVER_ASCENT > FlightTiers.ADVANCED_NANO_HOVER_ASCENT,
            "the better suit must fly faster, not slower");
        assertTrue(
            GraviSuiteFlightPatch.HOVER_JUMP_CLAMP < ChestplateFlight.hoverAscentTarget(),
            "the Advanced Nano must sit below the Quantum");
    }

    @Test
    void theQuantumTargetsAreTheLadderRungAndItsNegation() {
        assertEquals(FlightTiers.QUANTUM_HOVER_ASCENT, ChestplateFlight.hoverAscentTarget());
        assertEquals(-FlightTiers.QUANTUM_HOVER_ASCENT, ChestplateFlight.hoverDescentTarget());
        assertEquals(
            (float) (2.0d * GraviSuiteFlightPatch.HOVER_JUMP_CLAMP),
            Math.abs(ChestplateFlight.hoverAscentTarget()),
            0.0f,
            "the Quantum magnitude must be twice the Advanced Nano's stock 0.2");
        assertEquals(
            2.0f * FlightTiers.ADVANCED_NANO_HOVER_ASCENT,
            Math.abs(ChestplateFlight.hoverAscentTarget()),
            0.0f,
            "the Quantum magnitude must be twice the float rung too");
    }

    @Test
    void theHorizontalSpeedsAreNotOnTheLadder() {
        assertEquals(0.08888892f, ChestplateFlight.HOVER_HORIZONTAL_SPEED, "IC2's sideways hover speed");
        // The vertical rung is deliberately a different number from the sideways one.
        assertTrue(
            ChestplateFlight.HOVER_HORIZONTAL_SPEED != ChestplateFlight.hoverAscentTarget(),
            "the sideways speed must not be reused as the vertical target");
    }
}
