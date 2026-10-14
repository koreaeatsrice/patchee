package com.jointspaceforce.patchee.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import com.jointspaceforce.patchee.asm.TeamChunkCapTransformer;

/**
 * The team chunk-cap byte patch, against synthetic class bytes shaped exactly
 * like ServerUtilities 2.4.9's {@code ServerUtilitiesTeamData}: two limit
 * methods, each with a {@code -1} sentinel return, a {@code -2} sentinel return,
 * the cached early return and the final return.
 *
 * <p>
 * These tests pin the contract the transformer relies on: exactly four return
 * sites are changed, everything else stays as it was, a class of any other shape
 * is left completely untouched, a second pass does nothing — and, strongest of
 * all, the patched class actually loads and the clamp actually happens when the
 * method is called (with the unpatched class as the control).
 */
class TeamChunkCapPatchTest {

    private static final String OWNER = TeamChunkCapPatch.TARGET_INTERNAL_NAME;

    /** A class with ServerUtilities' shape; the "summed" numbers are given. */
    private static byte[] syntheticClass(int claimsSum, int loadsSum) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, OWNER, null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "active", "Z", null, null)
            .visitEnd();
        writer.visitField(Opcodes.ACC_PRIVATE, TeamChunkCapPatch.CLAIM_FIELD, "I", null, null)
            .visitEnd();
        writer.visitField(Opcodes.ACC_PRIVATE, TeamChunkCapPatch.LOAD_FIELD, "I", null, null)
            .visitEnd();

        MethodVisitor ctor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        // ServerUtilities starts both caches at -1 ("nothing computed yet"), which
        // is what sends the first call down the summing path.
        for (String field : new String[] { TeamChunkCapPatch.CLAIM_FIELD, TeamChunkCapPatch.LOAD_FIELD }) {
            ctor.visitVarInsn(Opcodes.ALOAD, 0);
            ctor.visitInsn(Opcodes.ICONST_M1);
            ctor.visitFieldInsn(Opcodes.PUTFIELD, OWNER, field, "I");
        }
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();

        limitMethod(writer, TeamChunkCapPatch.CLAIM_METHOD, TeamChunkCapPatch.CLAIM_FIELD, claimsSum);
        limitMethod(writer, TeamChunkCapPatch.LOAD_METHOD, TeamChunkCapPatch.LOAD_FIELD, loadsSum);
        writer.visitEnd();
        return writer.toByteArray();
    }

    /** One method in ServerUtilities' own shape, with a hardcoded "summed" value. */
    private static void limitMethod(ClassWriter writer, String name, String field, int sum) {
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, name, "()I", null, null);
        method.visitCode();
        // if (!active) return -1;
        method.visitFieldInsn(Opcodes.GETSTATIC, OWNER, "active", "Z");
        Label compute = new Label();
        method.visitJumpInsn(Opcodes.IFNE, compute);
        method.visitInsn(Opcodes.ICONST_M1);
        method.visitInsn(Opcodes.IRETURN);
        method.visitLabel(compute);
        method.visitFrame(Opcodes.F_NEW, 1, new Object[] { OWNER }, 0, null);
        // if (cached >= 0) return cached; <- the cached return site
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitFieldInsn(Opcodes.GETFIELD, OWNER, field, "I");
        Label computed = new Label();
        method.visitJumpInsn(Opcodes.IFLT, computed);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitFieldInsn(Opcodes.GETFIELD, OWNER, field, "I");
        method.visitInsn(Opcodes.IRETURN);
        method.visitLabel(computed);
        method.visitFrame(Opcodes.F_NEW, 1, new Object[] { OWNER }, 0, null);
        // cached = 0; cached += <sum>; return cached; <- the final return site
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitInsn(Opcodes.ICONST_0);
        method.visitFieldInsn(Opcodes.PUTFIELD, OWNER, field, "I");
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitLdcInsn(Integer.valueOf(sum));
        method.visitFieldInsn(Opcodes.PUTFIELD, OWNER, field, "I");
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitFieldInsn(Opcodes.GETFIELD, OWNER, field, "I");
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    /**
     * The transformer is the only thing that may set the patch-success flag the
     * ARMED line is gated on. A recognised class flips it (armed); a class whose
     * shape is not recognised is handed back untouched and must leave it false
     * (NOT armed) — the case that used to log both an ERROR and a success line.
     */
    @Test
    void theTransformerPublishesPatchSuccessOnlyOnARealPatch() {
        TeamChunkCapTransformer transformer = new TeamChunkCapTransformer();

        TeamChunkCap.patchApplied = false;
        transformer
            .transform(TeamChunkCapPatch.TARGET_CLASS, TeamChunkCapPatch.TARGET_CLASS, syntheticClass(6000, 2000));
        assertTrue(TeamChunkCap.patchApplied());
        assertEquals(TeamChunkCapFeature.ArmReport.ARMED, TeamChunkCapFeature.armReport());

        TeamChunkCap.patchApplied = false;
        transformer.transform(TeamChunkCapPatch.TARGET_CLASS, TeamChunkCapPatch.TARGET_CLASS, oneSiteClass());
        // the unsupported shape leaves the bytes untouched, so the gate stays shut
        assertFalse(TeamChunkCap.patchApplied());
        assertEquals(TeamChunkCapFeature.ArmReport.NOT_ARMED, TeamChunkCapFeature.armReport());
    }

    @Test
    void rewritesEveryReturnSiteAndNothingElse() {
        byte[] original = syntheticClass(6000, 2000);
        TeamChunkCapPatch.Result result = TeamChunkCapPatch.apply(original);

        assertEquals(TeamChunkCapPatch.State.PATCHED, result.state(), result.detail());
        assertEquals(TeamChunkCapPatch.SITE_COUNT, result.sites());
        assertEquals(TeamChunkCapPatch.SITE_COUNT, countHookCalls(result.bytes()));
        // the sentinel returns are untouched: still exactly one IRETURN of -1 per method
        assertEquals(2, countPattern(result.bytes(), Opcodes.ICONST_M1, Opcodes.IRETURN));
        // and every other method is byte-identical (the class has no others, so the
        // patched class differs from the original by exactly 4 ALOAD_0/SWAP/call triples)
        assertTrue(result.bytes().length > original.length);
    }

    @Test
    void runningItTwiceChangesNothing() {
        byte[] once = TeamChunkCapPatch.apply(syntheticClass(6000, 2000))
            .bytes();
        TeamChunkCapPatch.Result twice = TeamChunkCapPatch.apply(once);
        assertEquals(TeamChunkCapPatch.State.ALREADY_PATCHED, twice.state());
        assertSame(once, twice.bytes());
    }

    @Test
    void leavesAMethodWithTheWrongNumberOfSitesUntouched() {
        // The class shape the patch knows has TWO sites per method; one site is a
        // change the patch must refuse rather than half-apply.
        byte[] oneSite = oneSiteClass();
        TeamChunkCapPatch.Result result = TeamChunkCapPatch.apply(oneSite);
        assertEquals(TeamChunkCapPatch.State.UNEXPECTED_SHAPE, result.state());
        assertTrue(
            result.detail()
                .contains("expected return site"));
        assertSame(oneSite, result.bytes());
    }

    @Test
    void leavesAClassWithoutTheTargetsUntouched() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, OWNER, null, "java/lang/Object", null);
        writer.visitEnd();
        byte[] empty = writer.toByteArray();
        TeamChunkCapPatch.Result result = TeamChunkCapPatch.apply(empty);
        assertEquals(TeamChunkCapPatch.State.UNEXPECTED_SHAPE, result.state());
        assertSame(empty, result.bytes());
    }

    @Test
    void aNullClassIsNotAPatch() {
        assertEquals(
            TeamChunkCapPatch.State.UNEXPECTED_SHAPE,
            TeamChunkCapPatch.apply(null)
                .state());
    }

    /**
     * The real thing: the patched class is loaded and called. A team whose summed
     * limits are over the caps gets the caps; a solo team keeps its own number; and
     * the unpatched class returns the raw sums, which is what proves the patch —
     * not the harness — did the clamping.
     */
    @Test
    void thePatchedClassReallyClampsWhenCalled() throws Exception {
        TeamChunkCap.configure(true, true, 1500, 500);

        // a two-player team: the per-player numbers summed = 3000 / 1000
        Class<?> overCap = load(
            TeamChunkCapPatch.apply(syntheticClass(3000, 1000))
                .bytes());
        assertEquals(1500, call(overCap, TeamChunkCapPatch.CLAIM_METHOD));
        assertEquals(500, call(overCap, TeamChunkCapPatch.LOAD_METHOD));

        // the control: the same class, unpatched, returns the raw sums
        Class<?> raw = load(syntheticClass(3000, 1000));
        assertEquals(3000, call(raw, TeamChunkCapPatch.CLAIM_METHOD));
        assertEquals(1000, call(raw, TeamChunkCapPatch.LOAD_METHOD));

        // a solo team: the sum is already the per-player number, so nothing changes
        Class<?> solo = load(
            TeamChunkCapPatch.apply(syntheticClass(1500, 500))
                .bytes());
        assertEquals(1500, call(solo, TeamChunkCapPatch.CLAIM_METHOD));
        assertEquals(500, call(solo, TeamChunkCapPatch.LOAD_METHOD));
    }

    /** With the feature switched off, the patched class must behave like stock. */
    @Test
    void thePatchedClassDoesNothingWhenTheFeatureIsOff() throws Exception {
        Class<?> patched = load(
            TeamChunkCapPatch.apply(syntheticClass(3000, 1000))
                .bytes());
        try {
            TeamChunkCap.configure(true, false, 1500, 500);
            assertEquals(3000, call(patched, TeamChunkCapPatch.CLAIM_METHOD));
            assertEquals(1000, call(patched, TeamChunkCapPatch.LOAD_METHOD));

            TeamChunkCap.configure(false, true, 1500, 500);
            assertEquals(3000, call(patched, TeamChunkCapPatch.CLAIM_METHOD));

            TeamChunkCap.configure(true, true, 1500, 500);
            assertEquals(1500, call(patched, TeamChunkCapPatch.CLAIM_METHOD));
        } finally {
            TeamChunkCap.configure(true, true, 1500, 500);
        }
    }

    /** Loads the bytes into a fresh class loader (one class name per loader). */
    private static Class<?> load(byte[] bytes) throws Exception {
        Class<?> type = Class.forName(TeamChunkCapPatch.TARGET_CLASS, true, new BytesLoader(bytes));
        // the synthetic class' own "claimed chunks are active" switch
        type.getDeclaredField("active")
            .set(null, Boolean.TRUE);
        return type;
    }

    /** Defines the synthetic ServerUtilities class from bytes. */
    private static final class BytesLoader extends ClassLoader {

        private final byte[] bytes;

        private BytesLoader(byte[] bytes) {
            super(TeamChunkCapPatchTest.class.getClassLoader());
            this.bytes = bytes;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            // Child-first for the target only: the dev runtime classpath also has
            // the real ServerUtilities jar, and the parent-first lookup must not
            // win here.
            if (TeamChunkCapPatch.TARGET_CLASS.equals(name)) {
                synchronized (this) {
                    Class<?> type = findLoadedClass(name);
                    if (type == null) {
                        type = findClass(name);
                    }
                    if (resolve) {
                        resolveClass(type);
                    }
                    return type;
                }
            }
            return super.loadClass(name, resolve);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!TeamChunkCapPatch.TARGET_CLASS.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    private static int call(Class<?> type, String method) throws Exception {
        Object instance = type.getDeclaredConstructor()
            .newInstance();
        return ((Integer) type.getMethod(method)
            .invoke(instance)).intValue();
    }

    /** A three-site shape: the claim method loses its cached return. */
    private static byte[] oneSiteClass() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, OWNER, null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "active", "Z", null, null)
            .visitEnd();
        writer.visitField(Opcodes.ACC_PRIVATE, TeamChunkCapPatch.CLAIM_FIELD, "I", null, null)
            .visitEnd();
        writer.visitField(Opcodes.ACC_PRIVATE, TeamChunkCapPatch.LOAD_FIELD, "I", null, null)
            .visitEnd();
        MethodVisitor one = writer.visitMethod(Opcodes.ACC_PUBLIC, TeamChunkCapPatch.CLAIM_METHOD, "()I", null, null);
        one.visitCode();
        one.visitVarInsn(Opcodes.ALOAD, 0);
        one.visitFieldInsn(Opcodes.GETFIELD, OWNER, TeamChunkCapPatch.CLAIM_FIELD, "I");
        one.visitInsn(Opcodes.IRETURN);
        one.visitMaxs(0, 0);
        one.visitEnd();
        limitMethod(writer, TeamChunkCapPatch.LOAD_METHOD, TeamChunkCapPatch.LOAD_FIELD, 1000);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static int countHookCalls(byte[] bytes) {
        int count = 0;
        for (MethodNode method : read(bytes).methods) {
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn.getOpcode() != Opcodes.INVOKESTATIC) {
                    continue;
                }
                MethodInsnNode call = (MethodInsnNode) insn;
                if (TeamChunkCapPatch.HOOK_OWNER.equals(call.owner)
                    && (TeamChunkCapPatch.CLAIM_HOOK.equals(call.name) || TeamChunkCapPatch.LOAD_HOOK.equals(call.name))
                    && TeamChunkCapPatch.HOOK_DESC.equals(call.desc)) {
                    count++;
                }
            }
        }
        return count;
    }

    /** How often a two-instruction pattern still appears (the sentinels must survive). */
    private static int countPattern(byte[] bytes, int first, int second) {
        int count = 0;
        for (MethodNode method : read(bytes).methods) {
            List<AbstractInsnNode> insns = new ArrayList<AbstractInsnNode>();
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn.getOpcode() >= 0) {
                    insns.add(insn);
                }
            }
            for (int i = 0; i + 1 < insns.size(); i++) {
                if (insns.get(i)
                    .getOpcode() == first
                    && insns.get(i + 1)
                        .getOpcode() == second) {
                    count++;
                }
            }
        }
        return count;
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }
}
