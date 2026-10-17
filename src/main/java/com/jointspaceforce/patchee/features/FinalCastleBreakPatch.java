package com.jointspaceforce.patchee.features;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * The FinalCastleBreak bytecode patch: one call site in the castle bricks and
 * two in the thorn wall, redirected to {@link FinalCastleBreakHooks}.
 *
 * <p>
 * In {@code twilightforest.block.BlockTFCastleBlock} (Twilight Forest 2.7.41,
 * the GTNH fork) {@code harvestBlock(...)}
 * ({@code (World, EntityPlayer, int, int, int, int) -> void}) drains the
 * player's pickaxe:
 *
 * <pre>
 * for (int i = 0; i &lt; 16 &amp;&amp; cei.stackSize &gt; 0; i++) {
 *     item.onBlockDestroyed(cei, entityplayer.worldObj, this, x, y, z, entityplayer);
 * }
 * </pre>
 *
 * so there is exactly one {@code INVOKEVIRTUAL net/minecraft/item/Item.onBlockDestroyed}
 * site with descriptor {@code (ItemStack, World, Block, int, int, int, EntityLivingBase) -&gt; boolean}.
 *
 * <p>
 * In {@code twilightforest.block.BlockTFThorns}, {@code removedByPlayer(...)}
 * ({@code (World, EntityPlayer, int, int, int) -> boolean}) grows the block back
 * and then bursts more thorns:
 *
 * <pre>
 * world.setBlock(x, y, z, this, (meta &amp; 12) | 1, 2); // grow back
 * this.doThornBurst(world, x, y, z, meta); // grow more
 * </pre>
 *
 * so there is exactly one {@code INVOKEVIRTUAL net/minecraft/world/World.setBlock}
 * site with descriptor {@code (int, int, int, Block, int, int) -> boolean} and
 * exactly one {@code INVOKESPECIAL doThornBurst} site with descriptor
 * {@code (World, int, int, int, int) -> void}.
 *
 * <p>
 * The patch is deliberately <b>name-agnostic</b>: this class is read from the
 * live SRG server jar and the MCP dev jar alike, and the two only differ in
 * <i>member</i> names ({@code func_150894_a} vs {@code onBlockDestroyed}), never
 * in class names or descriptors. A matcher that pinned the names would ship
 * inert on one of the two. So every site is found by opcode + descriptor + call
 * shape, and an exact site count is asserted; anything else is refused.
 *
 * <p>
 * <b>The rewrite.</b> For the castle site the receiver ({@code item}) is already
 * argument 0 of the call, so the {@code INVOKEVIRTUAL} becomes a bare
 * {@code INVOKESTATIC FinalCastleBreakHooks.onBlockDestroyed(...): Z} with the
 * same stack shape — no instruction is added.
 *
 * <p>
 * For the thorn set-block site the operand stack is
 * {@code [world, x, y, z, block, meta, flags]} (seven values), so the receiver
 * ({@code world}) becomes argument 0 and the {@code EntityPlayer} the hook also
 * needs is appended with one {@code ALOAD 2} (local 2 is the {@code player}
 * parameter). For the burst site the stack is
 * {@code [this, world, x, y, z, meta]} and the receiver ({@code this}, the
 * thorns block — needed so the hook can reach Twilight Forest's original burst)
 * becomes argument 0, with {@code ALOAD 2} appended the same way. Both
 * {@code INVOKESPECIAL}s become {@code INVOKESTATIC}s with the receiver as
 * argument 0.
 *
 * <p>
 * A class already carrying the patch is recognised so a second pass changes
 * nothing, and a class whose shape is anything else is handed back completely
 * untouched.
 */
public final class FinalCastleBreakPatch {

    /** The castle-brick class, as the class loader names it. */
    public static final String CASTLE_CLASS = "twilightforest.block.BlockTFCastleBlock";

    /** The thorn-wall class, as the class loader names it. */
    public static final String THORNS_CLASS = "twilightforest.block.BlockTFThorns";

    /** The Patchee class holding the injected methods. */
    public static final String HOOK_OWNER = "com/jointspaceforce/patchee/features/FinalCastleBreakHooks";

    /** The injected method for the castle-brick pickaxe damage. */
    public static final String CASTLE_HOOK = "onBlockDestroyed";

    /** The injected method for the thorn-wall set-block call. */
    public static final String SETBLOCK_HOOK = "setThornBlock";

    /** The injected method for the thorn-wall burst call. */
    public static final String BURST_HOOK = "thornBurst";

    /**
     * {@code (Item, ItemStack, World, Block, int, int, int, EntityLivingBase) -> boolean}.
     * The receiver of the original call is the first argument.
     */
    public static final String CASTLE_HOOK_DESC = "(Lnet/minecraft/item/Item;Lnet/minecraft/item/ItemStack;"
        + "Lnet/minecraft/world/World;Lnet/minecraft/block/Block;III"
        + "Lnet/minecraft/entity/EntityLivingBase;)Z";

    /**
     * {@code (World, int, int, int, Block, int, int, EntityPlayer) -> boolean}. The
     * trailing {@code int} is the original {@code flags} operand (Twilight Forest
     * passes {@code 2}), kept so the pass-through reproduces the stock call exactly.
     */
    public static final String SETBLOCK_HOOK_DESC = "(Lnet/minecraft/world/World;III"
        + "Lnet/minecraft/block/Block;IILnet/minecraft/entity/player/EntityPlayer;)Z";

    /**
     * {@code (Block, World, int, int, int, int, EntityPlayer) -> void}. The first
     * {@code Block} is the original receiver ({@code this}).
     */
    public static final String BURST_HOOK_DESC = "(Lnet/minecraft/block/Block;Lnet/minecraft/world/World;IIII"
        + "Lnet/minecraft/entity/player/EntityPlayer;)V";

    // ---- the shapes found in the input class (names deliberately not matched) ----

    /** The receiver of the castle damage call. */
    public static final String ITEM_OWNER = "net/minecraft/item/Item";

    /** The descriptor of the castle damage call. */
    public static final String CASTLE_CALL_DESC = "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;"
        + "Lnet/minecraft/block/Block;IIILnet/minecraft/entity/EntityLivingBase;)Z";

    /** The receiver of the thorn set-block call. */
    public static final String WORLD_OWNER = "net/minecraft/world/World";

    /** The descriptor of the thorn set-block call. */
    public static final String SETBLOCK_CALL_DESC = "(IIILnet/minecraft/block/Block;II)Z";

    /** The descriptor of the thorn burst call. */
    public static final String BURST_CALL_DESC = "(Lnet/minecraft/world/World;IIII)V";

    /** How many sites a healthy {@code BlockTFCastleBlock} has. */
    public static final int CASTLE_SITE_COUNT = 1;

    /** How many sites a healthy {@code BlockTFThorns} has. */
    public static final int THORNS_SITE_COUNT = 2;

    /** How many sites in total, across both classes. */
    public static final int SITE_COUNT = CASTLE_SITE_COUNT + THORNS_SITE_COUNT;

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

        /** How many call sites were rewritten this pass. */
        public int sites() {
            return sites;
        }

        /** What went wrong, in one line, or the observed shape when nothing did. */
        public String detail() {
            return detail;
        }
    }

    private FinalCastleBreakPatch() {}

    /**
     * Redirect Twilight Forest's castle / thorn call sites to
     * {@link FinalCastleBreakHooks}.
     *
     * @param original the class bytes as the loader sees them
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

        int castle = 0;
        List<MethodNode> burstMethods = new ArrayList<MethodNode>();
        for (MethodNode method : node.methods) {
            boolean burst = false;
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (isCastleCall(insn)) {
                    castle++;
                } else if (isBurstCall(insn, node.name)) {
                    burst = true;
                }
            }
            if (burst) {
                burstMethods.add(method);
            }
        }

        if (castle == CASTLE_SITE_COUNT && burstMethods.isEmpty()) {
            return patchCastle(node, original);
        }
        if (castle == 0 && burstMethods.size() == 1) {
            // The thorn burst lives in exactly one method (removedByPlayer); the
            // other World.setBlock in the class (growThorns) carries the same call
            // shape but is NOT ours to touch, so the sites are counted inside the
            // burst method only.
            MethodNode thorn = burstMethods.get(0);
            int setBlocks = countSites(thorn, FinalCastleBreakPatch::isSetBlockCall);
            int bursts = countSites(thorn, FinalCastleBreakPatch::isBurstCallShape);
            if (setBlocks == THORNS_SITE_COUNT / 2 && bursts == THORNS_SITE_COUNT / 2) {
                return patchThorns(node, original);
            }
            return new Result(
                State.UNEXPECTED_SHAPE,
                original,
                0,
                "the thorn burst method has " + setBlocks
                    + " World.setBlock and "
                    + bursts
                    + " doThornBurst site(s), expected exactly 1 of each");
        }
        return new Result(
            State.UNEXPECTED_SHAPE,
            original,
            0,
            "expected either " + CASTLE_SITE_COUNT
                + " Item.onBlockDestroyed site ("
                + CASTLE_CLASS
                + ") or 1 burst method carrying 1 World.setBlock + 1 doThornBurst ("
                + THORNS_CLASS
                + "); found "
                + castle
                + " castle site(s) and "
                + burstMethods.size()
                + " burst method(s)");
    }

    /** The castle class: re-point the one damage call at the hook. */
    private static Result patchCastle(ClassNode node, byte[] original) {
        int injected = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (isCastleCall(insn)) {
                    MethodInsnNode call = (MethodInsnNode) insn;
                    // The receiver ('item') is already argument 0: INVOKEVIRTUAL
                    // -> INVOKESTATIC, same stack shape, nothing to insert.
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = HOOK_OWNER;
                    call.name = CASTLE_HOOK;
                    call.desc = CASTLE_HOOK_DESC;
                    call.itf = false;
                    injected++;
                }
            }
        }
        if (injected != CASTLE_SITE_COUNT) {
            return new Result(
                State.UNEXPECTED_SHAPE,
                original,
                0,
                "injected " + injected + " of the " + CASTLE_SITE_COUNT + " expected castle call(s)");
        }
        return new Result(State.PATCHED, write(node), injected, CASTLE_SITE_COUNT + " site in " + CASTLE_CLASS);
    }

    /** The thorn class: re-point the grow-back and burst calls at the hooks (in the burst method only). */
    private static Result patchThorns(ClassNode node, byte[] original) {
        MethodNode thorn = null;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (isBurstCall(insn, node.name)) {
                    thorn = method;
                    break;
                }
            }
            if (thorn != null) {
                break;
            }
        }
        if (thorn == null) {
            return new Result(State.UNEXPECTED_SHAPE, original, 0, "the thorn burst method is missing");
        }
        int injected = 0;
        for (AbstractInsnNode insn : thorn.instructions.toArray()) {
            if (isSetBlockCall(insn)) {
                MethodInsnNode call = (MethodInsnNode) insn;
                // Stack is [world, x, y, z, block, meta, flags]; append the
                // player the hook needs, then INVOKESTATIC with 'world' as
                // argument 0.
                thorn.instructions.insertBefore(insn, new VarInsnNode(Opcodes.ALOAD, 2));
                call.setOpcode(Opcodes.INVOKESTATIC);
                call.owner = HOOK_OWNER;
                call.name = SETBLOCK_HOOK;
                call.desc = SETBLOCK_HOOK_DESC;
                call.itf = false;
                injected++;
            } else if (isBurstCallShape(insn)) {
                MethodInsnNode call = (MethodInsnNode) insn;
                // Stack is [this, world, x, y, z, meta]; append the player,
                // then INVOKESTATIC with the block ('this') as argument 0.
                thorn.instructions.insertBefore(insn, new VarInsnNode(Opcodes.ALOAD, 2));
                call.setOpcode(Opcodes.INVOKESTATIC);
                call.owner = HOOK_OWNER;
                call.name = BURST_HOOK;
                call.desc = BURST_HOOK_DESC;
                call.itf = false;
                injected++;
            }
        }
        if (injected != THORNS_SITE_COUNT) {
            return new Result(
                State.UNEXPECTED_SHAPE,
                original,
                0,
                "injected " + injected + " of the " + THORNS_SITE_COUNT + " expected thorn call(s)");
        }
        return new Result(State.PATCHED, write(node), injected, THORNS_SITE_COUNT + " sites in " + THORNS_CLASS);
    }

    /** True when the class already calls one of the injected methods. */
    static boolean alreadyPatched(ClassNode node) {
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn.getOpcode() != Opcodes.INVOKESTATIC) {
                    continue;
                }
                MethodInsnNode call = (MethodInsnNode) insn;
                if (HOOK_OWNER.equals(call.owner) && (CASTLE_HOOK.equals(call.name) || SETBLOCK_HOOK.equals(call.name)
                    || BURST_HOOK.equals(call.name))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isCastleCall(AbstractInsnNode insn) {
        if (insn.getOpcode() != Opcodes.INVOKEVIRTUAL) {
            return false;
        }
        MethodInsnNode call = (MethodInsnNode) insn;
        return ITEM_OWNER.equals(call.owner) && CASTLE_CALL_DESC.equals(call.desc);
    }

    private static boolean isSetBlockCall(AbstractInsnNode insn) {
        if (insn.getOpcode() != Opcodes.INVOKEVIRTUAL) {
            return false;
        }
        MethodInsnNode call = (MethodInsnNode) insn;
        return WORLD_OWNER.equals(call.owner) && SETBLOCK_CALL_DESC.equals(call.desc);
    }

    private static boolean isBurstCall(AbstractInsnNode insn, String owner) {
        if (insn.getOpcode() != Opcodes.INVOKESPECIAL) {
            return false;
        }
        MethodInsnNode call = (MethodInsnNode) insn;
        return owner.equals(call.owner) && BURST_CALL_DESC.equals(call.desc);
    }

    /** The burst call by shape alone (no owner), for counting inside the burst method. */
    private static boolean isBurstCallShape(AbstractInsnNode insn) {
        return insn.getOpcode() == Opcodes.INVOKESPECIAL && BURST_CALL_DESC.equals(((MethodInsnNode) insn).desc);
    }

    /** How many instructions in one method match {@code match}. */
    private static int countSites(MethodNode method, InsnMatch match) {
        int count = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (match.test(insn)) {
                count++;
            }
        }
        return count;
    }

    /** A one-instruction predicate, kept tiny so no lambda machinery is needed. */
    private interface InsnMatch {

        boolean test(AbstractInsnNode insn);
    }

    // COMPUTE_MAXS, never COMPUTE_FRAMES: the frames read from Twilight Forest's
    // class stay exactly as they are (the injected instructions change no stack
    // depth at any label) and ASM only moves them to their new offsets.
    private static byte[] write(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    /** One line per instruction of each method, for the fail-loud dump. */
    public static String dump(byte[] bytes) {
        try {
            ClassNode node = read(bytes);
            StringBuilder text = new StringBuilder();
            for (MethodNode method : node.methods) {
                text.append(method.name)
                    .append(method.desc)
                    .append(": ");
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn.getOpcode() < 0) {
                        continue;
                    }
                    text.append(opcodeText(insn))
                        .append(' ');
                }
            }
            return text.toString();
        } catch (RuntimeException unreadable) {
            return "the class bytes could not be read: " + unreadable;
        }
    }

    private static String opcodeText(AbstractInsnNode insn) {
        if (insn instanceof MethodInsnNode) {
            MethodInsnNode call = (MethodInsnNode) insn;
            return call.owner + "." + call.name + call.desc;
        }
        return String.valueOf(insn.getOpcode());
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
