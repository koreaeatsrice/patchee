import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import com.jointspaceforce.patchee.asm.FinalCastleBreakTransformer;
import com.jointspaceforce.patchee.features.FinalCastleBreakPatch;

/**
 * Runs Patchee's REAL {@link FinalCastleBreakTransformer} over the REAL bytes of
 * the Twilight Forest classes it has to recognise, and asserts:
 *
 * <ul>
 * <li>the live server jar ({@code TwilightForest-2.7.40}, SRG member names) and
 * the 2.7.41 dev dependency (MCP member names) are BOTH patched: exactly
 * {@code FinalCastleBreakPatch.CASTLE_SITE_COUNT} site in
 * {@code BlockTFCastleBlock} and exactly
 * {@code FinalCastleBreakPatch.THORNS_SITE_COUNT} sites in {@code BlockTFThorns}
 * (nothing else in the class moves, and the second {@code World.setBlock} in
 * {@code growThorns} is left alone);</li>
 * <li>a second pass is idempotent (skip, bytes identical);</li>
 * <li>a non-target class of the same jar passes through byte-identical;</li>
 * <li>a class whose shape is not the one this patch knows is reported and handed
 * back byte-identical (the fail-loud path).</li>
 * </ul>
 *
 * Build &amp; run: see this folder's README.md.
 */
public class CastleCheck {

    private static final String CASTLE = FinalCastleBreakPatch.CASTLE_CLASS;
    private static final String THORNS = FinalCastleBreakPatch.THORNS_CLASS;
    private static final String MAGIC = "twilightforest.block.BlockTFCastleMagic";

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        String liveDir = args[0];
        String devDir = args[1];
        String outDir = args.length > 2 ? args[2] : ".";

        byte[] liveCastle = readTree(liveDir, CASTLE);
        byte[] liveThorns = readTree(liveDir, THORNS);
        byte[] liveMagic = readTree(liveDir, MAGIC);
        byte[] devCastle = readTree(devDir, CASTLE);
        byte[] devThorns = readTree(devDir, THORNS);

        System.out.println("live class dir : " + liveDir);
        System.out.println("dev class dir  : " + devDir);

        // 1. the live jar
        checkCastle("live BlockTFCastleBlock patched", liveCastle, outDir, "live");
        checkThorns("live BlockTFThorns patched", liveThorns, outDir, "live");
        // 2. the dev jar
        checkCastle("dev BlockTFCastleBlock patched", devCastle, outDir, "dev");
        checkThorns("dev BlockTFThorns patched", devThorns, outDir, "dev");

        // 3. a non-target class of the live jar is untouched
        byte[] magicOut = new FinalCastleBreakTransformer().transform(MAGIC, MAGIC, liveMagic);
        check(
            "non-target class passes through untouched",
            Arrays.equals(magicOut, liveMagic),
            "unchanged=" + Arrays.equals(magicOut, liveMagic) + " (" + MAGIC + ")");

        // 4. the fail-loud path: a class of the wrong shape is reported and returned as-is
        byte[] wrongShape = wrongShapeClass();
        byte[] wrongOut = new FinalCastleBreakTransformer().transform(THORNS, THORNS, wrongShape);
        FinalCastleBreakPatch.Result direct = FinalCastleBreakPatch.apply(wrongShape);
        check(
            "wrong shape -> reported, bytes returned untouched",
            Arrays.equals(wrongOut, wrongShape) && direct.state() == FinalCastleBreakPatch.State.UNEXPECTED_SHAPE
                && direct.detail()
                    .length() > 0,
            direct.detail());
        System.out.println("  reported    : " + direct.detail());
        System.out.println("  instruction dump: " + FinalCastleBreakPatch.dump(wrongShape));

        System.out.println("RESULT: " + passed + "/" + (passed + failed) + " PASS");
        System.exit(failed == 0 ? 0 : 1);
    }

    private static void checkCastle(String label, byte[] real, String outDir, String tag) throws Exception {
        byte[] patched = new FinalCastleBreakTransformer().transform(CASTLE, CASTLE, real);
        int hooks = countAll(patched, CastleCheck::isHookCall);
        int castles = countAll(patched, CastleCheck::isCastleCall);
        MethodNode before = methodWithCastle(real);
        MethodNode after = find(patched, before.name, before.desc);
        check(
            label,
            hooks == FinalCastleBreakPatch.CASTLE_SITE_COUNT && castles == 0
                && !Arrays.equals(patched, real)
                && realCount(before) == realCount(after)
                && otherMethodsUnchanged(real, patched),
            "hook sites=" + hooks + " (expected " + FinalCastleBreakPatch.CASTLE_SITE_COUNT + "), castle sites left="
                + castles + ", instruction count " + realCount(before) + "->" + realCount(after));
        // idempotence
        byte[] second = new FinalCastleBreakTransformer().transform(CASTLE, CASTLE, patched);
        check(
            label + " — second pass idempotent",
            Arrays.equals(second, patched),
            "unchanged=" + Arrays.equals(second, patched));
        Files.write(
            Paths.get(outDir, "BlockTFCastleBlock-" + tag + "-patched.class"),
            patched);
        Files.write(Paths.get(outDir, "BlockTFCastleBlock-" + tag + "-original.class"), real);
    }

    private static void checkThorns(String label, byte[] real, String outDir, String tag) throws Exception {
        byte[] patched = new FinalCastleBreakTransformer().transform(THORNS, THORNS, real);
        int hooks = countAll(patched, CastleCheck::isHookCall);
        MethodNode before = methodWithBurst(real);
        MethodNode after = find(patched, before.name, before.desc);
        int setLeft = countAll(patched, CastleCheck::isSetBlockCall);
        int burstLeft = countAll(patched, CastleCheck::isBurstCall);
        check(
            label,
            hooks == FinalCastleBreakPatch.THORNS_SITE_COUNT && setLeft == 1 && burstLeft == 0
                && !Arrays.equals(patched, real)
                && realCount(before) + 2 == realCount(after)
                && otherMethodsUnchanged(real, patched),
            "hook sites=" + hooks + " (expected " + FinalCastleBreakPatch.THORNS_SITE_COUNT + "), remaining "
                + "setBlock=" + setLeft + " (the untouched growThorns one) burst=" + burstLeft + ", burst method "
                + realCount(before) + "->" + realCount(after) + " instructions");
        // idempotence
        byte[] second = new FinalCastleBreakTransformer().transform(THORNS, THORNS, patched);
        check(
            label + " — second pass idempotent",
            Arrays.equals(second, patched),
            "unchanged=" + Arrays.equals(second, patched));
        Files.write(Paths.get(outDir, "BlockTFThorns-" + tag + "-patched.class"), patched);
        Files.write(Paths.get(outDir, "BlockTFThorns-" + tag + "-original.class"), real);
    }

    // ---- checks -----------------------------------------------------------

    /** Every method other than the one owned shape must have the same opcode sequence. */
    private static boolean otherMethodsUnchanged(byte[] before, byte[] after) {
        ClassNode a = read(before);
        ClassNode b = read(after);
        Set<String> targets = targetKeys(before);
        for (MethodNode original : a.methods) {
            if (targets.contains(original.name + original.desc)) {
                continue;
            }
            MethodNode now = findNode(b, original.name, original.desc);
            if (now == null || !opcodes(original).equals(opcodes(now))) {
                return false;
            }
        }
        return true;
    }

    private static Set<String> targetKeys(byte[] bytes) {
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

    private static MethodNode methodWithCastle(byte[] bytes) {
        for (MethodNode method : read(bytes).methods) {
            if (countMatching(method, CastleCheck::isCastleCall) == 1) {
                return method;
            }
        }
        throw new IllegalStateException("no castle site");
    }

    private static MethodNode methodWithBurst(byte[] bytes) {
        for (MethodNode method : read(bytes).methods) {
            if (countMatching(method, CastleCheck::isBurstCall) == 1) {
                return method;
            }
        }
        throw new IllegalStateException("no burst site");
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
        return insn.getOpcode() == Opcodes.INVOKESPECIAL
            && FinalCastleBreakPatch.BURST_CALL_DESC.equals(((MethodInsnNode) insn).desc);
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

    private static int countAll(byte[] bytes, Match match) {
        int count = 0;
        for (MethodNode method : read(bytes).methods) {
            count += countMatching(method, match);
        }
        return count;
    }

    private static int countMatching(MethodNode method, Match match) {
        int count = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (match.test(insn)) {
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
        return String.valueOf(insn.getOpcode());
    }

    // ---- asm helpers ------------------------------------------------------

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static MethodNode find(byte[] bytes, String name, String desc) {
        return findNode(read(bytes), name, desc);
    }

    private static MethodNode findNode(ClassNode node, String name, String desc) {
        for (MethodNode method : node.methods) {
            if (method.name.equals(name) && method.desc.equals(desc)) {
                return method;
            }
        }
        return null;
    }

    private static byte[] readTree(String root, String className) throws Exception {
        String simple = className.substring(className.lastIndexOf('.') + 1);
        String entry = className.replace('.', '/') + ".class";
        File file = new File(root);
        // A jar: read the class straight out of the archive.
        if (file.isFile()) {
            try (ZipFile zip = new ZipFile(file)) {
                ZipEntry zipEntry = zip.getEntry(entry);
                if (zipEntry == null) {
                    throw new IllegalArgumentException("no " + entry + " in " + root);
                }
                try (InputStream in = zip.getInputStream(zipEntry)) {
                    return readAll(in);
                }
            }
        }
        // A directory: accept the flat layout (live/BlockX.class) and the
        // package-nested one (tree/twilightforest/block/BlockX.class) alike.
        File flat = new File(root, simple + ".class");
        File nested = new File(root, entry);
        File chosen = flat.isFile() ? flat : nested;
        try (InputStream in = new FileInputStream(chosen)) {
            return readAll(in);
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    /** A class carrying the thorn name but none of the shapes the patch owns. */
    private static byte[] wrongShapeClass() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, THORNS.replace('.', '/'), null, "java/lang/Object", null);
        writer.visitMethod(Opcodes.ACC_PUBLIC, "removedByPlayer", "(Lnet/minecraft/world/World;)Z", null, null)
            .visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private interface Match {

        boolean test(AbstractInsnNode insn);
    }

    private static void check(String label, boolean ok, String detail) {
        System.out.println((ok ? "PASS" : "FAIL") + " — " + label + " (" + detail + ")");
        if (ok) {
            passed++;
        } else {
            failed++;
        }
    }
}
