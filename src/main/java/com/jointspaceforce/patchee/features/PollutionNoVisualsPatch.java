package com.jointspaceforce.patchee.features;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * The pollution-visuals bytecode patch: the one constructor that carries a
 * pollution value is made to store {@code 0} instead of its argument.
 *
 * <p>
 * GregTech 5.09.54.265 has exactly one pollution packet,
 * {@code gregtech.api.net.GTPacketPollution}, with two fields
 * ({@code private ChunkCoordIntPair chunk} and {@code private int pollution}).
 * Its {@code (ChunkCoordIntPair, int)} constructor compiles to exactly:
 *
 * <pre>
 *  0: aload_0
 *  1: invokespecial gregtech/api/net/GTPacket."&lt;init&gt;":()V
 *  4: aload_0
 *  5: aload_1
 *  6: putfield      chunk:Lnet/minecraft/world/ChunkCoordIntPair;
 *  9: aload_0
 * 10: iload_2
 * 11: putfield      pollution:I
 * 14: return
 * </pre>
 *
 * <p>
 * The server sends that packet from exactly two places — {@code Pollution} tick
 * loop and its chunk-watch handler — and both construct it through this
 * constructor; a client that decodes a packet also constructs it through this
 * constructor. So replacing the single {@code ILOAD 2} with {@code ICONST_0}
 * makes the field {@code 0} at construction on both sides, and every pollution
 * value the server ever sends is 0. The real server storage was written before
 * the send ({@code setChunkPollution} runs first), so mechanics keep their exact
 * numbers: damage, sour rain, vegetation checks, scanners, the pollution
 * detector and machines all read storage, never the packet.
 *
 * <p>
 * <b>Why this is safe to do in place.</b> It is a value rewrite, not an
 * injected call: one instruction node is replaced by another of the same size
 * and the same stack effect (both push one int), nothing is added, no branch is
 * retargeted, and the constructor has no {@code StackMapTable} at all. The
 * writer runs with {@code COMPUTE_MAXS} only, so any frames a different build
 * does carry stay exactly as they were read.
 *
 * <p>
 * <b>What it cannot do.</b> Nothing client-side can be shown the real number
 * through GregTech's own packet after this: the number is not in the packet any
 * more. Anything a client reads from it — a pollution readout, a debug display
 * built on {@code GTClientPollutionMap} — reads 0.
 *
 * <p>
 * It is deliberately suspicious: zero or more than one matching constructor, or
 * a pollution store fed by anything other than the int argument, is reported
 * and handed back completely untouched, and a class that already stores
 * {@code ICONST_0} is recognised so a second pass changes nothing.
 */
public final class PollutionNoVisualsPatch {

    /** The class this patch knows, as the class loader names it. */
    public static final String TARGET_CLASS = "gregtech.api.net.GTPacketPollution";

    /** The same class in internal (slash) form, as the constant pool spells it. */
    public static final String TARGET_INTERNAL_NAME = "gregtech/api/net/GTPacketPollution";

    /** The field the constructor stores the pollution number into. */
    public static final String FIELD = "pollution";

    /** The field's type: a plain int. */
    public static final String FIELD_DESC = "I";

    /** How many constructor sites a healthy class has: exactly one. */
    public static final int SITE_COUNT = 1;

    /** The constructor name. */
    public static final String CTOR = "<init>";

    /** What {@link #apply} found and did. */
    public enum State {

        /** The constructor site was rewritten; {@link Result#bytes()} is the patched class. */
        PATCHED,

        /** The class already stores 0; bytes are unchanged. */
        ALREADY_PATCHED,

        /** The class is not the shape this patch knows; bytes are unchanged. */
        UNEXPECTED_SHAPE
    }

    /** The outcome of one {@link #apply} call. */
    public static final class Result {

        private final State state;
        private final byte[] bytes;
        private final int sites;
        private final String detail;

        Result(State state, byte[] bytes, int sites, String detail) {
            this.state = state;
            this.bytes = bytes;
            this.sites = sites;
            this.detail = detail;
        }

        public State state() {
            return state;
        }

        /** The class bytes to hand back to the loader (never null). */
        public byte[] bytes() {
            return bytes;
        }

        /** How many constructor sites were rewritten ({@link PollutionNoVisualsPatch#SITE_COUNT} on success). */
        public int sites() {
            return sites;
        }

        /** What went wrong, in one line, or the observed shape when nothing did. */
        public String detail() {
            return detail;
        }
    }

    private PollutionNoVisualsPatch() {}

    /**
     * Make the packet's {@code (chunk, int)} constructor store 0.
     *
     * @param original GregTech's packet class bytes as the loader sees them
     */
    public static Result apply(byte[] original) {
        if (original == null || original.length == 0) {
            return new Result(State.UNEXPECTED_SHAPE, original, 0, "the class bytes are empty");
        }
        ClassNode node;
        try {
            node = read(original);
        } catch (RuntimeException unreadable) {
            return new Result(State.UNEXPECTED_SHAPE, original, 0, "the class bytes could not be read: " + unreadable);
        }

        List<MethodNode> ctors = ctorsWithIntArgument(node);
        if (ctors.isEmpty()) {
            return new Result(
                State.UNEXPECTED_SHAPE,
                original,
                0,
                "no " + CTOR
                    + "(..., int) constructor found (the patch needs one that takes the chunk and the pollution number)");
        }

        // The constructor this patch owns: the one that stores the field exactly once.
        List<MethodNode> candidates = new ArrayList<MethodNode>();
        for (MethodNode ctor : ctors) {
            if (countStores(ctor) == SITE_COUNT) {
                candidates.add(ctor);
            }
        }
        if (candidates.size() != 1) {
            return new Result(
                State.UNEXPECTED_SHAPE,
                original,
                0,
                "found " + candidates
                    .size() + " constructor(s) that store " + FIELD + ": " + FIELD_DESC + " exactly once (expected 1)");
        }

        MethodNode ctor = candidates.get(0);
        AbstractInsnNode store = storeSite(ctor);
        AbstractInsnNode source = previousReal(store);
        if (source == null) {
            return new Result(
                State.UNEXPECTED_SHAPE,
                original,
                0,
                "the store into " + FIELD + " is the first instruction of the constructor");
        }
        if (source.getOpcode() == Opcodes.ICONST_0) {
            return new Result(State.ALREADY_PATCHED, original, 0, "");
        }
        int intSlot = intArgumentSlot(ctor);
        if (source.getOpcode() == Opcodes.ILOAD && ((VarInsnNode) source).var == intSlot) {
            // Replace the one load in place: nothing added, nothing retargeted, and
            // the stack still holds one int where it did before.
            ctor.instructions.set(source, new InsnNode(Opcodes.ICONST_0));
        } else {
            return new Result(
                State.UNEXPECTED_SHAPE,
                original,
                0,
                "the store into " + FIELD
                    + " takes its value from "
                    + describe(source)
                    + ", not the int argument (ILOAD "
                    + intSlot
                    + ")");
        }

        // COMPUTE_MAXS, never COMPUTE_FRAMES: no stack depth changes at any label,
        // so any frames read from a different GregTech build stay as they were.
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return new Result(
            State.PATCHED,
            writer.toByteArray(),
            SITE_COUNT,
            "the constructor's pollution store now writes 0");
    }

    /** Every {@code <init>} whose descriptor takes exactly two arguments and ends in an int. */
    static List<MethodNode> ctorsWithIntArgument(ClassNode node) {
        List<MethodNode> ctors = new ArrayList<MethodNode>();
        for (MethodNode method : node.methods) {
            if (!CTOR.equals(method.name)) {
                continue;
            }
            Type[] args = Type.getArgumentTypes(method.desc);
            if (args.length != 2) {
                continue;
            }
            if (args[1].getSort() != Type.INT) {
                continue;
            }
            ctors.add(method);
        }
        return ctors;
    }

    /** How many {@code PUTFIELD pollution: I} instructions the constructor has. */
    static int countStores(MethodNode method) {
        int found = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (isStore(insn)) {
                found++;
            }
        }
        return found;
    }

    /** The single {@code PUTFIELD pollution: I} of a constructor known to have one. */
    private static AbstractInsnNode storeSite(MethodNode method) {
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (isStore(insn)) {
                return insn;
            }
        }
        return null;
    }

    private static boolean isStore(AbstractInsnNode insn) {
        if (insn.getOpcode() != Opcodes.PUTFIELD) {
            return false;
        }
        FieldInsnNode store = (FieldInsnNode) insn;
        return TARGET_INTERNAL_NAME.equals(store.owner) && FIELD.equals(store.name) && FIELD_DESC.equals(store.desc);
    }

    /**
     * The local slot the constructor's {@code int} argument lives in: the object
     * takes slot 0 (the constructor is never static), the chunk takes one slot,
     * so the number is in slot 2.
     */
    static int intArgumentSlot(MethodNode ctor) {
        Type[] args = Type.getArgumentTypes(ctor.desc);
        int slot = (ctor.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
        for (int i = 0; i < args.length - 1; i++) {
            slot += args[i].getSize();
        }
        return slot;
    }

    /** The previous instruction that is not a label, a line number or a frame. */
    private static AbstractInsnNode previousReal(AbstractInsnNode from) {
        AbstractInsnNode previous = from.getPrevious();
        while (previous != null && previous.getOpcode() < 0) {
            previous = previous.getPrevious();
        }
        return previous;
    }

    /** One readable name for the fail-loud detail. */
    private static String describe(AbstractInsnNode insn) {
        int opcode = insn.getOpcode();
        if (insn instanceof VarInsnNode) {
            return opcodeName(opcode) + " " + ((VarInsnNode) insn).var;
        }
        return opcodeName(opcode);
    }

    /** A short name for the opcodes a pollution store could plausibly be fed by. */
    private static String opcodeName(int opcode) {
        switch (opcode) {
            case Opcodes.ICONST_M1:
                return "ICONST_M1";
            case Opcodes.ICONST_0:
                return "ICONST_0";
            case Opcodes.ILOAD:
                return "ILOAD";
            case Opcodes.ALOAD:
                return "ALOAD";
            case Opcodes.GETSTATIC:
                return "GETSTATIC";
            case Opcodes.GETFIELD:
                return "GETFIELD";
            case Opcodes.BIPUSH:
                return "BIPUSH";
            case Opcodes.SIPUSH:
                return "SIPUSH";
            case Opcodes.LDC:
                return "LDC";
            case Opcodes.IALOAD:
                return "IALOAD";
            case Opcodes.INVOKEVIRTUAL:
                return "INVOKEVIRTUAL";
            case Opcodes.INVOKESTATIC:
                return "INVOKESTATIC";
            default:
                return "opcode " + opcode;
        }
    }

    /** One line of the constructor's opcodes, for the fail-loud dump. */
    public static String dump(byte[] bytes) {
        try {
            ClassNode node = read(bytes);
            List<MethodNode> ctors = ctorsWithIntArgument(node);
            if (ctors.isEmpty()) {
                ctors = new ArrayList<MethodNode>();
                for (MethodNode method : node.methods) {
                    if (CTOR.equals(method.name)) {
                        ctors.add(method);
                    }
                }
            }
            StringBuilder text = new StringBuilder();
            for (MethodNode method : ctors) {
                text.append(method.name)
                    .append(method.desc)
                    .append(": ");
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn.getOpcode() < 0) {
                        continue;
                    }
                    text.append(insn.getOpcode())
                        .append(' ');
                }
            }
            return text.length() == 0 ? "no constructor found" : text.toString();
        } catch (RuntimeException unreadable) {
            return "the class bytes could not be read: " + unreadable;
        }
    }

    static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        // No flags: the StackMapTable frames must be kept, so the writer can move
        // them to their new offsets instead of recomputing (and possibly breaking)
        // them.
        new ClassReader(bytes).accept(node, 0);
        return node;
    }
}
