package com.jointspaceforce.patchee.features;

/**
 * The chestplate-flight rules, plus the small holder the transformer writes into.
 *
 * <p>
 * IC2's Quantum Chestplate has two ways to fly. With hover mode <b>off</b> the
 * suit rises at up to {@value #IC2_JETPACK_CEILING} blocks a tick (the literal
 * {@code 0.6} in {@code ItemArmorQuantumSuit.useJetpack}). With hover mode
 * <b>on</b>, the same method picks a vertical target {@code v}, then clamps the
 * motion to it:
 *
 * <pre>
 * v = -0.025f                                  // no key: sink slowly
 * if (isSneakKeyDown) v = -0.1f                // sneak: stock sink
 * if (isJumpKeyDown)  v = 0.1f                 // jump: stock climb
 * </pre>
 *
 * <p>
 * Those three hover branches are what this patch changes, and each has its own
 * switch:
 *
 * <ol>
 * <li><b>Hover climb and sink now sit on the flight ladder.</b> Stock IC2
 * climbs at {@value #IC2_HOVER_JUMP_CLAMP} with jump held and sinks at
 * {@value #IC2_SNEAK_CLAMP} with sneak held. This patch writes both at
 * {@value FlightTiers#QUANTUM_HOVER_ASCENT} a tick: 0.4 up, 0.4 down. That is
 * exactly twice the Advanced Nano's stock 0.2, so the two chestplates form one
 * ascending ladder — Quantum above Advanced Nano (see {@link FlightTiers}). Up
 * and down stay equal in magnitude. <b>No-input hover is left at stock {@value
 * #HOVER_DEFAULT_TARGET}</b>, so hovering without a key still hovers. The
 * sideways speed is not touched: IC2 keeps its own {@value
 * #HOVER_HORIZONTAL_SPEED} a tick when moving sideways, which is a different
 * number and a different axis. IC2's existing {@code oldMotionY} clamp still
 * restores a larger motion already in progress, which is what damps the lift
 * instead of cutting it dead.</li>
 * <li><b>Shift + space drifts upward.</b> The jump test runs after the sneak
 * test, so holding both keys gives the jump target and the player climbs. The
 * two-key case must win over the jump branch;
 * {@link #hoverVerticalTarget(boolean, boolean, boolean)} is the decision table,
 * and the bytecode patch gets the same result by writing 0.0 over the jump
 * target after the jump-only store (see {@code ChestplateFlightPatch}).</li>
 * <li><b>Standing on the ground counts as flying.</b> The method ends by clearing
 * {@code fallDistance}, {@code field_70140_Q} and - through
 * {@code IC2.platform.resetPlayerInAirTime} - the server's
 * {@code floatingTickCount} anti-fly counter. IC2 calls the method while you
 * hold jump, so a player standing on the floor with jump held clears that
 * counter every tick. {@link #guardFires(boolean, double)} skips that tick while
 * the player is on the ground and not rising.</li>
 * </ol>
 *
 * <p>
 * Every decision here is pure — booleans and doubles in, booleans or floats out —
 * so the contract is pinned by unit tests with no game on the classpath.
 */
public final class ChestplateFlight {

    /** Log id and config lookup key for the vertical-speed fix. */
    public static final String LIFT_ID = "ChestplateHoverLift";

    /** Log id and config lookup key for the shift+space fix. */
    public static final String HOLD_ID = "ChestplateHoldAltitude";

    /** Log id and config lookup key for the ground guard. */
    public static final String GUARD_ID = "ChestplateGroundGuard";

    /** The switch Forge writes, e.g. {@code B:enableChestplateHoverLift=true}. */
    public static final String LIFT_KEY = "enableChestplateHoverLift";

    /** The switch Forge writes, e.g. {@code B:enableChestplateHoldAltitude=true}. */
    public static final String HOLD_KEY = "enableChestplateHoldAltitude";

    /** The switch Forge writes, e.g. {@code B:enableChestplateGroundGuard=true}. */
    public static final String GUARD_KEY = "enableChestplateGroundGuard";

    /** Used when the config file or a key is not there yet. */
    public static final boolean DEFAULT_ENABLED = true;

    /**
     * The forward argument IC2 hands vanilla's {@code moveFlying} while hovering,
     * with load factor 1: {@code EntityPlayer.func_70060_a(0.0f, 0.4f * load *
     * 0.5f * 2.0f, 0.02f)}, so {@code 0.4f * 0.5f * 2.0f = 0.4f}. The
     * {@code 0.5f} is IC2's hover-mode base, {@code 3.5f} is its normal-flight
     * base.
     */
    public static final float HORIZONTAL_FORWARD = 0.4f * 0.5f * 2.0f;

    /** The third argument of that same call: the per-tick push {@code 0.02f}. */
    public static final float HORIZONTAL_FRICTION = 0.02f;

    /** Vanilla's horizontal air drag applied to the player every tick: {@code 0.91f}. */
    public static final float AIR_DRAG = 0.91f;

    /**
     * The speed IC2's hover flight already moves the player sideways at, derived
     * from its own bytecode: vanilla's {@code moveFlying} adds {@code friction *
     * forward / max(1, length)} = {@code 0.02f * 0.4f = 0.008f} to
     * {@code motionX}/{@code motionZ} each tick and then multiplies by
     * {@value #AIR_DRAG}, so the steady state is {@code 0.008f / (1 - 0.91f)}.
     * IC2 has no ramp of its own; this is the accumulation.
     *
     * <p>
     * Horizontal only. This is the sideways speed IC2's hover flight gives the
     * player, not the vertical target. The vertical target this patch writes is
     * {@link FlightTiers#QUANTUM_HOVER_ASCENT} (0.4), twice the Advanced Nano's
     * stock 0.2; it happens to be a different number on purpose, because the
     * ladder is vertical and the sideways axis is untouched.
     */
    public static final float HOVER_HORIZONTAL_SPEED = HORIZONTAL_FRICTION * HORIZONTAL_FORWARD / (1.0f - AIR_DRAG);

    /**
     * The per-tick climb ceiling normal (non-hover) jetpack flight uses:
     * {@code Math.min(motionY + power * 0.2f, 0.6000000238418579d)}. This patch
     * no longer writes it anywhere — hover mode gets
     * {@link FlightTiers#QUANTUM_HOVER_ASCENT} instead — but the method must
     * still carry exactly one such literal, which is part of the shape check.
     */
    public static final float IC2_JETPACK_CEILING = 0.6f;

    /** IC2's own hover+jump clamp ({@code ldc 0.1f}), kept for the log lines. */
    public static final float IC2_HOVER_JUMP_CLAMP = 0.1f;

    /** IC2's own hover+sneak clamp ({@code ldc -0.1f}), kept for the log lines. */
    public static final float IC2_SNEAK_CLAMP = -0.1f;

    /** IC2's hover target with no key held ({@code ldc -0.025f}). */
    public static final float HOVER_DEFAULT_TARGET = -0.025f;

    /** Shift + space: the vertical target is 0.0; a climb already in progress damps to it. */
    public static final float HOLD_ALTITUDE_TARGET = 0.0f;

    private static volatile boolean liftPatched;
    private static volatile boolean holdPatched;
    private static volatile boolean guardPatched;

    private ChestplateFlight() {}

    /**
     * The clamp hover mode applies while the jump key is held: the Quantum rung
     * of the flight ladder, {@link FlightTiers#QUANTUM_HOVER_ASCENT} (0.4), twice
     * the Advanced Nano's 0.2. Pure.
     */
    public static float hoverAscentTarget() {
        return FlightTiers.QUANTUM_HOVER_ASCENT;
    }

    /**
     * The clamp hover mode applies while the sneak key is held: the exact
     * negation of {@link #hoverAscentTarget()}, so up and down are equal. Pure.
     */
    public static float hoverDescentTarget() {
        return -FlightTiers.QUANTUM_HOVER_ASCENT;
    }

    /**
     * The whole hover vertical-target table. Pure.
     *
     * <pre>
     * sneak  jump   verticalFix   target
     *  no     no      either      -0.025  (IC2's default sink; Hodgepodge's optional fix makes it 0.0)
     *  yes    no       on         -0.4    (hoverDescentTarget: Quantum sink)
     *  yes    no       off        -0.1    (IC2's stock sneak sink)
     *  no     yes      on         +0.4    (hoverAscentTarget: Quantum climb, twice the Advanced Nano's 0.2)
     *  no     yes      off        0.1     (IC2's stock hover climb)
     *  yes    yes      either     0.0     (hold: target 0.0, an in-progress climb damps to it)
     * </pre>
     *
     * The two-key case is decided first in this table because it must win over the
     * jump branch. The shipped patch reaches the same result bytecode-side by
     * overwriting the jump target with 0.0 after the jump-only store; no
     * instructions are reordered.
     *
     * @param sneak       sneak key held
     * @param jump        jump key held
     * @param verticalFix true when this patch's ladder speeds (Quantum 0.4 both
     *                    ways) are in the class; false for stock IC2's 0.1 / -0.1 pair
     */
    public static float hoverVerticalTarget(boolean sneak, boolean jump, boolean verticalFix) {
        if (sneak && jump) {
            return HOLD_ALTITUDE_TARGET;
        }
        if (jump) {
            return verticalFix ? hoverAscentTarget() : IC2_HOVER_JUMP_CLAMP;
        }
        if (sneak) {
            return verticalFix ? hoverDescentTarget() : IC2_SNEAK_CLAMP;
        }
        return HOVER_DEFAULT_TARGET;
    }

    /**
     * The one decision behind the ground guard: skip the jetpack tick when the
     * player is standing on the ground and not rising.
     *
     * <p>
     * {@code motionY <= 0} is the "not asking to ascend" half. It is read from
     * the player's motion rather than the jump key because the armour tick runs
     * <b>before</b> the jump and gravity step: {@code EntityPlayer.onLivingUpdate}
     * calls {@code inventory.decrementAnimations()} at {@code EntityPlayer.java:610},
     * then {@code super.onLivingUpdate()} at {@code :613}, which is where {@code jump()}
     * and gravity run. On the takeoff tick {@code motionY} is still 0, so the guard
     * does fire there, and lift-off comes from vanilla's own 0.42 jump impulse, not
     * from the jetpack; the jetpack takes over on the next tick. The key also reaches
     * the server, but one tick late and only after it changes.
     */
    public static boolean guardFires(boolean onGround, double motionY) {
        return onGround && motionY <= 0.0;
    }

    /** The transformer publishes a real site rewrite here; the boot line reads it. */
    public static void markLiftPatched() {
        liftPatched = true;
    }

    /** The transformer publishes a real site rewrite here; the boot line reads it. */
    public static void markHoldPatched() {
        holdPatched = true;
    }

    /** The transformer publishes a real site rewrite here; the boot line reads it. */
    public static void markGuardPatched() {
        guardPatched = true;
    }

    /** True when IC2's hover clamp was really rewritten this boot. */
    public static boolean liftPatched() {
        return liftPatched;
    }

    /** True when the shift+space branch was really injected this boot. */
    public static boolean holdPatched() {
        return holdPatched;
    }

    /** True when the ground guard was really injected this boot. */
    public static boolean guardPatched() {
        return guardPatched;
    }

    /** Test seam: forget what this JVM has patched so far. */
    static void resetForTest() {
        liftPatched = false;
        holdPatched = false;
        guardPatched = false;
    }
}
