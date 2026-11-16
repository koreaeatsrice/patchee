package com.jointspaceforce.patchee.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodNode;

import com.jointspaceforce.patchee.asm.PollutionNoVisualsTransformer;

/**
 * The pollution-visuals byte patch, against synthetic class bytes shaped exactly
 * like GregTech 5.09.54.265's {@code gregtech.api.net.GTPacketPollution}: a
 * {@code chunk} field, a {@code pollution} int, the {@code (chunk, int)}
 * constructor that stores both, and the two methods that read the number back
 * ({@code encode} and {@code process}).
 *
 * <p>
 * These tests pin the contract the transformer relies on: the constructor's
 * {@code ILOAD} of the int argument becomes {@code ICONST_0} and nothing else
 * changes, a class of any other shape is left completely untouched, a second
 * pass does nothing — and, strongest of all, the patched class actually loads
 * and a packet built with 12345 carries 0 while the unpatched control keeps
 * 12345.
 */
class PollutionNoVisualsPatchTest {

    private static final String OWNER = PollutionNoVisualsPatch.TARGET_INTERNAL_NAME;

    /**
     * The two one-way flags live on a static holder, so a test that leaves them
     * set would change what the next one sees. Reset before every test, not
     * after, so test order can never matter.
     */
    @BeforeEach
    void resetFeatureFlags() {
        PollutionNoVisuals.patchApplied = false;
        PollutionNoVisuals.loadTimeDisabled = false;
    }

    /** How the constructor's pollution store is fed, so a wrong shape can be built. */
    private enum StoreSource {

        /** The real shape: the constructor's own int argument. */
        INT_ARGUMENT,
        /** What the patched class looks like: a constant 0. */
        ZERO,
        /** A constant -1 instead of the argument. */
        MINUS_ONE,
        /** Another field read instead of the argument. */
        FIELD
    }

    /** A class with GregTech's packet shape; the constructor stores as asked. */
    private static byte[] syntheticClass(StoreSource source) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, OWNER, null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PRIVATE, "chunk", "Ljava/lang/Object;", null, null)
            .visitEnd();
        writer
            .visitField(
                Opcodes.ACC_PRIVATE,
                PollutionNoVisualsPatch.FIELD,
                PollutionNoVisualsPatch.FIELD_DESC,
                null,
                null)
            .visitEnd();

        MethodVisitor ctor = writer
            .visitMethod(Opcodes.ACC_PUBLIC, PollutionNoVisualsPatch.CTOR, "(Ljava/lang/Object;I)V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", PollutionNoVisualsPatch.CTOR, "()V", false);
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitVarInsn(Opcodes.ALOAD, 1);
        ctor.visitFieldInsn(Opcodes.PUTFIELD, OWNER, "chunk", "Ljava/lang/Object;");
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        switch (source) {
            case ZERO:
                ctor.visitInsn(Opcodes.ICONST_0);
                break;
            case MINUS_ONE:
                ctor.visitInsn(Opcodes.ICONST_M1);
                break;
            case FIELD:
                ctor.visitVarInsn(Opcodes.ALOAD, 0);
                ctor.visitFieldInsn(
                    Opcodes.GETFIELD,
                    OWNER,
                    PollutionNoVisualsPatch.FIELD,
                    PollutionNoVisualsPatch.FIELD_DESC);
                break;
            default:
                ctor.visitVarInsn(Opcodes.ILOAD, 2);
                break;
        }
        ctor.visitFieldInsn(Opcodes.PUTFIELD, OWNER, PollutionNoVisualsPatch.FIELD, PollutionNoVisualsPatch.FIELD_DESC);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();

        readerMethod(writer, "encode");
        readerMethod(writer, "process");
        writer.visitEnd();
        return writer.toByteArray();
    }

    /** A method that answers the stored pollution number, like encode/process read it. */
    private static void readerMethod(ClassWriter writer, String name) {
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, name, "()I", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method
            .visitFieldInsn(Opcodes.GETFIELD, OWNER, PollutionNoVisualsPatch.FIELD, PollutionNoVisualsPatch.FIELD_DESC);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    @Test
    void rewritesTheArgumentLoadAndNothingElse() {
        byte[] original = syntheticClass(StoreSource.INT_ARGUMENT);
        PollutionNoVisualsPatch.Result result = PollutionNoVisualsPatch.apply(original);

        assertEquals(PollutionNoVisualsPatch.State.PATCHED, result.state(), result.detail());
        assertEquals(PollutionNoVisualsPatch.SITE_COUNT, result.sites());

        List<Integer> before = ctorOpcodes(original);
        List<Integer> after = ctorOpcodes(result.bytes());
        assertEquals(before.size(), after.size(), "the constructor must not grow or shrink");
        int changed = 0;
        for (int i = 0; i < before.size(); i++) {
            if (!before.get(i)
                .equals(after.get(i))) {
                assertEquals(Integer.valueOf(Opcodes.ILOAD), before.get(i));
                assertEquals(Integer.valueOf(Opcodes.ICONST_0), after.get(i));
                changed++;
            }
        }
        assertEquals(1, changed, "exactly the one argument load may change");
        assertTrue(storesZero(result.bytes()), "ICONST_0 must sit immediately before PUTFIELD pollution");
        // the readers and every other method are untouched
        assertTrue(opcodes("encode", result.bytes()).equals(opcodes("encode", original)));
        assertTrue(opcodes("process", result.bytes()).equals(opcodes("process", original)));
    }

    @Test
    void runningItTwiceChangesNothing() {
        byte[] once = PollutionNoVisualsPatch.apply(syntheticClass(StoreSource.INT_ARGUMENT))
            .bytes();
        PollutionNoVisualsPatch.Result twice = PollutionNoVisualsPatch.apply(once);
        assertEquals(PollutionNoVisualsPatch.State.ALREADY_PATCHED, twice.state());
        assertSame(once, twice.bytes());
    }

    @Test
    void leavesAClassWithoutTheConstructorUntouched() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, OWNER, null, "java/lang/Object", null);
        writer
            .visitField(
                Opcodes.ACC_PRIVATE,
                PollutionNoVisualsPatch.FIELD,
                PollutionNoVisualsPatch.FIELD_DESC,
                null,
                null)
            .visitEnd();
        writer.visitEnd();
        byte[] empty = writer.toByteArray();

        PollutionNoVisualsPatch.Result result = PollutionNoVisualsPatch.apply(empty);
        assertEquals(PollutionNoVisualsPatch.State.UNEXPECTED_SHAPE, result.state());
        assertSame(empty, result.bytes());

        assertEquals(
            PollutionNoVisualsPatch.State.UNEXPECTED_SHAPE,
            PollutionNoVisualsPatch.apply(null)
                .state());
        assertEquals(
            PollutionNoVisualsPatch.State.UNEXPECTED_SHAPE,
            PollutionNoVisualsPatch.apply(new byte[0])
                .state());
    }

    @Test
    void leavesAStoreFedBySomethingOtherThanTheArgumentUntouched() {
        for (StoreSource source : new StoreSource[] { StoreSource.MINUS_ONE, StoreSource.FIELD }) {
            byte[] wrong = syntheticClass(source);
            PollutionNoVisualsPatch.Result result = PollutionNoVisualsPatch.apply(wrong);
            assertEquals(PollutionNoVisualsPatch.State.UNEXPECTED_SHAPE, result.state(), source.name());
            assertTrue(
                result.detail()
                    .contains("not the int argument"),
                result.detail());
            assertSame(wrong, result.bytes());
        }
    }

    /**
     * The real thing: the patched class is loaded and a packet is built with a
     * real number. The patched class stores 0 and leaves the chunk field alone;
     * the unpatched control keeps 12345. The readers see what the field holds.
     */
    @Test
    void thePatchedClassSendsZeroAndKeepsTheChunk() throws Exception {
        Class<?> patched = load(
            PollutionNoVisualsPatch.apply(syntheticClass(StoreSource.INT_ARGUMENT))
                .bytes());
        Object packet = patched.getDeclaredConstructor(Object.class, int.class)
            .newInstance("chunk-a", Integer.valueOf(12345));
        assertEquals(0, intField(patched, packet, PollutionNoVisualsPatch.FIELD));
        assertEquals("chunk-a", packetField(patched, packet, "chunk"));
        assertEquals(
            0,
            ((Integer) patched.getMethod("process")
                .invoke(packet)).intValue());
        assertEquals(
            0,
            ((Integer) patched.getMethod("encode")
                .invoke(packet)).intValue());

        Class<?> raw = load(syntheticClass(StoreSource.INT_ARGUMENT));
        Object untouched = raw.getDeclaredConstructor(Object.class, int.class)
            .newInstance("chunk-b", Integer.valueOf(12345));
        assertEquals(12345, intField(raw, untouched, PollutionNoVisualsPatch.FIELD));
        assertEquals("chunk-b", packetField(raw, untouched, "chunk"));
        assertEquals(
            12345,
            ((Integer) raw.getMethod("process")
                .invoke(untouched)).intValue());
    }

    /**
     * The transformer is the only thing that may set the patch-success flag the
     * ARMED line is gated on: a recognised class flips it, garbage and a
     * non-target name do not.
     *
     * <p>
     * The switches are handed in as parsed text, so this never reads the
     * developer's own {@code config/patchee.cfg} — the old form went through
     * {@code transform}, which read the real file and made the result depend on
     * one person's machine.
     */
    @Test
    void theTransformerFailsSoftAndPublishesSuccessOnlyOnARealPatch() {
        PollutionNoVisualsTransformer transformer = new PollutionNoVisualsTransformer();

        byte[] garbage = new byte[] { 1, 2, 3, 4, 5 };
        assertSame(garbage, transformer.patch(garbage, armedSettings()));
        assertFalse(PollutionNoVisuals.patchApplied(), "garbage must not arm the feature");
        assertFalse(PollutionNoVisuals.loadTimeDisabled());

        byte[] other = syntheticClass(StoreSource.INT_ARGUMENT);
        assertSame(other, transformer.transform("com.example.Other", "com.example.Other", other));
        assertFalse(PollutionNoVisuals.patchApplied());

        byte[] patched = transformer.patch(syntheticClass(StoreSource.INT_ARGUMENT), armedSettings());
        assertTrue(PollutionNoVisuals.patchApplied());
        assertFalse(PollutionNoVisuals.loadTimeDisabled());
        assertTrue(storesZero(patched));
    }

    /**
     * Switched off when the class loaded: the bytes come back untouched, nothing
     * is armed, and the flag the boot line reads says the switch — not a bad
     * shape — is why.
     */
    @Test
    void aClassThatLoadedWhileSwitchedOffIsHandedBackAndReportedAsSuch() {
        PollutionNoVisualsTransformer transformer = new PollutionNoVisualsTransformer();
        byte[] original = syntheticClass(StoreSource.INT_ARGUMENT);

        byte[] untouched = transformer
            .patch(original, PollutionNoVisualsSettings.parse("B:enabled=true\nB:enablePollutionNoVisuals=false\n"));

        assertSame(original, untouched, "the class must come back as the same array instance");
        assertFalse(storesZero(original), "the constructor still loads its int argument");
        assertFalse(PollutionNoVisuals.patchApplied(), "a switched-off class must not arm the feature");
        assertTrue(PollutionNoVisuals.loadTimeDisabled(), "the boot line needs the real reason");
    }

    /** Both switches on, from text. */
    private static PollutionNoVisualsSettings armedSettings() {
        return PollutionNoVisualsSettings.parse("B:enabled=true\nB:enablePollutionNoVisuals=true\n");
    }

    /** Loads the bytes into a fresh class loader (one class name per loader). */
    private static Class<?> load(byte[] bytes) throws Exception {
        return Class.forName(PollutionNoVisualsPatch.TARGET_CLASS, true, new BytesLoader(bytes));
    }

    /** Defines the synthetic GregTech packet class from bytes. */
    private static final class BytesLoader extends ClassLoader {

        private final byte[] bytes;

        private BytesLoader(byte[] bytes) {
            super(PollutionNoVisualsPatchTest.class.getClassLoader());
            this.bytes = bytes;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            // Child-first for the target only: a dev runtime classpath may also
            // have the real GregTech jar, and the parent-first lookup must not win.
            if (PollutionNoVisualsPatch.TARGET_CLASS.equals(name)) {
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
            if (!PollutionNoVisualsPatch.TARGET_CLASS.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    private static int intField(Class<?> type, Object instance, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(instance);
    }

    private static Object packetField(Class<?> type, Object instance, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    /** True when a PUTFIELD pollution: I is immediately preceded by ICONST_0. */
    private static boolean storesZero(byte[] bytes) {
        MethodNode ctor = ctorOf(bytes);
        AbstractInsnNode previous = null;
        for (AbstractInsnNode insn : ctor.instructions.toArray()) {
            if (insn.getOpcode() >= 0) {
                if (isStore(insn) && (previous == null || previous.getOpcode() != Opcodes.ICONST_0)) {
                    return false;
                }
                previous = insn;
            }
        }
        return true;
    }

    private static MethodNode ctorOf(byte[] bytes) {
        for (MethodNode method : read(bytes).methods) {
            if (PollutionNoVisualsPatch.CTOR.equals(method.name) && "(Ljava/lang/Object;I)V".equals(method.desc)) {
                return method;
            }
        }
        fail("the synthetic class has no (Object, int) constructor");
        return null;
    }

    private static List<Integer> ctorOpcodes(byte[] bytes) {
        List<Integer> ops = new ArrayList<Integer>();
        for (AbstractInsnNode insn : ctorOf(bytes).instructions.toArray()) {
            if (insn.getOpcode() >= 0) {
                ops.add(Integer.valueOf(insn.getOpcode()));
            }
        }
        return ops;
    }

    private static List<Integer> opcodes(String name, byte[] bytes) {
        List<Integer> ops = new ArrayList<Integer>();
        for (MethodNode method : read(bytes).methods) {
            if (!name.equals(method.name)) {
                continue;
            }
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn.getOpcode() >= 0) {
                    ops.add(Integer.valueOf(insn.getOpcode()));
                }
            }
        }
        return ops;
    }

    private static boolean isStore(AbstractInsnNode insn) {
        if (insn.getOpcode() != Opcodes.PUTFIELD) {
            return false;
        }
        FieldInsnNode store = (FieldInsnNode) insn;
        return OWNER.equals(store.owner) && PollutionNoVisualsPatch.FIELD.equals(store.name)
            && PollutionNoVisualsPatch.FIELD_DESC.equals(store.desc);
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }
}
