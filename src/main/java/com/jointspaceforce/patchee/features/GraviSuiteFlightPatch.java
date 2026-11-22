package com.jointspaceforce.patchee.features;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * The GraviSuite counterpart of {@link ChestplateFlightPatch}: what it adds is
 * the shift+space hold and the ground guard inside GraviSuite's
 * {@code ItemAdvancedJetPack.useJetpack(EntityPlayer, boolean)}.
 *
 * <p>
 * The method is <b>static</b>, so local 0 is the player and local 1 is the
 * caller's work-mode flag (GraviSuite passes it as the hover boolean). The
 * shipped {@code gravisuite-2.0.3} build computes the climb as
 *
 * <pre>
 * motionY = Math.min(motionY + power * 0.2f, 0.6000000238418579d); // offsets 300-325
 * </pre>
 *
 * and then, only while hovering, clamps it to a local {@code v} (offsets
 * 328-368):
 *
 * <pre>
 * v = -hoverModeFallSpeed;
 * if (Keyboard.isJumpKeyDown(player)) v = 0.2d;
 * if (Keyboard.isSneakKeyDown(player)) v = -0.2d; // tested AFTER jump
 * </pre>
 *
 * followed by {@code if (motionY > v) { motionY = v; ... } } (offsets 443-472).
 * Those two clamps are GraviSuite's stock numbers, and this patch leaves them
 * exactly where they are: 0.2 up, 0.2 down. Holding both keys still
 * <i>descends</i>, because the sneak test runs last — which is the one thing the
 * hold edit below changes.
 *
 * <p>
 * GraviSuite's hover horizontal flight is not a literal: the same method calls
 * {@code func_70060_a(0.0f, 0.4f * load * base * 2.0f + boost, 0.02f + boost)}
 * with {@code base = 0.3f} normal and {@code 0.65f} while hovering, and {@code
 * boost = 0.09f} normal and {@code 0.07f} while hovering. Without the boost key
 * that is forward {@code 0.4f * 0.65f * 2.0f = 0.52f} and friction
 * {@code 0.02f}. Vanilla's {@code moveFlying} adds {@code friction * forward} to
 * {@code motionX}/{@code motionZ} each tick and multiplies by the 0.91 air drag,
 * so the steady hover sideways speed is {@code 0.02f * 0.52f / (1 - 0.91f)} =
 * {@value #HOVER_HORIZONTAL_SPEED} blocks a tick.
 *
 * <p>
 * <b>1. No vertical rewrite.</b> The Advanced Nano keeps GraviSuite's own stock
 * vertical hover clamps: {@value #HOVER_JUMP_CLAMP} up with jump and
 * {@value #SNEAK_CLAMP} down with sneak, equal in magnitude. That is its rung on
 * the flight ladder — the lowest one, below the IC2 Quantum's 0.4 — and it is not
 * trimmed towards the 0.1156 sideways speed or towards any other number, because
 * the ladder is vertical (see {@link FlightTiers}). This patch never rewrites
 * either clamp.
 *
 * <p>
 * The {@code -0.2d} literal also has to survive for its own sake:
 * {@code Gravitation-Suite-Neo} 1.3.16 (shipped on both sides of GTNH 2.9.0) has
 * a {@code ModifyExpressionValue} on this method at {@code CONSTANT} with
 * {@code doubleValue=-0.2}, and a non-optional injector with no target fails
 * critically. Patchee never rewrites it, so Neo finds it whether it runs before
 * or after Patchee. Patchee only reads it to find the sneak-clamp site that
 * anchors the hold.
 *
 * <p>
 * The clamp sites are still found by <i>shape</i> — {@code GETSTATIC keyboard,
 * POP, ALOAD player, INVOKESTATIC key, IFEQ, LDC2_W double, DSTORE local} —
 * because the shift+space hold is anchored inside the sneak-taken block. The
 * value filter tells them apart: the jump site accepts only the stock
 * {@value #HOVER_JUMP_CLAMP}, the sneak site only the stock {@value #SNEAK_CLAMP}.
 * The hold branch's own {@code 0.0d} store has the same shape as a jump site, so
 * it is excluded by value.
 *
 * <p>
 * <b>2. Hold.</b> Because sneak is tested after jump, holding both keys gives the
 * sneak target (stock {@code -0.2d}) and the player sinks. Edit 2 inserts seven
 * instructions after the sneak store and before the label its {@code IFEQ} jumps
 * to:
 *
 * <pre>
 * v = -0.2d; // stock, untouched
 * if (isJumpKeyDown(player)) v = 0.0d; // &lt;- inserted last, so it wins
 * </pre>
 *
 * Sneak alone keeps the stock sink, sneak + jump overrides it to {@code 0.0d},
 * and the clamp then targets level flight. GraviSuite still restores a larger
 * previous motion, so a climb already in progress is damped to a stop over a few
 * ticks, not cut dead. The keyboard field and the player local are copied from
 * the method's own instructions — no name is guessed. The branch adds no
 * {@code GOTO}, no new label and no new frame: its {@code IFEQ} jumps to the
 * sneak branch's own join label, which already carries a frame. Sitting after
 * the store also keeps the sneak-clamp shape intact, so a second pass still finds
 * the site.
 *
 * <p>
 * <b>3. Ground guard.</b> The method ends (offsets 499-549) by clearing
 * {@code fallDistance} and {@code field_70140_Q} and reflectively zeroing the
 * server's {@code NetHandlerPlayServer.floatingTickCount}, with no
 * {@code onGround} guard. The guard is one branch at method entry:
 *
 * <pre>
 * if (hover &amp;&amp; ChestplateFlightHooks.skipGrounded(player)) return false;
 * </pre>
 *
 * gated on the method's own hover flag (local 1), so it runs only while hover
 * mode is on. It calls the same hook class the IC2 patch uses; no second hooks
 * class exists. A player standing on the ground no longer looks like a flyer.
 * The armour tick runs <b>before</b> the jump and gravity step: {@code
 * EntityPlayer.onLivingUpdate} calls {@code inventory.decrementAnimations()} at
 * {@code EntityPlayer.java:610}, and only then {@code super.onLivingUpdate()} at
 * {@code :613}, which is where {@code jump()} and gravity run. On the takeoff
 * tick {@code motionY} is therefore still 0, the guard does fire, and lift-off
 * comes from vanilla's own 0.42 jump impulse rather than from the jetpack; the
 * jetpack takes over on the next tick.
 *
 * <p>
 * <b>One method, two items.</b> {@code ItemAdvancedNanoChestPlate.onArmorTick}
 * calls {@code ItemAdvancedJetPack.onArmorTick}, which calls this static method,
 * so one patch covers both the Advanced Jetpack and the Advanced Nano
 * Chestplate. {@code ItemGraviChestPlate} is a different code path (creative
 * flight through {@code PlayerCapabilities}) and is deliberately not touched.
 *
 * <p>
 * Everything is deliberately suspicious: the class name must match, the method
 * must exist with the expected descriptor and be static, exactly one 0.6
 * ceiling literal must exist, and exactly one jump-clamp site of the shape above
 * whose double is {@value #HOVER_JUMP_CLAMP}, next to at most one <b>stock</b>
 * sneak site storing to the same local whose double
 * is {@value #SNEAK_CLAMP}. A jump clamp with any other value (say one already
 * retargeted to {@code 0.6}) is simply not a site, and such a class is refused
 * with the "expected exactly 1 hover+jump clamp site … found 0" message. Any
 * other shape is refused with a reason and the class is handed back untouched.
 *
 * <p>
 * A <i>missing</i> sneak clamp is not a refusal. The shift+space hold is anchored
 * inside the sneak-taken block, so when the hold switch is on and that site is
 * absent the hold cannot be placed: it is skipped with a reason while the ground
 * guard, if requested, still applies, which makes the result
 * {@link State#PARTIAL}. With the hold switch off the missing site is irrelevant:
 * the guard alone still applies and there is no PARTIAL. With nothing left to
 * apply the bytes are unchanged and the reason says so. Idempotent: a second pass
 * over patched bytes reports {@link State#ALREADY_PATCHED}.
 */
public final class GraviSuiteFlightPatch {

    /** The class GraviSuite ships, as the class loader names it. */
    public static final String TARGET_CLASS = "gravisuite.ItemAdvancedJetPack";

    /** The same class, in internal (slash) form. */
    public static final String TARGET_INTERNAL_NAME = "gravisuite/ItemAdvancedJetPack";

    /** The one method this patch touches. */
    public static final String METHOD_NAME = "useJetpack";

    /** {@code (EntityPlayer, boolean) -> boolean}: the second argument is the hover flag. */
    public static final String METHOD_DESC = "(Lnet/minecraft/entity/player/EntityPlayer;Z)Z";

    /** GraviSuite's keyboard helper — the class the jump/sneak checks are called on. */
    public static final String KEYBOARD_OWNER = "gravisuite/keyboard/Keyboard";

    /** The static field that pushes the keyboard, checked through its type. */
    public static final String KEYBOARD_FIELD_DESC = "L" + KEYBOARD_OWNER + ";";

    /** The jump-key query that guards the hover climb clamp. */
    public static final String JUMP_KEY = "isJumpKeyDown";

    /** The sneak-key query that runs after it and sinks the player. */
    public static final String SNEAK_KEY = "isSneakKeyDown";

    /** {@code (EntityPlayer) -> boolean}. */
    public static final String KEY_DESC = "(Lnet/minecraft/entity/player/EntityPlayer;)Z";

    /** The Patchee class holding the injected method — the existing IC2 hook class. */
    public static final String HOOK_OWNER = ChestplateFlightPatch.HOOK_OWNER;

    /** The injected ground-guard predicate. */
    public static final String GUARD_HOOK = ChestplateFlightPatch.GUARD_HOOK;

    /** {@code (EntityPlayer) -> boolean}. */
    public static final String GUARD_HOOK_DESC = ChestplateFlightPatch.GUARD_HOOK_DESC;

    /** The normal ascent ceiling as the class stores it: {@code (double) 0.6f}. */
    public static final double ASCENT_CEILING_CONSTANT = 0.6000000238418579d;

    /**
     * The forward argument GraviSuite hands vanilla's {@code moveFlying} while
     * hovering, with load factor 1: {@code func_70060_a(0.0f, 0.4f * load * base
     * * 2.0f + boost, 0.02f + boost)} with {@code base = 0.65f} and, without the
     * boost key, {@code boost = 0f}, so {@code 0.4f * 0.65f * 2.0f = 0.52f}.
     */
    public static final float HORIZONTAL_FORWARD = 0.4f * 0.65f * 2.0f;

    /** The third argument of that same call: the per-tick push {@code 0.02f}. */
    public static final float HORIZONTAL_FRICTION = 0.02f;

    /** Vanilla's horizontal air drag applied to the player every tick: {@code 0.91f}. */
    public static final float AIR_DRAG = 0.91f;

    /**
     * The speed GraviSuite's hover flight moves the player sideways at, derived
     * from its own bytecode: vanilla's {@code moveFlying} adds {@code friction *
     * forward / max(1, length)} = {@code 0.02f * 0.52f = 0.0104f} to
     * {@code motionX}/{@code motionZ} each tick and then multiplies by
     * {@value #AIR_DRAG}, so the steady state is {@code 0.0104f / (1 - 0.91f)}.
     * GraviSuite has no ramp of its own; this is the accumulation.
     *
     * <p>
     * Horizontal only. The vertical hover clamps are GraviSuite's stock 0.2 up /
     * 0.2 down and are not written from this number — the ladder is vertical
     * (see {@link FlightTiers}), the sideways axis is not.
     */
    public static final double HOVER_HORIZONTAL_SPEED = HORIZONTAL_FRICTION * HORIZONTAL_FORWARD / (1.0f - AIR_DRAG);

    /**
     * GraviSuite's stock hover+jump clamp, {@code 0.2d}. This patch reads it to
     * find the jump-clamp site and names it in the log as the value it leaves
     * alone; it never rewrites it.
     */
    public static final double HOVER_JUMP_CLAMP = 0.2d;

    /**
     * GraviSuite's stock hover+sneak clamp, {@code -0.2d}. This patch reads it to
     * find the sneak-clamp site that anchors the hold and names it in the log as
     * the value it leaves alone; it never rewrites it.
     */
    public static final double SNEAK_CLAMP = -0.2d;

    /** The vertical target for shift + space: hold altitude, no drift either way. */
    public static final double HOLD_ALTITUDE_TARGET = 0.0d;

    /** How many hover-clamp sites a healthy class has. */
    public static final int SITE_COUNT = 1;

    /** How many ascent-ceiling literals a healthy class has. */
    public static final int ASCENT_CEILING_SITES = 1;

    /**
     * The detail when the shift+space hold was requested and the hover+sneak
     * clamp site is not in these bytes.
     */
    static final String VERTICAL_SKIPPED_ALONE = "the hover+sneak clamp site is not in these bytes, so the shift+space hold could not be placed (another mixin may already handle shift+space); the ground guard still applies if it was requested";

    /** Appended to the detail when the guard went in but the hold could not be placed. */
    static final String VERTICAL_SKIPPED_SUFFIX = ", shift+space hold skipped: the hover+sneak clamp site is not in these bytes, so the hold could not be anchored";

    /** What {@link #apply} found and did. */
    public enum State {

        /** At least one requested edit was made; {@link Result#bytes()} is the patched class. */
        PATCHED,

        /**
         * No edit was made: either every requested edit was already in place, or
         * none of them could be placed here. Bytes are unchanged and
         * {@link Result#detail()} says which.
         */
        ALREADY_PATCHED,

        /**
         * At least one requested edit was made, and at least one requested edit
         * could not be located in these bytes and was skipped with a reason;
         * {@link Result#bytes()} is the patched class.
         */
        PARTIAL,

        /** The class is not the shape this patch knows; bytes are unchanged. */
        UNEXPECTED_SHAPE
    }

    /** The outcome of one {@link #apply} call. */
    public static final class Result {

        private final State state;
        private final byte[] bytes;
        private final int sites;
        private final boolean liftApplied;
        private final boolean guardApplied;
        private final boolean holdApplied;
        private final boolean skipped;
        private final String detail;

        Result(State state, byte[] bytes, int sites, boolean liftApplied, boolean guardApplied, boolean holdApplied,
            boolean skipped, String detail) {
            this.state = state;
            this.bytes = bytes;
            this.sites = sites;
            this.liftApplied = liftApplied;
            this.guardApplied = guardApplied;
            this.holdApplied = holdApplied;
            this.skipped = skipped;
            this.detail = detail;
        }

        public State state() {
            return state;
        }

        /**
         * The class bytes to hand back to the loader. Only null when the input was
         * null, which is refused and handed back as-is.
         */
        public byte[] bytes() {
            return bytes;
        }

        /** How many edits this pass made (0 for an already-patched class). */
        public int sites() {
            return sites;
        }

        /**
         * Always false. This patch never rewrites the vertical clamps of the
         * Advanced Nano, so no pass ever applies a lift. Kept so callers can ask
         * the question without special-casing this patch.
         */
        public boolean liftApplied() {
            return liftApplied;
        }

        /** True when this pass injected the ground guard. */
        public boolean guardApplied() {
            return guardApplied;
        }

        /** True when this pass injected the shift+space hold. */
        public boolean holdApplied() {
            return holdApplied;
        }

        /** True when the requested shift+space hold was skipped because its site is absent. */
        public boolean skipped() {
            return skipped;
        }

        /** What was wrong, or what was done, in one line. */
        public String detail() {
            return detail;
        }
    }

    private GraviSuiteFlightPatch() {}

    /**
     * Apply the requested edits.
     *
     * @param original the class bytes as the loader sees it
     * @param lift     the hover-lift switch; accepted for a common call shape but
     *                 it has no effect here, because this patch never rewrites the
     *                 Advanced Nano's stock vertical clamps
     * @param guard    inject the on-the-ground early return
     * @param hold     inject the shift + space hold-altitude branch
     */
    public static Result apply(byte[] original, boolean lift, boolean guard, boolean hold) {
        if (original == null || original.length == 0) {
            return new Result(
                State.UNEXPECTED_SHAPE,
                original,
                0,
                false,
                false,
                false,
                false,
                "the class bytes are empty");
        }
        ClassNode node;
        try {
            node = read(original);
        } catch (RuntimeException unreadable) {
            return new Result(
                State.UNEXPECTED_SHAPE,
                original,
                0,
                false,
                false,
                false,
                false,
                "the class bytes could not be read: " + unreadable);
        }
        if (!TARGET_INTERNAL_NAME.equals(node.name)) {
            return new Result(
                State.UNEXPECTED_SHAPE,
                original,
                0,
                false,
                false,
                false,
                false,
                "this is " + node.name + ", not " + TARGET_INTERNAL_NAME);
        }
        MethodNode method = findTarget(node);
        if (method == null) {
            return new Result(
                State.UNEXPECTED_SHAPE,
                original,
                0,
                false,
                false,
                false,
                false,
                "no " + METHOD_NAME + METHOD_DESC + " method in " + TARGET_CLASS);
        }
        if ((method.access & Opcodes.ACC_STATIC) == 0) {
            return new Result(
                State.UNEXPECTED_SHAPE,
                original,
                0,
                false,
                false,
                false,
                false,
                METHOD_NAME + " is not static, so local 0 is not the player and this patch cannot locate it");
        }

        List<ClampSite> jumpSites = clampSites(method, JUMP_KEY);
        int ceilings = countAscentCeilings(method);
        if (ceilings != ASCENT_CEILING_SITES) {
            return new Result(
                State.UNEXPECTED_SHAPE,
                original,
                0,
                false,
                false,
                false,
                false,
                "expected exactly " + ASCENT_CEILING_SITES
                    + " ascent ceiling literal ("
                    + ASCENT_CEILING_CONSTANT
                    + ") in "
                    + METHOD_NAME
                    + ", found "
                    + ceilings);
        }
        if (jumpSites.size() != SITE_COUNT) {
            return new Result(
                State.UNEXPECTED_SHAPE,
                original,
                0,
                false,
                false,
                false,
                false,
                "expected exactly " + SITE_COUNT
                    + " hover+jump clamp site (isJumpKeyDown -> ifeq -> ldc2_w double -> dstore), found "
                    + jumpSites.size());
        }
        ClampSite clamp = jumpSites.get(0);
        int clampLocal = clamp.local;

        List<ClampSite> sneakSites = clampSites(method, SNEAK_KEY);
        ClampSite sneak = null;
        int sneaks = 0;
        for (ClampSite candidate : sneakSites) {
            if (candidate.local != clampLocal) {
                continue;
            }
            sneaks++;
            sneak = candidate;
        }
        if (sneaks > SITE_COUNT) {
            return new Result(
                State.UNEXPECTED_SHAPE,
                original,
                0,
                false,
                false,
                false,
                false,
                "expected at most " + SITE_COUNT
                    + " hover+sneak clamp site storing to local "
                    + clampLocal
                    + " (the jump clamp's local), found "
                    + sneaks);
        }

        // The Advanced Nano sits on the lowest rung of the flight ladder, so this
        // patch never rewrites the vertical clamps: the jump clamp keeps
        // GraviSuite's stock 0.2d and the stock -0.2d sink stays as it ships. The
        // shift+space hold still has to be anchored inside the sneak-taken block,
        // so the stock sneak site is still required for it; the ground guard does
        // not need it.
        //
        // This is the one place the twin classes differ, and it is geometry, not
        // intent: GraviSuite's hold can only be anchored on the sneak-taken block's
        // join label, which is not there when the stock sneak clamp site is gone, so
        // the hold is skipped without the site. IC2's hold is anchored on the jump
        // store instead, so ChestplateFlightPatch still places it in that case.
        boolean verticalApplicable = sneak != null;
        // The lift argument is deliberately not consulted: no vertical rewrite ships
        // for this item, so liftApplied is false on every path.
        boolean guardDone = hasGuardHook(method);
        boolean holdDone = verticalApplicable && hasHoldBranch(method, sneak);
        // The hold is added inside the sneak-taken block by placeHold.
        boolean needHold = hold && verticalApplicable && !holdDone;
        boolean needGuard = guard && !guardDone;
        // A missing sneak site is not a refusal: the guard still applies, and only a
        // hold that really could not be placed is PARTIAL.
        boolean holdBlocked = hold && !verticalApplicable;
        boolean skipped = holdBlocked;
        if (!needGuard && !needHold) {
            return new Result(
                State.ALREADY_PATCHED,
                original,
                0,
                false,
                false,
                false,
                skipped,
                skipped ? VERTICAL_SKIPPED_ALONE : "");
        }

        int sitesChanged = 0;
        if (needHold) {
            if (!placeHold(method, sneak, clampLocal)) {
                return new Result(
                    State.UNEXPECTED_SHAPE,
                    original,
                    0,
                    false,
                    false,
                    false,
                    false,
                    "the sneak branch's stock store is not a DSTORE to the jump clamp's local, or its join "
                        + "label is not the target of exactly one jump, so the shift+space branch cannot be placed");
            }
            sitesChanged++;
        }
        if (needGuard) {
            insertGuard(method);
            sitesChanged++;
        }
        String detail = describe(needGuard, needHold);
        if (skipped) {
            return new Result(
                State.PARTIAL,
                write(node),
                sitesChanged,
                false,
                needGuard,
                needHold,
                true,
                detail + VERTICAL_SKIPPED_SUFFIX);
        }
        return new Result(State.PATCHED, write(node), sitesChanged, false, needGuard, needHold, false, detail);
    }

    private static String describe(boolean guard, boolean hold) {
        StringBuilder text = new StringBuilder();
        if (hold) {
            text.append("shift+space target ")
                .append(HOLD_ALTITUDE_TARGET);
        }
        if (guard) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append("ground guard");
        }
        return text.toString();
    }

    /** The one {@code useJetpack(EntityPlayer, boolean)} method. */
    private static MethodNode findTarget(ClassNode node) {
        for (MethodNode method : node.methods) {
            if (METHOD_NAME.equals(method.name) && METHOD_DESC.equals(method.desc)) {
                return method;
            }
        }
        return null;
    }

    /**
     * How many {@code (double) 0.6f} literals the method carries. The patch never
     * writes this value, so a patched class carries exactly the one ceiling; no
     * jump-site subtraction is needed, because {@link #clampSites} only accepts the
     * stock {@value #HOVER_JUMP_CLAMP} at a jump site, never the ceiling. The
     * ceiling count is checked before the jump-site count, so a class whose jump
     * clamp was retargeted to {@code 0.6} by something else carries two ceilings
     * and is refused by this count, not by the site check.
     */
    private static int countAscentCeilings(MethodNode method) {
        int count = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof LdcInsnNode) {
                Object cst = ((LdcInsnNode) insn).cst;
                if (cst instanceof Double && ((Double) cst).doubleValue() == ASCENT_CEILING_CONSTANT) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * The clamp constants one key guard writes:
     * {@code GETSTATIC keyboard, POP, ALOAD player, INVOKESTATIC key,
     * IFEQ join, LDC2_W double, DSTORE local}.
     *
     * <p>
     * The value is filtered by the caller: the jump site accepts only the stock
     * {@value #HOVER_JUMP_CLAMP}, the sneak site only the stock
     * {@value #SNEAK_CLAMP}. A jump query with any other value (including the 0.0
     * the hold branch writes) is not this shape.
     */
    private static List<ClampSite> clampSites(MethodNode method, String key) {
        List<ClampSite> sites = new ArrayList<ClampSite>();
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (!isKeyCall(insn, key)) {
                continue;
            }
            AbstractInsnNode player = previousReal(insn);
            if (player == null || player.getOpcode() != Opcodes.ALOAD) {
                continue;
            }
            AbstractInsnNode pop = previousReal(player);
            if (pop == null || pop.getOpcode() != Opcodes.POP) {
                continue;
            }
            AbstractInsnNode field = previousReal(pop);
            if (!(field instanceof FieldInsnNode) || field.getOpcode() != Opcodes.GETSTATIC) {
                continue;
            }
            FieldInsnNode keyboard = (FieldInsnNode) field;
            if (!KEYBOARD_FIELD_DESC.equals(keyboard.desc)) {
                continue;
            }
            AbstractInsnNode branch = nextReal(insn.getNext());
            if (branch == null || branch.getOpcode() != Opcodes.IFEQ) {
                continue;
            }
            AbstractInsnNode value = nextReal(branch.getNext());
            if (!(value instanceof LdcInsnNode) || !(((LdcInsnNode) value).cst instanceof Double)) {
                continue;
            }
            AbstractInsnNode store = nextReal(value.getNext());
            if (store == null || store.getOpcode() != Opcodes.DSTORE) {
                continue;
            }
            double constant = ((Double) ((LdcInsnNode) value).cst).doubleValue();
            if (JUMP_KEY.equals(key)) {
                if (constant != HOVER_JUMP_CLAMP) {
                    continue;
                }
            } else if (constant != SNEAK_CLAMP) {
                continue;
            }
            sites.add(
                new ClampSite(
                    (LdcInsnNode) value,
                    ((JumpInsnNode) branch).label,
                    ((VarInsnNode) store).var,
                    keyboard,
                    ((VarInsnNode) player).var,
                    (MethodInsnNode) insn));
        }
        return sites;
    }

    private static boolean isKeyCall(AbstractInsnNode insn, String key) {
        if (insn.getOpcode() != Opcodes.INVOKESTATIC) {
            return false;
        }
        MethodInsnNode call = (MethodInsnNode) insn;
        return KEYBOARD_OWNER.equals(call.owner) && key.equals(call.name) && KEY_DESC.equals(call.desc);
    }

    /** The next instruction that is more than a label, line number or frame. */
    private static AbstractInsnNode nextReal(AbstractInsnNode insn) {
        AbstractInsnNode cursor = insn;
        while (cursor != null && cursor.getOpcode() < 0) {
            cursor = cursor.getNext();
        }
        return cursor;
    }

    /** The previous instruction that is more than a label, line number or frame. */
    private static AbstractInsnNode previousReal(AbstractInsnNode insn) {
        AbstractInsnNode cursor = insn.getPrevious();
        while (cursor != null && cursor.getOpcode() < 0) {
            cursor = cursor.getPrevious();
        }
        return cursor;
    }

    /** True when the method already calls this patch's ground guard. */
    static boolean hasGuardHook(MethodNode method) {
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn.getOpcode() != Opcodes.INVOKESTATIC) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode) insn;
            if (HOOK_OWNER.equals(call.owner) && GUARD_HOOK.equals(call.name) && GUARD_HOOK_DESC.equals(call.desc)) {
                return true;
            }
        }
        return false;
    }

    /**
     * True when the shift+space overwrite is already in the sneak-taken block: a
     * {@code 0.0d} store to the clamp local sitting after the sneak-only store,
     * before the sneak branch's join label.
     */
    static boolean hasHoldBranch(MethodNode method, ClampSite sneak) {
        AbstractInsnNode store = nextReal(sneak.value.getNext());
        if (store == null) {
            return false;
        }
        for (AbstractInsnNode insn = store.getNext(); insn != null; insn = insn.getNext()) {
            if (insn == sneak.join) {
                return false;
            }
            if (!(insn instanceof LdcInsnNode)) {
                continue;
            }
            Object cst = ((LdcInsnNode) insn).cst;
            if (!(cst instanceof Double) || ((Double) cst).doubleValue() != HOLD_ALTITUDE_TARGET) {
                continue;
            }
            AbstractInsnNode after = nextReal(insn.getNext());
            if (after != null && after.getOpcode() == Opcodes.DSTORE && ((VarInsnNode) after).var == sneak.local) {
                return true;
            }
        }
        return false;
    }

    /**
     * Put the shift+space hold inside the sneak-taken block, right after the stock
     * sneak store:
     *
     * <pre>
     * v = -0.2d; // stock, untouched
     * if (isJumpKeyDown(player)) v = 0.0d; // hold, so it wins
     * </pre>
     *
     * <p>
     * The {@code IFEQ} jumps to the sneak branch's own join label, so no
     * {@code GOTO}, no new label and no new frame is added. Refuses - the caller
     * fails the whole patch - unless exactly one jump targets that join label.
     *
     * <p>
     * Refuses - the caller fails the whole patch - unless the stock sneak store is
     * a {@code DSTORE} to the jump clamp's local and exactly one jump targets the
     * join label.
     *
     * @return true when the hold was placed
     */
    private static boolean placeHold(MethodNode method, ClampSite sneak, int local) {
        AbstractInsnNode stockStore = nextReal(sneak.value.getNext());
        if (stockStore == null || stockStore.getOpcode() != Opcodes.DSTORE || ((VarInsnNode) stockStore).var != local) {
            return false;
        }
        InsnList ordered = new InsnList();
        if (!appendHold(method, sneak, local, ordered)) {
            return false;
        }
        method.instructions.insert(stockStore, ordered);
        return true;
    }

    /**
     * Append the shift+space overwrite to the ordered edit list that goes inside
     * the sneak-taken block:
     *
     * <pre>
     * v = -0.2d; // stock, untouched
     * if (isJumpKeyDown(player)) v = 0.0d; // &lt;- appended, so it wins
     * </pre>
     *
     * The {@code IFEQ} jumps to the sneak branch's own join label, so no
     * {@code GOTO}, no new label and no new frame is added. Refuses - the caller
     * fails the whole patch - unless exactly one jump targets that join label.
     *
     * @return true when the seven instructions were appended
     */
    private static boolean appendHold(MethodNode method, ClampSite sneak, int local, InsnList inserted) {
        LabelNode join = sneak.join;
        // the join label must belong to the sneak branch and nothing else
        int joins = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof JumpInsnNode && ((JumpInsnNode) insn).label == join) {
                joins++;
            }
        }
        if (joins != 1) {
            return false;
        }
        inserted
            .add(new FieldInsnNode(Opcodes.GETSTATIC, sneak.keyboard.owner, sneak.keyboard.name, sneak.keyboard.desc));
        inserted.add(new InsnNode(Opcodes.POP));
        inserted.add(new VarInsnNode(Opcodes.ALOAD, sneak.playerLocal));
        inserted.add(new MethodInsnNode(Opcodes.INVOKESTATIC, sneak.jumpCall.owner, JUMP_KEY, KEY_DESC, false));
        inserted.add(new JumpInsnNode(Opcodes.IFEQ, join));
        inserted.add(new LdcInsnNode(Double.valueOf(HOLD_ALTITUDE_TARGET)));
        inserted.add(new VarInsnNode(Opcodes.DSTORE, local));
        return true;
    }

    /**
     * Prepend the hover-gated ground guard:
     *
     * <pre>
     * if (hover &amp;&amp; ChestplateFlightHooks.skipGrounded(player)) return false;
     * </pre>
     *
     * <p>
     * The method is static, so the player is local 0 and the hover flag is local
     * 1. The first {@code IFEQ} on the hover flag skips the rest of the guard,
     * so hover-off jetpack flight keeps stock GraviSuite behaviour. Both
     * {@code IFEQ}s branch to one new label placed at the original first
     * instruction. That label is a branch target, so it needs a stack map frame:
     * {@code F_SAME} (same locals as the implicit entry frame, empty stack),
     * which is exactly true there. No local is added and the stack depth at
     * every existing label is unchanged, so the class's own frames are simply
     * moved — {@code COMPUTE_MAXS}, never {@code COMPUTE_FRAMES}.
     */
    private static void insertGuard(MethodNode method) {
        LabelNode proceed = new LabelNode();
        InsnList guard = new InsnList();
        guard.add(new VarInsnNode(Opcodes.ILOAD, 1));
        guard.add(new JumpInsnNode(Opcodes.IFEQ, proceed));
        guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
        guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK_OWNER, GUARD_HOOK, GUARD_HOOK_DESC, false));
        guard.add(new JumpInsnNode(Opcodes.IFEQ, proceed));
        guard.add(new InsnNode(Opcodes.ICONST_0));
        guard.add(new InsnNode(Opcodes.IRETURN));
        guard.add(proceed);
        guard.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        method.instructions.insert(guard);
    }

    // COMPUTE_MAXS, never COMPUTE_FRAMES: the frames read from GraviSuite's class
    // stay as they are (each edit adds exactly one new frame at its own branch
    // target and changes no stack depth at any existing label) and ASM only moves
    // them.
    private static byte[] write(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        // No EXPAND_FRAMES: the StackMapTable frames must be kept in the form the
        // class carries, so the writer can move them instead of recomputing them.
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    /** One clamp site: the value, the join label, the store's local, and its prelude. */
    static final class ClampSite {

        final LdcInsnNode value;
        final LabelNode join;
        final int local;
        final FieldInsnNode keyboard;
        final int playerLocal;
        final MethodInsnNode jumpCall;

        ClampSite(LdcInsnNode value, LabelNode join, int local, FieldInsnNode keyboard, int playerLocal,
            MethodInsnNode jumpCall) {
            this.value = value;
            this.join = join;
            this.local = local;
            this.keyboard = keyboard;
            this.playerLocal = playerLocal;
            this.jumpCall = jumpCall;
        }
    }

    /** One line per instruction of {@code useJetpack}, for the fail-loud dump. */
    public static String dump(byte[] bytes) {
        try {
            ClassNode node = read(bytes);
            MethodNode method = findTarget(node);
            if (method == null) {
                return "no " + METHOD_NAME + METHOD_DESC + " method";
            }
            StringBuilder text = new StringBuilder();
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn.getOpcode() < 0) {
                    continue;
                }
                text.append(opcodeText(insn))
                    .append(' ');
            }
            return text.toString();
        } catch (RuntimeException unreadable) {
            return "the class bytes could not be read: " + unreadable;
        }
    }

    private static String opcodeText(AbstractInsnNode insn) {
        if (insn instanceof MethodInsnNode) {
            MethodInsnNode call = (MethodInsnNode) insn;
            return call.owner + "." + call.name + call.desc;
        }
        if (insn instanceof LdcInsnNode) {
            return "#" + ((LdcInsnNode) insn).cst;
        }
        return String.valueOf(insn.getOpcode());
    }
}
