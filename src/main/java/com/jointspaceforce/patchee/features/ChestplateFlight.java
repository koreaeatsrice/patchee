package com.jointspaceforce.patchee.features;

/**
 * The chestplate-flight rules, plus the small holder the transformer writes into.
 *
 * <p>
 * IC2's Quantum Chestplate has two ways to fly. With hover mode <b>off</b> the
 * suit rises at up to {@value #ASCENT_CEILING} blocks a tick (the literal
 * {@code 0.6} in {@code ItemArmorQuantumSuit.useJetpack}). With hover mode
 * <b>on</b>, the same method picks a vertical target {@code v}, then clamps the
 * motion to it:
 *
 * <pre>
 * v = -0.025f                                  // no key: sink slowly
 * if (isSneakKeyDown) v = -0.1f                // sneak: sink faster
 * if (isJumpKeyDown)  v = 0.1f                 // jump: climb at 0.1/tick
 * </pre>
 *
 * <p>
 * Three defects follow from that order, and each has its own switch:
 *
 * <ol>
 * <li><b>Hover climb is one sixth of normal flight.</b> Holding jump while
 * hovering rises at {@value #IC2_HOVER_JUMP_CLAMP} a tick instead of the
 * {@value #ASCENT_CEILING} the same method already allows with hover off.
 * {@link #hoverLiftClamp()} is the replacement.</li>
 * <li><b>Shift + space drifts upward.</b> The jump test runs after the sneak
 * test, so holding both keys gives the jump target and the player climbs. The
 * two-key case must win over the jump branch;
 * {@link #hoverVerticalTarget(boolean, boolean, float)} is the decision table,
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
     * The per-tick climb ceiling normal (non-hover) jetpack flight already uses:
     * {@code Math.min(motionY + power * 0.2f, 0.6000000238418579d)}. Hover mode
     * must reach the same number instead of its own clamp.
     */
    public static final float ASCENT_CEILING = 0.6f;

    /** IC2's own hover+jump clamp ({@code ldc 0.1f}), kept for the log lines. */
    public static final float IC2_HOVER_JUMP_CLAMP = 0.1f;

    /** IC2's hover target with no key held ({@code ldc -0.025f}). */
    public static final float HOVER_DEFAULT_TARGET = -0.025f;

    /** IC2's hover target while sneaking ({@code ldc -0.1f}). */
    public static final float SNEAK_TARGET = -0.1f;

    /** Shift + space: the vertical target is 0.0; a climb already in progress damps to it. */
    public static final float HOLD_ALTITUDE_TARGET = 0.0f;

    private static volatile boolean liftPatched;
    private static volatile boolean holdPatched;
    private static volatile boolean guardPatched;

    private ChestplateFlight() {}

    /**
     * The one decision behind the vertical fix: the clamp hover mode applies
     * while the jump key is held is the normal ascent ceiling. Pure.
     */
    public static float hoverLiftClamp() {
        return ASCENT_CEILING;
    }

    /**
     * The whole hover vertical-target table. Pure.
     *
     * <pre>
     * sneak  jump   target
     *  no     no    -0.025   (IC2's default sink; Hodgepodge's optional fix makes it 0.0)
     *  yes    no    -0.1     (sink faster)
     *  no     yes   ascentTarget (0.6 with the vertical fix on, IC2's 0.1 with it off)
     *  yes    yes   0.0      (hold: target 0.0, an in-progress climb damps to it)
     * </pre>
     *
     * The two-key case is decided first in this table because it must win over the
     * jump branch. The shipped patch reaches the same result bytecode-side by
     * overwriting the jump target with 0.0 after the jump-only store; no
     * instructions are reordered.
     *
     * @param sneak        sneak key held
     * @param jump         jump key held
     * @param ascentTarget what a jump-only hover should climb at — the value the
     *                     vertical fix (or stock IC2) puts in the jump branch
     */
    public static float hoverVerticalTarget(boolean sneak, boolean jump, float ascentTarget) {
        if (sneak && jump) {
            return HOLD_ALTITUDE_TARGET;
        }
        if (jump) {
            return ascentTarget;
        }
        if (sneak) {
            return SNEAK_TARGET;
        }
        return HOVER_DEFAULT_TARGET;
    }

    /**
     * The one decision behind the ground guard: skip the jetpack tick when the
     * player is standing on the ground and not rising.
     *
     * <p>
     * {@code motionY <= 0} is the "not asking to ascend" half. It is read from
     * the player's motion rather than the jump key because the motion is already
     * positive on the very tick vanilla applies a jump, so a jump leaves the
     * guard inert; the key also reaches the server, but one tick late and only
     * after it changes.
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
