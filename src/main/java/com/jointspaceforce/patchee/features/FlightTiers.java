package com.jointspaceforce.patchee.features;

/**
 * The hover vertical ladder: two chestplates, two speeds, lowest first.
 *
 * <p>
 * The ladder runs from the lowest hover speed to the highest:
 *
 * <ol>
 * <li><b>GraviSuite Advanced Nano</b> — climbs at 0.2 blocks a tick and sinks at
 * 0.2 blocks a tick. That is GraviSuite's own stock literal; Patchee no longer
 * changes it. {@link #ADVANCED_NANO_HOVER_ASCENT} names it so the second rung
 * can be written as a multiple of the first instead of as a second literal.</li>
 * <li><b>IC2 Quantum</b> — climbs at 0.4 blocks a tick and sinks at 0.4 blocks a
 * tick, exactly twice the Advanced Nano's 0.2. {@link #QUANTUM_HOVER_ASCENT} is
 * that number, and it is a product of the rung below it, not a free literal.</li>
 * </ol>
 *
 * <p>
 * Order matters: the ladder must <b>ascend</b>. The Quantum is the better suit,
 * so it flies up and down faster than the Advanced Nano; it must never be the
 * slower of the two.
 *
 * <p>
 * {@code ItemGraviChestPlate} is a different item with its own creative-flight
 * path ({@code PlayerCapabilities}). It is not part of this ladder and nothing
 * here applies to it.
 *
 * <p>
 * Horizontal speeds are untouched by the ladder: IC2 Quantum keeps its own
 * 0.0889 blocks a tick sideways and GraviSuite keeps its own 0.1156. Only the
 * up/down hover clamps differ, and only by the factor below.
 */
public final class FlightTiers {

    /** The Advanced Nano's stock hover climb, as GraviSuite stores it ({@code ldc2_w 0.2d}). */
    public static final float ADVANCED_NANO_HOVER_ASCENT = 0.2f;

    /** The Quantum's hover climb: exactly twice the Advanced Nano's 0.2, so 0.4. */
    public static final float QUANTUM_HOVER_ASCENT = 2.0f * ADVANCED_NANO_HOVER_ASCENT;

    private FlightTiers() {}
}
