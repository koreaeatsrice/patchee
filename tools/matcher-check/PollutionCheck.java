import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodNode;

import com.jointspaceforce.patchee.asm.PollutionNoVisualsTransformer;
import com.jointspaceforce.patchee.features.PollutionNoVisualsPatch;

/**
 * Runs Patchee's REAL {@link PollutionNoVisualsTransformer} over the REAL bytes
 * of the GregTech jar the live server runs (5.09.54.265, GT5-Unofficial), and
 * asserts:
 *
 * <ul>
 * <li>{@code gregtech/api/net/GTPacketPollution.class} is patched, with exactly
 * {@link PollutionNoVisualsPatch#SITE_COUNT} constructor site changed;</li>
 * <li>the constructor's opcode sequence is the real one with the single
 * {@code ILOAD} of the int argument replaced by {@code ICONST_0} — same length,
 * one differing instruction, and no other change;</li>
 * <li>every other method's opcode sequence is identical;</li>
 * <li>a second pass is idempotent (already patched, bytes identical);</li>
 * <li>a non-target class of the same jar ({@code Pollution}) passes through
 * byte-identical;</li>
 * <li>a class whose shape is not the one this patch knows is reported and handed
 * back byte-identical (the fail-loud path).</li>
 * </ul>
 *
 * Build &amp; run: see this folder's README.md.
 */
public class PollutionCheck {

    private static final String TARGET = PollutionNoVisualsPatch.TARGET_CLASS;
    private static final String TARGET_ENTRY = PollutionNoVisualsPatch.TARGET_INTERNAL_NAME + ".class";
    private static final String NON_TARGET = "gregtech.common.pollution.Pollution";
    private static final String NON_TARGET_ENTRY = "gregtech/common/pollution/Pollution.class";

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        String gtJar = args[0];
        String outDir = args.length > 1 ? args[1] : ".";

        byte[] real = readEntry(gtJar, TARGET_ENTRY);
        System.out.println("GregTech jar: " + gtJar);
        System.out.println("class: " + TARGET_ENTRY + " (" + real.length + " bytes)");
        System.out.println("constructor before: " + ctorOpcodes(real));

        // 1. the real class is patched
        PollutionNoVisualsPatch.Result direct = PollutionNoVisualsPatch.apply(real);
        byte[] patched = new PollutionNoVisualsTransformer().transform(TARGET, TARGET, real);
        check(
            "real GregTech bytes patched",
            direct.state() == PollutionNoVisualsPatch.State.PATCHED
                && direct.sites() == PollutionNoVisualsPatch.SITE_COUNT
                && !Arrays.equals(patched, real),
            "state=" + direct.state() + " sites=" + direct.sites());
        System.out.println("constructor after:  " + ctorOpcodes(patched));

        // 2. the constructor is the real one with the argument load replaced by 0
        check("constructor replaces one ILOAD with ICONST_0", oneLoadBecomesZero(real, patched), changedDetail(real, patched));
        check("the store into pollution still follows it", storesZero(patched), storeDetail(patched));

        // 3. nothing else moved
        check(
            "every other method opcode-identical",
            otherMethodsUnchanged(real, patched),
            "methods compared: " + (read(real).methods.size() - 1) + " (all except <init>)");

        // 4. idempotence
        byte[] second = new PollutionNoVisualsTransformer().transform(TARGET, TARGET, patched);
        PollutionNoVisualsPatch.Result twice = PollutionNoVisualsPatch.apply(patched);
        check(
            "second pass is idempotent (already patched, unchanged)",
            Arrays.equals(second, patched) && twice.state() == PollutionNoVisualsPatch.State.ALREADY_PATCHED,
            "state=" + twice.state() + " unchanged=" + Arrays.equals(second, patched));

        // 5. a non-target class of the same jar is untouched
        byte[] other = readEntry(gtJar, NON_TARGET_ENTRY);
        byte[] otherOut = new PollutionNoVisualsTransformer().transform(NON_TARGET, NON_TARGET, other);
        check(
            "non-target class passes through untouched",
            Arrays.equals(otherOut, other) && otherOut == other,
            "unchanged=" + Arrays.equals(otherOut, other) + " (" + NON_TARGET_ENTRY + ")");

        // 6. the fail-loud path: a class of the wrong shape is reported and returned as-is
        byte[] wrongShape = wrongShapeClass();
        byte[] wrongOut = new PollutionNoVisualsTransformer().transform(TARGET, TARGET, wrongShape);
        PollutionNoVisualsPatch.Result wrong = PollutionNoVisualsPatch.apply(wrongShape);
        check(
            "wrong shape -> reported, bytes returned untouched",
            Arrays.equals(wrongOut, wrongShape)
                && wrong.state() == PollutionNoVisualsPatch.State.UNEXPECTED_SHAPE
                && wrong.detail()
                    .contains("not the int argument"),
            wrong.detail());

        // hand the bytes to javap for the report
        Files.write(Paths.get(outDir, "GTPacketPollution-patched.class"), patched);
        Files.write(Paths.get(outDir, "GTPacketPollution-original.class"), real);

        System.out.println("RESULT: " + passed + "/" + (passed + failed) + " PASS");
        System.exit(failed == 0 ? 0 : 1);
    }

    // ---- checks -----------------------------------------------------------

    /** True when the patched constructor is the real one with exactly one ILOAD replaced by ICONST_0. */
    private static boolean oneLoadBecomesZero(byte[] before, byte[] after) {
        List<Integer> a = ctorOpcodes(before);
        List<Integer> b = ctorOpcodes(after);
        if (a.size() != b.size()) {
            return false;
        }
        int changed = 0;
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i)
                .equals(b.get(i))) {
                if (a.get(i)
                    .intValue() != Opcodes.ILOAD
                    || b.get(i)
                        .intValue() != Opcodes.ICONST_0) {
                    return false;
                }
                changed++;
            }
        }
        return changed == PollutionNoVisualsPatch.SITE_COUNT;
    }

    /** The store into the pollution field is still there, just fed by ICONST_0. */
    private static boolean storesZero(byte[] bytes) {
        MethodNode ctor = ctorOf(bytes);
        AbstractInsnNode previous = null;
        int stores = 0;
        for (AbstractInsnNode insn : ctor.instructions.toArray()) {
            if (insn.getOpcode() < 0) {
                continue;
            }
            if (isStore(insn)) {
                stores++;
                if (previous == null || previous.getOpcode() != Opcodes.ICONST_0) {
                    return false;
                }
            }
            previous = insn;
        }
        return stores == PollutionNoVisualsPatch.SITE_COUNT;
    }

    private static String storeDetail(byte[] bytes) {
        MethodNode ctor = ctorOf(bytes);
        AbstractInsnNode previous = null;
        for (AbstractInsnNode insn : ctor.instructions.toArray()) {
            if (insn.getOpcode() >= 0) {
                if (isStore(insn)) {
                    return "PUTFIELD " + PollutionNoVisualsPatch.FIELD + " fed by "
                        + (previous == null ? "nothing" : opcodeText(previous));
                }
                previous = insn;
            }
        }
        return "no PUTFIELD " + PollutionNoVisualsPatch.FIELD + " found";
    }

    private static String changedDetail(byte[] before, byte[] after) {
        List<Integer> a = ctorOpcodes(before);
        List<Integer> b = ctorOpcodes(after);
        if (a.size() != b.size()) {
            return "length " + a.size() + " -> " + b.size();
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i)
                .equals(b.get(i))) {
                return "index " + i + ": " + a.get(i) + " -> " + b.get(i);
            }
        }
        return "no change";
    }

    /** Every method other than the constructor must have the same opcode sequence. */
    private static boolean otherMethodsUnchanged(byte[] before, byte[] after) {
        ClassNode a = read(before);
        ClassNode b = read(after);
        for (MethodNode original : a.methods) {
            if (PollutionNoVisualsPatch.CTOR.equals(original.name)) {
                continue;
            }
            MethodNode now = find(b, original.name, original.desc);
            if (now == null || !opcodes(original).equals(opcodes(now))) {
                return false;
            }
        }
        return true;
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

    private static MethodNode ctorOf(byte[] bytes) {
        for (MethodNode method : read(bytes).methods) {
            if (PollutionNoVisualsPatch.CTOR.equals(method.name)) {
                String desc = method.desc;
                if (desc.startsWith("(L") && desc.indexOf(';') > 0 && desc.endsWith(";I)V")) {
                    return method;
                }
            }
        }
        throw new IllegalStateException("no (..., int) constructor found");
    }

    private static List<Integer> ctorOpcodes(byte[] bytes) {
        return opcodes(ctorOf(bytes));
    }

    private static List<Integer> opcodes(MethodNode method) {
        List<Integer> ops = new ArrayList<Integer>();
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn.getOpcode() >= 0) {
                ops.add(Integer.valueOf(insn.getOpcode()));
            }
        }
        return ops;
    }

    private static String opcodeText(AbstractInsnNode insn) {
        if (insn instanceof FieldInsnNode) {
            FieldInsnNode field = (FieldInsnNode) insn;
            return insn.getOpcode() + ":" + field.owner + "." + field.name + ":" + field.desc;
        }
        return String.valueOf(insn.getOpcode());
    }

    private static boolean isStore(AbstractInsnNode insn) {
        if (insn.getOpcode() != Opcodes.PUTFIELD) {
            return false;
        }
        FieldInsnNode store = (FieldInsnNode) insn;
        return PollutionNoVisualsPatch.TARGET_INTERNAL_NAME.equals(store.owner)
            && PollutionNoVisualsPatch.FIELD.equals(store.name)
            && PollutionNoVisualsPatch.FIELD_DESC.equals(store.desc);
    }

    /** A class with the target's name whose pollution store is fed by ICONST_M1. */
    private static byte[] wrongShapeClass() {
        String owner = PollutionNoVisualsPatch.TARGET_INTERNAL_NAME;
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PRIVATE, PollutionNoVisualsPatch.FIELD, PollutionNoVisualsPatch.FIELD_DESC, null, null)
            .visitEnd();
        MethodVisitor ctor = writer.visitMethod(Opcodes.ACC_PUBLIC, PollutionNoVisualsPatch.CTOR, "(Ljava/lang/Object;I)V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", PollutionNoVisualsPatch.CTOR, "()V", false);
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitInsn(Opcodes.ICONST_M1);
        ctor.visitFieldInsn(Opcodes.PUTFIELD, owner, PollutionNoVisualsPatch.FIELD, PollutionNoVisualsPatch.FIELD_DESC);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();
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
