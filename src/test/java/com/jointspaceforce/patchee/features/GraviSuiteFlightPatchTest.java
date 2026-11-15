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

/**
 * The GraviSuite half of the flight patch: the same three edits, in
 * {@code ItemAdvancedJetPack.useJetpack(EntityPlayer, boolean)}.
 *
 * <p>
 * The repo does not carry GraviSuite's jar, so the tests rebuild the instruction
 * sequence this patch keys on — the shipped {@code gravisuite-2.0.3} order
 * ({@code javap -p -c gravisuite.ItemAdvancedJetPack#useJetpack}: ceiling at
 * offset 319, jump clamp {@code 0.2d} at 349, sneak clamp {@code -0.2d} at 365,
 * clamp chain at 443-472) — and pin:
 *
 * <ul>
 * <li>the stock shape is patched: the jump clamp becomes 0.6, the shift+space
 * branch writes 0.0, the ground guard is at the top, and the {@code -0.2d}
 * descent is untouched;</li>
 * <li>each of the three switches is independent;</li>
 * <li>idempotence, and refusal of every other shape;</li>
 * <li>the patched bytes still pass the JVM verifier (both injected frames);</li>
 * <li>optionally, against the real jar when {@code PATCHEE_GRAVISUITE_JAR} is
 * set.</li>
 * </ul>
 */
class GraviSuiteFlightPatchTest {

    /** Set to the GraviSuite jar to run the real-bytes checks; skipped otherwise. */
    private static final String REAL_JAR_ENV = "PATCHEE_GRAVISUITE_JAR";

    private static final String PLAYER = "net/minecraft/entity/player/EntityPlayer";
    private static final String MOTION_Y = "field_70181_x";
    private static final String KEYBOARD = "gravisuite/keyboard/Keyboard";
    private static final String GRAVISUITE = "gravisuite/GraviSuite";

    /** Seven inserted instructions, plus the label the sneak branch already had. */
    private static final int HOLD_INSTRUCTIONS = 7;

    /** The guard's eight list entries: seven opcodes plus the shared proceed label. */
    private static final int GUARD_INSTRUCTIONS = 8;

    // ---- the stock shape ---------------------------------------------------

    @Test
    void patchesTheStockShapeAndLiftsOnlyTheJumpClamp() {
        byte[] original = graviClass(GraviSuiteFlightPatch.ASCENT_CEILING_CONSTANT, 0.2d);
        GraviSuiteFlightPatch.Result result = GraviSuiteFlightPatch.apply(original, true, true, true);

        assertEquals(GraviSuiteFlightPatch.State.PATCHED, result.state(), result.detail());
        assertEquals(3, result.sites());
        assertTrue(result.liftApplied(), "the lift must be reported as applied");
        assertTrue(result.holdApplied(), "the hold must be reported as applied");
        assertTrue(result.guardApplied(), "the guard must be reported as applied");

        MethodNode method = method(result.bytes());
        // the 0.2 descent is gone; the 0.6 ceiling and the lifted clamp are the
        // only two 0.6 doubles left, and exactly one of them is the clamp store
        assertEquals(0, count(method, 0.2d), "the stock jump clamp is still there: " + doubleConstants(method));
        assertEquals(
            2,
            count(method, GraviSuiteFlightPatch.ASCENT_CEILING_CONSTANT),
            "expected the ceiling plus the lifted clamp: " + doubleConstants(method));
        assertEquals(1, clampStores(method), "exactly one 0.6 store to the clamp local");
        assertEquals(1, count(method, -0.2d), "the sneak descent moved: " + doubleConstants(method));
        assertEquals(1, holdStores(method), "the shift+space target is missing: " + doubleConstants(method));
        assertTrue(GraviSuiteFlightPatch.hasGuardHook(method), "the ground guard was not injected");
    }

    @Test
    void theGuardIsExactlyTheEntryBranch() {
        MethodNode method = method(
            GraviSuiteFlightPatch
                .apply(graviClass(GraviSuiteFlightPatch.ASCENT_CEILING_CONSTANT, 0.2d), false, true, false)
                .bytes());
        AbstractInsnNode[] all = method.instructions.toArray();
        assertTrue(all.length >= GUARD_INSTRUCTIONS, "the method is too short to hold the guard");
        AbstractInsnNode[] head = new AbstractInsnNode[GUARD_INSTRUCTIONS];
        System.arraycopy(all, 0, head, 0, GUARD_INSTRUCTIONS);

        // 1. only while hovering — the method is static, so the flag is local 1
        assertEquals(Opcodes.ILOAD, head[0].getOpcode());
        assertEquals(1, ((VarInsnNode) head[0]).var, "the guard must read the hover flag in local 1");
        assertEquals(Opcodes.IFEQ, head[1].getOpcode(), "hover off must skip the guard");

        // 2. the pure decision in the hook — the player is local 0
        assertEquals(Opcodes.ALOAD, head[2].getOpcode());
        assertEquals(0, ((VarInsnNode) head[2]).var, "the guard must read the player in local 0");
        assertTrue(head[3] instanceof MethodInsnNode, "expected the hook call");
        MethodInsnNode call = (MethodInsnNode) head[3];
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals(GraviSuiteFlightPatch.HOOK_OWNER, call.owner);
        assertEquals(GraviSuiteFlightPatch.GUARD_HOOK, call.name);
        assertEquals(GraviSuiteFlightPatch.GUARD_HOOK_DESC, call.desc);
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
    void theHoldBranchIsPlacedAfterTheSneakOnlyStore() {
        MethodNode method = method(
            GraviSuiteFlightPatch
                .apply(graviClass(GraviSuiteFlightPatch.ASCENT_CEILING_CONSTANT, 0.2d), false, false, true)
                .bytes());
        List<AbstractInsnNode> insns = realInsns(method);

        int sneakCall = -1;
        for (int i = 0; i < insns.size(); i++) {
            if (insns.get(i) instanceof MethodInsnNode
                && GraviSuiteFlightPatch.SNEAK_KEY.equals(((MethodInsnNode) insns.get(i)).name)) {
                sneakCall = i;
            }
        }
        assertTrue(sneakCall > 0, "no sneak-key query in the method");
        JumpInsnNode sneakBranch = (JumpInsnNode) insns.get(sneakCall + 1);
        LabelNode join = sneakBranch.label;
        assertEquals(Opcodes.IFEQ, sneakBranch.getOpcode());

        // stock sneak clamp: ldc2_w -0.2d, dstore local
        int storeIndex = sneakCall + 3;
        assertTrue(insns.get(sneakCall + 2) instanceof LdcInsnNode, "no sneak-only clamp value");
        assertEquals(
            Opcodes.DSTORE,
            insns.get(storeIndex)
                .getOpcode());
        VarInsnNode sneakStore = (VarInsnNode) insns.get(storeIndex);

        // everything between that store and the sneak branch's join: our seven instructions
        List<AbstractInsnNode> between = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode node = sneakStore.getNext(); node != null && node != join; node = node.getNext()) {
            if (node.getOpcode() >= 0) {
                between.add(node);
            }
        }
        assertEquals(HOLD_INSTRUCTIONS, between.size(), "the hold branch must sit after the sneak-only store");

        assertEquals(
            Opcodes.GETSTATIC,
            between.get(0)
                .getOpcode(),
            "the branch must push the keyboard the method uses");
        assertEquals(GRAVISUITE, ((FieldInsnNode) between.get(0)).owner);
        assertEquals("keyboard", ((FieldInsnNode) between.get(0)).name);
        assertEquals("L" + KEYBOARD + ";", ((FieldInsnNode) between.get(0)).desc);
        assertEquals(
            Opcodes.POP,
            between.get(1)
                .getOpcode());
        assertEquals(
            Opcodes.ALOAD,
            between.get(2)
                .getOpcode());
        assertEquals(0, ((VarInsnNode) between.get(2)).var, "the branch must read the player local the method uses");
        MethodInsnNode jump = (MethodInsnNode) between.get(3);
        assertEquals(Opcodes.INVOKESTATIC, jump.getOpcode());
        assertEquals(KEYBOARD, jump.owner);
        assertEquals(GraviSuiteFlightPatch.JUMP_KEY, jump.name);
        assertEquals(
            Opcodes.IFEQ,
            between.get(4)
                .getOpcode());
        assertSame(join, ((JumpInsnNode) between.get(4)).label, "the hold IFEQ must join where the sneak branch joins");
        assertTrue(between.get(5) instanceof LdcInsnNode, "no hold target");
        assertEquals(
            GraviSuiteFlightPatch.HOLD_ALTITUDE_TARGET,
            ((Double) ((LdcInsnNode) between.get(5)).cst).doubleValue());
        assertEquals(
            Opcodes.DSTORE,
            between.get(6)
                .getOpcode());
        assertEquals(sneakStore.var, ((VarInsnNode) between.get(6)).var, "the hold branch must write the clamp local");

        // no GOTO and no extra frame: one plain overwrite after the store
        for (AbstractInsnNode node : method.instructions.toArray()) {
            assertFalse(node.getOpcode() == Opcodes.GOTO, "the hold branch must not need a GOTO");
        }

        // the sneak-only value is still stock
        assertEquals(1, count(method, -0.2d), "the sneak-only clamp moved");
        assertEquals(
            1,
            count(method, GraviSuiteFlightPatch.ASCENT_CEILING_CONSTANT),
            "the clamp was lifted by the hold branch");
    }

    // ---- the switches are independent --------------------------------------

    @Test
    void liftAloneLeavesTheOtherTwoOut() {
        GraviSuiteFlightPatch.Result result = GraviSuiteFlightPatch
            .apply(graviClass(GraviSuiteFlightPatch.ASCENT_CEILING_CONSTANT, 0.2d), true, false, false);
        assertEquals(1, result.sites());
        MethodNode method = method(result.bytes());
        assertFalse(GraviSuiteFlightPatch.hasGuardHook(method));
        assertEquals(1, clampStores(method), "the jump clamp was not lifted");
        assertEquals(0, count(method, 0.2d));
        assertEquals(0, holdStores(method), "the hold branch leaked in");
    }

    @Test
    void holdAloneLeavesTheClampStock() {
        GraviSuiteFlightPatch.Result result = GraviSuiteFlightPatch
            .apply(graviClass(GraviSuiteFlightPatch.ASCENT_CEILING_CONSTANT, 0.2d), false, false, true);
        assertEquals(1, result.sites());
        MethodNode method = method(result.bytes());
        assertFalse(GraviSuiteFlightPatch.hasGuardHook(method));
        assertEquals(1, count(method, 0.2d), "the jump-only clamp was changed by the hold branch");
        assertEquals(
            1,
            count(method, GraviSuiteFlightPatch.ASCENT_CEILING_CONSTANT),
            "the ceiling is the only 0.6 double without the lift");
        assertEquals(1, holdStores(method));
    }

    @Test
    void guardAloneLeavesTheClampStock() {
        GraviSuiteFlightPatch.Result result = GraviSuiteFlightPatch
            .apply(graviClass(GraviSuiteFlightPatch.ASCENT_CEILING_CONSTANT, 0.2d), false, true, false);
        assertEquals(1, result.sites());
        MethodNode method = method(result.bytes());
        assertTrue(GraviSuiteFlightPatch.hasGuardHook(method));
        assertEquals(1, count(method, 0.2d), "the clamp was changed by the guard");
        assertEquals(1, count(method, GraviSuiteFlightPatch.ASCENT_CEILING_CONSTANT));
        assertEquals(0, holdStores(method), "the hold branch leaked in");
    }

    @Test
    void holdWorksWithTheLiftOnToo() {
        MethodNode method = method(
            GraviSuiteFlightPatch
                .apply(graviClass(GraviSuiteFlightPatch.ASCENT_CEILING_CONSTANT, 0.2d), true, false, true)
                .bytes());
        assertEquals(1, clampStores(method), "jump alone must climb at the ceiling");
        assertEquals(1, holdStores(method), "sneak+jump must hold altitude");
        assertEquals(0, count(method, 0.2d));
    }

    @Test
    void allOffChangesNothing() {
        byte[] original = graviClass(GraviSuiteFlightPatch.ASCENT_CEILING_CONSTANT, 0.2d);
        GraviSuiteFlightPatch.Result result = GraviSuiteFlightPatch.apply(original, false, false, false);
        assertEquals(GraviSuiteFlightPatch.State.ALREADY_PATCHED, result.state());
        assertSame(original, result.bytes());
    }

    // ---- idempotence -------------------------------------------------------

    @Test
    void aSecondPassChangesNothing() {
        byte[] once = GraviSuiteFlightPatch
            .apply(graviClass(GraviSuiteFlightPatch.ASCENT_CEILING_CONSTANT, 0.2d), true, true, true)
            .bytes();
        GraviSuiteFlightPatch.Result twice = GraviSuiteFlightPatch.apply(once, true, true, true);
        assertEquals(GraviSuiteFlightPatch.State.ALREADY_PATCHED, twice.state(), twice.detail());
        assertSame(once, twice.bytes());
        MethodNode method = method(once);
        assertEquals(1, clampStores(method), "the 0.6 clamp was duplicated");
        assertEquals(1, guardHooks(method), "the guard was duplicated");
        assertEquals(1, holdStores(method), "the hold store was duplicated");
    }

    @Test
    void aSecondPassWithOneThirdAlreadyDoneAppliesOnlyTheRest() {
        byte[] liftOnly = GraviSuiteFlightPatch
            .apply(graviClass(GraviSuiteFlightPatch.ASCENT_CEILING_CONSTANT, 0.2d), true, false, false)
            .bytes();
        GraviSuiteFlightPatch.Result result = GraviSuiteFlightPatch.apply(liftOnly, true, true, true);
        assertEquals(GraviSuiteFlightPatch.State.PATCHED, result.state(), result.detail());
        assertEquals(2, result.sites());
        assertFalse(result.liftApplied(), "the lift was made by the first pass, not this one");
        assertTrue(result.holdApplied(), "the hold must be reported as applied");
        assertTrue(result.guardApplied(), "the guard must be reported as applied");
        MethodNode method = method(result.bytes());
        assertEquals(1, clampStores(method));
        assertEquals(1, guardHooks(method));
        assertEquals(1, holdStores(method));
    }

    // ---- another mixin already rewrote shift+space --------------------------

    @Test
    void appliesOnlyTheGuardWhenTheSneakClampWasAlreadyRewritten() {
        byte[] original = graviClassWithCeiling(0.2d, GraviSuiteFlightPatch.ASCENT_CEILING_CONSTANT, false);
        GraviSuiteFlightPatch.Result result = GraviSuiteFlightPatch.apply(original, true, true, true);

        assertEquals(GraviSuiteFlightPatch.State.PARTIAL, result.state(), result.detail());
        assertEquals(1, result.sites(), "only the guard may land without the sneak clamp site");
        assertFalse(result.liftApplied(), "the climb must be refused without the sneak clamp site");
        assertFalse(result.holdApplied(), "the shift+space edit must be refused with the climb");
        assertTrue(result.guardApplied(), "the ground guard must still land");
        assertFalse(java.util.Arrays.equals(original, result.bytes()), "the class must be rewritten");

        MethodNode method = method(result.bytes());
        assertTrue(GraviSuiteFlightPatch.hasGuardHook(method), "the ground guard was not injected");
        assertEquals(0, clampStores(method), "the jump clamp must stay stock");
        assertEquals(1, count(method, 0.2d), "the jump clamp is still 0.2d: " + doubleConstants(method));
        assertEquals(0, holdStores(method), "the shift+space branch leaked in without a clamp site");
        assertTrue(
            result.detail()
                .contains("shift+space"),
            result.detail());
    }

    @Test
    void aClassWithOnlyTheHoldRequestedAndNoSneakClampIsUnchanged() {
        byte[] original = graviClassWithCeiling(0.2d, GraviSuiteFlightPatch.ASCENT_CEILING_CONSTANT, false);
        GraviSuiteFlightPatch.Result result = GraviSuiteFlightPatch.apply(original, false, false, true);

        assertEquals(GraviSuiteFlightPatch.State.ALREADY_PATCHED, result.state(), result.detail());
        assertEquals(0, result.sites());
        assertTrue(result.skipped(), "the refusal to place the hold must be reported");
        assertSame(original, result.bytes(), "nothing was edited, so the bytes are handed back as they came");
        assertTrue(
            result.detail()
                .contains("shift+space"),
            result.detail());
    }

    @Test
    void refusesToRaiseTheClimbAloneWhenTheSneakClampIsMissing() {
        byte[] original = graviClassWithCeiling(0.2d, GraviSuiteFlightPatch.ASCENT_CEILING_CONSTANT, false);
        GraviSuiteFlightPatch.Result result = GraviSuiteFlightPatch.apply(original, true, false, false);

        assertEquals(GraviSuiteFlightPatch.State.ALREADY_PATCHED, result.state(), result.detail());
        assertSame(original, result.bytes(), "raising the climb alone would turn the other mixin's hold into a climb");
        assertTrue(result.skipped(), "the refusal to raise the climb must be reported");
        assertEquals(1, count(method(result.bytes()), 0.2d), "the jump clamp must still be stock 0.2d");
    }

    // ---- refusal -----------------------------------------------------------

    @Test
    void refusesAClassWithoutTheAscentCeiling() {
        byte[] other = graviClassWithCeiling(0.2d, 0.7d);
        GraviSuiteFlightPatch.Result result = GraviSuiteFlightPatch.apply(other, true, true, true);
        assertEquals(GraviSuiteFlightPatch.State.UNEXPECTED_SHAPE, result.state());
        assertSame(other, result.bytes());
        assertTrue(
            result.detail()
                .contains("ascent ceiling"),
            result.detail());
    }

    @Test
    void refusesAnUnknownClampValue() {
        byte[] unknown = graviClass(GraviSuiteFlightPatch.ASCENT_CEILING_CONSTANT, 0.25d);
        GraviSuiteFlightPatch.Result result = GraviSuiteFlightPatch.apply(unknown, true, true, true);
        assertEquals(GraviSuiteFlightPatch.State.UNEXPECTED_SHAPE, result.state());
        assertTrue(
            result.detail()
                .length() > 0,
            "the refusal must say why");
        assertTrue(
            result.detail()
                .contains("clamp site"),
            result.detail());
        assertSame(unknown, result.bytes());
    }

    @Test
    void refusesAMissingMethod() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(
            Opcodes.V1_8,
            Opcodes.ACC_PUBLIC,
            GraviSuiteFlightPatch.TARGET_INTERNAL_NAME,
            null,
            "java/lang/Object",
            null);
        writer.visitEnd();
        byte[] empty = writer.toByteArray();
        GraviSuiteFlightPatch.Result result = GraviSuiteFlightPatch.apply(empty, true, true, true);
        assertEquals(GraviSuiteFlightPatch.State.UNEXPECTED_SHAPE, result.state());
        assertTrue(
            result.detail()
                .contains("no useJetpack"),
            result.detail());
        assertSame(empty, result.bytes());
    }

    @Test
    void refusesAClassThatIsNotGraviSuites() {
        byte[] renamed = rename(
            graviClass(GraviSuiteFlightPatch.ASCENT_CEILING_CONSTANT, 0.2d),
            "gravisuite/ItemGraviChestPlate");
        GraviSuiteFlightPatch.Result result = GraviSuiteFlightPatch.apply(renamed, true, true, true);
        assertEquals(GraviSuiteFlightPatch.State.UNEXPECTED_SHAPE, result.state());
        assertSame(renamed, result.bytes());
        assertTrue(
            result.detail()
                .contains(GraviSuiteFlightPatch.TARGET_INTERNAL_NAME),
            result.detail());
    }

    @Test
    void aNullClassIsNotAPatch() {
        assertEquals(
            GraviSuiteFlightPatch.State.UNEXPECTED_SHAPE,
            GraviSuiteFlightPatch.apply(null, true, true, true)
                .state());
    }

    // ---- the transformer ---------------------------------------------------

    @Test
    void anUnknownClassPassesThroughTheTransformerByteIdentical() {
        byte[] bytes = graviClass(GraviSuiteFlightPatch.ASCENT_CEILING_CONSTANT, 0.2d);
        byte[] out = new com.jointspaceforce.patchee.asm.GraviSuiteFlightTransformer()
            .transform("gravisuite.ItemGraviChestPlate", "gravisuite.ItemGraviChestPlate", bytes);
        assertSame(bytes, out);
    }

    @Test
    void allSwitchesOffLeaveTheClassByteIdentical() {
        byte[] real = graviClass(GraviSuiteFlightPatch.ASCENT_CEILING_CONSTANT, 0.2d);
        ChestplateFlightSettings.injectForTest(
            ChestplateFlightSettings.parse(
                "B:enabled=true\nB:enableChestplateHoverLift=false\nB:enableChestplateHoldAltitude=false\n"
                    + "B:enableChestplateGroundGuard=false\n"));
        try {
            byte[] out = new com.jointspaceforce.patchee.asm.GraviSuiteFlightTransformer()
                .transform(GraviSuiteFlightPatch.TARGET_CLASS, GraviSuiteFlightPatch.TARGET_CLASS, real);
            assertSame(real, out);
        } finally {
            ChestplateFlightSettings.clearInjectedForTest();
        }
    }

    @Test
    void theTransformerPatchesWithTheChestplateSwitches() {
        byte[] real = graviClass(GraviSuiteFlightPatch.ASCENT_CEILING_CONSTANT, 0.2d);
        ChestplateFlightSettings.injectForTest(
            ChestplateFlightSettings.parse(
                "B:enabled=true\nB:enableChestplateHoverLift=true\nB:enableChestplateHoldAltitude=true\n"
                    + "B:enableChestplateGroundGuard=false\n"));
        try {
            byte[] out = new com.jointspaceforce.patchee.asm.GraviSuiteFlightTransformer()
                .transform(GraviSuiteFlightPatch.TARGET_CLASS, GraviSuiteFlightPatch.TARGET_CLASS, real);
            MethodNode method = method(out);
            assertEquals(1, clampStores(method));
            assertEquals(1, holdStores(method));
            assertFalse(GraviSuiteFlightPatch.hasGuardHook(method));
        } finally {
            ChestplateFlightSettings.clearInjectedForTest();
        }
    }

    @Test
    void theTransformerPassesAnUnknownShapeThroughUntouched() {
        byte[] other = graviClassWithCeiling(0.2d, 0.7d);
        ChestplateFlightSettings.injectForTest(ChestplateFlightSettings.parse("B:enabled=true\n"));
        try {
            byte[] out = new com.jointspaceforce.patchee.asm.GraviSuiteFlightTransformer()
                .transform(GraviSuiteFlightPatch.TARGET_CLASS, GraviSuiteFlightPatch.TARGET_CLASS, other);
            assertSame(other, out, "an unknown shape must be handed back untouched");
        } finally {
            ChestplateFlightSettings.clearInjectedForTest();
        }
    }

    // ---- the patched bytes still verify ------------------------------------

    @Test
    void patchedBytesPassTheJvmVerifier(@TempDir Path dir) throws Exception {
        byte[] patched = GraviSuiteFlightPatch
            .apply(graviClass(GraviSuiteFlightPatch.ASCENT_CEILING_CONSTANT, 0.2d), true, true, true)
            .bytes();
        // GraviSuite is not on the test classpath, so the two classes the method
        // references are stubbed next to the patched class; the JVM then verifies
        // the real patched bytes, both injected frames included.
        writeClass(dir, GRAVISUITE, graviSuiteStub());
        writeClass(dir, KEYBOARD, keyboardStub());
        writeClass(dir, GraviSuiteFlightPatch.TARGET_INTERNAL_NAME, patched);

        try (URLClassLoader loader = new URLClassLoader(
            new URL[] { dir.toUri()
                .toURL() },
            getClass().getClassLoader())) {
            Class<?> loaded = Class.forName(GraviSuiteFlightPatch.TARGET_INTERNAL_NAME.replace('/', '.'), true, loader);
            // Instantiating forces link + verification of every method.
            loaded.getDeclaredConstructor()
                .newInstance();
            assertNotNull(
                loaded.getDeclaredMethod("useJetpack", net.minecraft.entity.player.EntityPlayer.class, boolean.class));
        }
    }

    // ---- the real jar, when it is pointed at -------------------------------

    @Test
    void patchesTheRealGraviSuiteBytesWhenTheJarIsAvailable() throws Exception {
        String jar = System.getenv(REAL_JAR_ENV);
        Assumptions.assumeTrue(
            jar != null && Files.isRegularFile(Paths.get(jar)),
            "set " + REAL_JAR_ENV + " to the GraviSuite jar to run the real-bytes checks");

        byte[] real = readEntry(jar, GraviSuiteFlightPatch.TARGET_INTERNAL_NAME + ".class");
        GraviSuiteFlightPatch.Result result = GraviSuiteFlightPatch.apply(real, true, true, true);
        assertEquals(GraviSuiteFlightPatch.State.PATCHED, result.state(), result.detail());
        assertEquals(3, result.sites());

        MethodNode after = method(result.bytes());
        assertEquals(0, count(after, 0.2d), "real bytes: the stock jump clamp is still there");
        assertEquals(
            2,
            count(after, GraviSuiteFlightPatch.ASCENT_CEILING_CONSTANT),
            "real bytes: expected the ceiling plus the lifted clamp");
        assertEquals(1, clampStores(after), "real bytes: no lifted clamp store");
        assertEquals(1, count(after, -0.2d), "real bytes: the sneak clamp moved");
        assertEquals(1, holdStores(after), "real bytes: no shift+space target");
        assertTrue(GraviSuiteFlightPatch.hasGuardHook(after), "real bytes: no ground guard");
        assertTrue(otherMethodsUnchanged(real, result.bytes()), "real bytes: another method changed");

        // the transform is idempotent on the real bytes too
        GraviSuiteFlightPatch.Result twice = GraviSuiteFlightPatch.apply(result.bytes(), true, true, true);
        assertEquals(GraviSuiteFlightPatch.State.ALREADY_PATCHED, twice.state());
        assertSame(result.bytes(), twice.bytes());

        // leave the patched class on disk so a person can javap it afterwards
        Path out = Paths.get("build", "patched-gravisuite", GraviSuiteFlightPatch.TARGET_INTERNAL_NAME + ".class");
        Files.createDirectories(out.getParent());
        Files.write(out, result.bytes());
        assertTrue(Files.size(out) > 0, "the patched class was not written");
    }

    // ---- helpers: reading the built class ----------------------------------

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static MethodNode method(byte[] bytes) {
        for (MethodNode method : read(bytes).methods) {
            if (GraviSuiteFlightPatch.METHOD_NAME.equals(method.name)
                && GraviSuiteFlightPatch.METHOD_DESC.equals(method.desc)) {
                return method;
            }
        }
        throw new AssertionError("no useJetpack in " + bytes.length + " bytes");
    }

    private static byte[] rename(byte[] bytes, String internalName) {
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(0);
        reader.accept(new org.objectweb.asm.ClassVisitor(Opcodes.ASM5, writer) {

            @Override
            public void visit(int version, int access, String name, String signature, String superName,
                String[] interfaces) {
                super.visit(version, access, internalName, signature, superName, interfaces);
            }
        }, 0);
        return writer.toByteArray();
    }

    private static List<Double> doubleConstants(MethodNode method) {
        List<Double> out = new ArrayList<Double>();
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof Double) {
                out.add((Double) ((LdcInsnNode) insn).cst);
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

    private static int count(MethodNode method, double value) {
        int n = 0;
        for (Double found : doubleConstants(method)) {
            if (found.doubleValue() == value) {
                n++;
            }
        }
        return n;
    }

    /** How many {@code 0.6d -> DSTORE} pairs the method holds: the lifted clamp. */
    private static int clampStores(MethodNode method) {
        int n = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (!(insn instanceof LdcInsnNode) || !(((LdcInsnNode) insn).cst instanceof Double)) {
                continue;
            }
            if (((Double) ((LdcInsnNode) insn).cst).doubleValue() != GraviSuiteFlightPatch.ASCENT_CEILING_CONSTANT) {
                continue;
            }
            AbstractInsnNode next = insn.getNext();
            while (next != null && next.getOpcode() < 0) {
                next = next.getNext();
            }
            if (next != null && next.getOpcode() == Opcodes.DSTORE) {
                n++;
            }
        }
        return n;
    }

    /** How many {@code 0.0d -> DSTORE} pairs the method holds: the hold branch. */
    private static int holdStores(MethodNode method) {
        int n = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (!(insn instanceof LdcInsnNode) || !(((LdcInsnNode) insn).cst instanceof Double)) {
                continue;
            }
            if (((Double) ((LdcInsnNode) insn).cst).doubleValue() != GraviSuiteFlightPatch.HOLD_ALTITUDE_TARGET) {
                continue;
            }
            AbstractInsnNode next = insn.getNext();
            while (next != null && next.getOpcode() < 0) {
                next = next.getNext();
            }
            if (next != null && next.getOpcode() == Opcodes.DSTORE) {
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
                if (GraviSuiteFlightPatch.HOOK_OWNER.equals(call.owner)
                    && GraviSuiteFlightPatch.GUARD_HOOK.equals(call.name)) {
                    n++;
                }
            }
        }
        return n;
    }

    private static boolean otherMethodsUnchanged(byte[] before, byte[] after) {
        ClassNode b = read(after);
        for (MethodNode method : read(before).methods) {
            if (method.name.equals(GraviSuiteFlightPatch.METHOD_NAME)) {
                continue;
            }
            MethodNode now = null;
            for (MethodNode candidate : b.methods) {
                if (candidate.name.equals(method.name) && candidate.desc.equals(method.desc)) {
                    now = candidate;
                    break;
                }
            }
            if (now == null || realInsns(now).size() != realInsns(method).size()) {
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

    // ---- helpers: the synthetic GraviSuite shape ---------------------------

    private static byte[] graviClass(double ceiling, double jumpClamp) {
        return graviClassWithCeiling(jumpClamp, ceiling);
    }

    private static byte[] graviClassWithCeiling(double jumpClamp, double ceiling) {
        return graviClassWithCeiling(jumpClamp, ceiling, true);
    }

    /**
     * The same shape, optionally without the hover+sneak clamp store — the shape
     * another mixin leaves behind when it has already rewritten shift+space.
     *
     * <p>
     * The instruction sequence of the shipped {@code useJetpack}, reduced to the
     * parts this patch keys on, in the same order and with the same constants.
     * Static: local 0 is the player, local 1 the hover flag.
     */
    private static byte[] graviClassWithCeiling(double jumpClamp, double ceiling, boolean includeSneakClamp) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        writer.visit(
            Opcodes.V1_8,
            Opcodes.ACC_PUBLIC,
            GraviSuiteFlightPatch.TARGET_INTERNAL_NAME,
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
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            GraviSuiteFlightPatch.METHOD_NAME,
            GraviSuiteFlightPatch.METHOD_DESC,
            null,
            null);
        mv.visitCode();

        // oldMotionY = player.motionY
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, PLAYER, MOTION_Y, "D");
        mv.visitVarInsn(Opcodes.DSTORE, 11);

        // player.motionY = Math.min(player.motionY + power * 0.2f, ceiling)
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
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
        mv.visitVarInsn(Opcodes.ILOAD, 1);
        mv.visitJumpInsn(Opcodes.IFEQ, end);

        // v = -hoverModeFallSpeed
        mv.visitFieldInsn(Opcodes.GETSTATIC, GraviSuiteFlightPatch.TARGET_INTERNAL_NAME, "hoverModeFallSpeed", "D");
        mv.visitInsn(Opcodes.DNEG);
        mv.visitVarInsn(Opcodes.DSTORE, 13);

        Label afterJump = new Label();
        mv.visitFieldInsn(Opcodes.GETSTATIC, GRAVISUITE, "keyboard", "L" + KEYBOARD + ";");
        mv.visitInsn(Opcodes.POP);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            KEYBOARD,
            GraviSuiteFlightPatch.JUMP_KEY,
            "(L" + PLAYER + ";)Z",
            false);
        mv.visitJumpInsn(Opcodes.IFEQ, afterJump);
        mv.visitLdcInsn(Double.valueOf(jumpClamp));
        mv.visitVarInsn(Opcodes.DSTORE, 13);
        mv.visitLabel(afterJump);

        if (includeSneakClamp) {
            Label afterSneak = new Label();
            mv.visitFieldInsn(Opcodes.GETSTATIC, GRAVISUITE, "keyboard", "L" + KEYBOARD + ";");
            mv.visitInsn(Opcodes.POP);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                KEYBOARD,
                GraviSuiteFlightPatch.SNEAK_KEY,
                "(L" + PLAYER + ";)Z",
                false);
            mv.visitJumpInsn(Opcodes.IFEQ, afterSneak);
            mv.visitLdcInsn(Double.valueOf(GraviSuiteFlightPatch.SNEAK_CLAMP));
            mv.visitVarInsn(Opcodes.DSTORE, 13);
            mv.visitLabel(afterSneak);
        }

        // if (motionY > v) { motionY = v; if (oldMotionY > motionY) motionY = oldMotionY; }
        Label afterClamp = new Label();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, PLAYER, MOTION_Y, "D");
        mv.visitVarInsn(Opcodes.DLOAD, 13);
        mv.visitInsn(Opcodes.DCMPL);
        mv.visitJumpInsn(Opcodes.IFLE, afterClamp);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.DLOAD, 13);
        mv.visitFieldInsn(Opcodes.PUTFIELD, PLAYER, MOTION_Y, "D");
        mv.visitVarInsn(Opcodes.DLOAD, 11);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, PLAYER, MOTION_Y, "D");
        mv.visitInsn(Opcodes.DCMPL);
        mv.visitJumpInsn(Opcodes.IFLE, afterClamp);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.DLOAD, 11);
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

    /** {@code gravisuite.GraviSuite}, with the one public static field the method reads. */
    private static byte[] graviSuiteStub() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, GRAVISUITE, null, "java/lang/Object", null);
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

    /** {@code gravisuite.keyboard.Keyboard}, with the two static queries, both false. */
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
        for (String name : new String[] { GraviSuiteFlightPatch.JUMP_KEY, GraviSuiteFlightPatch.SNEAK_KEY }) {
            MethodVisitor query = writer
                .visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, name, "(L" + PLAYER + ";)Z", null, null);
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
