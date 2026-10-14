import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import com.jointspaceforce.patchee.asm.TeamChunkCapTransformer;
import com.jointspaceforce.patchee.features.TeamChunkCapPatch;

/**
 * Runs Patchee's REAL {@link TeamChunkCapTransformer} over the REAL bytes of the
 * ServerUtilities jar the live server runs (2.4.9), and asserts:
 *
 * <ul>
 * <li>exactly {@code TeamChunkCapPatch.SITE_COUNT} return sites are rewritten in
 * {@code serverutils.data.ServerUtilitiesTeamData} (2 in each of
 * {@code getMaxClaimChunks()} and {@code getMaxChunkloaderChunks()});</li>
 * <li>the {@code -1} / {@code -2} sentinel returns and every other method are
 * untouched (opcode sequences compared before/after);</li>
 * <li>a second pass is idempotent (skip, bytes identical);</li>
 * <li>a non-target class of the same jar passes through byte-identical;</li>
 * <li>a class whose shape is not the one this patch knows is reported and handed
 * back byte-identical (the fail-loud path).</li>
 * </ul>
 *
 * Build &amp; run: see this folder's README.md.
 */
public class MatcherCheck {

    private static final String TARGET = TeamChunkCapPatch.TARGET_CLASS;
    private static final String TARGET_ENTRY = TeamChunkCapPatch.TARGET_INTERNAL_NAME + ".class";
    private static final String NON_TARGET = "serverutils.data.ClaimedChunks";
    private static final String NON_TARGET_ENTRY = "serverutils/data/ClaimedChunks.class";

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        String suJar = args[0];
        String outDir = args.length > 1 ? args[1] : ".";

        byte[] real = readEntry(suJar, TARGET_ENTRY);
        System.out.println("ServerUtilities jar: " + suJar);
        System.out.println("class: " + TARGET_ENTRY + " (" + real.length + " bytes)");

        // 1. the real class is patched, with the expected number of sites
        byte[] patched = new TeamChunkCapTransformer().transform(TARGET, TARGET, real);
        int sites = countHookCalls(patched);
        check(
            "real ServerUtilities bytes patched",
            sites == TeamChunkCapPatch.SITE_COUNT && !Arrays.equals(patched, real),
            "hook call sites=" + sites + " (expected " + TeamChunkCapPatch.SITE_COUNT + ")");

        // 2. nothing else moved: same fields, same opcode sequence everywhere, the
        //    sentinel returns still return -1 and -2
        check(
            "class shape (fields) unchanged",
            fieldsOf(real)
                .equals(fieldsOf(patched)),
            fieldsOf(patched)
                .toString());
        check(
            "every other method byte-for-byte identical",
            otherMethodsUnchanged(real, patched),
            "methods compared: " + methodNames(real));
        check(
            "-1 and -2 sentinel returns untouched",
            countPattern(patched, Opcodes.ICONST_M1, Opcodes.IRETURN) == countPattern(real, Opcodes.ICONST_M1, Opcodes.IRETURN)
                && countPattern(patched, Opcodes.BIPUSH, Opcodes.IRETURN)
                    == countPattern(real, Opcodes.BIPUSH, Opcodes.IRETURN),
            "iconst_m1+ireturn=" + countPattern(patched, Opcodes.ICONST_M1, Opcodes.IRETURN)
                + " (was " + countPattern(real, Opcodes.ICONST_M1, Opcodes.IRETURN) + ")"
                + ", bipush+ireturn=" + countPattern(patched, Opcodes.BIPUSH, Opcodes.IRETURN)
                + " (was " + countPattern(real, Opcodes.BIPUSH, Opcodes.IRETURN) + ")");
        check(
            "target methods differ only by the injected calls",
            targetMethodsUnchangedApartFromCalls(real, patched),
            "four ALOAD_0/SWAP/INVOKESTATIC triples to " + TeamChunkCapPatch.HOOK_OWNER);

        // 3. idempotence
        byte[] second = new TeamChunkCapTransformer().transform(TARGET, TARGET, patched);
        check(
            "second pass is idempotent (skip, unchanged)",
            Arrays.equals(second, patched) && countHookCalls(second) == TeamChunkCapPatch.SITE_COUNT,
            "unchanged=" + Arrays.equals(second, patched) + " sites=" + countHookCalls(second));

        // 4. a non-target class of the same jar is untouched
        byte[] other = readEntry(suJar, NON_TARGET_ENTRY);
        byte[] otherOut = new TeamChunkCapTransformer().transform(NON_TARGET, NON_TARGET, other);
        check(
            "non-target class passes through untouched",
            Arrays.equals(otherOut, other),
            "unchanged=" + Arrays.equals(otherOut, other) + " (" + NON_TARGET_ENTRY + ")");

        // 5. the fail-loud path: a class of the wrong shape is reported and returned as-is
        byte[] wrongShape = wrongShapeClass();
        byte[] wrongOut = new TeamChunkCapTransformer().transform(TARGET, TARGET, wrongShape);
        TeamChunkCapPatch.Result direct = TeamChunkCapPatch.apply(wrongShape);
        check(
            "wrong shape -> reported, bytes returned untouched",
            Arrays.equals(wrongOut, wrongShape)
                && direct.state() == TeamChunkCapPatch.State.UNEXPECTED_SHAPE
                && direct.detail()
                    .contains("expected return site"),
            direct.detail());

        // hand the patched bytes to javap for the report
        Files.write(Paths.get(outDir, "ServerUtilitiesTeamData-patched.class"), patched);
        Files.write(Paths.get(outDir, "ServerUtilitiesTeamData-original.class"), real);

        System.out.println("RESULT: " + passed + "/" + (passed + failed) + " PASS");
        System.exit(failed == 0 ? 0 : 1);
    }

    // ---- checks -----------------------------------------------------------

    /** Every method other than the two targets must have the same opcode sequence. */
    private static boolean otherMethodsUnchanged(byte[] before, byte[] after) {
        ClassNode a = read(before);
        ClassNode b = read(after);
        for (MethodNode original : a.methods) {
            if (isTarget(original)) {
                continue;
            }
            MethodNode now = find(b, original.name, original.desc);
            if (now == null || !opcodes(original).equals(opcodes(now))) {
                return false;
            }
        }
        return true;
    }

    /** The two target methods differ only by a call inserted between GETFIELD and IRETURN. */
    private static boolean targetMethodsUnchangedApartFromCalls(byte[] before, byte[] after) {
        ClassNode a = read(before);
        ClassNode b = read(after);
        for (MethodNode original : a.methods) {
            if (!isTarget(original)) {
                continue;
            }
            MethodNode now = find(b, original.name, original.desc);
            if (now == null) {
                return false;
            }
            List<String> originalOps = new ArrayList<String>();
            for (String op : opcodes(original)) {
                originalOps.add(op);
            }
            // The pattern is GETFIELD <field>: I, INVOKESTATIC, IRETURN where it was
            // GETFIELD <field>: I, IRETURN: drop the injected calls, then compare.
            List<AbstractInsnNode> patchedInsns = real(after, original.name, original.desc);
            int dropped = 0;
            List<String> rebuilt = new ArrayList<String>();
            for (int i = 0; i < patchedInsns.size(); i++) {
                AbstractInsnNode insn = patchedInsns.get(i);
                if (insn.getOpcode() == Opcodes.INVOKESTATIC && isHookCall(insn)) {
                    // the injected triple is ALOAD_0, SWAP, INVOKESTATIC: drop all three
                    if (rebuilt.size() >= 2
                        && "95".equals(rebuilt.get(rebuilt.size() - 1))
                        && "25".equals(rebuilt.get(rebuilt.size() - 2))) {
                        rebuilt.remove(rebuilt.size() - 1);
                        rebuilt.remove(rebuilt.size() - 1);
                        dropped++;
                        continue;
                    }
                    return false;
                }
                rebuilt.add(opcodeText(insn));
            }
            if (dropped != TeamChunkCapPatch.SITES_PER_METHOD || !rebuilt.equals(originalOps)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isTarget(MethodNode method) {
        return ("getMaxClaimChunks".equals(method.name) || "getMaxChunkloaderChunks".equals(method.name))
            && "()I".equals(method.desc);
    }

    private static boolean isHookCall(AbstractInsnNode insn) {
        MethodInsnNode call = (MethodInsnNode) insn;
        return TeamChunkCapPatch.HOOK_OWNER.equals(call.owner)
            && (TeamChunkCapPatch.CLAIM_HOOK.equals(call.name) || TeamChunkCapPatch.LOAD_HOOK.equals(call.name));
    }

    private static int countHookCalls(byte[] bytes) {
        int count = 0;
        for (MethodNode method : read(bytes).methods) {
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn.getOpcode() == Opcodes.INVOKESTATIC && isHookCall(insn)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int countPattern(byte[] bytes, int first, int second) {
        int count = 0;
        for (MethodNode method : read(bytes).methods) {
            List<AbstractInsnNode> insns = real(bytes, method.name, method.desc);
            for (int i = 0; i + 1 < insns.size(); i++) {
                if (insns.get(i)
                    .getOpcode() == first && insns.get(i + 1)
                        .getOpcode() == second) {
                    count++;
                }
            }
        }
        return count;
    }

    private static List<String> fieldsOf(byte[] bytes) {
        List<String> fields = new ArrayList<String>();
        for (FieldNode field : read(bytes).fields) {
            fields.add(field.access + " " + field.name + ":" + field.desc);
        }
        return fields;
    }

    private static List<String> methodNames(byte[] bytes) {
        List<String> names = new ArrayList<String>();
        for (MethodNode method : read(bytes).methods) {
            if (isTarget(method)) {
                names.add(method.name + method.desc);
            }
        }
        return names;
    }

    // ---- asm helpers ------------------------------------------------------

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
        if (insn instanceof org.objectweb.asm.tree.FieldInsnNode) {
            org.objectweb.asm.tree.FieldInsnNode field = (org.objectweb.asm.tree.FieldInsnNode) insn;
            return insn.getOpcode() + ":" + field.owner + "." + field.name + ":" + field.desc;
        }
        return String.valueOf(insn.getOpcode());
    }

    private static List<AbstractInsnNode> real(byte[] bytes, String name, String desc) {
        List<AbstractInsnNode> insns = new ArrayList<AbstractInsnNode>();
        MethodNode method = find(read(bytes), name, desc);
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn.getOpcode() >= 0) {
                insns.add(insn);
            }
        }
        return insns;
    }

    /** A class with the target's name but only one return site. */
    private static byte[] wrongShapeClass() {
        String owner = TeamChunkCapPatch.TARGET_INTERNAL_NAME;
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PRIVATE, "cachedMaxClaimChunks", "I", null, null)
            .visitEnd();
        writer.visitField(Opcodes.ACC_PRIVATE, "cachedMaxChunkloaderChunks", "I", null, null)
            .visitEnd();
        MethodVisitor claim = writer.visitMethod(Opcodes.ACC_PUBLIC, "getMaxClaimChunks", "()I", null, null);
        claim.visitCode();
        claim.visitVarInsn(Opcodes.ALOAD, 0);
        claim.visitFieldInsn(Opcodes.GETFIELD, owner, "cachedMaxClaimChunks", "I");
        claim.visitInsn(Opcodes.IRETURN);
        claim.visitMaxs(0, 0);
        claim.visitEnd();
        MethodVisitor load = writer.visitMethod(Opcodes.ACC_PUBLIC, "getMaxChunkloaderChunks", "()I", null, null);
        load.visitCode();
        load.visitVarInsn(Opcodes.ALOAD, 0);
        load.visitFieldInsn(Opcodes.GETFIELD, owner, "cachedMaxChunkloaderChunks", "I");
        Label l = new Label();
        load.visitJumpInsn(Opcodes.IFLT, l);
        load.visitVarInsn(Opcodes.ALOAD, 0);
        load.visitFieldInsn(Opcodes.GETFIELD, owner, "cachedMaxChunkloaderChunks", "I");
        load.visitInsn(Opcodes.IRETURN);
        load.visitLabel(l);
        load.visitFrame(Opcodes.F_NEW, 1, new Object[] { owner }, 0, null);
        load.visitVarInsn(Opcodes.ALOAD, 0);
        load.visitFieldInsn(Opcodes.GETFIELD, owner, "cachedMaxChunkloaderChunks", "I");
        load.visitInsn(Opcodes.IRETURN);
        load.visitMaxs(0, 0);
        load.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] readEntry(String jar, String name) throws Exception {
        try (ZipFile zip = new ZipFile(jar)) {
            return zip.getInputStream(zip.getEntry(name))
                .readAllBytes();
        }
    }

    private static void check(String label, boolean ok, String detail) {
        System.out.println((ok ? "PASS" : "FAIL") + " — " + label + " (" + detail + ")");
        if (ok) passed++;
        else failed++;
    }
}
