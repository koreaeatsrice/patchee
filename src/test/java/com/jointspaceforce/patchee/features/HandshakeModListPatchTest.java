package com.jointspaceforce.patchee.features;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import com.jointspaceforce.patchee.asm.HandshakeModListTransformer;

/**
 * The handshake patch, checked against the real Forge class as it sits on the
 * test runtime classpath. That resource is the build's recompiled Forge jar, and
 * it carries the same HELLO shape as the shipped universal jar.
 *
 * <p>
 * The patched method is the HELLO state handler's
 * {@code accept(ChannelHandlerContext, FMLHandshakeMessage)}: the one place FML
 * reads the joining client's mod list. The hook must be the method's first
 * instruction, before any vanilla work, so it runs even on the branch that
 * rejects the join.
 *
 * <p>
 * Nothing here is skipped. If the Forge class is missing from the classpath the
 * tests fail with a message saying so: a green build must mean the patch was
 * checked, never that the check quietly did not run.
 */
class HandshakeModListPatchTest {

    /** The HELLO handler's own method signature: it returns the state to move to next. */
    private static final String ACCEPT_DESC = "(Lio/netty/channel/ChannelHandlerContext;"
        + "Lcpw/mods/fml/common/network/handshake/FMLHandshakeMessage;)"
        + "Lcpw/mods/fml/common/network/handshake/FMLHandshakeServerState;";

    private static final String HOOK_OWNER = "com/jointspaceforce/patchee/features/SuperTankHandshake";
    private static final String HOOK_NAME = "observe";
    private static final String HOOK_DESC = "(Lio/netty/channel/ChannelHandlerContext;Lcpw/mods/fml/common/network/handshake/FMLHandshakeMessage;)V";

    /**
     * The two message types whose presence pins the target to FML's HELLO state.
     * Every {@code FMLHandshakeServerState$N} has an {@code accept} with the same
     * descriptor, so the descriptor alone cannot tell them apart; only HELLO
     * reads the joining client's mod list.
     */
    private static final String MOD_LIST = "cpw/mods/fml/common/network/handshake/FMLHandshakeMessage$ModList";

    private static final String CLIENT_HELLO = "cpw/mods/fml/common/network/handshake/FMLHandshakeMessage$ClientHello";

    /**
     * The real class bytes. Fails the calling test — never skips — when the class
     * is not on the test runtime classpath.
     */
    private static byte[] realForgeClass() {
        byte[] bytes = readResource(HandshakeModListPatch.TARGET_CLASS.replace('.', '/') + ".class");
        if (bytes == null) {
            return fail(
                "the Forge handshake class " + HandshakeModListPatch.TARGET_CLASS
                    + " is not on the test runtime classpath, so the real-byte checks cannot run."
                    + " This is a hard failure on purpose: a skipped check would let a broken patch"
                    + " ship with a green build. Put Forge's FMLHandshakeServerState on the test"
                    + " classpath (the build's recompiled Forge jar) and run again.");
        }
        return bytes;
    }

    /**
     * The class named by {@link HandshakeModListPatch#TARGET_CLASS} must be the
     * HELLO state. Retargeting the patch at any other
     * {@code FMLHandshakeServerState$N} — which shares the accept descriptor —
     * would patch a handler that never sees the mod list, so this check is what
     * makes such a retarget fail loudly.
     */
    @Test
    void theTargetClassIsTheStateThatReadsTheClientModList() {
        MethodNode accept = acceptMethod(realForgeClass());

        assertTrue(
            references(accept, MOD_LIST),
            "the accept method of " + HandshakeModListPatch.TARGET_CLASS
                + " must read "
                + MOD_LIST
                + ", the message that carries the joining client's mod list. It does not, so the"
                + " patch is aimed at the wrong FML handshake state and would never learn whether"
                + " a client runs Patchee.");
        assertTrue(
            references(accept, CLIENT_HELLO),
            "the accept method of " + HandshakeModListPatch.TARGET_CLASS
                + " must handle "
                + CLIENT_HELLO
                + ", the first message of the HELLO state. It does not, so the patch is aimed at"
                + " the wrong FML handshake state.");
    }

    @Test
    void theRealForgeClassIsPatched() {
        byte[] forgeClass = realForgeClass();
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
            HandshakeModListPatch.apply(realForgeClass())
                .bytes());

        List<AbstractInsnNode> real = realInstructions(accept);

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

        assertHookOpensTheMethod(real, hook, "the patched accept" + ACCEPT_DESC);
    }

    @Test
    void applyingItTwiceChangesNothing() {
        byte[] forgeClass = realForgeClass();
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

    /**
     * The transformer itself, not just the patch it delegates to: with the fix
     * armed it must rewrite FML's real class and put the hook first. Without this
     * the transformer could be unwired (wrong name comparison, early return) and
     * every lower-level test would still pass.
     */
    @Test
    void theTransformerPatchesTheRealForgeClassOnceTheFixIsArmed() {
        byte[] original = realForgeClass();
        SuperTankFeature.markArmed();

        byte[] patched = new HandshakeModListTransformer()
            .transform(HandshakeModListPatch.TARGET_CLASS, HandshakeModListPatch.TARGET_CLASS, original);

        assertNotNull(patched, "the transformer must never return null for a non-null class");
        assertFalse(
            Arrays.equals(original, patched),
            "with the fix armed, the transformer must rewrite " + HandshakeModListPatch.TARGET_CLASS);
        assertSame(
            HandshakeModListPatch.State.ALREADY_PATCHED,
            HandshakeModListPatch.apply(patched)
                .state(),
            "the transformed bytes must be recognisable as the patched class, not something else");

        MethodNode accept = acceptMethod(patched);
        AbstractInsnNode hook = null;
        for (AbstractInsnNode insn : realInstructions(accept)) {
            if (isHook(insn)) {
                hook = insn;
                break;
            }
        }
        assertNotNull(
            hook,
            "the transformer's output must carry an " + HOOK_OWNER + "." + HOOK_NAME + " call in accept" + ACCEPT_DESC);
        assertHookOpensTheMethod(
            realInstructions(accept),
            hook,
            "the class the transformer returns; its accept" + ACCEPT_DESC);
    }

    /** A class the patch does not know must be handed back as the very same array, untouched. */
    @Test
    void theTransformerReturnsTheSameArrayForAnUnrelatedClassName() {
        byte[] unrelated = readResource("com/jointspaceforce/patchee/features/SuperTankDecision.class");
        assertNotNull(unrelated, "the test needs SuperTankDecision.class on the classpath");

        byte[] passedThrough = new HandshakeModListTransformer().transform(
            "com.jointspaceforce.patchee.features.SuperTankDecision",
            "com.jointspaceforce.patchee.features.SuperTankDecision",
            unrelated);
        assertSame(unrelated, passedThrough, "an unrelated class name must be returned as the identical array");
    }

    /** Fails unless the hook is the third real instruction, after ALOAD 1 and ALOAD 2. */
    private static void assertHookOpensTheMethod(List<AbstractInsnNode> real, AbstractInsnNode hook, String where) {
        if (real.size() < 3) {
            fail(
                where + " must open with the hook's injected prologue, ALOAD 1, ALOAD 2, then INVOKESTATIC "
                    + HOOK_OWNER
                    + "."
                    + HOOK_NAME
                    + ", but it carries only "
                    + real.size()
                    + " real instruction(s)");
        }
        assertAload(real.get(0), 1, where + " must start with ALOAD 1, loading the ChannelHandlerContext argument");
        assertAload(real.get(1), 2, where + " must then ALOAD 2, loading the FMLHandshakeMessage argument");
        assertSame(
            hook,
            real.get(2),
            where + " must call the hook as its third real instruction, before any of Forge's own work");
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
        return fail("the class " + HandshakeModListPatch.TARGET_CLASS + " has no accept" + ACCEPT_DESC + " method");
    }

    /** The method's instructions, minus labels, line numbers and frames. */
    private static List<AbstractInsnNode> realInstructions(MethodNode method) {
        List<AbstractInsnNode> real = new ArrayList<>();
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (!isMetadata(insn)) {
                real.add(insn);
            }
        }
        return real;
    }

    /** True when the method mentions the given internal class name anywhere in its code. */
    private static boolean references(MethodNode method, String internalName) {
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof MethodInsnNode && internalName.equals(((MethodInsnNode) insn).owner)) {
                return true;
            }
            if (insn instanceof FieldInsnNode && internalName.equals(((FieldInsnNode) insn).owner)) {
                return true;
            }
            if (insn instanceof TypeInsnNode && internalName.equals(((TypeInsnNode) insn).desc)) {
                return true;
            }
            if (insn instanceof LdcInsnNode) {
                Object constant = ((LdcInsnNode) insn).cst;
                if (constant instanceof Type && internalName.equals(((Type) constant).getInternalName())) {
                    return true;
                }
            }
        }
        return false;
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
