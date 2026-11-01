package com.jointspaceforce.patchee.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import com.jointspaceforce.patchee.asm.FinalCastleBreakTransformer;

/**
 * The FinalCastleBreak byte patch, against the REAL bytes of the Twilight Forest
 * classes it has to recognise:
 *
 * <ul>
 * <li>the live server's {@code TwilightForest-2.7.40.jar} (SRG member names —
 * {@code func_150894_a}, {@code func_147465_d});</li>
 * <li>the 2.7.41 dev dependency in the Gradle cache (MCP member names —
 * {@code onBlockDestroyed}, {@code setBlock}).</li>
 * </ul>
 *
 * <p>
 * The two jars differ only in member names, never in class names or descriptors,
 * so these tests pin the one thing that matters: the patch finds the right call
 * sites in both, changes nothing else, is idempotent, leaves a non-target class
 * byte-identical, and refuses any other shape. Provenance of the bytes is
 * documented in {@code src/test/resources/finalcastlebreak/README.md}.
 *
 * <p>
 * Note that {@code BlockTFThorns} carries a <i>second</i> {@code World.setBlock}
 * call with the same descriptor in {@code growThorns}; the tests check that only
 * the burst method ({@code removedByPlayer}) is touched.
 */
class FinalCastleBreakPatchTest {

    private static final String LIVE = "live/";
    private static final String DEV = "dev/";

    /**
     * sha256 of every stored {@code .class} fixture, keyed by its path under this
     * package's resource root. ONE place only — the same values are recorded in
     * {@code src/test/resources/finalcastlebreak/README.md} and the two MUST agree.
     *
     * <p>
     * This is a guard, not bookkeeping. These fixtures are real binary class
     * bytes, but the repo's top {@code .gitattributes} rule is {@code * text
     * eol=lf}; unless a {@code .class ... binary} rule overrides it, git
     * normalizes the line endings inside these files on publish. A class carrying
     * a CRLF pair (live/TFBlocks.class does) then silently loses a byte, and ASM
     * dies with a cryptic {@code ArrayIndexOutOfBoundsException} instead of saying
     * why. This check fails loudly, naming the file, both shas and the pitfall.
     */
    private static final Map<String, String> FIXTURE_SHA256 = fixtureShas();

    private static Map<String, String> fixtureShas() {
        Map<String, String> shas = new LinkedHashMap<String, String>();
        shas.put(LIVE + "BlockTFCastleBlock.class", "332ac1774a3d7808cbe1def4fddd4fafb1b1e218012da9a30e3537f2498af1b8");
        shas.put(LIVE + "BlockTFThorns.class", "5af1ce7c4f7b50204e188f79e70872557e2aa3da0ba81991396279e48d01d375");
        shas.put(LIVE + "BlockTFCastleMagic.class", "3717ecf1336ecf2f82fa6e60f67822db6a94cc45b06c209c3d13e92116b1ab65");
        shas.put(LIVE + "TFBlocks.class", "2c1e57b206d7fb69515aedc3d2d781e7d073043448339ccf8561fa67a2aef52a");
        shas.put(DEV + "BlockTFCastleBlock.class", "4b72c0f60a305f091945dfca12ac29858e107ea39f511a73510450ec35bb7127");
        shas.put(DEV + "BlockTFThorns.class", "af9ea6a07bb32028453c952652571689ad3af8503a98abbfa755beacf5e9c09d");
        return shas;
    }

    // ---- the castle bricks -------------------------------------------------

    @Test
    void patchesTheLiveCastleBytesWithExactlyOneSite() throws Exception {
        assertCastle(resource(LIVE + "BlockTFCastleBlock.class"));
    }

    @Test
    void patchesTheDevCastleBytesWithExactlyOneSite() throws Exception {
        assertCastle(resource(DEV + "BlockTFCastleBlock.class"));
    }

    private void assertCastle(byte[] original) {
        FinalCastleBreakPatch.Result result = FinalCastleBreakPatch.apply(original);
        assertEquals(FinalCastleBreakPatch.State.PATCHED, result.state(), result.detail());
        assertEquals(FinalCastleBreakPatch.CASTLE_SITE_COUNT, result.sites());
        assertEquals(FinalCastleBreakPatch.CASTLE_SITE_COUNT, countHookCalls(result.bytes()));
        MethodNode before = methodWithCastle(original);
        MethodNode after = findAs(result.bytes(), before);
        assertEquals(
            FinalCastleBreakPatch.CASTLE_SITE_COUNT,
            countMatching(before, FinalCastleBreakPatchTest::isCastleCall));
        assertEquals(0, countMatching(after, FinalCastleBreakPatchTest::isCastleCall));
        assertEquals(
            FinalCastleBreakPatch.CASTLE_SITE_COUNT,
            countMatching(after, FinalCastleBreakPatchTest::isHookCall));
        // and no instruction was inserted: the method differs only by the re-pointed call
        assertEquals(realCount(before), realCount(after));
    }

    // ---- the thorn wall ----------------------------------------------------

    @Test
    void patchesTheLiveThornsBytesWithExactlyTwoSites() throws Exception {
        assertThorns(resource(LIVE + "BlockTFThorns.class"));
    }

    @Test
    void patchesTheDevThornsBytesWithExactlyTwoSites() throws Exception {
        assertThorns(resource(DEV + "BlockTFThorns.class"));
    }

    private void assertThorns(byte[] original) {
        FinalCastleBreakPatch.Result result = FinalCastleBreakPatch.apply(original);
        assertEquals(FinalCastleBreakPatch.State.PATCHED, result.state(), result.detail());
        assertEquals(FinalCastleBreakPatch.THORNS_SITE_COUNT, result.sites());
        assertEquals(FinalCastleBreakPatch.THORNS_SITE_COUNT, countHookCalls(result.bytes()));

        MethodNode before = methodWithBurst(original);
        MethodNode after = findAs(result.bytes(), before);
        assertEquals(1, countMatching(before, FinalCastleBreakPatchTest::isSetBlockCall));
        assertEquals(1, countMatching(before, FinalCastleBreakPatchTest::isBurstCall));
        assertEquals(0, countMatching(after, FinalCastleBreakPatchTest::isSetBlockCall));
        assertEquals(0, countMatching(after, FinalCastleBreakPatchTest::isBurstCall));
        assertEquals(2, countMatching(after, FinalCastleBreakPatchTest::isHookCall));
        // one ALOAD 2 per site: the method grew by exactly two instructions
        assertEquals(aload2(before) + 2, aload2(after));
        assertEquals(realCount(before) + 2, realCount(after));
    }

    // ---- nothing else moves ------------------------------------------------

    @Test
    void everyOtherMethodOfBothClassesIsUnchanged() throws Exception {
        assertOtherMethodsUnchanged(resource(LIVE + "BlockTFCastleBlock.class"));
        assertOtherMethodsUnchanged(resource(DEV + "BlockTFCastleBlock.class"));
        assertOtherMethodsUnchanged(resource(LIVE + "BlockTFThorns.class"));
        assertOtherMethodsUnchanged(resource(DEV + "BlockTFThorns.class"));
    }

    private void assertOtherMethodsUnchanged(byte[] original) {
        byte[] patched = FinalCastleBreakPatch.apply(original)
            .bytes();
        Set<String> targets = targetMethodKeys(original);
        ClassNode before = read(original);
        ClassNode after = read(patched);
        int compared = 0;
        for (MethodNode method : before.methods) {
            String key = method.name + method.desc;
            if (targets.contains(key)) {
                continue;
            }
            MethodNode now = find(after, method.name, method.desc);
            assertTrue(now != null, "method vanished: " + key);
            assertEquals(opcodes(method), opcodes(now), "method changed: " + key);
            compared++;
        }
        assertTrue(compared > 0, "no non-target method was compared");
    }

    @Test
    void theCastleMethodChangesOnlyByTheRepointeredCall() throws Exception {
        assertCastleDelta(resource(LIVE + "BlockTFCastleBlock.class"));
        assertCastleDelta(resource(DEV + "BlockTFCastleBlock.class"));
    }

    private void assertCastleDelta(byte[] original) {
        byte[] patched = FinalCastleBreakPatch.apply(original)
            .bytes();
        MethodNode before = methodWithCastle(original);
        MethodNode after = findAs(patched, before);
        assertEquals(1, countMatching(before, FinalCastleBreakPatchTest::isCastleCall));
        assertEquals(0, countMatching(after, FinalCastleBreakPatchTest::isCastleCall));
        assertEquals(1, countMatching(after, FinalCastleBreakPatchTest::isHookCall));
        assertEquals(realCount(before), realCount(after));
    }

    @Test
    void theThornsMethodGrowsByExactlyTheTwoALoads() throws Exception {
        assertThornsDelta(resource(LIVE + "BlockTFThorns.class"));
        assertThornsDelta(resource(DEV + "BlockTFThorns.class"));
    }

    private void assertThornsDelta(byte[] original) {
        byte[] patched = FinalCastleBreakPatch.apply(original)
            .bytes();
        MethodNode before = methodWithBurst(original);
        MethodNode after = findAs(patched, before);
        assertEquals(1, countMatching(before, FinalCastleBreakPatchTest::isSetBlockCall));
        assertEquals(1, countMatching(before, FinalCastleBreakPatchTest::isBurstCall));
        assertEquals(0, countMatching(after, FinalCastleBreakPatchTest::isSetBlockCall));
        assertEquals(0, countMatching(after, FinalCastleBreakPatchTest::isBurstCall));
        assertEquals(2, countMatching(after, FinalCastleBreakPatchTest::isHookCall));
        assertEquals(realCount(before) + 2, realCount(after));
    }

    @Test
    void theGrowThornsSetBlockIsLeftAlone() throws Exception {
        // growThorns carries a second World.setBlock with the same descriptor; it
        // must NOT be touched by the patch (it is a different method, with no burst).
        for (String path : new String[] { LIVE + "BlockTFThorns.class", DEV + "BlockTFThorns.class" }) {
            byte[] original = resource(path);
            byte[] patched = FinalCastleBreakPatch.apply(original)
                .bytes();
            int beforeGlobal = countAll(original, FinalCastleBreakPatchTest::isSetBlockCall);
            int afterGlobal = countAll(patched, FinalCastleBreakPatchTest::isSetBlockCall);
            // the class has two setBlock sites; exactly one (in the burst method) is re-pointed
            assertEquals(2, beforeGlobal, path);
            assertEquals(1, afterGlobal, path);
        }
    }

    // ---- idempotence and pass-through --------------------------------------

    @Test
    void aSecondPassChangesNothing() throws Exception {
        for (String path : new String[] { LIVE + "BlockTFCastleBlock.class", DEV + "BlockTFCastleBlock.class",
            LIVE + "BlockTFThorns.class", DEV + "BlockTFThorns.class" }) {
            byte[] once = FinalCastleBreakPatch.apply(resource(path))
                .bytes();
            FinalCastleBreakPatch.Result twice = FinalCastleBreakPatch.apply(once);
            assertEquals(FinalCastleBreakPatch.State.ALREADY_PATCHED, twice.state(), path);
            assertSame(once, twice.bytes(), path);
        }
    }

    @Test
    void theNonTargetClassPassesThroughByteIdentical() throws Exception {
        byte[] magic = resource(LIVE + "BlockTFCastleMagic.class");
        FinalCastleBreakPatch.Result result = FinalCastleBreakPatch.apply(magic);
        assertEquals(FinalCastleBreakPatch.State.UNEXPECTED_SHAPE, result.state());
        assertSame(magic, result.bytes());
    }

    // ---- refusal -----------------------------------------------------------

    @Test
    void aClassWithoutTheTargetsIsRefusedUntouched() throws Exception {
        byte[] empty = emptyClass();
        FinalCastleBreakPatch.Result result = FinalCastleBreakPatch.apply(empty);
        assertEquals(FinalCastleBreakPatch.State.UNEXPECTED_SHAPE, result.state());
        assertSame(empty, result.bytes());
    }

    @Test
    void aNullClassIsNotAPatch() {
        assertEquals(
            FinalCastleBreakPatch.State.UNEXPECTED_SHAPE,
            FinalCastleBreakPatch.apply(null)
                .state());
    }

    // ---- the transformer's ARMED gate + the holder -------------------------

    @Test
    void theTransformerPublishesPatchSuccessPerTargetClass() throws Exception {
        withEnabledSettings(() -> {
            FinalCastleBreakTransformer transformer = new FinalCastleBreakTransformer();
            FinalCastleBreak.resetForTest();

            transformer.transform(
                FinalCastleBreakPatch.CASTLE_CLASS,
                FinalCastleBreakPatch.CASTLE_CLASS,
                resource(LIVE + "BlockTFCastleBlock.class"));
            assertTrue(FinalCastleBreak.patchedCastle());
            assertFalse(FinalCastleBreak.patchedThorns());
            // one half only: the feature is NOT armed
            assertEquals(FinalCastleBreakFeature.ArmReport.NOT_ARMED, FinalCastleBreakFeature.armReport());

            transformer.transform(
                FinalCastleBreakPatch.THORNS_CLASS,
                FinalCastleBreakPatch.THORNS_CLASS,
                resource(LIVE + "BlockTFThorns.class"));
            assertTrue(FinalCastleBreak.patchedThorns());
            // both halves now: ARMED
            assertEquals(FinalCastleBreakFeature.ArmReport.ARMED, FinalCastleBreakFeature.armReport());
        });
    }

    @Test
    void theTransformerLeavesTheHalfShutOnARefusedShape() throws Exception {
        // The exact bug the per-target tracking fixes: the castle half patched,
        // the thorn wall's shape refused — the boot line must NOT say "armed".
        withEnabledSettings(() -> {
            FinalCastleBreakTransformer transformer = new FinalCastleBreakTransformer();
            FinalCastleBreak.resetForTest();
            transformer.transform(
                FinalCastleBreakPatch.CASTLE_CLASS,
                FinalCastleBreakPatch.CASTLE_CLASS,
                resource(LIVE + "BlockTFCastleBlock.class"));
            transformer.transform(FinalCastleBreakPatch.THORNS_CLASS, FinalCastleBreakPatch.THORNS_CLASS, emptyClass());
            // the unsupported shape leaves the bytes untouched, so that half stays shut
            assertTrue(FinalCastleBreak.patchedCastle());
            assertFalse(FinalCastleBreak.patchedThorns());
            assertEquals(FinalCastleBreakFeature.ArmReport.NOT_ARMED, FinalCastleBreakFeature.armReport());
            // and the NOT-armed line names the missing half
            assertTrue(
                FinalCastleBreakFeature.missingHalf()
                    .contains(FinalCastleBreakPatch.THORNS_CLASS),
                FinalCastleBreakFeature.missingHalf());
        });
    }

    @Test
    void theArmedLineGoesOutOnlyWhenBothHalvesArePatched() {
        FinalCastleBreak.resetForTest();
        assertEquals(FinalCastleBreakFeature.ArmReport.NOT_ARMED, FinalCastleBreakFeature.armReport());
        FinalCastleBreak.patchedCastle = true;
        assertEquals(FinalCastleBreakFeature.ArmReport.NOT_ARMED, FinalCastleBreakFeature.armReport());
        FinalCastleBreak.patchedThorns = true;
        assertEquals(FinalCastleBreakFeature.ArmReport.ARMED, FinalCastleBreakFeature.armReport());
    }

    @Test
    void theLiveTfBlocksHasTheFourPublicStaticBlockFields() throws Exception {
        // The docs promise the floor on the castle bricks, the castle magic, the
        // thorn wall and Weathered Deadrock; the bytes of the live build (pulled
        // read-only, sha in the resources README) prove all four field names
        // exist as public static Block fields.
        ClassNode node = read(resource(LIVE + "TFBlocks.class"));
        for (String name : FinalCastleBreakFeature.BLOCK_FIELDS) {
            assertTrue(
                hasPublicStaticBlockField(node, name),
                name + " is not a public static Block field in the live TFBlocks");
        }
    }

    private static boolean hasPublicStaticBlockField(ClassNode node, String name) {
        for (FieldNode field : node.fields) {
            if (field.name.equals(name)) {
                return (field.access & Opcodes.ACC_PUBLIC) != 0 && (field.access & Opcodes.ACC_STATIC) != 0
                    && "Lnet/minecraft/block/Block;".equals(field.desc);
            }
        }
        return false;
    }

    /** Runs {@code body} with the transformer's settings seam pinned to 'on'. */
    private static void withEnabledSettings(ThrowingRunnable body) throws Exception {
        FinalCastleBreakSettings
            .injectForTest(FinalCastleBreakSettings.parse("B:enabled=true\nB:enableFinalCastleBreak=true\n"));
        try {
            body.run();
        } finally {
            FinalCastleBreakSettings.clearInjectedForTest();
        }
    }

    private interface ThrowingRunnable {

        void run() throws Exception;
    }

    @Test
    void anUnknownClassIsNotTouchedByTheTransformer() throws Exception {
        byte[] magic = resource(LIVE + "BlockTFCastleMagic.class");
        byte[] out = new FinalCastleBreakTransformer()
            .transform("twilightforest.block.BlockTFCastleMagic", "twilightforest.block.BlockTFCastleMagic", magic);
        assertSame(magic, out);
    }

    @Test
    void theSeedAloneNeverArmsTheHooks() {
        // The class-load seed may say 'on', but the hooks stay on the stock path
        // until Patchee's own pipeline confirms the switches through configure().
        FinalCastleBreak.resetForTest();
        FinalCastleBreak.seed(true, true);
        assertTrue(FinalCastleBreak.masterEnabled());
        assertTrue(FinalCastleBreak.featureEnabled());
        assertFalse(FinalCastleBreak.active());
    }

    @Test
    void theHooksActOnlyAfterThePipelineConfirmsTheSwitches() {
        FinalCastleBreak.resetForTest();
        assertFalse(FinalCastleBreak.active()); // nothing published yet
        FinalCastleBreak.configure(true, false);
        assertFalse(FinalCastleBreak.active());
        FinalCastleBreak.configure(false, true);
        assertFalse(FinalCastleBreak.active());
        FinalCastleBreak.configure(true, true);
        assertTrue(FinalCastleBreak.active());
    }

    @Test
    void theGateFailsSafeWhenTheAchievementWasNotPublished() {
        FinalCastleBreak.publishGlacier(null);
        assertFalse(FinalCastleBreak.earnedGlacier(null));
    }

    @Test
    void thePureGateDecisionNeedsBothTheSwitchAndTheEarn() {
        assertTrue(FinalCastleBreakHooks.bypass(true, true));
        assertFalse(FinalCastleBreakHooks.bypass(true, false));
        assertFalse(FinalCastleBreakHooks.bypass(false, true));
        assertFalse(FinalCastleBreakHooks.bypass(false, false));
    }

    // ---- helpers -----------------------------------------------------------

    private static byte[] resource(String path) throws Exception {
        byte[] bytes;
        try (InputStream in = FinalCastleBreakPatchTest.class.getClassLoader()
            .getResourceAsStream("finalcastlebreak/" + path)) {
            assertTrue(in != null, "missing test resource: " + path);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            bytes = out.toByteArray();
        }
        assertFixtureBytes(path, bytes);
        return bytes;
    }

    /**
     * Fails loudly if a stored {@code .class} fixture no longer matches its
     * recorded sha256 — the symptom of a text/EOL normalization dropping bytes
     * from a binary class file (see {@link #FIXTURE_SHA256}).
     */
    private static void assertFixtureBytes(String path, byte[] bytes) throws Exception {
        String expected = FIXTURE_SHA256.get(path);
        assertTrue(expected != null, "no recorded sha256 for fixture: " + path);
        String actual = sha256(bytes);
        assertEquals(
            expected,
            actual,
            "stored class bytes for " + path
                + " are not the recorded bytes (sha256 "
                + actual
                + ", expected "
                + expected
                + ", "
                + bytes.length
                + " bytes). The most likely cause is TEXT normalization of a BINARY"
                + " .class file: the repo's top .gitattributes rule is '* text eol=lf', so unless a `*.[cC][lL][aA][sS][sS]"
                + " binary` rule is present, git rewrites CRLF inside these files on publish and silently drops bytes"
                + " (this already happened to live/TFBlocks.class, which lost 1 byte and broke ASM). See"
                + " src/test/resources/finalcastlebreak/README.md ('File-type pitfall').");
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256")
            .digest(bytes);
        StringBuilder out = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            out.append(Character.forDigit((b >> 4) & 0xF, 16));
            out.append(Character.forDigit(b & 0xF, 16));
        }
        return out.toString();
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static MethodNode find(ClassNode node, String name, String desc) {
        for (MethodNode method : node.methods) {
            if (method.name.equals(name) && method.desc.equals(desc)) {
                return method;
            }
        }
        return null;
    }

    /** The same method (by name+desc) in another class version. */
    private static MethodNode findAs(byte[] bytes, MethodNode original) {
        MethodNode method = find(read(bytes), original.name, original.desc);
        assertTrue(method != null, "method vanished: " + original.name + original.desc);
        return method;
    }

    /** The one method carrying the castle damage call. */
    private static MethodNode methodWithCastle(byte[] bytes) {
        for (MethodNode method : read(bytes).methods) {
            if (countMatching(method, FinalCastleBreakPatchTest::isCastleCall) == 1) {
                return method;
            }
        }
        throw new AssertionError("no castle site found");
    }

    /** The one method carrying the thorn burst. */
    private static MethodNode methodWithBurst(byte[] bytes) {
        for (MethodNode method : read(bytes).methods) {
            if (countMatching(method, FinalCastleBreakPatchTest::isBurstCall) == 1) {
                return method;
            }
        }
        throw new AssertionError("no burst site found");
    }

    /**
     * The methods that carry one of the two shapes the patch owns, by name+desc:
     * the castle damage method and the thorn burst method. growThorns (a plain
     * setBlock with no burst) is deliberately NOT a target, so it is compared.
     */
    private static Set<String> targetMethodKeys(byte[] bytes) {
        Set<String> keys = new HashSet<String>();
        for (MethodNode method : read(bytes).methods) {
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (isCastleCall(insn) || isBurstCall(insn)) {
                    keys.add(method.name + method.desc);
                    break;
                }
            }
        }
        return keys;
    }

    private static boolean isCastleCall(AbstractInsnNode insn) {
        if (insn.getOpcode() != Opcodes.INVOKEVIRTUAL) {
            return false;
        }
        MethodInsnNode call = (MethodInsnNode) insn;
        return FinalCastleBreakPatch.ITEM_OWNER.equals(call.owner)
            && FinalCastleBreakPatch.CASTLE_CALL_DESC.equals(call.desc);
    }

    private static boolean isSetBlockCall(AbstractInsnNode insn) {
        if (insn.getOpcode() != Opcodes.INVOKEVIRTUAL) {
            return false;
        }
        MethodInsnNode call = (MethodInsnNode) insn;
        return FinalCastleBreakPatch.WORLD_OWNER.equals(call.owner)
            && FinalCastleBreakPatch.SETBLOCK_CALL_DESC.equals(call.desc);
    }

    private static boolean isBurstCall(AbstractInsnNode insn) {
        if (insn.getOpcode() != Opcodes.INVOKESPECIAL) {
            return false;
        }
        return FinalCastleBreakPatch.BURST_CALL_DESC.equals(((MethodInsnNode) insn).desc);
    }

    private static boolean isHookCall(AbstractInsnNode insn) {
        if (insn.getOpcode() != Opcodes.INVOKESTATIC) {
            return false;
        }
        MethodInsnNode call = (MethodInsnNode) insn;
        return FinalCastleBreakPatch.HOOK_OWNER.equals(call.owner)
            && (FinalCastleBreakPatch.CASTLE_HOOK.equals(call.name)
                || FinalCastleBreakPatch.SETBLOCK_HOOK.equals(call.name)
                || FinalCastleBreakPatch.BURST_HOOK.equals(call.name));
    }

    private static int countHookCalls(byte[] bytes) {
        return countAll(bytes, FinalCastleBreakPatchTest::isHookCall);
    }

    private static int countMatching(MethodNode method, InsnMatch match) {
        int count = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (match.test(insn)) {
                count++;
            }
        }
        return count;
    }

    private static int countAll(byte[] bytes, InsnMatch match) {
        int count = 0;
        for (MethodNode method : read(bytes).methods) {
            count += countMatching(method, match);
        }
        return count;
    }

    private static int aload2(MethodNode method) {
        int count = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn.getOpcode() == Opcodes.ALOAD && ((VarInsnNode) insn).var == 2) {
                count++;
            }
        }
        return count;
    }

    private static int realCount(MethodNode method) {
        int count = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn.getOpcode() >= 0) {
                count++;
            }
        }
        return count;
    }

    private static List<String> opcodes(MethodNode method) {
        List<String> ops = new ArrayList<String>();
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn.getOpcode() >= 0) {
                ops.add(opcodeText(insn));
            }
        }
        return ops;
    }

    private static String opcodeText(AbstractInsnNode insn) {
        if (insn instanceof MethodInsnNode) {
            MethodInsnNode call = (MethodInsnNode) insn;
            return insn.getOpcode() + ":" + call.owner + "." + call.name + call.desc;
        }
        if (insn instanceof FieldInsnNode) {
            FieldInsnNode field = (FieldInsnNode) insn;
            return insn.getOpcode() + ":" + field.owner + "." + field.name + ":" + field.desc;
        }
        return String.valueOf(insn.getOpcode());
    }

    /** A class carrying the thorn name but none of the shapes the patch owns. */
    private static byte[] emptyClass() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(
            Opcodes.V1_8,
            Opcodes.ACC_PUBLIC,
            FinalCastleBreakPatch.THORNS_CLASS.replace('.', '/'),
            null,
            "java/lang/Object",
            null);
        writer.visitEnd();
        return writer.toByteArray();
    }

    /** A one-instruction predicate, kept tiny so no lambda machinery is needed. */
    private interface InsnMatch {

        boolean test(AbstractInsnNode insn);
    }
}
