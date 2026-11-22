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
 * The GraviSuite half of the flight patch: the shift+space hold and the ground
 * guard, in {@code ItemAdvancedJetPack.useJetpack(EntityPlayer, boolean)}.
 *
 * <p>
 * The repo does not carry GraviSuite's jar, so the tests rebuild the instruction
 * sequence this patch keys on — the shipped {@code gravisuite-2.0.3} order
 * ({@code javap -p -c gravisuite.ItemAdvancedJetPack#useJetpack}: ceiling at
 * offset 319, jump clamp {@code 0.2d} at 349, sneak clamp {@code -0.2d} at 365,
 * clamp chain at 443-472, boost {@code 2.0d} doubling) — and pin:
 *
 * <ul>
 * <li>the Advanced Nano is the lowest rung of the flight ladder, so there is
 * <b>no</b> vertical rewrite: the stock {@code 0.2d} climb and {@code -0.2d} sink
 * are left exactly where GraviSuite puts them;</li>
 * <li>the full apply writes the shift+space hold (one {@code 0.0d} store right
 * after the stock sneak store) and the ground guard at the top, and nothing
 * else — two sites, no lift;</li>
 * <li>the stock {@code -0.2d} literal survives, because Gravitation-Suite-Neo
 * 1.3.16 matches it by value; a class where another mixin already turned that
 * store into an expression gets the hold skipped, guard only;</li>
 * <li>each switch is independent, with a lift request a no-op;</li>
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
    void patchesTheStockShapeWithTheHoldAndGuardButNoVerticalRewrite() {
        byte[] original = graviClass(GraviSuiteFlightPatch.ASCENT_CEILING_CONSTANT, 0.2d);
        GraviSuiteFlightPatch.Result result = GraviSuiteFlightPatch.apply(original, true, true, true);

        assertEquals(GraviSuiteFlightPatch.State.PATCHED, result.state(), result.detail());
        assertEquals(2, result.sites(), "only the hold and the guard may be written");
        assertFalse(result.liftApplied(), "the Advanced Nano keeps GraviSuite's stock vertical clamps");
        assertTrue(result.holdApplied(), "the hold must be reported as applied");
        assertTrue(result.guardApplied(), "the guard must be reported as applied");

        MethodNode method = method(result.bytes());
        // The stock vertical clamps are untouched: 0.2d up, -0.2d down, once each.
        assertEquals(
            1,
            count(method, 0.2d),
            "the stock jump clamp must survive exactly once: " + doubleConstants(method));
        // DELIBERATE: the stock -0.2d literal stays, because Gravitation-Suite-Neo's
        // ModifyExpressionValue(at=CONSTANT, doubleValue=-0.2) matches it by value.
        assertEquals(
            1,
            count(method, -0.2d),
            "the stock sneak literal must survive exactly once: " + doubleConstants(method));
        assertEquals(0.2d, jumpClampStoredValue(method), 0.0d, "the jump clamp was rewritten");
        assertEquals(-0.2d, stockSneakStoredValue(method), 0.0d, "the sneak clamp was rewritten");
        assertEquals(1, storesOf(method, 0.2d), "exactly one ascent store: " + doubleConstants(method));
        assertEquals(1, storesOf(method, -0.2d), "exactly one descent store: " + doubleConstants(method));
        assertEquals(0, count(method, 0.11555557698011398d), "a lifted ascent was written: " + doubleConstants(method));
        assertEquals(
            0,
            count(method, -0.11555557698011398d),
            "an injected descent was written: " + doubleConstants(method));

        // the ceiling and the boost double are left exactly as GraviSuite ships them
        assertEquals(
            1,
            count(method, GraviSuiteFlightPatch.ASCENT_CEILING_CONSTANT),
            "the 0.6 ceiling must survive exactly once: " + doubleConstants(method));
        assertEquals(1, count(method, 2.0d), "the boost key's 2.0 must survive: " + doubleConstants(method));
        assertEquals(1, holdStores(method), "the shift+space target is missing: " + doubleConstants(method));
        assertTrue(GraviSuiteFlightPatch.hasGuardHook(method), "the ground guard was not injected");
    }

    @Test
    void theStockVerticalClampsStayExactOpposites() {
        byte[] original = graviClass(GraviSuiteFlightPatch.ASCENT_CEILING_CONSTANT, 0.2d);
        // No vertical edit is shipped, so a lift-only request changes nothing.
        GraviSuiteFlightPatch.Result result = GraviSuiteFlightPatch.apply(original, true, false, false);
        assertEquals(GraviSuiteFlightPatch.State.ALREADY_PATCHED, result.state(), result.detail());
        assertSame(original, result.bytes());

        MethodNode method = method(original);
        double ascent = jumpClampStoredValue(method);
        double descent = stockSneakStoredValue(method);
        assertEquals(1, storesOf(method, ascent), "exactly one ascent store: " + doubleConstants(method));
        assertEquals(1, storesOf(method, descent), "exactly one descent store: " + doubleConstants(method));
        assertEquals(
            0,
            Double.compare(ascent, -descent),
            "up and down must be equal in magnitude: " + ascent + " / " + descent);
        assertTrue(ascent > 0.0d && descent < 0.0d, "jump goes up, sneak goes down");
        assertEquals(0.2d, ascent, 0.0d, "the stock climb");
        assertEquals(-0.2d, descent, 0.0d, "the stock sink");
    }

    @Test
    void theDerivedNumbersArePinnedByBareLiterals() {
        assertEquals(0.52f, GraviSuiteFlightPatch.HORIZONTAL_FORWARD, "0.4f * 0.65f * 2.0f at load 1");
        assertEquals(0.02f, GraviSuiteFlightPatch.HORIZONTAL_FRICTION);
        assertEquals(0.91f, GraviSuiteFlightPatch.AIR_DRAG);
        // Horizontal only: the sideways speed is untouched by the ladder.
        assertEquals(0.11555557698011398d, GraviSuiteFlightPatch.HOVER_HORIZONTAL_SPEED, 0.0d, "the sideways speed");
        // Vertical: the Advanced Nano rung is GraviSuite's own stock 0.2, so no
        // rewrite ships and this patch only reads those clamps to find its sites.
        assertEquals(0.2d, GraviSuiteFlightPatch.HOVER_JUMP_CLAMP, 0.0d);
        assertEquals(-0.2d, GraviSuiteFlightPatch.SNEAK_CLAMP, 0.0d);
    }

    @Test
    void theStockSinkLiteralSurvivesSoNeoStillFindsIt() {
        MethodNode method = method(
            GraviSuiteFlightPatch
                .apply(graviClass(GraviSuiteFlightPatch.ASCENT_CEILING_CONSTANT, 0.2d), true, true, true)
                .bytes());
        // DELIBERATE: Gravitation-Suite-Neo 1.3.16 ships on both sides of GTNH 2.9.0
        // with a ModifyExpressionValue on this method at CONSTANT with
        // doubleValue=-0.2. Patchee never rewrites that literal; it only reads it
        // to find the sneak-clamp site that anchors the hold.
        assertEquals(1, count(method, -0.2d), "Neo's matcher target was consumed: " + doubleConstants(method));
        assertEquals(-0.2d, stockSneakStoredValue(method), 0.0d, "the stock store must stay intact");
        assertEquals(
            0,
            count(method, -0.11555557698011398d),
            "an injected sink store must not be written: " + doubleConstants(method));
        assertEquals(
            0,
            storesOf(method, -0.11555557698011398d),
            "no lifted descent store may exist: " + doubleConstants(method));
        // and the jump clamp is still the stock 0.2d, not a lifted value
        assertEquals(1, count(method, 0.2d), "the jump clamp moved: " + doubleConstants(method));
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
    void aLiftRequestIsANoOpBecauseTheAdvancedNanoKeepsStockClamps() {
        byte[] original = graviClass(GraviSuiteFlightPatch.ASCENT_CEILING_CONSTANT, 0.2d);
        GraviSuiteFlightPatch.Result result = GraviSuiteFlightPatch.apply(original, true, false, false);
        assertEquals(GraviSuiteFlightPatch.State.ALREADY_PATCHED, result.state(), result.detail());
        assertEquals(0, result.sites());
        assertFalse(result.liftApplied(), "no vertical lift ships for this item");
        assertFalse(result.skipped(), "asking for a lift that is switched off is not a skip");
        assertSame(original, result.bytes());
        MethodNode method = method(result.bytes());
        assertEquals(1, count(method, 0.2d), "the jump clamp moved");
        assertEquals(1, count(method, -0.2d), "the stock sink literal moved");
        assertEquals(0, count(method, 0.11555557698011398d));
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
    void theHoldStillLandsWhenTheLiftIsRequested() {
        GraviSuiteFlightPatch.Result result = GraviSuiteFlightPatch
            .apply(graviClass(GraviSuiteFlightPatch.ASCENT_CEILING_CONSTANT, 0.2d), true, false, true);
        assertEquals(GraviSuiteFlightPatch.State.PATCHED, result.state(), result.detail());
        assertEquals(1, result.sites(), "only the hold may be written");
        assertFalse(result.liftApplied(), "no vertical lift ships");
        assertTrue(result.holdApplied());
        MethodNode method = method(result.bytes());
        assertEquals(1, count(method, 0.2d), "jump alone must stay at GraviSuite's stock 0.2d");
        assertEquals(1, count(method, -0.2d), "sneak alone must stay at GraviSuite's stock -0.2d");
        assertEquals(1, holdStores(method), "sneak+jump must hold altitude");
        assertFalse(GraviSuiteFlightPatch.hasGuardHook(method), "the guard was not requested");
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
        assertEquals(0, twice.sites());
        assertFalse(twice.skipped(), "a second pass must not invent a partial");
        MethodNode method = method(once);
        assertEquals(1, count(method, 0.2d), "the stock jump clamp was duplicated: " + doubleConstants(method));
        assertEquals(1, count(method, -0.2d), "the stock sink literal was duplicated: " + doubleConstants(method));
        assertEquals(0, count(method, 0.11555557698011398d), "a lifted ascent appeared");
        assertEquals(0, count(method, -0.11555557698011398d), "a lifted descent appeared");
        assertEquals(1, guardHooks(method), "the guard was duplicated");
        assertEquals(1, holdStores(method), "the hold store was duplicated");
    }

    @Test
    void aSecondPassAddsOnlyTheGuardWhenTheHoldIsAlreadyThere() {
        byte[] holdOnly = GraviSuiteFlightPatch
            .apply(graviClass(GraviSuiteFlightPatch.ASCENT_CEILING_CONSTANT, 0.2d), false, false, true)
            .bytes();
        assertEquals(1, holdStores(method(holdOnly)), "the first pass must write the hold");

        GraviSuiteFlightPatch.Result result = GraviSuiteFlightPatch.apply(holdOnly, true, true, true);
        assertEquals(GraviSuiteFlightPatch.State.PATCHED, result.state(), result.detail());
        assertEquals(1, result.sites(), "only the guard may be written");
        assertFalse(result.liftApplied(), "no vertical lift ships");
        assertFalse(result.holdApplied(), "the hold was already there");
        assertTrue(result.guardApplied(), "the guard must be reported as applied");
        MethodNode method = method(result.bytes());
        assertEquals(1, count(method, 0.2d), "the stock jump clamp moved");
        assertEquals(1, count(method, -0.2d), "the stock sink literal moved");
        assertEquals(1, guardHooks(method), "the guard was not injected");
        assertEquals(1, holdStores(method), "the hold store was duplicated");
    }

    // ---- another mixin already rewrote shift+space --------------------------

    @Test
    void appliesOnlyTheGuardWhenTheSneakClampWasAlreadyRewritten() {
        byte[] original = graviClassWithCeiling(0.2d, GraviSuiteFlightPatch.ASCENT_CEILING_CONSTANT, false);
        GraviSuiteFlightPatch.Result result = GraviSuiteFlightPatch.apply(original, true, true, true);

        assertEquals(GraviSuiteFlightPatch.State.PARTIAL, result.state(), result.detail());
        assertEquals(1, result.sites(), "only the guard may land without the sneak clamp site");
        assertFalse(result.liftApplied(), "no vertical lift ships for this item");
        assertFalse(result.holdApplied(), "the shift+space edit must be refused when there is no sneak clamp site");
        assertTrue(result.guardApplied(), "the ground guard must still land");
        assertFalse(java.util.Arrays.equals(original, result.bytes()), "the class must be rewritten");

        MethodNode method = method(result.bytes());
        assertTrue(GraviSuiteFlightPatch.hasGuardHook(method), "the ground guard was not injected");
        assertEquals(
            0,
            storesOf(method, GraviSuiteFlightPatch.HOVER_HORIZONTAL_SPEED),
            "no ascent value may be written");
        assertEquals(
            0,
            storesOf(method, -GraviSuiteFlightPatch.HOVER_HORIZONTAL_SPEED),
            "no descent value may be written");
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
    void aLiftRequestAgainstAMixinRewrittenSneakClampChangesNoBytes() {
        byte[] original = graviClassWithCeiling(0.2d, GraviSuiteFlightPatch.ASCENT_CEILING_CONSTANT, false);
        GraviSuiteFlightPatch.Result result = GraviSuiteFlightPatch.apply(original, true, false, false);

        assertEquals(GraviSuiteFlightPatch.State.ALREADY_PATCHED, result.state(), result.detail());
        assertSame(original, result.bytes(), "no vertical lift ships, so nothing may be edited");
        assertFalse(result.skipped(), "a lift request that is switched off is not a skip");
        assertEquals(0, result.sites());
        assertEquals(0, storesOf(method(result.bytes()), GraviSuiteFlightPatch.HOVER_HORIZONTAL_SPEED));
        assertEquals(1, count(method(result.bytes()), 0.2d), "the jump clamp must still be stock 0.2d");
    }

    // ---- Gravitation-Suite-Neo ran first -----------------------------------

    @Test
    void neoFirstLeavesNoStockSneakSiteSoTheHoldIsSkipped() {
        // Neo's ModifyExpressionValue already replaced the -0.2d store with
        // "-0.2d + maxHoverY", so there is no constant store to anchor the hold on.
        // The hold must be skipped, never half-placed.
        byte[] neo = graviClassWithSneakExpression();
        GraviSuiteFlightPatch.Result result = GraviSuiteFlightPatch.apply(neo, true, true, true);

        assertEquals(GraviSuiteFlightPatch.State.PARTIAL, result.state(), result.detail());
        assertTrue(result.skipped(), "the skip must be reported");
        assertFalse(result.liftApplied(), "no rise may be claimed");
        assertFalse(result.holdApplied(), "the hold must be skipped when there is no sneak clamp site");
        assertTrue(result.guardApplied(), "the ground guard must still land");
        assertTrue(
            result.detail()
                .contains("shift+space"),
            result.detail());

        MethodNode method = method(result.bytes());
        assertEquals(0, storesOf(method, 0.11555557698011398d), "a rise was written: " + doubleConstants(method));
        assertEquals(0, storesOf(method, -0.11555557698011398d), "a sink was written: " + doubleConstants(method));
        assertEquals(0.2d, jumpClampStoredValue(method), 0.0d, "the jump clamp must stay stock");
        assertTrue(GraviSuiteFlightPatch.hasGuardHook(method), "the guard was not injected");
    }

    @Test
    void neoFirstWithNothingElseRequestedLeavesTheBytesAlone() {
        byte[] neo = graviClassWithSneakExpression();
        GraviSuiteFlightPatch.Result result = GraviSuiteFlightPatch.apply(neo, true, false, false);

        assertEquals(GraviSuiteFlightPatch.State.ALREADY_PATCHED, result.state(), result.detail());
        assertFalse(result.skipped(), "no vertical lift is requested, so nothing can be skipped");
        assertFalse(result.liftApplied());
        assertSame(neo, result.bytes(), "a switched-off lift must not rewrite a byte");
        assertEquals(0, storesOf(method(result.bytes()), 0.11555557698011398d), "a rise was written");
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
    void refusesAJumpClampAnotherPatchAlreadyRaised() {
        // A 0.6 jump clamp is not a site this patch knows (it accepts only the
        // stock 0.2), so the class is refused by the site check with a "found 0"
        // message, not silently treated as a ceiling.
        byte[] raised = graviClass(GraviSuiteFlightPatch.ASCENT_CEILING_CONSTANT, 0.6d);
        GraviSuiteFlightPatch.Result result = GraviSuiteFlightPatch.apply(raised, true, true, true);
        assertEquals(GraviSuiteFlightPatch.State.UNEXPECTED_SHAPE, result.state(), result.detail());
        assertSame(raised, result.bytes());
        assertTrue(
            result.detail()
                .contains("hover+jump clamp site"),
            result.detail());
        assertTrue(
            result.detail()
                .contains("found 0"),
            result.detail());
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
            assertEquals(1, count(method, 0.2d), "the stock climb must survive the hold edit");
            assertEquals(1, count(method, -0.2d), "the stock sink must survive the hold edit");
            assertEquals(0, count(method, 0.11555557698011398d), "no vertical lift may be written");
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
        assertEquals(2, result.sites(), "real bytes: only the hold and the guard may be written");
        assertFalse(result.liftApplied(), "real bytes: no vertical rewrite ships");
        assertTrue(result.holdApplied(), "real bytes: the hold must be reported as applied");
        assertTrue(result.guardApplied(), "real bytes: the guard must be reported as applied");

        MethodNode after = method(result.bytes());
        assertEquals(1, count(after, 0.2d), "real bytes: the stock jump clamp must survive exactly once");
        assertEquals(0.2d, jumpClampStoredValue(after), 0.0d, "real bytes: the jump clamp was rewritten");
        assertEquals(1, count(after, -0.2d), "real bytes: the stock sink literal must survive for Neo");
        assertEquals(-0.2d, stockSneakStoredValue(after), 0.0d, "real bytes: the sneak clamp was rewritten");
        assertEquals(0, count(after, 0.11555557698011398d), "real bytes: a lifted ascent was written");
        assertEquals(0, count(after, -0.11555557698011398d), "real bytes: an injected descent was written");
        assertEquals(
            1,
            count(after, GraviSuiteFlightPatch.ASCENT_CEILING_CONSTANT),
            "real bytes: the 0.6 ceiling must survive exactly once");
        assertEquals(1, count(after, 2.0d), "real bytes: the boost 2.0 must survive");
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

    /** How many {@code ldc2_w <value> -> DSTORE} pairs the method holds. */
    private static int storesOf(MethodNode method, double value) {
        int n = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (!(insn instanceof LdcInsnNode) || !(((LdcInsnNode) insn).cst instanceof Double)) {
                continue;
            }
            if (((Double) ((LdcInsnNode) insn).cst).doubleValue() != value) {
                continue;
            }
            if (storedAfter(insn)) {
                n++;
            }
        }
        return n;
    }

    /** True when the next real instruction after this node is a {@code DSTORE}. */
    private static boolean storedAfter(AbstractInsnNode insn) {
        AbstractInsnNode next = insn.getNext();
        while (next != null && next.getOpcode() < 0) {
            next = next.getNext();
        }
        return next != null && next.getOpcode() == Opcodes.DSTORE;
    }

    /** The key query whose {@code IFEQ} gates this clamp value. */
    private static MethodInsnNode keyBefore(AbstractInsnNode value) {
        AbstractInsnNode branch = value.getPrevious();
        while (branch != null && branch.getOpcode() < 0) {
            branch = branch.getPrevious();
        }
        assertEquals(
            Opcodes.IFEQ,
            branch == null ? -1 : branch.getOpcode(),
            "the clamp value must sit inside a key branch");
        AbstractInsnNode call = branch.getPrevious();
        while (call != null && call.getOpcode() < 0) {
            call = call.getPrevious();
        }
        assertTrue(call instanceof MethodInsnNode, "no key query before the clamp branch");
        return (MethodInsnNode) call;
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

    /** The double stored at the jump clamp site: {@code isJumpKeyDown -> IFEQ -> LDC2_W -> DSTORE}. */
    private static double jumpClampStoredValue(MethodNode method) {
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn.getOpcode() != Opcodes.INVOKESTATIC) {
                continue;
            }
            if (!GraviSuiteFlightPatch.JUMP_KEY.equals(((MethodInsnNode) insn).name)) {
                continue;
            }
            AbstractInsnNode branch = nextReal(insn.getNext());
            assertNotNull(branch, "no branch after the jump query");
            assertEquals(Opcodes.IFEQ, branch.getOpcode(), "the jump query must be followed by IFEQ");
            AbstractInsnNode value = nextReal(branch.getNext());
            assertTrue(value instanceof LdcInsnNode, "no jump clamp value after the branch");
            assertTrue(((LdcInsnNode) value).cst instanceof Double, "the jump clamp must be a double");
            AbstractInsnNode store = nextReal(value.getNext());
            assertEquals(Opcodes.DSTORE, store.getOpcode(), "the jump clamp value must be stored");
            return ((Double) ((LdcInsnNode) value).cst).doubleValue();
        }
        throw new AssertionError("no jump clamp site in these bytes");
    }

    /** The stock sneak value: the {@code -0.2d} the class ships, read from the bytes. */
    private static double stockSneakStoredValue(MethodNode method) {
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (!(insn instanceof LdcInsnNode) || !(((LdcInsnNode) insn).cst instanceof Double)) {
                continue;
            }
            if (((Double) ((LdcInsnNode) insn).cst).doubleValue() != -0.2d) {
                continue;
            }
            AbstractInsnNode store = nextReal(insn.getNext());
            if (store != null && store.getOpcode() == Opcodes.DSTORE) {
                return -0.2d;
            }
        }
        throw new AssertionError("the stock -0.2d store is not in these bytes");
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode insn) {
        AbstractInsnNode cursor = insn;
        while (cursor != null && cursor.getOpcode() < 0) {
            cursor = cursor.getNext();
        }
        return cursor;
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
     * The shape Gravitation-Suite-Neo leaves behind: its {@code ModifyExpressionValue}
     * has replaced the sneak clamp constant with {@code -0.2d + 0.2d} (the real one
     * adds {@code maxHoverY}), so no constant is stored at the sneak site any more.
     */
    private static byte[] graviClassWithSneakExpression() {
        return graviClassWithCeiling(0.2d, GraviSuiteFlightPatch.ASCENT_CEILING_CONSTANT, true, true);
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
        return graviClassWithCeiling(jumpClamp, ceiling, includeSneakClamp, false);
    }

    private static byte[] graviClassWithCeiling(double jumpClamp, double ceiling, boolean includeSneakClamp,
        boolean sneakExpression) {
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
            if (sneakExpression) {
                // the shape Neo's ModifyExpressionValue leaves: no constant store
                mv.visitLdcInsn(Double.valueOf(-0.2d));
                mv.visitLdcInsn(Double.valueOf(0.2d));
                mv.visitInsn(Opcodes.DADD);
                mv.visitVarInsn(Opcodes.DSTORE, 13);
            } else {
                mv.visitLdcInsn(Double.valueOf(GraviSuiteFlightPatch.SNEAK_CLAMP));
                mv.visitVarInsn(Opcodes.DSTORE, 13);
            }
            mv.visitLabel(afterSneak);
        }

        // the boost key: v = v * 2.0d (GraviSuite ships this; the patch must leave it alone)
        mv.visitVarInsn(Opcodes.DLOAD, 13);
        mv.visitLdcInsn(Double.valueOf(2.0d));
        mv.visitInsn(Opcodes.DMUL);
        mv.visitVarInsn(Opcodes.DSTORE, 13);

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
