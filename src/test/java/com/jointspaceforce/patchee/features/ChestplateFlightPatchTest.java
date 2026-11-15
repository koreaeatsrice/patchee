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
 * <li>the stock shape is patched: the clamp is lifted to 0.6, the shift+space
 * branch writes 0.0, the ground guard is at the top, and the descent constants
 * are untouched;</li>
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

    /** The guard's eight nodes are seven opcodes plus the proceed label they jump to. */
    private static final int GUARD_REAL_INSTRUCTIONS = GUARD_INSTRUCTIONS - 1;

    // ---- the stock shape ---------------------------------------------------

    @Test
    void patchesTheStockShapeAndLiftsOnlyTheJumpClamp() {
        byte[] original = ic2Class(-0.025f, 0.1f);
        ChestplateFlightPatch.Result result = ChestplateFlightPatch.apply(original, true, true, true);

        assertEquals(ChestplateFlightPatch.State.PATCHED, result.state(), result.detail());
        assertEquals(3, result.sites());

        MethodNode method = method(result.bytes());
        List<Float> clamps = floatConstants(method);
        assertEquals(1, count(method, Float.valueOf(0.6f)), "the jump clamp was not lifted: " + clamps);
        assertEquals(1, count(method, Float.valueOf(-0.025f)), "the no-input hover clamp moved: " + clamps);
        assertEquals(1, count(method, Float.valueOf(-0.1f)), "the sneak clamp moved: " + clamps);
        assertEquals(1, count(method, Float.valueOf(0.0f)), "the shift+space target is missing: " + clamps);
        assertEquals(0, count(method, Float.valueOf(0.1f)), "the stock jump clamp is still there: " + clamps);
        assertTrue(ChestplateFlightPatch.hasGuardHook(method), "the ground guard was not injected");
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
        assertEquals(1, count(method, Float.valueOf(0.6f)), "the lift did not fire: " + clamps);
        assertEquals(
            2,
            count(method, Float.valueOf(0.0f)),
            "Hodgepodge's 0.0 default and our 0.0 hold target must both be there: " + clamps);
        assertEquals(0, count(method, Float.valueOf(-0.025f)), "the -0.025 clamp came back: " + clamps);
        // the hold case never passes through the default, so Hodgepodge cannot reach it
        assertEquals(1, count(method, Float.valueOf(-0.1f)));
    }

    @Test
    void leavesHodgepodgesTargetInPlaceWhenThisPatchRunsFirst() {
        // Patchee ran first: -0.025f must still be there for Hodgepodge's
        // @ModifyConstant to find, next to every edit this patch made.
        byte[] patched = ChestplateFlightPatch.apply(ic2Class(-0.025f, 0.1f), true, true, true)
            .bytes();
        MethodNode method = method(patched);
        assertEquals(1, count(method, Float.valueOf(-0.025f)), "Hodgepodge's -0.025f target is gone");
        assertEquals(1, count(method, Float.valueOf(0.6f)), "the lift is missing");
        assertEquals(1, count(method, Float.valueOf(0.0f)), "the hold target is missing");
    }

    // ---- the switches are independent --------------------------------------

    @Test
    void liftAloneLeavesTheOtherTwoOut() {
        ChestplateFlightPatch.Result result = ChestplateFlightPatch.apply(ic2Class(-0.025f, 0.1f), true, false, false);
        assertEquals(1, result.sites());
        MethodNode method = method(result.bytes());
        assertFalse(ChestplateFlightPatch.hasGuardHook(method));
        assertTrue(floatConstants(method).contains(Float.valueOf(0.6f)));
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
        assertEquals(1, count(method, Float.valueOf(0.6f)), "jump-only must climb at the ceiling");
        assertEquals(1, count(method, Float.valueOf(0.0f)), "sneak+jump must hold altitude");
    }

    @Test
    void allOffChangesNothing() {
        byte[] original = ic2Class(-0.025f, 0.1f);
        ChestplateFlightPatch.Result result = ChestplateFlightPatch.apply(original, false, false, false);
        assertEquals(ChestplateFlightPatch.State.ALREADY_PATCHED, result.state());
        assertSame(original, result.bytes());
    }

    // ---- idempotence -------------------------------------------------------

    @Test
    void aSecondPassChangesNothing() {
        byte[] once = ChestplateFlightPatch.apply(ic2Class(-0.025f, 0.1f), true, true, true)
            .bytes();
        ChestplateFlightPatch.Result twice = ChestplateFlightPatch.apply(once, true, true, true);
        assertEquals(ChestplateFlightPatch.State.ALREADY_PATCHED, twice.state(), twice.detail());
        assertSame(once, twice.bytes());
        assertEquals(1, count(method(once), Float.valueOf(0.6f)), "the 0.6 clamp was duplicated");
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
        MethodNode method = method(result.bytes());
        assertEquals(1, count(method, Float.valueOf(0.6f)));
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
            assertEquals(1, count(method, Float.valueOf(0.6f)));
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
    void theLiftedClampIsTheNormalAscentCeiling() {
        assertEquals(0.6f, ChestplateFlight.hoverLiftClamp());
        assertEquals(0.6f, ChestplateFlightPatch.HOVER_JUMP_LIFTED);
        assertEquals(
            (double) ChestplateFlight.ASCENT_CEILING,
            ChestplateFlightPatch.ASCENT_CEILING_CONSTANT,
            "the clamp must be the same number the ascent ceiling uses");
    }

    @Test
    void theHoverTargetTableIsSneakThenJumpThenBoth() {
        float lifted = ChestplateFlight.hoverLiftClamp();
        float stock = ChestplateFlight.IC2_HOVER_JUMP_CLAMP;

        // (-,-) IC2's own default sink, untouched by us
        assertEquals(-0.025f, ChestplateFlight.hoverVerticalTarget(false, false, lifted));
        assertEquals(-0.025f, ChestplateFlight.hoverVerticalTarget(false, false, stock));
        // (+,-) sneak alone still sinks at 0.1
        assertEquals(-0.1f, ChestplateFlight.hoverVerticalTarget(true, false, lifted));
        assertEquals(-0.1f, ChestplateFlight.hoverVerticalTarget(true, false, stock));
        // (-,+) jump alone climbs: the ceiling with the fix, 0.1 without it
        assertEquals(lifted, ChestplateFlight.hoverVerticalTarget(false, true, lifted));
        assertEquals(stock, ChestplateFlight.hoverVerticalTarget(false, true, stock));
        // (-,+) jump alone must reach the 0.6 ceiling the fix installs
        assertEquals(
            0.6f,
            ChestplateFlight.hoverVerticalTarget(false, true, 0.6f),
            "jump alone reaches the 0.6 ceiling");
        // (+,+) both keys: the target is 0.0 (an in-progress climb damps to it)
        assertEquals(0.0f, ChestplateFlight.hoverVerticalTarget(true, true, lifted));
        assertEquals(0.0f, ChestplateFlight.hoverVerticalTarget(true, true, stock));
        // the two-key case must not be reachable through the stock ordering
        assertTrue(
            ChestplateFlight.hoverVerticalTarget(true, true, lifted)
                != ChestplateFlight.hoverVerticalTarget(false, true, lifted),
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
        assertEquals(1, count(after, Float.valueOf(0.6f)), "real bytes: no 0.6 clamp");
        assertEquals(1, count(after, Float.valueOf(-0.025f)), "real bytes: the no-input hover clamp moved");
        assertEquals(1, count(after, Float.valueOf(-0.1f)), "real bytes: the sneak clamp moved");
        assertEquals(1, count(after, Float.valueOf(0.0f)), "real bytes: no shift+space target");
        assertEquals(0, count(after, Float.valueOf(0.1f)), "real bytes: the stock clamp is still there");
        assertTrue(ChestplateFlightPatch.hasGuardHook(after));
        assertTrue(holdTargetIndex(realInsns(after)) > 0, "real bytes: no shift+space branch");
        assertEquals(
            realCount(before) + GUARD_REAL_INSTRUCTIONS + HOLD_INSTRUCTIONS,
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
                if (method.instructions.get(i)
                    .getOpcode()
                    != now.instructions.get(i)
                        .getOpcode()) {
                    return false;
                }
            }
        }
        return true;
    }

    // ---- helpers: the synthetic IC2 shape ----------------------------------

    private static byte[] ic2Class(float hoverDefault, float jumpClamp) {
        return ic2ClassWithCeiling(hoverDefault, jumpClamp, ChestplateFlightPatch.ASCENT_CEILING_CONSTANT);
    }

    /**
     * The instruction sequence of the shipped {@code useJetpack}, reduced to the
     * parts this patch keys on, in the same order and with the same constants.
     */
    private static byte[] ic2ClassWithCeiling(float hoverDefault, float jumpClamp, double ceiling) {
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
        mv.visitLdcInsn(Float.valueOf(-0.1f));
        mv.visitVarInsn(Opcodes.FSTORE, 11);
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
