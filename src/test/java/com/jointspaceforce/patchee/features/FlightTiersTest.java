package com.jointspaceforce.patchee.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertEquals(0.2d, GraviSuiteFlightPatch.HOVER_JUMP_CLAMP, "GraviSuite's own stock climb");
        assertEquals(0.2d, Math.abs(GraviSuiteFlightPatch.SNEAK_CLAMP), "GraviSuite's own stock sink");
    }

    @Test
    void theQuantumRungIsExactlyTwiceTheAdvancedNanoRung() {
        assertEquals(0.4f, FlightTiers.QUANTUM_HOVER_ASCENT, "the Quantum climbs at 0.4 a tick");
        assertEquals(
            (float) (2.0d * GraviSuiteFlightPatch.HOVER_JUMP_CLAMP),
            ChestplateFlight.hoverAscentTarget(),
            "the Quantum must be twice the Advanced Nano's stock 0.2");
        assertTrue(
            FlightTiers.QUANTUM_HOVER_ASCENT > GraviSuiteFlightPatch.HOVER_JUMP_CLAMP,
            "the Quantum must climb faster than the Advanced Nano");
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
        assertEquals(-0.4f, ChestplateFlight.hoverDescentTarget(), "the sink is the climb with the sign flipped");
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
