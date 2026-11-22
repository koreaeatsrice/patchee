package com.jointspaceforce.patchee.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import com.jointspaceforce.patchee.asm.ChestplateFlightTransformer;

/**
 * The ChestplateFlight byte patch: three independent edits in one method.
 *
 * <p>
 * IC2 ships closed source, so the repo does not carry its class file. The tests
 * rebuild the exact instruction sequence this patch keys on — the same constants
 * in the same order as the shipped GTNH build
 * ({@code javap -p -c ic2.core.item.armor.ItemArmorQuantumSuit#useJetpack}:
 * ceiling at offset 215, {@code -0.025f} at 228, {@code -0.1f} at 243,
 * {@code 0.1f} at 258, the clamp chain at 263-294) — and pin:
 *
 * <ul>
 * <li>the stock shape is patched: the jump clamp becomes
 * {@code +0.4} and the descent is injected as {@code -0.4} right after the stock
 * sneak store, the shift+space branch writes 0.0, the ground guard is at the top,
 * and the no-input {@code -0.025f} sink is untouched;</li>
 * <li>rising and sinking are the same size, exactly, read back from the patched
 * bytes, and that size is twice the Advanced Nano's stock 0.2
 * ({@link FlightTiers#ADVANCED_NANO_HOVER_ASCENT}); the stock {@code 0.1f} is
 * gone but the stock {@code -0.1f} survives on purpose, with the descent injected
 * as a second store after it;</li>
 * <li>a class with no hover+sneak clamp store gets <b>no</b> climb either — the
 * lift is skipped, never half-applied;</li>
 * <li>Hodgepodge's {@code @ModifyConstant} (which rewrites {@code -0.025f} to
 * {@code 0.0f}) survives in <b>either</b> order, and cannot touch the
 * shift+space case at all;</li>
 * <li>each of the three switches is independent;</li>
 * <li>idempotence, and refusal of every other shape;</li>
 * <li>the patched bytes still pass the JVM verifier (the injected guard frame);</li>
 * <li>optionally, against the real jar when {@code PATCHEE_IC2_JAR} is set.</li>
 * </ul>
 */
class ChestplateFlightPatchTest {

    /** Set to the GTNH IC2 jar to run the real-bytes checks; skipped otherwise. */
    private static final String REAL_JAR_ENV = "PATCHEE_IC2_JAR";

    private static final String PLAYER = "net/minecraft/entity/player/EntityPlayer";
    private static final String MOTION_Y = "field_70181_x";
    private static final String KEYBOARD = "ic2/core/util/Keyboard";
    private static final String IC2 = "ic2/core/IC2";

    private static final int GUARD_INSTRUCTIONS = 8;
    private static final int HOLD_INSTRUCTIONS = 6;

    /** The injected descent store: {@code LDC -0.4f; FSTORE local}. */
    private static final int DESCENT_INSTRUCTIONS = 2;

    /** The guard's eight nodes are seven opcodes plus the proceed label they jump to. */
    private static final int GUARD_REAL_INSTRUCTIONS = GUARD_INSTRUCTIONS - 1;

    // ---- the stock shape ---------------------------------------------------

    @Test
    void patchesTheStockShapeAndMakesBothHoverSpeedsSymmetric() {
        byte[] original = ic2Class(-0.025f, 0.1f);
        ChestplateFlightPatch.Result result = ChestplateFlightPatch.apply(original, true, true, true);

        assertEquals(ChestplateFlightPatch.State.PATCHED, result.state(), result.detail());
        assertEquals(3, result.sites());
        assertTrue(result.liftApplied(), "the lift must report itself");
        assertTrue(result.holdApplied());
        assertTrue(result.guardApplied());

        MethodNode method = method(result.bytes());
        List<Float> clamps = floatConstants(method);
        // Both sides of the comparison come from the patched bytes: the jump clamp
        // reads the value stored at the jump site, the descent reads the value
        // injected after the stock sneak store.
        float ascent = jumpClampStoredValue(method);
        float descent = injectedDescentValue(method);
        assertEquals(1, count(method, Float.valueOf(ascent)), "no single ascent constant: " + clamps);
        assertEquals(1, count(method, Float.valueOf(descent)), "no single descent constant: " + clamps);
        assertEquals(
            0,
            Float.compare(ascent, -descent),
            "up and down must be the same size, read from the bytes: " + ascent + " / " + descent);
        assertEquals(0, count(method, Float.valueOf(0.1f)), "the stock jump clamp is still there: " + clamps);
        // DELIBERATE: the stock -0.1f literal stays in the class. Hodgepodge matches
        // -0.025f by value on this method and value-matching mixins must keep their
        // targets; the descent is an extra store after this one.
        assertEquals(1, count(method, Float.valueOf(-0.1f)), "the stock sneak literal must survive: " + clamps);
        assertEquals(1, count(method, Float.valueOf(-0.025f)), "the no-input hover clamp moved: " + clamps);
        assertEquals(1, count(method, Float.valueOf(0.0f)), "the shift+space target is missing: " + clamps);
        assertEquals(0, count(method, Float.valueOf(0.6f)), "the old 0.6f hover clamp must be gone: " + clamps);
        assertEquals(
            1,
            countDouble(method, ChestplateFlightPatch.ASCENT_CEILING_CONSTANT),
            "the 0.6d jetpack ceiling must still be there");
        assertTrue(ChestplateFlightPatch.hasGuardHook(method), "the ground guard was not injected");
    }

    @Test
    void theAscentAndDescentConstantsComeFromThePatchedBytes() {
        MethodNode method = method(
            ChestplateFlightPatch.apply(ic2Class(-0.025f, 0.1f), true, false, false)
                .bytes());
        List<Float> clamps = floatConstants(method);
        // One side is the float stored at the jump site, the other the float stored
        // at the injected descent site. Neither is read from a constant or from the
        // other side, so changing only one of the two writes fails this test.
        float ascent = jumpClampStoredValue(method);
        float descent = injectedDescentValue(method);
        assertEquals(0, Float.compare(ascent, -descent), "same magnitude, exact: " + ascent + " / " + descent);
        assertTrue(ascent > 0.0f && descent < 0.0f, "jump goes up, sneak goes down");
        // Bare literals, not the constant: a wrong base or a wrong drag fails here.
        assertEquals(0.4f, ascent, "the written rise: " + clamps);
        assertEquals(-0.4f, descent, "the written sink: " + clamps);
    }

    @Test
    void theStockSneakLiteralIsStillInThePatchedBytesExactlyOnce() {
        MethodNode method = method(
            ChestplateFlightPatch.apply(ic2Class(-0.025f, 0.1f), true, true, true)
                .bytes());
        // DELIBERATE: the stock -0.1f stays because other mods match stock literals
        // by value (Hodgepodge does this on -0.025f in this very method), and the
        // descent is injected as a second store after it.
        assertEquals(
            1,
            count(method, Float.valueOf(-0.1f)),
            "the stock sink literal was consumed: " + floatConstants(method));
        assertEquals(-0.1f, stockSneakStoredValue(method), "the stock store itself must stay intact");
        assertEquals(-0.4f, injectedDescentValue(method), "the descent store must follow it");
    }

    @Test
    void theGuardIsExactlyTheEntryBranch() {
        MethodNode method = method(
            ChestplateFlightPatch.apply(ic2Class(-0.025f, 0.1f), false, true, false)
                .bytes());
        AbstractInsnNode[] all = method.instructions.toArray();
        assertTrue(all.length >= GUARD_INSTRUCTIONS, "the method is too short to hold the guard");
        AbstractInsnNode[] head = new AbstractInsnNode[GUARD_INSTRUCTIONS];
        System.arraycopy(all, 0, head, 0, GUARD_INSTRUCTIONS);

        // 1. only while hovering
        assertEquals(Opcodes.ILOAD, head[0].getOpcode());
        assertEquals(2, ((VarInsnNode) head[0]).var, "the guard must read the hover flag in local 2");
        assertEquals(Opcodes.IFEQ, head[1].getOpcode(), "hover off must skip the guard");

        // 2. the pure decision in the hook
        assertEquals(Opcodes.ALOAD, head[2].getOpcode());
        assertEquals(1, ((VarInsnNode) head[2]).var, "the guard must read the player in local 1");
        assertTrue(head[3] instanceof MethodInsnNode, "expected the hook call");
        MethodInsnNode call = (MethodInsnNode) head[3];
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals(ChestplateFlightPatch.HOOK_OWNER, call.owner);
        assertEquals(ChestplateFlightPatch.GUARD_HOOK, call.name);
        assertEquals(ChestplateFlightPatch.GUARD_HOOK_DESC, call.desc);
        assertEquals(Opcodes.IFEQ, head[4].getOpcode());

        // 3. return false
        assertEquals(Opcodes.ICONST_0, head[5].getOpcode());
        assertEquals(Opcodes.IRETURN, head[6].getOpcode());

        // 4. both tests jump to the same label, which starts the original method
        assertTrue(head[7] instanceof LabelNode, "the eighth node must be the shared proceed label");
        assertSame(((JumpInsnNode) head[1]).label, head[7]);
        assertSame(((JumpInsnNode) head[4]).label, head[7]);
    }

    @Test
    void theHoldBranchIsPlacedAfterTheJumpOnlyStore() {
        MethodNode method = method(
            ChestplateFlightPatch.apply(ic2Class(-0.025f, 0.1f), false, false, true)
                .bytes());
        List<AbstractInsnNode> insns = realInsns(method);

        int jumpCall = -1;
        for (int i = 0; i < insns.size(); i++) {
            if (insns.get(i) instanceof MethodInsnNode
                && ChestplateFlightPatch.JUMP_KEY.equals(((MethodInsnNode) insns.get(i)).name)) {
                jumpCall = i;
            }
        }
        assertTrue(jumpCall > 0, "no jump-key query in the method");
        JumpInsnNode jumpBranch = (JumpInsnNode) insns.get(jumpCall + 1);
        LabelNode join = jumpBranch.label;
        assertEquals(Opcodes.IFEQ, jumpBranch.getOpcode());

        // stock jump clamp: ldc float, fstore local
        int storeIndex = jumpCall + 3;
        assertTrue(insns.get(jumpCall + 2) instanceof LdcInsnNode, "no jump-only clamp value");
        assertEquals(
            Opcodes.FSTORE,
            insns.get(storeIndex)
                .getOpcode());
        VarInsnNode jumpStore = (VarInsnNode) insns.get(storeIndex);

        // everything between that store and the jump branch's join: our six instructions
        List<AbstractInsnNode> between = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode node = jumpStore.getNext(); node != null && node != join; node = node.getNext()) {
            if (node.getOpcode() >= 0) {
                between.add(node);
            }
        }
        assertEquals(HOLD_INSTRUCTIONS, between.size(), "the hold branch must sit after the jump-only store");

        assertEquals(
            Opcodes.GETSTATIC,
            between.get(0)
                .getOpcode(),
            "the branch must push the keyboard the method uses");
        assertEquals("L" + KEYBOARD + ";", ((FieldInsnNode) between.get(0)).desc);
        assertEquals(
            Opcodes.ALOAD,
            between.get(1)
                .getOpcode());
        assertEquals(1, ((VarInsnNode) between.get(1)).var);
        MethodInsnNode sneak = (MethodInsnNode) between.get(2);
        assertEquals(Opcodes.INVOKEVIRTUAL, sneak.getOpcode());
        assertEquals(KEYBOARD, sneak.owner, "the branch must call the same keyboard class");
        assertEquals(
            Opcodes.IFEQ,
            between.get(3)
                .getOpcode());
        assertSame(join, ((JumpInsnNode) between.get(3)).label, "the hold IFEQ must join where the jump branch joins");
        assertTrue(between.get(4) instanceof LdcInsnNode, "no hold target");
        assertEquals(
            ChestplateFlightPatch.HOLD_ALTITUDE_TARGET,
            ((Float) ((LdcInsnNode) between.get(4)).cst).floatValue());
        assertEquals(
            Opcodes.FSTORE,
            between.get(5)
                .getOpcode());
        assertEquals(jumpStore.var, ((VarInsnNode) between.get(5)).var, "the hold branch must write the clamp local");

        // no GOTO and no extra frame: one plain overwrite after the store
        for (AbstractInsnNode node : method.instructions.toArray()) {
            assertFalse(node.getOpcode() == Opcodes.GOTO, "the hold branch must not need a GOTO");
        }

        // the jump-only value is still stock
        assertEquals(1, count(method, Float.valueOf(0.1f)), "the jump-only clamp moved");
    }

    // ---- the Hodgepodge conflict ------------------------------------------

    @Test
    void patchesBytesHodgepodgeAlreadyRewrote() {
        // Hodgepodge ran first: -0.025f is already 0.0f. The patch must not care.
        byte[] hodgepodge = ic2Class(0.0f, 0.1f);
        ChestplateFlightPatch.Result result = ChestplateFlightPatch.apply(hodgepodge, true, true, true);

        assertEquals(ChestplateFlightPatch.State.PATCHED, result.state(), result.detail());
        MethodNode method = method(result.bytes());
        List<Float> clamps = floatConstants(method);
        float ascent = ChestplateFlight.hoverAscentTarget();
        assertEquals(0.4f, ascent, "the Quantum rung");
        assertEquals(1, count(method, Float.valueOf(ascent)), "the lift did not fire: " + clamps);
        assertEquals(1, count(method, Float.valueOf(-ascent)), "the sink did not fire: " + clamps);
        assertEquals(
            2,
            count(method, Float.valueOf(0.0f)),
            "Hodgepodge's 0.0 default and our 0.0 hold target must both be there: " + clamps);
        assertEquals(0, count(method, Float.valueOf(-0.025f)), "the -0.025 clamp came back: " + clamps);
        // the stock -0.1f is kept on purpose; the descent is a second store after it
        assertEquals(1, count(method, Float.valueOf(-0.1f)), "the stock sneak clamp must survive: " + clamps);
    }

    @Test
    void leavesHodgepodgesTargetInPlaceWhenThisPatchRunsFirst() {
        // Patchee ran first: -0.025f must still be there for Hodgepodge's
        // @ModifyConstant to find, next to every edit this patch made.
        byte[] patched = ChestplateFlightPatch.apply(ic2Class(-0.025f, 0.1f), true, true, true)
            .bytes();
        MethodNode method = method(patched);
        assertEquals(1, count(method, Float.valueOf(-0.025f)), "Hodgepodge's -0.025f target is gone");
        assertEquals(1, count(method, Float.valueOf(0.4f)), "the lift is missing");
        assertEquals(1, count(method, Float.valueOf(0.0f)), "the hold target is missing");
    }

    // ---- the switches are independent --------------------------------------

    @Test
    void liftAloneLeavesTheOtherTwoOut() {
        ChestplateFlightPatch.Result result = ChestplateFlightPatch.apply(ic2Class(-0.025f, 0.1f), true, false, false);
        assertEquals(1, result.sites());
        assertTrue(result.liftApplied());
        assertFalse(result.guardApplied());
        assertFalse(result.holdApplied());
        MethodNode method = method(result.bytes());
        assertFalse(ChestplateFlightPatch.hasGuardHook(method));
        List<Float> clamps = floatConstants(method);
        // both stores moved, and only those two
        assertEquals(1, count(method, Float.valueOf(ChestplateFlight.hoverAscentTarget())), clamps.toString());
        assertEquals(1, count(method, Float.valueOf(ChestplateFlight.hoverDescentTarget())), clamps.toString());
        assertEquals(0, count(method, Float.valueOf(0.1f)), clamps.toString());
        assertEquals(1, count(method, Float.valueOf(-0.1f)), "the stock sink literal must survive: " + clamps);
        assertEquals(
            -0.4f,
            injectedDescentValue(method),
            "the injected descent store must sit after the stock one: " + clamps);
        assertFalse(floatConstants(method).contains(Float.valueOf(0.0f)), "the hold branch leaked in");
    }

    @Test
    void holdAloneLeavesTheClampStock() {
        ChestplateFlightPatch.Result result = ChestplateFlightPatch.apply(ic2Class(-0.025f, 0.1f), false, false, true);
        assertEquals(1, result.sites());
        MethodNode method = method(result.bytes());
        assertFalse(ChestplateFlightPatch.hasGuardHook(method));
        assertEquals(1, count(method, Float.valueOf(0.1f)), "the jump-only clamp was changed by the hold branch");
        assertEquals(0, count(method, Float.valueOf(0.6f)));
        assertTrue(holdTargetIndex(realInsns(method)) > 0);
    }

    @Test
    void guardAloneLeavesTheClampStock() {
        ChestplateFlightPatch.Result result = ChestplateFlightPatch.apply(ic2Class(-0.025f, 0.1f), false, true, false);
        assertEquals(1, result.sites());
        MethodNode method = method(result.bytes());
        assertTrue(ChestplateFlightPatch.hasGuardHook(method));
        assertEquals(1, count(method, Float.valueOf(0.1f)), "the clamp was changed by the guard");
        assertEquals(0, count(method, Float.valueOf(0.6f)));
        assertEquals(0, holdTargetIndex(realInsns(method)) + 1, "the hold branch leaked in");
    }

    @Test
    void holdWorksWithTheLiftOnToo() {
        MethodNode method = method(
            ChestplateFlightPatch.apply(ic2Class(-0.025f, 0.1f), true, false, true)
                .bytes());
        assertEquals(
            1,
            count(method, Float.valueOf(ChestplateFlight.hoverAscentTarget())),
            "jump-only must rise at the Quantum rung");
        assertEquals(
            1,
            count(method, Float.valueOf(ChestplateFlight.hoverDescentTarget())),
            "sneak-only must sink at the same speed");
        assertEquals(1, count(method, Float.valueOf(0.0f)), "sneak+jump must hold altitude");
    }

    @Test
    void allOffChangesNothing() {
        byte[] original = ic2Class(-0.025f, 0.1f);
        ChestplateFlightPatch.Result result = ChestplateFlightPatch.apply(original, false, false, false);
        assertEquals(ChestplateFlightPatch.State.ALREADY_PATCHED, result.state());
        assertSame(original, result.bytes());
    }

    // ---- a class without the hover+sneak clamp -----------------------------

    @Test
    void aMissingSneakClampSkipsTheLiftAndReportsPartial() {
        byte[] noSneak = ic2ClassWithCeiling(-0.025f, 0.1f, ChestplateFlightPatch.ASCENT_CEILING_CONSTANT, false);
        ChestplateFlightPatch.Result result = ChestplateFlightPatch.apply(noSneak, true, true, true);

        assertEquals(ChestplateFlightPatch.State.PARTIAL, result.state(), result.detail());
        assertTrue(result.skipped(), "the skip must be reported");
        assertFalse(result.liftApplied(), "the lift must never be claimed when it was skipped");
        assertTrue(result.holdApplied());
        assertTrue(result.guardApplied());
        assertEquals(2, result.sites());

        MethodNode method = method(result.bytes());
        List<Float> clamps = floatConstants(method);
        assertEquals(
            0,
            count(method, Float.valueOf(ChestplateFlight.hoverAscentTarget())),
            "a half-symmetric ascent constant was written: " + clamps);
        assertEquals(
            0,
            count(method, Float.valueOf(ChestplateFlight.hoverDescentTarget())),
            "a descent constant appeared from nowhere: " + clamps);
        assertEquals(1, count(method, Float.valueOf(0.1f)), "the stock jump clamp must stay: " + clamps);
        assertEquals(1, count(method, Float.valueOf(0.0f)), "the hold branch must still go in: " + clamps);
        assertTrue(ChestplateFlightPatch.hasGuardHook(method), "the guard must still go in");
    }

    @Test
    void aMissingSneakClampWithNothingElseRequestedChangesNoBytes() {
        byte[] noSneak = ic2ClassWithCeiling(-0.025f, 0.1f, ChestplateFlightPatch.ASCENT_CEILING_CONSTANT, false);
        ChestplateFlightPatch.Result result = ChestplateFlightPatch.apply(noSneak, true, false, false);

        assertEquals(ChestplateFlightPatch.State.ALREADY_PATCHED, result.state(), result.detail());
        assertTrue(result.skipped(), "the skip must be reported");
        assertFalse(result.liftApplied());
        assertEquals(0, result.sites());
        assertSame(noSneak, result.bytes(), "a skipped lift must not rewrite a byte");
        assertTrue(
            result.detail()
                .contains("hover+sneak"),
            result.detail());
    }

    // ---- idempotence -------------------------------------------------------

    @Test
    void aSecondPassChangesNothing() {
        byte[] once = ChestplateFlightPatch.apply(ic2Class(-0.025f, 0.1f), true, true, true)
            .bytes();
        ChestplateFlightPatch.Result twice = ChestplateFlightPatch.apply(once, true, true, true);
        assertEquals(ChestplateFlightPatch.State.ALREADY_PATCHED, twice.state(), twice.detail());
        assertSame(once, twice.bytes());
        assertFalse(twice.liftApplied());
        assertFalse(twice.skipped());
        assertEquals(
            1,
            count(method(once), Float.valueOf(ChestplateFlight.hoverAscentTarget())),
            "the ascent clamp was duplicated");
        assertEquals(
            1,
            count(method(once), Float.valueOf(ChestplateFlight.hoverDescentTarget())),
            "the descent clamp was duplicated");
        assertEquals(1, guardHooks(method(once)), "the guard was duplicated");
        assertEquals(1, count(method(once), Float.valueOf(0.0f)), "the hold store was duplicated");
    }

    @Test
    void aSecondPassWithOneThirdAlreadyDoneAppliesOnlyTheRest() {
        byte[] liftOnly = ChestplateFlightPatch.apply(ic2Class(-0.025f, 0.1f), true, false, false)
            .bytes();
        ChestplateFlightPatch.Result result = ChestplateFlightPatch.apply(liftOnly, true, true, true);
        assertEquals(ChestplateFlightPatch.State.PATCHED, result.state());
        assertEquals(2, result.sites());
        assertFalse(result.liftApplied(), "the lift was already in place");
        assertTrue(result.guardApplied());
        assertTrue(result.holdApplied());
        MethodNode method = method(result.bytes());
        assertEquals(1, count(method, Float.valueOf(ChestplateFlight.hoverAscentTarget())));
        assertEquals(1, count(method, Float.valueOf(ChestplateFlight.hoverDescentTarget())));
        assertEquals(1, guardHooks(method));
        assertEquals(1, count(method, Float.valueOf(0.0f)));
    }

    // ---- refusal -----------------------------------------------------------

    @Test
    void refusesAClassWithoutTheAscentCeiling() {
        byte[] other = ic2ClassWithCeiling(-0.025f, 0.1f, 0.7d);
        ChestplateFlightPatch.Result result = ChestplateFlightPatch.apply(other, true, true, true);
        assertEquals(ChestplateFlightPatch.State.UNEXPECTED_SHAPE, result.state());
        assertSame(other, result.bytes());
        assertTrue(
            result.detail()
                .contains("ascent ceiling"),
            result.detail());
    }

    @Test
    void refusesAnUnknownClampValue() {
        byte[] unknown = ic2Class(-0.025f, 0.15f);
        ChestplateFlightPatch.Result result = ChestplateFlightPatch.apply(unknown, true, true, true);
        assertEquals(ChestplateFlightPatch.State.UNEXPECTED_SHAPE, result.state());
        assertTrue(
            result.detail()
                .contains("hover+jump clamp"),
            result.detail());
        assertSame(unknown, result.bytes());
    }

    @Test
    void refusesAMissingMethod() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(
            Opcodes.V1_8,
            Opcodes.ACC_PUBLIC,
            ChestplateFlightPatch.TARGET_INTERNAL_NAME,
            null,
            "java/lang/Object",
            null);
        writer.visitEnd();
        byte[] empty = writer.toByteArray();
        ChestplateFlightPatch.Result result = ChestplateFlightPatch.apply(empty, true, true, true);
        assertEquals(ChestplateFlightPatch.State.UNEXPECTED_SHAPE, result.state());
        assertTrue(
            result.detail()
                .contains("no useJetpack"),
            result.detail());
        assertSame(empty, result.bytes());
    }

    @Test
    void refusesAStaticUseJetpack() throws Exception {
        ClassNode node = new ClassNode();
        new ClassReader(ic2Class(-0.025f, 0.1f)).accept(node, 0);
        MethodNode target = null;
        for (MethodNode m : node.methods) {
            if (ChestplateFlightPatch.METHOD_NAME.equals(m.name)) {
                target = m;
            }
        }
        assertNotNull(target, "the fixture must carry useJetpack");
        target.access |= Opcodes.ACC_STATIC;
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        byte[] statics = writer.toByteArray();

        ChestplateFlightPatch.Result result = ChestplateFlightPatch.apply(statics, true, true, true);
        assertEquals(ChestplateFlightPatch.State.UNEXPECTED_SHAPE, result.state(), result.detail());
        assertSame(statics, result.bytes(), "a static method must be refused without editing a byte");
        assertTrue(
            result.detail()
                .contains("static"),
            result.detail());
    }

    @Test
    void aNullClassIsNotAPatch() {
        assertEquals(
            ChestplateFlightPatch.State.UNEXPECTED_SHAPE,
            ChestplateFlightPatch.apply(null, true, true, true)
                .state());
    }

    @Test
    void anUnknownClassPassesThroughTheTransformerByteIdentical() {
        byte[] bytes = ic2Class(-0.025f, 0.1f);
        byte[] out = new ChestplateFlightTransformer()
            .transform("ic2.core.item.armor.ItemArmorRubberBoots", "ic2.core.item.armor.ItemArmorRubberBoots", bytes);
        assertSame(bytes, out);
    }

    @Test
    void bothSwitchesOffLeaveTheClassByteIdentical() {
        byte[] real = ic2Class(-0.025f, 0.1f);
        ChestplateFlightSettings.injectForTest(
            ChestplateFlightSettings.parse(
                "B:enabled=true\nB:enableChestplateHoverLift=false\nB:enableChestplateHoldAltitude=false\n"
                    + "B:enableChestplateGroundGuard=false\n"));
        try {
            byte[] out = new ChestplateFlightTransformer()
                .transform(ChestplateFlightPatch.TARGET_CLASS, ChestplateFlightPatch.TARGET_CLASS, real);
            assertSame(real, out);
        } finally {
            ChestplateFlightSettings.clearInjectedForTest();
        }
    }

    @Test
    void theTransformerStillPatchesWhenOnlySomeSwitchesAreOn() {
        byte[] real = ic2Class(-0.025f, 0.1f);
        ChestplateFlightSettings.injectForTest(
            ChestplateFlightSettings.parse(
                "B:enabled=true\nB:enableChestplateHoverLift=true\nB:enableChestplateHoldAltitude=true\n"
                    + "B:enableChestplateGroundGuard=false\n"));
        try {
            byte[] out = new ChestplateFlightTransformer()
                .transform(ChestplateFlightPatch.TARGET_CLASS, ChestplateFlightPatch.TARGET_CLASS, real);
            MethodNode method = method(out);
            assertEquals(1, count(method, Float.valueOf(ChestplateFlight.hoverAscentTarget())));
            assertEquals(1, count(method, Float.valueOf(ChestplateFlight.hoverDescentTarget())));
            assertEquals(1, count(method, Float.valueOf(0.0f)));
            assertFalse(ChestplateFlightPatch.hasGuardHook(method));
        } finally {
            ChestplateFlightSettings.clearInjectedForTest();
        }
    }

    @Test
    void theTransformerPassesAnUnknownShapeThroughUntouched() {
        byte[] other = ic2ClassWithCeiling(-0.025f, 0.1f, 0.7d);
        ChestplateFlightSettings.injectForTest(ChestplateFlightSettings.parse("B:enabled=true\n"));
        try {
            byte[] out = new ChestplateFlightTransformer()
                .transform(ChestplateFlightPatch.TARGET_CLASS, ChestplateFlightPatch.TARGET_CLASS, other);
            assertSame(other, out, "an unknown shape must be handed back untouched");
        } finally {
            ChestplateFlightSettings.clearInjectedForTest();
        }
    }

    // ---- the pure decisions ------------------------------------------------

    @Test
    void theGuardFiresOnlyWhenGroundedAndNotRising() {
        assertTrue(ChestplateFlight.guardFires(true, 0.0d));
        assertTrue(ChestplateFlight.guardFires(true, -0.0784d), "standing still sinks slightly");
        assertTrue(ChestplateFlight.guardFires(true, -0.6d));
        assertFalse(ChestplateFlight.guardFires(true, 0.42d), "a jump must lift off");
        assertFalse(ChestplateFlight.guardFires(false, 0.0d), "in the air the jetpack must run");
        assertFalse(ChestplateFlight.guardFires(false, -0.6d));
    }

    @Test
    void theHoverSpeedsAreTwiceTheAdvancedNano() {
        assertEquals(0.4f * 0.5f * 2.0f, ChestplateFlight.HORIZONTAL_FORWARD);
        assertEquals(0.4f, ChestplateFlight.HORIZONTAL_FORWARD, "the forward argument at load 1");
        assertEquals(0.02f, ChestplateFlight.HORIZONTAL_FRICTION);
        assertEquals(0.91f, ChestplateFlight.AIR_DRAG);
        assertEquals(
            ChestplateFlight.HORIZONTAL_FRICTION * ChestplateFlight.HORIZONTAL_FORWARD
                / (1.0f - ChestplateFlight.AIR_DRAG),
            ChestplateFlight.HOVER_HORIZONTAL_SPEED);
        // Horizontal only: the sideways speed is not on the ladder and did not move.
        assertEquals(0.08888892f, ChestplateFlight.HOVER_HORIZONTAL_SPEED, "the derived steady-state speed");
        // Vertical: the Quantum rung, twice the Advanced Nano's stock 0.2.
        assertEquals(0.4f, ChestplateFlight.hoverAscentTarget(), "the Quantum hover climb");
        assertEquals(-0.4f, ChestplateFlight.hoverDescentTarget(), "the Quantum hover sink");
        assertEquals(
            0.4f,
            2.0f * FlightTiers.ADVANCED_NANO_HOVER_ASCENT,
            "the Quantum rung must be twice the Advanced Nano rung");
        assertEquals(
            0,
            Float.compare(ChestplateFlight.hoverAscentTarget(), -ChestplateFlight.hoverDescentTarget()),
            "the two targets must be the same size");
        assertEquals(0.6f, ChestplateFlight.IC2_JETPACK_CEILING, "IC2's non-hover ceiling is unchanged");
        assertEquals(0.1f, ChestplateFlight.IC2_HOVER_JUMP_CLAMP);
        assertEquals(-0.1f, ChestplateFlight.IC2_SNEAK_CLAMP);
        assertEquals(ChestplateFlight.hoverAscentTarget(), ChestplateFlightPatch.HOVER_JUMP_LIFTED);
        assertEquals(ChestplateFlight.hoverDescentTarget(), ChestplateFlightPatch.SNEAK_LIFTED);
        assertEquals(
            (double) ChestplateFlight.IC2_JETPACK_CEILING,
            ChestplateFlightPatch.ASCENT_CEILING_CONSTANT,
            "the 0.6d shape literal is still IC2's own ceiling");
    }

    @Test
    void theHoverTargetTableIsSneakThenJumpThenBoth() {
        float up = ChestplateFlight.hoverAscentTarget();
        float down = ChestplateFlight.hoverDescentTarget();

        // (-,-) IC2's own default sink, untouched by us either way
        assertEquals(-0.025f, ChestplateFlight.hoverVerticalTarget(false, false, true));
        assertEquals(-0.025f, ChestplateFlight.hoverVerticalTarget(false, false, false));
        // (+,-) sneak alone: the patched sink, or IC2's stock -0.1 with the fix off
        assertEquals(down, ChestplateFlight.hoverVerticalTarget(true, false, true));
        assertEquals(-0.1f, ChestplateFlight.hoverVerticalTarget(true, false, false));
        // (-,+) jump alone: the patched rise, or IC2's stock 0.1 with the fix off
        assertEquals(up, ChestplateFlight.hoverVerticalTarget(false, true, true));
        assertEquals(0.1f, ChestplateFlight.hoverVerticalTarget(false, true, false));
        // (+,+) both keys: the target is 0.0 (an in-progress climb damps to it)
        assertEquals(0.0f, ChestplateFlight.hoverVerticalTarget(true, true, true));
        assertEquals(0.0f, ChestplateFlight.hoverVerticalTarget(true, true, false));
        // up and down must be the same size, and the two-key case must differ from jump alone
        assertEquals(
            0,
            Float.compare(
                ChestplateFlight.hoverVerticalTarget(false, true, true),
                -ChestplateFlight.hoverVerticalTarget(true, false, true)),
            "shift alone must sink exactly as fast as jump alone rises");
        assertTrue(
            ChestplateFlight.hoverVerticalTarget(true, true, true)
                != ChestplateFlight.hoverVerticalTarget(false, true, true),
            "shift+space must differ from jump alone");
    }

    // ---- the patched bytes still verify ------------------------------------

    @Test
    void patchedBytesPassTheJvmVerifier(@TempDir Path dir) throws Exception {
        byte[] patched = ChestplateFlightPatch.apply(ic2Class(-0.025f, 0.1f), true, true, true)
            .bytes();
        // IC2 is not on the test classpath, so the two classes the method
        // references are stubbed next to the patched class; the JVM then verifies
        // the real patched bytes, both injected frames included.
        writeClass(dir, IC2, ic2Stub());
        writeClass(dir, KEYBOARD, keyboardStub());
        writeClass(dir, ChestplateFlightPatch.TARGET_INTERNAL_NAME, patched);

        try (URLClassLoader loader = new URLClassLoader(
            new URL[] { dir.toUri()
                .toURL() },
            getClass().getClassLoader())) {
            Class<?> loaded = Class.forName(ChestplateFlightPatch.TARGET_INTERNAL_NAME.replace('/', '.'), true, loader);
            // Instantiating forces link + verification of every method.
            loaded.getDeclaredConstructor()
                .newInstance();
            assertNotNull(
                loaded.getDeclaredMethod("useJetpack", net.minecraft.entity.player.EntityPlayer.class, boolean.class));
        }
    }

    // ---- the real jar, when it is pointed at -------------------------------

    @Test
    void patchesTheRealIc2BytesWhenTheJarIsAvailable() throws Exception {
        String jar = System.getenv(REAL_JAR_ENV);
        Assumptions.assumeTrue(
            jar != null && Files.isRegularFile(Paths.get(jar)),
            "set " + REAL_JAR_ENV + " to the GTNH IC2 jar to run the real-bytes checks");

        byte[] real = readEntry(jar, ChestplateFlightPatch.TARGET_INTERNAL_NAME + ".class");
        ChestplateFlightPatch.Result result = ChestplateFlightPatch.apply(real, true, true, true);
        assertEquals(ChestplateFlightPatch.State.PATCHED, result.state(), result.detail());
        assertEquals(3, result.sites());

        MethodNode before = method(real);
        MethodNode after = method(result.bytes());
        // The real IC2 method also carries its own 0.4f sideways-forward argument,
        // so the ascent is pinned by "stored exactly once" (the clamp) rather than
        // by a bare count; the descent has no such twin.
        assertEquals(1, floatStores(after, 0.4f), "real bytes: the ascent literal is not stored exactly once");
        assertEquals(
            2,
            count(after, Float.valueOf(0.4f)),
            "real bytes: 0.4f must be only the ascent clamp and IC2's own horizontal 0.4f: " + floatConstants(after));
        assertEquals(1, count(after, Float.valueOf(-0.4f)), "real bytes: no single descent clamp");
        assertEquals(1, floatStores(after, -0.4f), "real bytes: the descent literal is not stored exactly once");
        assertEquals(0, count(after, Float.valueOf(0.1f)), "real bytes: the stock climb is still there");
        assertEquals(1, count(after, Float.valueOf(-0.1f)), "real bytes: the stock sink literal must survive");
        assertEquals(-0.4f, injectedDescentValue(after), "real bytes: the injected descent store is missing");
        assertEquals(
            0,
            Float.compare(jumpClampStoredValue(after), -injectedDescentValue(after)),
            "real bytes: rise and sink must be the same size");
        assertEquals(1, count(after, Float.valueOf(-0.025f)), "real bytes: the no-input hover clamp moved");
        assertEquals(1, count(after, Float.valueOf(0.0f)), "real bytes: no shift+space target");
        assertEquals(0, count(after, Float.valueOf(0.6f)), "real bytes: a 0.6f hover clamp was written");
        assertEquals(
            1,
            countDouble(after, ChestplateFlightPatch.ASCENT_CEILING_CONSTANT),
            "real bytes: the 0.6d jetpack ceiling moved");
        assertTrue(ChestplateFlightPatch.hasGuardHook(after));
        assertTrue(holdTargetIndex(realInsns(after)) > 0, "real bytes: no shift+space branch");
        assertEquals(
            realCount(before) + GUARD_REAL_INSTRUCTIONS + HOLD_INSTRUCTIONS + DESCENT_INSTRUCTIONS,
            realCount(after),
            "real bytes: the method grew by more than the two branches");
        assertTrue(otherMethodsUnchanged(real, result.bytes()), "real bytes: another method changed");

        // the transform is idempotent on the real bytes too
        ChestplateFlightPatch.Result twice = ChestplateFlightPatch.apply(result.bytes(), true, true, true);
        assertEquals(ChestplateFlightPatch.State.ALREADY_PATCHED, twice.state());
    }

    // ---- helpers: reading the built class ----------------------------------

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static MethodNode method(byte[] bytes) {
        for (MethodNode method : read(bytes).methods) {
            if (ChestplateFlightPatch.METHOD_NAME.equals(method.name)
                && ChestplateFlightPatch.METHOD_DESC.equals(method.desc)) {
                return method;
            }
        }
        throw new AssertionError("no useJetpack in " + bytes.length + " bytes");
    }

    private static List<Float> floatConstants(MethodNode method) {
        List<Float> out = new ArrayList<Float>();
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof Float) {
                out.add((Float) ((LdcInsnNode) insn).cst);
            }
        }
        return out;
    }

    /** The float stored at the jump clamp site: {@code isJumpKeyDown -> IFEQ -> LDC -> FSTORE}. */
    private static float jumpClampStoredValue(MethodNode method) {
        AbstractInsnNode value = nextReal(jumpClampStore(method).getPrevious());
        return ((Float) ((LdcInsnNode) value).cst).floatValue();
    }

    /** The local the jump clamp value is stored into. */
    private static int jumpClampLocal(MethodNode method) {
        return jumpClampStore(method).var;
    }

    /** The {@code FSTORE} of the jump clamp site: {@code isJumpKeyDown -> IFEQ -> LDC -> FSTORE}. */
    private static VarInsnNode jumpClampStore(MethodNode method) {
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (!isJumpQuery(insn)) {
                continue;
            }
            AbstractInsnNode branch = nextReal(insn.getNext());
            assertNotNull(branch, "no branch after the jump query");
            assertEquals(Opcodes.IFEQ, branch.getOpcode(), "the jump query must be followed by IFEQ");
            AbstractInsnNode value = nextReal(branch.getNext());
            assertTrue(value instanceof LdcInsnNode, "no jump clamp value after the branch");
            assertTrue(((LdcInsnNode) value).cst instanceof Float, "the jump clamp must be a float");
            AbstractInsnNode store = nextReal(value.getNext());
            assertEquals(Opcodes.FSTORE, store.getOpcode(), "the jump clamp value must be stored");
            return (VarInsnNode) store;
        }
        throw new AssertionError("no jump clamp site in these bytes");
    }

    /** The stock sneak value: the {@code -0.1f} the class ships, read from the bytes. */
    private static float stockSneakStoredValue(MethodNode method) {
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (!(insn instanceof LdcInsnNode) || !(((LdcInsnNode) insn).cst instanceof Float)) {
                continue;
            }
            if (((Float) ((LdcInsnNode) insn).cst).floatValue() != -0.1f) {
                continue;
            }
            AbstractInsnNode store = nextReal(insn.getNext());
            if (store != null && store.getOpcode() == Opcodes.FSTORE) {
                return -0.1f;
            }
        }
        throw new AssertionError("the stock -0.1f store is not in these bytes");
    }

    /**
     * The float this patch injected right after the stock sneak store, and before that
     * branch's join label: {@code IFEQ ..., LDC -0.1f, FSTORE local, LDC <descent>,
     * FSTORE local, <join>}.
     */
    private static float injectedDescentValue(MethodNode method) {
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (!(insn instanceof LdcInsnNode) || !(((LdcInsnNode) insn).cst instanceof Float)) {
                continue;
            }
            if (((Float) ((LdcInsnNode) insn).cst).floatValue() != -0.1f) {
                continue;
            }
            // the stock literal must be the body of an IFEQ, not a bare constant
            AbstractInsnNode branch = prevReal(insn);
            if (branch == null || branch.getOpcode() != Opcodes.IFEQ || !(branch instanceof JumpInsnNode)) {
                continue;
            }
            LabelNode join = ((JumpInsnNode) branch).label;
            AbstractInsnNode store = nextReal(insn.getNext());
            if (!(store instanceof VarInsnNode) || store.getOpcode() != Opcodes.FSTORE) {
                continue;
            }
            AbstractInsnNode value = nextReal(store.getNext());
            if (!(value instanceof LdcInsnNode) || !(((LdcInsnNode) value).cst instanceof Float)) {
                continue;
            }
            AbstractInsnNode again = nextReal(value.getNext());
            if (again == null || again.getOpcode() != Opcodes.FSTORE
                || ((VarInsnNode) again).var != ((VarInsnNode) store).var) {
                continue;
            }
            // the injected store must also land in the same local as the jump clamp store
            if (((VarInsnNode) again).var != jumpClampLocal(method)) {
                continue;
            }
            // the injected store must run before the branch's join label
            if (method.instructions.indexOf(again) >= method.instructions.indexOf(join)) {
                continue;
            }
            return ((Float) ((LdcInsnNode) value).cst).floatValue();
        }
        throw new AssertionError("no descent store after the stock -0.1f store, before its join label");
    }

    private static boolean isJumpQuery(AbstractInsnNode insn) {
        if (insn.getOpcode() != Opcodes.INVOKEVIRTUAL) {
            return false;
        }
        MethodInsnNode call = (MethodInsnNode) insn;
        return KEYBOARD.equals(call.owner) && ChestplateFlightPatch.JUMP_KEY.equals(call.name);
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode insn) {
        AbstractInsnNode cursor = insn;
        while (cursor != null && cursor.getOpcode() < 0) {
            cursor = cursor.getNext();
        }
        return cursor;
    }

    private static AbstractInsnNode prevReal(AbstractInsnNode insn) {
        AbstractInsnNode cursor = insn == null ? null : insn.getPrevious();
        while (cursor != null && cursor.getOpcode() < 0) {
            cursor = cursor.getPrevious();
        }
        return cursor;
    }

    private static List<AbstractInsnNode> realInsns(MethodNode method) {
        List<AbstractInsnNode> out = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn.getOpcode() >= 0) {
                out.add(insn);
            }
        }
        return out;
    }

    /** Index of the injected {@code 0.0f -> FSTORE} pair, or -1. */
    private static int holdTargetIndex(List<AbstractInsnNode> insns) {
        for (int i = 0; i + 1 < insns.size(); i++) {
            AbstractInsnNode insn = insns.get(i);
            if (!(insn instanceof LdcInsnNode) || !(((LdcInsnNode) insn).cst instanceof Float)) {
                continue;
            }
            if (((Float) ((LdcInsnNode) insn).cst).floatValue() != 0.0f) {
                continue;
            }
            if (insns.get(i + 1)
                .getOpcode() == Opcodes.FSTORE) {
                return i;
            }
        }
        return -1;
    }

    private static int count(MethodNode method, Float value) {
        int n = 0;
        for (Float found : floatConstants(method)) {
            if (found.equals(value)) {
                n++;
            }
        }
        return n;
    }

    /** How many {@code ldc float <value> -> FSTORE} pairs the method holds. */
    private static int floatStores(MethodNode method, float value) {
        int n = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (!(insn instanceof LdcInsnNode) || !(((LdcInsnNode) insn).cst instanceof Float)) {
                continue;
            }
            if (((Float) ((LdcInsnNode) insn).cst).floatValue() != value) {
                continue;
            }
            AbstractInsnNode store = nextReal(insn.getNext());
            if (store != null && store.getOpcode() == Opcodes.FSTORE) {
                n++;
            }
        }
        return n;
    }

    private static int countDouble(MethodNode method, double value) {
        int n = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof Double) {
                if (Double.compare((Double) ((LdcInsnNode) insn).cst, value) == 0) {
                    n++;
                }
            }
        }
        return n;
    }

    private static int guardHooks(MethodNode method) {
        int n = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn.getOpcode() == Opcodes.INVOKESTATIC && insn instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) insn;
                if (ChestplateFlightPatch.HOOK_OWNER.equals(call.owner)
                    && ChestplateFlightPatch.GUARD_HOOK.equals(call.name)) {
                    n++;
                }
            }
        }
        return n;
    }

    private static int realCount(MethodNode method) {
        return realInsns(method).size();
    }

    private static boolean otherMethodsUnchanged(byte[] before, byte[] after) {
        ClassNode b = read(after);
        for (MethodNode method : read(before).methods) {
            if (method.name.equals(ChestplateFlightPatch.METHOD_NAME)) {
                continue;
            }
            MethodNode now = null;
            for (MethodNode candidate : b.methods) {
                if (candidate.name.equals(method.name) && candidate.desc.equals(method.desc)) {
                    now = candidate;
                    break;
                }
            }
            if (now == null || realCount(now) != realCount(method)) {
                return false;
            }
            for (int i = 0; i < method.instructions.size(); i++) {
                if (!sameInstruction(method.instructions.get(i), now.instructions.get(i))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Same opcode and same operands: literal, member, local or int operand. */
    private static boolean sameInstruction(AbstractInsnNode a, AbstractInsnNode b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a.getOpcode() != b.getOpcode() || a.getType() != b.getType()) {
            return false;
        }
        if (a instanceof LdcInsnNode) {
            Object left = ((LdcInsnNode) a).cst;
            Object right = ((LdcInsnNode) b).cst;
            return left == null ? right == null : left.equals(right);
        }
        if (a instanceof MethodInsnNode) {
            MethodInsnNode left = (MethodInsnNode) a;
            MethodInsnNode right = (MethodInsnNode) b;
            return left.owner.equals(right.owner) && left.name.equals(right.name) && left.desc.equals(right.desc);
        }
        if (a instanceof FieldInsnNode) {
            FieldInsnNode left = (FieldInsnNode) a;
            FieldInsnNode right = (FieldInsnNode) b;
            return left.owner.equals(right.owner) && left.name.equals(right.name) && left.desc.equals(right.desc);
        }
        if (a instanceof VarInsnNode) {
            return ((VarInsnNode) a).var == ((VarInsnNode) b).var;
        }
        if (a instanceof IntInsnNode) {
            return ((IntInsnNode) a).operand == ((IntInsnNode) b).operand;
        }
        return true;
    }

    // ---- helpers: the synthetic IC2 shape ----------------------------------

    private static byte[] ic2Class(float hoverDefault, float jumpClamp) {
        return ic2ClassWithCeiling(hoverDefault, jumpClamp, ChestplateFlightPatch.ASCENT_CEILING_CONSTANT);
    }

    private static byte[] ic2ClassWithCeiling(float hoverDefault, float jumpClamp, double ceiling) {
        return ic2ClassWithCeiling(hoverDefault, jumpClamp, ceiling, true);
    }

    /**
     * The instruction sequence of the shipped {@code useJetpack}, reduced to the
     * parts this patch keys on, in the same order and with the same constants.
     *
     * @param withSneakClamp false to drop the {@code -0.1f -> FSTORE} pair while
     *                       keeping the sneak key test, i.e. a class whose
     *                       hover+sneak clamp store is missing
     */
    private static byte[] ic2ClassWithCeiling(float hoverDefault, float jumpClamp, double ceiling,
        boolean withSneakClamp) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        writer.visit(
            Opcodes.V1_8,
            Opcodes.ACC_PUBLIC,
            ChestplateFlightPatch.TARGET_INTERNAL_NAME,
            null,
            "java/lang/Object",
            null);

        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();

        MethodVisitor mv = writer.visitMethod(
            Opcodes.ACC_PUBLIC,
            ChestplateFlightPatch.METHOD_NAME,
            ChestplateFlightPatch.METHOD_DESC,
            null,
            null);
        mv.visitCode();

        // oldMotionY = player.motionY
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitFieldInsn(Opcodes.GETFIELD, PLAYER, MOTION_Y, "D");
        mv.visitVarInsn(Opcodes.DSTORE, 9);

        // player.motionY = Math.min(player.motionY + power * 0.2f, ceiling)
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitFieldInsn(Opcodes.GETFIELD, PLAYER, MOTION_Y, "D");
        mv.visitLdcInsn(Float.valueOf(0.3f));
        mv.visitLdcInsn(Float.valueOf(0.2f));
        mv.visitInsn(Opcodes.FMUL);
        mv.visitInsn(Opcodes.F2D);
        mv.visitInsn(Opcodes.DADD);
        mv.visitLdcInsn(Double.valueOf(ceiling));
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "min", "(DD)D", false);
        mv.visitFieldInsn(Opcodes.PUTFIELD, PLAYER, MOTION_Y, "D");

        // if (hover) { ... clamp ... }
        Label end = new Label();
        mv.visitVarInsn(Opcodes.ILOAD, 2);
        mv.visitJumpInsn(Opcodes.IFEQ, end);

        mv.visitLdcInsn(Float.valueOf(hoverDefault));
        mv.visitVarInsn(Opcodes.FSTORE, 11);

        Label afterSneak = new Label();
        mv.visitFieldInsn(Opcodes.GETSTATIC, IC2, "keyboard", "L" + KEYBOARD + ";");
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, KEYBOARD, "isSneakKeyDown", "(L" + PLAYER + ";)Z", false);
        mv.visitJumpInsn(Opcodes.IFEQ, afterSneak);
        if (withSneakClamp) {
            mv.visitLdcInsn(Float.valueOf(-0.1f));
            mv.visitVarInsn(Opcodes.FSTORE, 11);
        }
        mv.visitLabel(afterSneak);

        Label afterJump = new Label();
        mv.visitFieldInsn(Opcodes.GETSTATIC, IC2, "keyboard", "L" + KEYBOARD + ";");
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            KEYBOARD,
            ChestplateFlightPatch.JUMP_KEY,
            "(L" + PLAYER + ";)Z",
            false);
        mv.visitJumpInsn(Opcodes.IFEQ, afterJump);
        mv.visitLdcInsn(Float.valueOf(jumpClamp));
        mv.visitVarInsn(Opcodes.FSTORE, 11);
        mv.visitLabel(afterJump);

        // if (motionY > v) { motionY = v; if (oldMotionY > motionY) motionY = oldMotionY; }
        Label afterClamp = new Label();
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitFieldInsn(Opcodes.GETFIELD, PLAYER, MOTION_Y, "D");
        mv.visitVarInsn(Opcodes.FLOAD, 11);
        mv.visitInsn(Opcodes.F2D);
        mv.visitInsn(Opcodes.DCMPL);
        mv.visitJumpInsn(Opcodes.IFLE, afterClamp);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitVarInsn(Opcodes.FLOAD, 11);
        mv.visitInsn(Opcodes.F2D);
        mv.visitFieldInsn(Opcodes.PUTFIELD, PLAYER, MOTION_Y, "D");
        mv.visitVarInsn(Opcodes.DLOAD, 9);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitFieldInsn(Opcodes.GETFIELD, PLAYER, MOTION_Y, "D");
        mv.visitInsn(Opcodes.DCMPL);
        mv.visitJumpInsn(Opcodes.IFLE, afterClamp);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitVarInsn(Opcodes.DLOAD, 9);
        mv.visitFieldInsn(Opcodes.PUTFIELD, PLAYER, MOTION_Y, "D");
        mv.visitLabel(afterClamp);

        mv.visitLabel(end);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        writer.visitEnd();
        return writer.toByteArray();
    }

    /** {@code ic2.core.IC2}, with the one public static field the method reads. */
    private static byte[] ic2Stub() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, IC2, null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "keyboard", "L" + KEYBOARD + ";", null, null)
            .visitEnd();
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    /** {@code ic2.core.util.Keyboard}, with the two queries, both false. */
    private static byte[] keyboardStub() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, KEYBOARD, null, "java/lang/Object", null);
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();
        for (String name : new String[] { ChestplateFlightPatch.JUMP_KEY, "isSneakKeyDown" }) {
            MethodVisitor query = writer.visitMethod(Opcodes.ACC_PUBLIC, name, "(L" + PLAYER + ";)Z", null, null);
            query.visitCode();
            query.visitInsn(Opcodes.ICONST_0);
            query.visitInsn(Opcodes.IRETURN);
            query.visitMaxs(0, 0);
            query.visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void writeClass(Path dir, String internalName, byte[] bytes) throws Exception {
        Path file = dir.resolve(internalName + ".class");
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
    }

    private static byte[] readEntry(String jar, String entry) throws Exception {
        try (ZipFile zip = new ZipFile(jar)) {
            InputStream in = zip.getInputStream(zip.getEntry(entry));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }
}
