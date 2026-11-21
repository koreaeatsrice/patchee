package com.jointspaceforce.patchee.features;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * The handshake bytecode patch: a three-instruction hook at the entry of FML's
 * client mod-list step.
 *
 * <p>
 * In {@code cpw.mods.fml.common.network.handshake.FMLHandshakeServerState$2},
 * {@code accept(ChannelHandlerContext, FMLHandshakeMessage)} is the one place
 * the server sees the client's {@code ModList} before FML throws it away. A call
 * to {@link SuperTankHandshake#observe} is inserted at the very start of that
 * method, so the list is read on its way in.
 *
 * <p>
 * <b>Why the method entry is safe.</b> The three instructions go in before the
 * first real instruction, which means before the first {@code StackMapTable}
 * frame: no label moves relative to a branch, the frames read from FML's class
 * stay valid at their new offsets, and {@code COMPUTE_MAXS} is enough to fix the
 * one-slot depth the two arguments add. Nothing else in the method changes.
 *
 * <p>
 * It is deliberately suspicious: a class that does not carry the exact method,
 * or whose bytes cannot be read, is reported and handed back completely
 * untouched, and a class that already calls the hook is recognised so a second
 * pass changes nothing.
 */
public final class HandshakeModListPatch {

    /** The class this patch knows, as the class loader names it. */
    public static final String TARGET_CLASS = "cpw.mods.fml.common.network.handshake.FMLHandshakeServerState$2";

    /** The same class in internal (slash) form, as the constant pool spells it. */
    public static final String TARGET_INTERNAL_NAME = "cpw/mods/fml/common/network/handshake/FMLHandshakeServerState$2";

    /** The one method this patch changes. */
    public static final String METHOD = "accept";

    /** Its exact descriptor — the bridge method with the {@code Object} return is not this one. */
    public static final String METHOD_DESC = "(Lio/netty/channel/ChannelHandlerContext;"
        + "Lcpw/mods/fml/common/network/handshake/FMLHandshakeMessage;)"
        + "Lcpw/mods/fml/common/network/handshake/FMLHandshakeServerState;";

    /** The Patchee class holding the injected method. */
    public static final String HOOK_OWNER = "com/jointspaceforce/patchee/features/SuperTankHandshake";

    /** The injected method. */
    public static final String HOOK_NAME = "observe";

    /** {@code (ChannelHandlerContext, FMLHandshakeMessage) -> void}. */
    public static final String HOOK_DESC = "(Lio/netty/channel/ChannelHandlerContext;"
        + "Lcpw/mods/fml/common/network/handshake/FMLHandshakeMessage;)V";

    /** What {@link #apply} found and did. */
    public enum State {

        /** The hook was inserted; {@link Result#bytes()} is the patched class. */
        PATCHED,

        /** The class already carries this patch; bytes are unchanged. */
        ALREADY_PATCHED,

        /** The class is not the shape this patch knows; bytes are unchanged. */
        UNEXPECTED_SHAPE
    }

    /** The outcome of one {@link #apply} call. */
    public static final class Result {

        private final State state;
        private final byte[] bytes;

        Result(State state, byte[] bytes) {
            this.state = state;
            this.bytes = bytes;
        }

        public State state() {
            return state;
        }

        /** The class bytes to hand back to the loader (never null). */
        public byte[] bytes() {
            return bytes;
        }
    }

    private HandshakeModListPatch() {}

    /**
     * Insert {@link SuperTankHandshake#observe} at the entry of FML's mod-list step.
     *
     * @param original FML's class bytes as the loader sees them
     */
    public static Result apply(byte[] original) {
        if (original == null || original.length == 0) {
            return new Result(State.UNEXPECTED_SHAPE, original);
        }
        try {
            ClassNode node = new ClassNode();
            // No flags: the StackMapTable frames must be kept, so the writer can
            // move them to their new offsets instead of recomputing them.
            new ClassReader(original).accept(node, 0);

            MethodNode method = null;
            for (MethodNode candidate : node.methods) {
                if (METHOD.equals(candidate.name) && METHOD_DESC.equals(candidate.desc)) {
                    method = candidate;
                    break;
                }
            }
            if (method == null) {
                return new Result(State.UNEXPECTED_SHAPE, original);
            }
            if (alreadyPatched(method)) {
                return new Result(State.ALREADY_PATCHED, original);
            }

            // At entry the locals are exactly [this, ctx, message], so the hook's
            // two arguments are slots 1 and 2.
            InsnList inject = new InsnList();
            inject.add(new VarInsnNode(Opcodes.ALOAD, 1));
            inject.add(new VarInsnNode(Opcodes.ALOAD, 2));
            inject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK_OWNER, HOOK_NAME, HOOK_DESC, false));
            method.instructions.insert(inject);

            // COMPUTE_MAXS, never COMPUTE_FRAMES: the frames read from FML's class
            // stay exactly as they are and ASM only moves them to their new offsets.
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            return new Result(State.PATCHED, writer.toByteArray());
        } catch (Throwable unreadable) {
            // Never break the loader: an unreadable class is left exactly as it ships.
            return new Result(State.UNEXPECTED_SHAPE, original);
        }
    }

    /** True when the method already calls the injected hook. */
    private static boolean alreadyPatched(MethodNode method) {
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn.getOpcode() != Opcodes.INVOKESTATIC) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode) insn;
            if (HOOK_OWNER.equals(call.owner) && HOOK_NAME.equals(call.name)) {
                return true;
            }
        }
        return false;
    }
}
