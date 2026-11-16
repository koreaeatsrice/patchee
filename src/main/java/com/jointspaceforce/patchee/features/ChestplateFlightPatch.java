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
 * The ChestplateFlight byte patch: three small, independent edits inside IC2's
 * {@code ItemArmorQuantumSuit.useJetpack(EntityPlayer, boolean)}.
 *
 * <p>
 * <b>1. Hover vertical speed.</b> The shipped GTNH IC2 build computes the rise as
 *
 * <pre>
 * motionY = Math.min(motionY + f4 * 0.2f, 0.6000000238418579d); // offsets 196-221
 * </pre>
 *
 * and then, only while hovering, clamps it to a local {@code v} chosen from
 * three constants (offsets 224-261):
 *
 * <pre>
 * v = -0.025f; // hover, no key
 * if (Keyboard.isSneakKeyDown(player)) v = -0.1f; // &lt;- defect: sinks at 0.1
 * if (Keyboard.isJumpKeyDown(player)) v = 0.1f; // &lt;- defect: climbs at 0.1
 * </pre>
 *
 * followed by {@code if (motionY > v) motionY = v;} and
 * {@code if (oldMotionY > motionY) motionY = oldMotionY;} (offsets 263-294).
 *
 * <p>
 * The fix rewrites <b>the jump clamp only</b>: that {@code 0.1f} becomes
 * {@code +}{@value ChestplateFlight#HOVER_HORIZONTAL_SPEED}, the speed IC2's own
 * hover flight already moves the player sideways at
 * ({@code 0.02f * 0.4f / (1 - 0.91f)}, offsets 306-318 are the
 * {@code func_70060_a(0.0f, 0.4f, 0.02f)} call, at load factor 1). The descent is
 * <b>not</b> a rewrite: the stock {@code -0.1f} literal is deliberately kept in
 * the class (Hodgepodge matches {@code -0.025f} by value on this same method, and
 * keeping every stock literal is the rule for composing with value-matching
 * mixins), and the patch <b>injects</b> two instructions right after the stock
 * sneak store:
 *
 * <pre>
 * if (isSneakKeyDown(player)) {
 *     v = -0.1f; // stock, untouched
 *     v = -0.0888889f; // &lt;- inserted: LDC + FSTORE, same local
 * }
 * </pre>
 *
 * The two writes are one decision: the climb is never raised without the sink
 * matching it. The no-input {@code -0.025f} literal is never touched either, so
 * hovering still hovers, and the {@code 0.6d} ceiling is left in place (the patch
 * does not write it any more; the shape check still requires exactly one).
 *
 * <p>
 * Both sites are found by <i>shape</i>, never by the value of their neighbours:
 *
 * <pre>
 * INVOKEVIRTUAL ic2/core/util/Keyboard.isJumpKeyDown (LEntityPlayer;)Z
 * IFEQ ...
 * LDC &lt;float&gt;        // 0.1f stock, or +0.0888889 once this patch has run
 * FSTORE &lt;local&gt;     // the local both writes / the sneak store use
 * </pre>
 *
 * <p>
 * The sneak site, earlier in the method, is an {@code LDC float} of the
 * <b>stock</b> {@value ChestplateFlight#IC2_SNEAK_CLAMP} immediately followed by an
 * {@code FSTORE} of that <i>same</i> local. Only the stock value is accepted, so
 * the injected descent store is never mistaken for a second site: the site count
 * stays one on a patched class. Presence of the edit is decided separately, by
 * {@code descentPatched}: the exact sequence {@code LDC -0.1f, FSTORE local,
 * LDC -0.0888889f, FSTORE local}. A class that already carries it reports
 * {@link State#ALREADY_PATCHED}; it is never injected twice.
 *
 * <p>
 * A missing sneak site is not a refusal: the lift is then <b>skipped</b> with a
 * reason, because raising the climb alone is exactly the owner's bug. With
 * nothing left to apply the bytes are unchanged ({@link State#ALREADY_PATCHED},
 * {@link Result#skipped()}), and neither value is written.
 *
 * <p>
 * <b>2. Hold altitude (shift + space).</b> Because the jump test runs after the
 * sneak test, holding both keys gives the jump target and the player drifts
 * upward. Edit 2 inserts one more branch inside the jump-taken block, <i>after</i>
 * the jump-only store and before the block's join label:
 *
 * <pre>
 * if (isJumpKeyDown(player)) {
 *     v = 0.1f; // or +0.0888889 with edit 1
 *     if (isSneakKeyDown(player)) {
 *         v = 0.0f; // &lt;- inserted, checked last
 *     }
 * } // join label, frame unchanged
 * </pre>
 *
 * so jump alone keeps its patched value and {@code (sneak, jump) = (+, +)}
 * overwrites it with {@code 0.0f}; the clamp then targets level flight. IC2 still restores a larger previous motion, so
 * a climb already
 * in progress is damped to a stop over a few ticks, not cut dead. Each key alone keeps its meaning
 * ({@code (+,-)} sinks at the patched descent speed, {@code (-,+)} climbs at the
 * patched ascent speed). The keyboard call and the player local are copied from
 * the method's own instructions — no name is guessed.
 *
 * <p>
 * The branch adds no {@code GOTO}, no new label and no new frame: its
 * {@code IFEQ} jumps to the jump branch's own join label, which already carries
 * a frame. Sitting after the store also keeps the jump-clamp shape
 * ({@code isJumpKeyDown -> IFEQ -> ldc float -> fstore}) intact, so a second pass
 * still finds the site and reports {@link State#ALREADY_PATCHED}.
 *
 * <p>
 * <b>Hodgepodge.</b> GTNH's Hodgepodge ships
 * {@code mixins.late.ic2.MixinIc2QuantumSuitHoverMode}, a {@code @ModifyConstant}
 * on this same method that replaces the {@code -0.025f} literal with
 * {@code 0.0f} when its {@code fixIc2HoverMode} switch is on (the default). None
 * of the three edits reads, writes or matches that literal, and Hodgepodge only
 * needs {@code -0.025f} to still exist, so the two patches compose in
 * <b>either</b> order. Edit 2 cannot collide with it at all: the hold case is
 * reached only when <i>both</i> keys are down and never passes through the
 * default constant. Order-independence is checked by
 * {@code ChestplateFlightPatchTest} and by {@code tools/matcher-check} by
 * running the patch over both the stock bytes and the Hodgepodge-rewritten bytes
 * ({@code 0.0f} in the default clamp) and asserting every edit survives.
 *
 * <p>
 * <b>3. Ground guard.</b> The method ends by clearing {@code fallDistance}
 * ({@code field_70143_R}), {@code field_70140_Q} and calling
 * {@code IC2.platform.resetPlayerInAirTime(player)}, which reflectively zeroes
 * the server's {@code NetHandlerPlayServer.floatingTickCount} — vanilla's
 * anti-fly counter. The guard is one branch at method entry:
 *
 * <pre>
 * if (hover &amp;&amp; ChestplateFlightHooks.skipGrounded(player)) return false;
 * </pre>
 *
 * gated on the method's own hover flag (local 2), so it runs <i>only</i> while
 * hover mode is on: hover-off jetpack flight keeps stock IC2 behaviour on the
 * ground as well. With hover on, a player standing on the ground no longer looks
 * like they are flying. The armour tick runs <b>before</b> the jump and gravity
 * step ({@code EntityPlayer.onLivingUpdate} calls {@code inventory.decrementAnimations()}
 * at {@code EntityPlayer.java:610}, then {@code super.onLivingUpdate()} at
 * {@code :613}), so on the takeoff tick {@code motionY} is still 0 and the guard
 * does fire; lift-off comes from vanilla's own 0.42 jump impulse, not from the
 * jetpack, and the jetpack takes over on the next tick.
 *
 * <p>
 * Skipping the tick also skips that tick's return value, which IC2 stores as
 * {@code lastJetpackUsed} and uses to decide whether the jetpack loop sound keeps
 * playing; the sound stops on those grounded, non-rising ticks. Nothing else
 * consumes the value - in particular this method contains no container sync.
 *
 * <p>
 * Everything is deliberately suspicious: the method must exist with the expected
 * descriptor, the method must carry exactly one 0.6 ascent ceiling, exactly one
 * jump-clamp site of the shape above, and at most one stock sneak-clamp site
 * storing to the same local before it. Any other shape is refused with a reason
 * and the class is handed back untouched. A <i>missing</i> sneak site is not a
 * refusal: the lift is then skipped (the climb is never written without the
 * matching sink) and the
 * result is {@link State#PARTIAL} when the hold or the guard still went in, or
 * {@link State#ALREADY_PATCHED} with {@link Result#skipped()} when nothing else was
 * requested. Idempotent: a second pass over patched bytes reports
 * {@link State#ALREADY_PATCHED}.
 */
public final class ChestplateFlightPatch {

    /** The class IC2 ships, as the class loader names it. */
    public static final String TARGET_CLASS = "ic2.core.item.armor.ItemArmorQuantumSuit";

    /** The same class, in internal (slash) form. */
    public static final String TARGET_INTERNAL_NAME = "ic2/core/item/armor/ItemArmorQuantumSuit";

    /** The one method this patch touches. */
    public static final String METHOD_NAME = "useJetpack";

    /** {@code (EntityPlayer, boolean) -> boolean}: the second argument is the hover flag. */
    public static final String METHOD_DESC = "(Lnet/minecraft/entity/player/EntityPlayer;Z)Z";

    /** The Patchee class holding the injected method. */
    public static final String HOOK_OWNER = "com/jointspaceforce/patchee/features/ChestplateFlightHooks";

    /** The injected ground-guard predicate. */
    public static final String GUARD_HOOK = "skipGrounded";

    /** {@code (EntityPlayer) -> boolean}. */
    public static final String GUARD_HOOK_DESC = "(Lnet/minecraft/entity/player/EntityPlayer;)Z";

    /** IC2's keyboard helper — the class the jump/sneak checks are called on. */
    public static final String KEYBOARD_OWNER = "ic2/core/util/Keyboard";

    /** The jump-key query that guards the hover climb clamp. */
    public static final String JUMP_KEY = "isJumpKeyDown";

    /** {@code (EntityPlayer) -> boolean}. */
    public static final String JUMP_KEY_DESC = "(Lnet/minecraft/entity/player/EntityPlayer;)Z";

    /** The normal ascent ceiling as the class stores it: {@code (double) 0.6f}. */
    public static final double ASCENT_CEILING_CONSTANT = 0.6000000238418579d;

    /** IC2's stock hover+jump clamp. */
    public static final float HOVER_JUMP_CLAMP = ChestplateFlight.IC2_HOVER_JUMP_CLAMP;

    /** The value this patch writes at the jump site: IC2's own hover horizontal speed. */
    public static final float HOVER_JUMP_LIFTED = ChestplateFlight.hoverAscentTarget();

    /** IC2's stock hover+sneak clamp. */
    public static final float STOCK_SNEAK_CLAMP = ChestplateFlight.IC2_SNEAK_CLAMP;

    /**
     * The descent value this patch injects right after the stock sneak store: the
     * exact negation of the jump value, read from the pure decision so the two can
     * never drift apart.
     */
    public static final float SNEAK_LIFTED = ChestplateFlight.hoverDescentTarget();

    /**
     * The vertical target for shift + space: hold altitude, no drift either way.
     * Read from the pure decision table, not a second literal, so the float the
     * patch writes is the one the unit tests pin.
     */
    public static final float HOLD_ALTITUDE_TARGET = ChestplateFlight.hoverVerticalTarget(true, true, true);

    /** How many hover-clamp sites a healthy class has. */
    public static final int SITE_COUNT = 1;

    /** How many ascent-ceiling literals a healthy class has. */
    public static final int ASCENT_CEILING_SITES = 1;

    /** How many hover+sneak clamp sites a healthy class has. */
    public static final int SNEAK_SITE_COUNT = 1;

    /** The detail when the lift could not be applied because the sneak site is absent. */
    static final String LIFT_SKIPPED_ALONE = "the hover+sneak clamp site is not in these bytes, so the climb was left at stock (raising it without the matching sink would put the asymmetry back); nothing else was requested";

    /** Appended to the detail when other edits were made but the lift could not be. */
    static final String LIFT_SKIPPED_SUFFIX = ", climb and sink left at stock: the hover+sneak clamp site is not in these bytes, so raising the climb alone would put the asymmetry back";

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

        /** The class bytes to hand back to the loader (never null). */
        public byte[] bytes() {
            return bytes;
        }

        /** How many edits this pass made (0 for an already-patched class). */
        public int sites() {
            return sites;
        }

        /** True when this pass rewrote the hover+jump clamp and injected the descent store. */
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

        /** True when a requested edit was skipped because its site is absent. */
        public boolean skipped() {
            return skipped;
        }

        /** What was wrong, or what was done, in one line. */
        public String detail() {
            return detail;
        }
    }

    private ChestplateFlightPatch() {}

    /** Apply all three edits. */
    public static Result apply(byte[] original) {
        return apply(original, true, true, true);
    }

    /**
     * Apply the requested edits.
     *
     * @param original the class bytes as the loader sees them
     * @param lift     rewrite <b>both</b> hover clamps: jump to
     *                 {@link #HOVER_JUMP_LIFTED}, sneak to {@link #SNEAK_LIFTED}
     * @param guard    inject the on-the-ground early return
     * @param hold     inject the shift + space hold-altitude branch
     */
    public static Result apply(byte[] original, boolean lift, boolean guard, boolean hold) {
        if (original == null || original.length == 0) {
            return fail(original, "the class bytes are empty");
        }
        ClassNode node;
        try {
            node = read(original);
        } catch (RuntimeException unreadable) {
            return fail(original, "the class bytes could not be read: " + unreadable);
        }
        if (!TARGET_INTERNAL_NAME.equals(node.name)) {
            return fail(original, "this is " + node.name + ", not " + TARGET_INTERNAL_NAME);
        }
        MethodNode method = findTarget(node);
        if (method == null) {
            return fail(original, "no " + METHOD_NAME + METHOD_DESC + " method in " + TARGET_CLASS);
        }
        if ((method.access & Opcodes.ACC_STATIC) != 0) {
            return fail(
                original,
                METHOD_NAME + " is static, so local 1 is not the player and this patch cannot locate it");
        }

        int ceilings = countAscentCeilings(method);
        if (ceilings != ASCENT_CEILING_SITES) {
            return fail(
                original,
                "expected exactly " + ASCENT_CEILING_SITES
                    + " ascent ceiling literal ("
                    + ASCENT_CEILING_CONSTANT
                    + ") in "
                    + METHOD_NAME
                    + ", found "
                    + ceilings);
        }

        List<LdcInsnNode> sites = jumpClampSites(method);
        if (sites.size() != SITE_COUNT) {
            return fail(
                original,
                "expected exactly " + SITE_COUNT
                    + " hover+jump clamp site (isJumpKeyDown -> ifeq -> ldc float -> fstore), found "
                    + sites.size());
        }
        LdcInsnNode clamp = sites.get(0);
        float current = ((Float) clamp.cst).floatValue();
        if (current != HOVER_JUMP_CLAMP && current != HOVER_JUMP_LIFTED) {
            return fail(
                original,
                "the hover+jump clamp is " + current + ", expected " + HOVER_JUMP_CLAMP + " or " + HOVER_JUMP_LIFTED);
        }
        int clampLocal = storedLocal(clamp);

        List<LdcInsnNode> sneakSites = sneakClampSites(method, clamp, clampLocal);
        if (sneakSites.size() > SNEAK_SITE_COUNT) {
            return fail(
                original,
                "expected at most " + SNEAK_SITE_COUNT
                    + " hover+sneak clamp site (ldc float -> fstore to local "
                    + clampLocal
                    + ", before the jump clamp), found "
                    + sneakSites.size());
        }
        LdcInsnNode sneak = sneakSites.isEmpty() ? null : sneakSites.get(0);

        HoldCode holdCode = hold ? holdCode(method, clamp) : null;
        if (hold && holdCode == null && !hasHoldBranch(method, clamp, clampLocal)) {
            return fail(
                original,
                "cannot find the keyboard field / sneak query / player local around the jump clamp, so the "
                    + "shift+space branch cannot be built");
        }

        // The climb and the sink are one decision: the jump clamp plus the injected
        // descent store. A class without the stock sneak clamp site cannot be made
        // symmetric, so the lift is skipped rather than writing the ascent value
        // alone — that is the owner's bug.
        boolean liftDone = current == HOVER_JUMP_LIFTED && descentPatched(method, clampLocal);
        boolean guardDone = hasGuardHook(method);
        boolean holdDone = hasHoldBranch(method, clamp, clampLocal);
        boolean liftApplicable = sneak != null;
        boolean needLift = lift && liftApplicable && !liftDone;
        boolean needGuard = guard && !guardDone;
        // Unlike GraviSuite's twin, the hold here does not need the stock sneak
        // clamp site: it is anchored on the jump store, which is still present. That
        // is the one place the twins differ - GraviSuite's hold can only sit inside
        // the sneak-taken block, so it is skipped with the lift there. Same intent,
        // different geometry; each hold goes in wherever it can be placed in order.
        boolean needHold = hold && !holdDone;
        boolean skipped = lift && !liftApplicable;
        if (!needLift && !needGuard && !needHold) {
            return new Result(
                State.ALREADY_PATCHED,
                original,
                0,
                false,
                false,
                false,
                skipped,
                skipped ? LIFT_SKIPPED_ALONE : "");
        }

        int sitesChanged = 0;
        if (needLift) {
            // The jump clamp is rewritten in place; the descent is an extra store
            // after the stock sneak store, so the stock -0.1f literal survives for
            // anything that matches it by value.
            clamp.cst = Float.valueOf(HOVER_JUMP_LIFTED);
            if (!descentPatched(method, clampLocal) && !insertDescent(method, sneak, clampLocal)) {
                return fail(
                    original,
                    "the stock hover+sneak store is not followed by an FSTORE to the clamp local, so the "
                        + "descent store cannot be placed");
            }
            sitesChanged++;
        }
        if (needHold) {
            if (!insertHold(method, clamp, clampLocal, holdCode)) {
                return fail(
                    original,
                    "the jump branch's join label is not the target of exactly one jump, so the shift+space "
                        + "branch cannot be placed");
            }
            sitesChanged++;
        }
        if (needGuard) {
            insertGuard(method);
            sitesChanged++;
        }
        String detail = describe(needLift, needGuard, needHold);
        if (skipped) {
            return new Result(
                State.PARTIAL,
                write(node),
                sitesChanged,
                needLift,
                needGuard,
                needHold,
                true,
                detail + LIFT_SKIPPED_SUFFIX);
        }
        return new Result(State.PATCHED, write(node), sitesChanged, needLift, needGuard, needHold, false, detail);
    }

    private static Result fail(byte[] original, String detail) {
        return new Result(State.UNEXPECTED_SHAPE, original, 0, false, false, false, false, detail);
    }

    private static String describe(boolean lift, boolean guard, boolean hold) {
        StringBuilder text = new StringBuilder();
        if (lift) {
            text.append("hover vertical +/-")
                .append(HOVER_JUMP_LIFTED)
                .append(" (jump up, sneak down; was ")
                .append(HOVER_JUMP_CLAMP)
                .append(" / ")
                .append(STOCK_SNEAK_CLAMP)
                .append(')');
        }
        if (hold) {
            if (text.length() > 0) {
                text.append(", ");
            }
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

    /** How many {@code (double) 0.6f} literals the method carries. */
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
     * The hover+jump clamp constant: a {@code ldc} float immediately after an
     * {@code IFEQ} on {@code Keyboard.isJumpKeyDown}, stored straight into a local
     * by {@code FSTORE}. The <i>value</i> is checked by the caller, so the shape
     * survives a class that already carries this patch's number.
     */
    private static List<LdcInsnNode> jumpClampSites(MethodNode method) {
        List<LdcInsnNode> sites = new ArrayList<LdcInsnNode>();
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (!isJumpKeyCall(insn)) {
                continue;
            }
            AbstractInsnNode branch = nextReal(insn.getNext());
            if (branch == null || branch.getOpcode() != Opcodes.IFEQ) {
                continue;
            }
            AbstractInsnNode value = nextReal(branch.getNext());
            if (!(value instanceof LdcInsnNode) || !(((LdcInsnNode) value).cst instanceof Float)) {
                continue;
            }
            AbstractInsnNode store = nextReal(value.getNext());
            if (store == null || store.getOpcode() != Opcodes.FSTORE) {
                continue;
            }
            sites.add((LdcInsnNode) value);
        }
        return sites;
    }

    /**
     * The stock hover+sneak clamp constant before the jump clamp: an {@code ldc}
     * float of exactly {@value #STOCK_SNEAK_CLAMP}, stored straight into the
     * <i>same</i> local the jump clamp writes.
     *
     * <p>
     * Only the stock value is accepted. The patched class keeps that literal and
     * carries one extra store after it, so accepting the patched value too would
     * double-count the site; the injected store has no {@code IFEQ} in front of it
     * anyway. Whether the edit is present is a separate question, asked by
     * {@link #descentPatched}.
     *
     * <p>
     * The list is empty for a class that has no sneak clamp at all. That is not a
     * refusal; the caller skips the lift, because the climb and the sink have to
     * move together.
     */
    private static List<LdcInsnNode> sneakClampSites(MethodNode method, AbstractInsnNode jumpClamp, int local) {
        List<LdcInsnNode> sites = new ArrayList<LdcInsnNode>();
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn == jumpClamp) {
                return sites;
            }
            if (!(insn instanceof LdcInsnNode) || !(((LdcInsnNode) insn).cst instanceof Float)) {
                continue;
            }
            float constant = ((Float) ((LdcInsnNode) insn).cst).floatValue();
            if (constant != STOCK_SNEAK_CLAMP) {
                continue;
            }
            AbstractInsnNode store = nextReal(insn.getNext());
            if (store == null || store.getOpcode() != Opcodes.FSTORE || ((VarInsnNode) store).var != local) {
                continue;
            }
            sites.add((LdcInsnNode) insn);
        }
        return sites;
    }

    /**
     * True when the descent edit is already in these bytes: the exact sequence
     * {@code LDC float -0.1f, FSTORE local, LDC float -0.0888889f, FSTORE local},
     * with labels and line numbers allowed in between.
     *
     * <p>
     * This is what makes a second pass idempotent: the stock literal stays, so the
     * site is still found, and this check is what stops the store being injected
     * twice.
     */
    static boolean descentPatched(MethodNode method, int local) {
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (!(insn instanceof LdcInsnNode) || !(((LdcInsnNode) insn).cst instanceof Float)) {
                continue;
            }
            if (((Float) ((LdcInsnNode) insn).cst).floatValue() != STOCK_SNEAK_CLAMP) {
                continue;
            }
            AbstractInsnNode store = nextReal(insn.getNext());
            if (store == null || store.getOpcode() != Opcodes.FSTORE || ((VarInsnNode) store).var != local) {
                continue;
            }
            AbstractInsnNode value = nextReal(store.getNext());
            if (!(value instanceof LdcInsnNode) || !(((LdcInsnNode) value).cst instanceof Float)) {
                continue;
            }
            if (((Float) ((LdcInsnNode) value).cst).floatValue() != SNEAK_LIFTED) {
                continue;
            }
            AbstractInsnNode again = nextReal(value.getNext());
            if (again != null && again.getOpcode() == Opcodes.FSTORE && ((VarInsnNode) again).var == local) {
                return true;
            }
        }
        return false;
    }

    /**
     * Insert the descent target right after the stock sneak store, inside the
     * sneak-taken block:
     *
     * <pre>
     * v = -0.1f; // stock, left alone
     * v = -0.0888889f; // &lt;- LDC + FSTORE, same local
     * </pre>
     *
     * No label, no jump, no frame: the store is a plain overwrite in a straight
     * line, so the block's join label and its frame are untouched. That is why
     * {@code COMPUTE_MAXS} is enough.
     *
     * @return true when the two instructions were inserted
     */
    private static boolean insertDescent(MethodNode method, LdcInsnNode sneak, int local) {
        if (sneak == null) {
            return false;
        }
        AbstractInsnNode store = nextReal(sneak.getNext());
        if (store == null || store.getOpcode() != Opcodes.FSTORE || ((VarInsnNode) store).var != local) {
            return false;
        }
        InsnList inserted = new InsnList();
        inserted.add(new LdcInsnNode(Float.valueOf(SNEAK_LIFTED)));
        inserted.add(new VarInsnNode(Opcodes.FSTORE, local));
        method.instructions.insert(store, inserted);
        return true;
    }

    private static boolean isJumpKeyCall(AbstractInsnNode insn) {
        if (insn.getOpcode() != Opcodes.INVOKEVIRTUAL) {
            return false;
        }
        MethodInsnNode call = (MethodInsnNode) insn;
        return KEYBOARD_OWNER.equals(call.owner) && JUMP_KEY.equals(call.name) && JUMP_KEY_DESC.equals(call.desc);
    }

    /** The local index the clamp value is stored to. */
    private static int storedLocal(LdcInsnNode clamp) {
        AbstractInsnNode store = nextReal(clamp.getNext());
        return ((VarInsnNode) store).var;
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

    /** True when the method already starts with this patch's guard. */
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
     * True when the shift+space overwrite is already in the jump-taken block: a
     * {@code 0.0f} store to the clamp local sitting after the jump-only store,
     * before the jump branch's join label.
     */
    static boolean hasHoldBranch(MethodNode method, LdcInsnNode clamp, int local) {
        AbstractInsnNode branch = previousReal(clamp);
        if (branch == null || branch.getOpcode() != Opcodes.IFEQ) {
            return false;
        }
        LabelNode join = ((JumpInsnNode) branch).label;
        AbstractInsnNode store = nextReal(clamp.getNext());
        if (store == null) {
            return false;
        }
        for (AbstractInsnNode insn = store.getNext(); insn != null; insn = insn.getNext()) {
            if (insn == join) {
                return false;
            }
            if (!(insn instanceof LdcInsnNode)) {
                continue;
            }
            Object cst = ((LdcInsnNode) insn).cst;
            if (!(cst instanceof Float) || ((Float) cst).floatValue() != HOLD_ALTITUDE_TARGET) {
                continue;
            }
            AbstractInsnNode after = nextReal(insn.getNext());
            if (after != null && after.getOpcode() == Opcodes.FSTORE && ((VarInsnNode) after).var == local) {
                return true;
            }
        }
        return false;
    }

    /**
     * The three pieces the hold branch needs, copied from the method itself:
     * the {@code GETSTATIC} that pushes the keyboard, the sneak query (any other
     * {@code (EntityPlayer)Z} call on the same owner), and the player local.
     *
     * <p>
     * Nothing here is a guessed name: if any piece is missing the caller refuses
     * the whole patch rather than emitting bytecode that would not link.
     */
    static HoldCode holdCode(MethodNode method, LdcInsnNode clamp) {
        MethodInsnNode jumpCall = null;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (!isJumpKeyCall(insn)) {
                continue;
            }
            AbstractInsnNode branch = nextReal(insn.getNext());
            if (branch != null && branch.getOpcode() == Opcodes.IFEQ && nextReal(branch.getNext()) == clamp) {
                jumpCall = (MethodInsnNode) insn;
                break;
            }
        }
        if (jumpCall == null) {
            return null;
        }
        // the player local: the ALOAD just before the jump call
        AbstractInsnNode before = previousReal(jumpCall);
        if (before == null || before.getOpcode() != Opcodes.ALOAD) {
            return null;
        }
        int playerLocal = ((VarInsnNode) before).var;
        // the keyboard: the GETSTATIC just before that ALOAD
        AbstractInsnNode field = previousReal(before);
        if (!(field instanceof FieldInsnNode) || field.getOpcode() != Opcodes.GETSTATIC) {
            return null;
        }
        FieldInsnNode keyboard = (FieldInsnNode) field;
        // the sneak query: same owner, same descriptor, a different name, earlier in the method
        MethodInsnNode sneak = null;
        // Found by position, not by name: the last (EntityPlayer)Z keyboard query before the jump call. On the real IC2
        // jar that is isSneakKeyDown.
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn == jumpCall) {
                break;
            }
            if (insn.getOpcode() != Opcodes.INVOKEVIRTUAL) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode) insn;
            if (call.owner.equals(jumpCall.owner) && call.desc.equals(jumpCall.desc)
                && !call.name.equals(jumpCall.name)) {
                sneak = call;
            }
        }
        if (sneak == null) {
            return null;
        }
        return new HoldCode(keyboard, sneak, playerLocal);
    }

    /** What the hold branch needs, copied from IC2's own instructions. */
    static final class HoldCode {

        final FieldInsnNode keyboard;
        final MethodInsnNode sneakCall;
        final int playerLocal;

        HoldCode(FieldInsnNode keyboard, MethodInsnNode sneakCall, int playerLocal) {
            this.keyboard = keyboard;
            this.sneakCall = sneakCall;
            this.playerLocal = playerLocal;
        }
    }

    /**
     * Insert the shift+space overwrite just after the jump-only store, inside the
     * jump-taken block:
     *
     * <pre>
     * v = 0.1f; // or +0.0888889 with edit 1
     * if (isSneakKeyDown(player)) v = 0.0f;
     * </pre>
     *
     * The {@code IFEQ} jumps to the jump branch's own join label, so no
     * {@code GOTO}, no new label and no new frame is added. Refuses — method
     * untouched — unless exactly one jump targets that join label.
     *
     * @return true when the six instructions were inserted
     */
    private static boolean insertHold(MethodNode method, LdcInsnNode clamp, int local, HoldCode code) {
        AbstractInsnNode branch = previousReal(clamp);
        if (branch == null || branch.getOpcode() != Opcodes.IFEQ) {
            return false;
        }
        LabelNode join = ((JumpInsnNode) branch).label;
        AbstractInsnNode store = nextReal(clamp.getNext());
        if (store == null || store.getOpcode() != Opcodes.FSTORE || ((VarInsnNode) store).var != local) {
            return false;
        }
        // the join label must belong to the jump branch and nothing else
        int joins = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof JumpInsnNode && ((JumpInsnNode) insn).label == join) {
                joins++;
            }
        }
        if (joins != 1) {
            return false;
        }
        InsnList inserted = new InsnList();
        inserted.add(new FieldInsnNode(Opcodes.GETSTATIC, code.keyboard.owner, code.keyboard.name, code.keyboard.desc));
        inserted.add(new VarInsnNode(Opcodes.ALOAD, code.playerLocal));
        inserted.add(
            new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                code.sneakCall.owner,
                code.sneakCall.name,
                code.sneakCall.desc,
                false));
        inserted.add(new JumpInsnNode(Opcodes.IFEQ, join));
        inserted.add(new LdcInsnNode(Float.valueOf(HOLD_ALTITUDE_TARGET)));
        inserted.add(new VarInsnNode(Opcodes.FSTORE, local));
        method.instructions.insert(store, inserted);
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
     * The first {@code IFEQ} on the method's own hover flag (local 2) skips the
     * rest of the guard, so hover-off flight keeps stock IC2 behaviour. Both
     * {@code IFEQ}s branch to one new label placed at the original first
     * instruction. That label is a branch target, so it needs a stack map frame:
     * {@code F_SAME} (same locals as the implicit entry frame, empty stack),
     * which is exactly true there. No local is added and the stack depth at every
     * existing label is unchanged, so the class's own frames are simply moved —
     * {@code COMPUTE_MAXS}, never {@code COMPUTE_FRAMES}.
     */
    private static void insertGuard(MethodNode method) {
        LabelNode proceed = new LabelNode();
        InsnList guard = new InsnList();
        guard.add(new VarInsnNode(Opcodes.ILOAD, 2));
        guard.add(new JumpInsnNode(Opcodes.IFEQ, proceed));
        guard.add(new VarInsnNode(Opcodes.ALOAD, 1));
        guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK_OWNER, GUARD_HOOK, GUARD_HOOK_DESC, false));
        guard.add(new JumpInsnNode(Opcodes.IFEQ, proceed));
        guard.add(new InsnNode(Opcodes.ICONST_0));
        guard.add(new InsnNode(Opcodes.IRETURN));
        guard.add(proceed);
        guard.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        method.instructions.insert(guard);
    }

    // COMPUTE_MAXS, never COMPUTE_FRAMES: the frames read from IC2's class stay as
    // they are (each edit adds exactly one new frame at its own branch target and
    // changes no stack depth at any existing label) and ASM only moves them.
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
