package com.jointspaceforce.patchee.features;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * The handshake byte patch, applied to the real Forge class as it sits on the
 * test runtime classpath. That resource is the build's recompiled Forge jar, and
 * it carries the same HELLO shape as the shipped universal jar.
 *
 * <p>
 * The patched method is the HELLO state handler's
 * {@code accept(ChannelHandlerContext, FMLHandshakeMessage)}: the one place FML
 * reads the joining client's mod list. The hook must be the method's first
 * instruction, before any vanilla work, so it runs even on the branch that
 * rejects the join. The class is only skipped when Forge is not on the test
 * runtime classpath at all; when it is there, every check below runs.
 */
class HandshakeModListPatchTest {

    /** The HELLO handler's own method signature: it returns the state to move to next. */
    private static final String ACCEPT_DESC = "(Lio/netty/channel/ChannelHandlerContext;"
        + "Lcpw/mods/fml/common/network/handshake/FMLHandshakeMessage;)"
        + "Lcpw/mods/fml/common/network/handshake/FMLHandshakeServerState;";

    private static final String HOOK_OWNER = "com/jointspaceforce/patchee/features/SuperTankHandshake";
    private static final String HOOK_NAME = "observe";
    private static final String HOOK_DESC = "(Lio/netty/channel/ChannelHandlerContext;Lcpw/mods/fml/common/network/handshake/FMLHandshakeMessage;)V";

    /** The real class bytes, or null when Forge is not on this classpath. */
    private static byte[] forgeClass;

    @BeforeAll
    static void readTheForgeHandshakeClass() {
        forgeClass = readResource(HandshakeModListPatch.TARGET_CLASS.replace('.', '/') + ".class");
        Assumptions.assumeTrue(
            forgeClass != null,
            "the Forge handshake class " + HandshakeModListPatch.TARGET_CLASS
                + " is not on the test runtime classpath, so the real-byte checks cannot run");
    }

    @Test
    void theRealForgeClassIsPatched() {
        HandshakeModListPatch.Result result = HandshakeModListPatch.apply(forgeClass);
        assertEquals(
            HandshakeModListPatch.State.PATCHED,
            result.state(),
            "the real " + HandshakeModListPatch.TARGET_CLASS + " must be recognised and patched");
        assertFalse(Arrays.equals(forgeClass, result.bytes()), "the patched class must differ from the input bytes");
    }

    @Test
    void theHookIsTheFirstInstructionOfTheHelloAcceptMethod() {
        MethodNode accept = acceptMethod(
            HandshakeModListPatch.apply(forgeClass)
                .bytes());

        // Labels, line numbers and frames are bookkeeping: the real instructions
        // are what the JVM actually runs, in order.
        List<AbstractInsnNode> real = new ArrayList<>();
        for (AbstractInsnNode insn : accept.instructions.toArray()) {
            if (!isMetadata(insn)) {
                real.add(insn);
            }
        }

        List<AbstractInsnNode> hooks = new ArrayList<>();
        for (AbstractInsnNode insn : real) {
            if (isHook(insn)) {
                hooks.add(insn);
            }
        }
        assertEquals(
            1,
            hooks.size(),
            "the accept method must carry exactly one " + HOOK_OWNER + "." + HOOK_NAME + HOOK_DESC + " call");
        AbstractInsnNode hook = hooks.get(0);

        if (real.size() < 3) {
            fail(
                "the patched accept" + ACCEPT_DESC
                    + " must open with the hook's injected prologue, ALOAD 1,"
                    + " ALOAD 2, then INVOKESTATIC "
                    + HOOK_OWNER
                    + "."
                    + HOOK_NAME
                    + ", but it carries only "
                    + real.size()
                    + " real instruction(s)");
        }

        assertAload(
            real.get(0),
            1,
            "the first real instruction of accept" + ACCEPT_DESC
                + " must be ALOAD 1, the first half of the injected prologue that loads the ChannelHandlerContext"
                + " argument for the hook");
        assertAload(
            real.get(1),
            2,
            "the second real instruction of accept" + ACCEPT_DESC
                + " must be ALOAD 2, the second half of the injected prologue that loads the FMLHandshakeMessage"
                + " argument for the hook");
        assertSame(
            hook,
            real.get(2),
            "the handshake hook must be the third real instruction of accept" + ACCEPT_DESC
                + ", right after the prologue's two argument loads, so it runs before any of Forge's own work");
    }

    @Test
    void applyingItTwiceChangesNothing() {
        byte[] once = HandshakeModListPatch.apply(forgeClass)
            .bytes();
        HandshakeModListPatch.Result twice = HandshakeModListPatch.apply(once);
        assertEquals(
            HandshakeModListPatch.State.ALREADY_PATCHED,
            twice.state(),
            "a second pass must recognise its own hook");
        assertArrayEquals(once, twice.bytes(), "a second pass must hand the bytes back unchanged");
    }

    @Test
    void anUnrelatedClassIsLeftCompletelyAlone() {
        byte[] unrelated = readResource("com/jointspaceforce/patchee/features/SuperTankDecision.class");
        assertNotNull(unrelated, "the test needs SuperTankDecision.class on the classpath");

        HandshakeModListPatch.Result result = HandshakeModListPatch.apply(unrelated);
        assertEquals(
            HandshakeModListPatch.State.UNEXPECTED_SHAPE,
            result.state(),
            "a class that is not the Forge handshake must be refused");
        assertArrayEquals(unrelated, result.bytes(), "a refused class must be handed back byte-identical");
    }

    /** The patched class's own HELLO accept method. */
    private static MethodNode acceptMethod(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        for (MethodNode method : node.methods) {
            if ("accept".equals(method.name) && ACCEPT_DESC.equals(method.desc)) {
                return method;
            }
        }
        return fail("the patched " + HandshakeModListPatch.TARGET_CLASS + " has no accept" + ACCEPT_DESC + " method");
    }

    /** True for the one call the patch injects. */
    private static boolean isHook(AbstractInsnNode insn) {
        if (!(insn instanceof MethodInsnNode)) {
            return false;
        }
        MethodInsnNode call = (MethodInsnNode) insn;
        return HOOK_OWNER.equals(call.owner) && HOOK_NAME.equals(call.name) && HOOK_DESC.equals(call.desc);
    }

    /** Labels, line numbers and frames are bookkeeping, not instructions. */
    private static boolean isMetadata(AbstractInsnNode insn) {
        return insn instanceof LabelNode || insn instanceof LineNumberNode || insn instanceof FrameNode;
    }

    /** Fails unless {@code insn} is an ALOAD of the given local slot. */
    private static void assertAload(AbstractInsnNode insn, int slot, String message) {
        if (!(insn instanceof VarInsnNode)) {
            fail(
                message + "; found "
                    + insn.getClass()
                        .getSimpleName()
                    + " instead of a variable load");
            return;
        }
        VarInsnNode load = (VarInsnNode) insn;
        assertEquals(Opcodes.ALOAD, load.getOpcode(), message + "; the opcode must be ALOAD");
        assertEquals(slot, load.var, message + "; the loaded local slot must be " + slot);
    }

    /** The class file's bytes, or null when the resource is not on this classpath. */
    private static byte[] readResource(String path) {
        try (InputStream in = HandshakeModListPatchTest.class.getResourceAsStream("/" + path)) {
            if (in == null) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int read = in.read(buffer); read != -1; read = in.read(buffer)) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
