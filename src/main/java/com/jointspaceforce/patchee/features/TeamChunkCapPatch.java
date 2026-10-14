package com.jointspaceforce.patchee.features;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * The team chunk-cap bytecode patch: two return sites in each of ServerUtilities'
 * two limit methods, clamped in place.
 *
 * <p>
 * In {@code serverutils.data.ServerUtilitiesTeamData} (ServerUtilities 2.4.9)
 * the two methods have the same shape:
 *
 * <pre>
 * getMaxClaimChunks():                         getMaxChunkloaderChunks():
 *   if (!ClaimedChunks.isActive()) return -1;    ... same, with
 *   if (!team.isValid())          return -2;     cachedMaxChunkloaderChunks
 *   if (cachedMaxClaimChunks &gt;= 0)                      and
 *       return cachedMaxClaimChunks;             serverutilities.chunkloader.max_chunks
 *   cachedMaxClaimChunks = 0;
 *   for (member : team.getMembers())
 *       cachedMaxClaimChunks += member.getRankConfig(...).getInt();
 *   return cachedMaxClaimChunks;
 * </pre>
 *
 * <p>
 * So the per-player limits are summed over the whole team, and the summed value
 * leaves the method through exactly two {@code GETFIELD cachedMax...Chunks: I}
 * followed by {@code IRETURN} sites (the cached early return and the final
 * return). Both are rewritten to call
 * {@link TeamChunkCapHooks#clampClaims(Object, int)} /
 * {@link TeamChunkCapHooks#clampLoads(Object, int)} on the way out. The
 * {@code -1}, {@code -2} and {@code 0} paths return their own constants and are
 * not touched at all.
 *
 * <p>
 * <b>Why a call is injected here.</b> Every other Patchee patch rewrites a value
 * in place with no call; this one cannot. The number to clamp exists only on the
 * operand stack at the moment of the {@code IRETURN}, and the JVM has no
 * "min" instruction: clamping it needs a call (or a hand-written branch
 * sequence, which is the same injected code with more ways to get it wrong).
 * Three instructions go in at each site — {@code ALOAD_0}, {@code SWAP},
 * {@code INVOKESTATIC} — because the {@code GETFIELD} that produced the number
 * already consumed the {@code this} it was loaded from, so the team data has to
 * be loaded again to be handed to the hook. The whole difference is those three
 * instructions: no method is added, no branch is retargeted, and the
 * {@code StackMapTable} frames read from the class stay exactly as they were,
 * because they sit between a {@code GETFIELD} and its {@code IRETURN} with no
 * label in between and leave the stack at the {@code IRETURN} exactly as they
 * found it (one int).
 *
 * <p>
 * It is deliberately suspicious: a class whose shape is anything other than the
 * two known sites per method, or which does not have both methods, is reported
 * and handed back completely untouched, and a class that already carries the
 * patch is recognised so a second pass changes nothing.
 */
public final class TeamChunkCapPatch {

    /** The class this patch knows, as the class loader names it. */
    public static final String TARGET_CLASS = "serverutils.data.ServerUtilitiesTeamData";

    /**
     * The same class in internal (slash) form, as the constant pool spells it — the
     * form the matcher-check harness reads its class entry as.
     */
    public static final String TARGET_INTERNAL_NAME = "serverutils/data/ServerUtilitiesTeamData";

    /** The Patchee class holding the injected methods. */
    public static final String HOOK_OWNER = "com/jointspaceforce/patchee/features/TeamChunkCapHooks";

    /** The injected method for the team claim limit. */
    public static final String CLAIM_HOOK = "clampClaims";

    /** The injected method for the team loaded-chunk limit. */
    public static final String LOAD_HOOK = "clampLoads";

    /**
     * {@code (Object data, int raw) -> int}. The data object is ServerUtilities'
     * own team data, passed so the sampled log can name the team; any type is
     * accepted because Patchee never links against ServerUtilities.
     */
    public static final String HOOK_DESC = "(Ljava/lang/Object;I)I";

    static final String CLAIM_METHOD = "getMaxClaimChunks";
    static final String LOAD_METHOD = "getMaxChunkloaderChunks";
    static final String METHOD_DESC = "()I";
    static final String CLAIM_FIELD = "cachedMaxClaimChunks";
    static final String LOAD_FIELD = "cachedMaxChunkloaderChunks";

    /** How many sites a healthy class has: two per method. */
    public static final int SITES_PER_METHOD = 2;

    /** How many sites in total, across both methods. */
    public static final int SITE_COUNT = SITES_PER_METHOD * 2;

    /** What {@link #apply} found and did. */
    public enum State {

        /** Every site rewritten; {@link Result#bytes()} is the patched class. */
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

        /** How many return sites were rewritten ({@link TeamChunkCapPatch#SITE_COUNT} on success). */
        public int sites() {
            return sites;
        }

        /** What went wrong, in one line, or the observed shape when nothing did. */
        public String detail() {
            return detail;
        }
    }

    private TeamChunkCapPatch() {}

    /**
     * Clamp ServerUtilities' two team-limit methods in place.
     *
     * @param original ServerUtilities' class bytes as the loader sees them
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

        if (alreadyPatched(node)) {
            return new Result(State.ALREADY_PATCHED, original, 0, "");
        }

        List<MethodNode> targets = new ArrayList<MethodNode>();
        for (MethodNode method : node.methods) {
            if ((CLAIM_METHOD.equals(method.name) || LOAD_METHOD.equals(method.name))
                && METHOD_DESC.equals(method.desc)) {
                targets.add(method);
            }
        }
        if (targets.size() != 2) {
            return new Result(
                State.UNEXPECTED_SHAPE,
                original,
                0,
                "found " + targets
                    .size() + " of the 2 expected methods (" + CLAIM_METHOD + "() and " + LOAD_METHOD + "())");
        }

        List<InsnNode> returns = new ArrayList<InsnNode>();
        for (MethodNode method : targets) {
            String field = CLAIM_METHOD.equals(method.name) ? CLAIM_FIELD : LOAD_FIELD;
            int found = countSites(method, field);
            if (found != SITES_PER_METHOD) {
                return new Result(
                    State.UNEXPECTED_SHAPE,
                    original,
                    0,
                    method.name + "() has "
                        + found
                        + " of the "
                        + SITES_PER_METHOD
                        + " expected return site(s)"
                        + " (GETFIELD "
                        + field
                        + ": I followed by IRETURN)");
            }
            collectReturnSites(method, field, returns);
        }
        if (returns.size() != SITE_COUNT) {
            return new Result(
                State.UNEXPECTED_SHAPE,
                original,
                0,
                "found " + returns.size() + " of the " + SITE_COUNT + " expected return sites");
        }

        // Insert last: every site is known good, so a half-patched class is impossible.
        int injected = 0;
        for (MethodNode method : targets) {
            String hook = CLAIM_METHOD.equals(method.name) ? CLAIM_HOOK : LOAD_HOOK;
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn.getOpcode() == Opcodes.IRETURN && returns.contains(insn)) {
                    // At this point the stack holds the number alone (the GETFIELD
                    // consumed the 'this' it was loaded from). Re-load 'this' and
                    // swap, so the hook can be handed the team data as well as the
                    // number: [int] -> [int, this] -> [this, int] -> [int].
                    InsnList inject = new InsnList();
                    inject.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    inject.add(new InsnNode(Opcodes.SWAP));
                    inject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK_OWNER, hook, HOOK_DESC, false));
                    method.instructions.insertBefore(insn, inject);
                    injected++;
                }
            }
        }
        if (injected != SITE_COUNT) {
            return new Result(
                State.UNEXPECTED_SHAPE,
                original,
                0,
                "injected " + injected + " of the " + SITE_COUNT + " expected call(s)");
        }

        // COMPUTE_MAXS, never COMPUTE_FRAMES: the frames read from ServerUtilities'
        // class stay exactly as they are (the injected instruction changes no
        // stack depth at any label) and ASM only moves them to their new offsets.
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return new Result(State.PATCHED, writer.toByteArray(), injected, "2 sites in each limit method");
    }

    /** True when the class already calls one of the injected methods. */
    static boolean alreadyPatched(ClassNode node) {
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn.getOpcode() != Opcodes.INVOKESTATIC) {
                    continue;
                }
                MethodInsnNode call = (MethodInsnNode) insn;
                if (HOOK_OWNER.equals(call.owner) && (CLAIM_HOOK.equals(call.name) || LOAD_HOOK.equals(call.name))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** How many {@code GETFIELD field: I} + {@code IRETURN} sites the method has. */
    static int countSites(MethodNode method, String field) {
        int found = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (isSite(method, insn, field)) {
                found++;
            }
        }
        return found;
    }

    private static void collectReturnSites(MethodNode method, String field, List<InsnNode> out) {
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (isSite(method, insn, field)) {
                out.add((InsnNode) nextReal(method.instructions, insn));
            }
        }
    }

    /** The field load followed by the return it feeds: the one shape this patch owns. */
    private static boolean isSite(MethodNode method, AbstractInsnNode insn, String field) {
        if (insn.getOpcode() != Opcodes.GETFIELD) {
            return false;
        }
        FieldInsnNode load = (FieldInsnNode) insn;
        if (!field.equals(load.name) || !"I".equals(load.desc)) {
            return false;
        }
        AbstractInsnNode next = nextReal(method.instructions, insn);
        return next != null && next.getOpcode() == Opcodes.IRETURN;
    }

    /** The next instruction that is not a label, a line number or a frame. */
    private static AbstractInsnNode nextReal(InsnList instructions, AbstractInsnNode from) {
        AbstractInsnNode next = from.getNext();
        while (next != null && next.getOpcode() < 0) {
            next = next.getNext();
        }
        return next;
    }

    /** One line per instruction of each target method, for the fail-loud dump. */
    public static String dump(byte[] bytes) {
        try {
            ClassNode node = read(bytes);
            StringBuilder text = new StringBuilder();
            for (MethodNode method : node.methods) {
                if (!CLAIM_METHOD.equals(method.name) && !LOAD_METHOD.equals(method.name)) {
                    continue;
                }
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
            return text.toString();
        } catch (RuntimeException unreadable) {
            return "the class bytes could not be read: " + unreadable;
        }
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        // No flags: the StackMapTable frames must be kept, so the writer can move
        // them to their new offsets instead of recomputing (and possibly breaking)
        // them.
        new ClassReader(bytes).accept(node, 0);
        return node;
    }
}
