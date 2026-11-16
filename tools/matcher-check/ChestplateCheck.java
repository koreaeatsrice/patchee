import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import com.jointspaceforce.patchee.asm.ChestplateFlightTransformer;
import com.jointspaceforce.patchee.features.ChestplateFlightPatch;

/**
 * Runs Patchee's REAL {@link ChestplateFlightTransformer} over the REAL bytes of
 * the IC2 Quantum Chestplate that GTNH ships, and asserts the patch does what the
 * unit tests say it does on bytes that are not synthetic.
 *
 * <p>
 * Checks, all against {@code ic2/core/item/armor/ItemArmorQuantumSuit.class}:
 *
 * <ol>
 * <li>the shipped bytes are patched: exactly one {@code +0.0888889} (IC2's hover
 * horizontal speed) at the jump clamp, and exactly one exact negation of it
 * injected as a second store right after the stock sneak store, so rising and
 * sinking match; the stock {@code 0.1f} is gone but the stock {@code -0.1f} is
 * deliberately <b>kept</b> (value-matching mixins must keep their targets);
 * {@code -0.025f} untouched; and the {@code 0.6d} ascent ceiling still there
 * (this patch no longer writes it, and must never write {@code 0.6f});</li>
 * <li>the ground guard is injected as the first seven instructions (a hover-flag
 * {@code ILOAD 2} test, the hook call test, and the {@code return false}), followed
 * by the {@code F_SAME} frame at its join point;</li>
 * <li>nothing else in the class moved (every other method's opcode sequence is
 * identical, and the patched method grows by exactly fifteen instructions — guard
 * seven, hold six and the injected descent store two; the jump clamp rewrite
 * itself adds none);</li>
 * <li>a second pass is idempotent;</li>
 * <li>the Hodgepodge-simulated bytes ({@code -0.025f} already rewritten to
 * {@code 0.0f}) are still patched, and both edits survive — which is what makes
 * the two patches order-independent;</li>
 * <li>a non-target class of the same jar passes through byte-identical;</li>
 * <li>a class of any other shape is refused with a reason and returned
 * byte-identical.</li>
 * </ol>
 *
 * Build &amp; run: see this folder's README.md.
 */
public class ChestplateCheck {

    private static final String TARGET = ChestplateFlightPatch.TARGET_INTERNAL_NAME;
    private static final String NEIGHBOUR = "ic2/core/item/armor/ItemArmorJetpack";

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        String classRoot = args[0];
        String outDir = args.length > 1 ? args[1] : ".";

        byte[] stock = readTree(classRoot, TARGET);
        System.out.println("class root : " + classRoot);
        System.out.println("stock bytes: " + stock.length);

        // 1-3. the shipped bytes
        ChestplateFlightPatch.Result result = ChestplateFlightPatch.apply(stock, true, true, true);
        check(
            "shipped class patched",
            result.state() == ChestplateFlightPatch.State.PATCHED && result.sites() == 3,
            result.state() + " sites=" + result.sites() + " " + result.detail());

        MethodNode after = method(result.bytes());
        float ascent = ChestplateFlightPatch.HOVER_JUMP_LIFTED;
        float descent = ChestplateFlightPatch.SNEAK_LIFTED;
        check("exactly one +ascent hover constant", count(after, Float.valueOf(ascent)) == 1, floats(after));
        check(
            "exactly one exact -ascent hover constant (same magnitude)",
            count(after, Float.valueOf(descent)) == 1 && Float.compare(ascent, -descent) == 0,
            floats(after));
        check("the 0.1f stock climb clamp is gone", count(after, Float.valueOf(0.1f)) == 0, floats(after));
        check(
            "the stock -0.1f sink literal is still there exactly once (deliberately kept)",
            count(after, Float.valueOf(-0.1f)) == 1,
            floats(after));
        check(
            "the -ascent store sits immediately after the stock -0.1f store",
            Float.compare(descentAfterStock(after), descent) == 0,
            "injected=" + descentAfterStock(after));
        check("the -0.025f no-input hover clamp is untouched", count(after, Float.valueOf(-0.025f)) == 1, floats(after));
        check(
            "the 0.6d ascent ceiling is still there and no 0.6f was written",
            doubles(after).contains(Double.valueOf(ChestplateFlightPatch.ASCENT_CEILING_CONSTANT))
                && count(after, Float.valueOf(0.6f)) == 0,
            doubles(after) + " / " + floats(after));
        check("ground guard injected once", guardHooks(after) == 1, "hooks=" + guardHooks(after));
        check("shift+space target injected once", holdStores(after) == 1, "0.0f stores=" + holdStores(after));
        check(
            "guard starts with ILOAD of the hover flag and has its F_SAME frame",
            isHoverLoad(firstReal(after)) && hasFrameAtJoin(after),
            "first=" + firstOpcode(after) + " var=" + firstVar(after) + " instructions=" + realCount(after));
        check(
            "only the two branches and the descent store were added",
            realCount(after) == realCount(method(stock)) + 15,
            realCount(method(stock)) + " -> " + realCount(after));
        check("no other method moved", othersUnchanged(stock, result.bytes()), "compared every method");

        // 4. idempotence
        ChestplateFlightPatch.Result twice = ChestplateFlightPatch.apply(result.bytes(), true, true, true);
        check(
            "second pass is idempotent",
            twice.state() == ChestplateFlightPatch.State.ALREADY_PATCHED && twice.bytes() == result.bytes(),
            twice.state().toString());

        // 5. Hodgepodge ran first
        byte[] hodgepodged = rewrite(stock, Float.valueOf(-0.025f), Float.valueOf(0.0f));
        ChestplateFlightPatch.Result both = ChestplateFlightPatch.apply(hodgepodged, true, true, true);
        MethodNode bothMethod = method(both.bytes());
        check(
            "Hodgepodge-first bytes still patched",
            both.state() == ChestplateFlightPatch.State.PATCHED && count(bothMethod, Float.valueOf(ascent)) == 1
                && count(bothMethod, Float.valueOf(descent)) == 1,
            both.state() + " " + floats(bothMethod));
        check(
            "Hodgepodge's 0.0 default and our 0.0 hold target both survive",
            count(bothMethod, Float.valueOf(0.0f)) == 2 && count(bothMethod, Float.valueOf(-0.025f)) == 0,
            floats(bothMethod));

        // 6. a non-target class of the same jar
        byte[] neighbour = readTree(classRoot, NEIGHBOUR);
        byte[] neighbourOut = new ChestplateFlightTransformer().transform(NEIGHBOUR, NEIGHBOUR, neighbour);
        check("non-target class passes through untouched", Arrays.equals(neighbourOut, neighbour), NEIGHBOUR);

        // 7. the fail-loud path
        byte[] wrong = wrongShapeClass();
        ChestplateFlightPatch.Result refused = ChestplateFlightPatch.apply(wrong, true, true, true);
        byte[] wrongOut = new ChestplateFlightTransformer().transform(TARGET, TARGET, wrong);
        check(
            "wrong shape refused, bytes untouched",
            refused.state() == ChestplateFlightPatch.State.UNEXPECTED_SHAPE && refused.bytes() == wrong
                && Arrays.equals(wrongOut, wrong)
                && !refused.detail()
                    .isEmpty(),
            refused.detail());
        System.out.println("  reported        : " + refused.detail());
        System.out.println("  instruction dump: " + ChestplateFlightPatch.dump(wrong));

        // write the patched class out for javap
        Path out = Paths.get(outDir);
        Files.createDirectories(out);
        Files.write(out.resolve("ItemArmorQuantumSuit.patched.class"), result.bytes());
        Files.write(out.resolve("ItemArmorQuantumSuit.hodgepodge-first.patched.class"), both.bytes());
        System.out.println("wrote patched classes to " + out.toAbsolutePath());
        System.out.println("RESULT: " + passed + "/" + (passed + failed) + " PASS");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ---- checks ------------------------------------------------------------

    private static void check(String name, boolean ok, Object detail) {
        if (ok) {
            passed++;
            System.out.println("  PASS " + name + "  [" + detail + "]");
        } else {
            failed++;
            System.out.println("  FAIL " + name + "  [" + detail + "]");
        }
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static MethodNode method(byte[] bytes) {
        for (MethodNode method : read(bytes).methods) {
            if (ChestplateFlightPatch.METHOD_NAME.equals(method.name)
                && ChestplateFlightPatch.METHOD_DESC.equals(method.desc)) {
                return method;
            }
        }
        throw new IllegalStateException("no useJetpack in these bytes");
    }

    private static List<Float> floats(MethodNode method) {
        List<Float> out = new ArrayList<Float>();
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof Float) {
                out.add((Float) ((LdcInsnNode) insn).cst);
            }
        }
        return out;
    }

    private static List<Double> doubles(MethodNode method) {
        List<Double> out = new ArrayList<Double>();
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof Double) {
                out.add((Double) ((LdcInsnNode) insn).cst);
            }
        }
        return out;
    }

    private static int count(MethodNode method, Float value) {
        int n = 0;
        for (Float found : floats(method)) {
            if (found.equals(value)) {
                n++;
            }
        }
        return n;
    }

    /** The float stored right after the stock {@code -0.1f} store, or {@code NaN}. */
    private static float descentAfterStock(MethodNode method) {
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (!(insn instanceof LdcInsnNode) || !(((LdcInsnNode) insn).cst instanceof Float)) {
                continue;
            }
            if (((Float) ((LdcInsnNode) insn).cst).floatValue() != -0.1f) {
                continue;
            }
            AbstractInsnNode store = nextReal(insn.getNext());
            if (store == null || store.getOpcode() != Opcodes.FSTORE) {
                continue;
            }
            AbstractInsnNode value = nextReal(store.getNext());
            if (!(value instanceof LdcInsnNode) || !(((LdcInsnNode) value).cst instanceof Float)) {
                continue;
            }
            AbstractInsnNode again = nextReal(value.getNext());
            if (again != null && again.getOpcode() == Opcodes.FSTORE) {
                return ((Float) ((LdcInsnNode) value).cst).floatValue();
            }
        }
        return Float.NaN;
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode insn) {
        AbstractInsnNode cursor = insn;
        while (cursor != null && cursor.getOpcode() < 0) {
            cursor = cursor.getNext();
        }
        return cursor;
    }

    private static int guardHooks(MethodNode method) {
        int n = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) insn;
                if (ChestplateFlightPatch.HOOK_OWNER.equals(call.owner)
                    && ChestplateFlightPatch.GUARD_HOOK.equals(call.name)) {
                    n++;
                }
            }
        }
        return n;
    }

    private static int holdStores(MethodNode method) {
        int n = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (!(insn instanceof LdcInsnNode) || !(((LdcInsnNode) insn).cst instanceof Float)) {
                continue;
            }
            if (((Float) ((LdcInsnNode) insn).cst).floatValue() != 0.0f) {
                continue;
            }
            AbstractInsnNode next = insn.getNext();
            while (next != null && next.getOpcode() < 0) {
                next = next.getNext();
            }
            if (next != null && next.getOpcode() == Opcodes.FSTORE) {
                n++;
            }
        }
        return n;
    }

    private static AbstractInsnNode firstReal(MethodNode method) {
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn.getOpcode() >= 0) {
                return insn;
            }
        }
        return null;
    }

    /** The guard starts with {@code ILOAD 2}: {@code useJetpack} is an instance method, so this=0, player=1, hover=2. */
    private static boolean isHoverLoad(AbstractInsnNode insn) {
        return insn != null && insn.getOpcode() == Opcodes.ILOAD && insn instanceof VarInsnNode && ((VarInsnNode) insn).var == 2;
    }

    /** The var index of the first real instruction, or -1 when it is not a var load. */
    private static int firstVar(MethodNode method) {
        AbstractInsnNode insn = firstReal(method);
        return insn instanceof VarInsnNode ? ((VarInsnNode) insn).var : -1;
    }

    private static int firstOpcode(MethodNode method) {
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn.getOpcode() >= 0) {
                return insn.getOpcode();
            }
        }
        return -1;
    }

    private static boolean hasFrameAtJoin(MethodNode method) {
        boolean sawReturn = false;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (!sawReturn) {
                sawReturn = insn.getOpcode() == Opcodes.IRETURN;
                continue;
            }
            if (insn instanceof FrameNode && ((FrameNode) insn).type == Opcodes.F_SAME) {
                return true;
            }
        }
        return false;
    }

    private static int realCount(MethodNode method) {
        int n = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn.getOpcode() >= 0) {
                n++;
            }
        }
        return n;
    }

    private static boolean othersUnchanged(byte[] before, byte[] after) {
        ClassNode b = read(after);
        for (MethodNode method : read(before).methods) {
            if (method.name.equals(ChestplateFlightPatch.METHOD_NAME)) {
                continue;
            }
            MethodNode now = null;
            for (MethodNode candidate : b.methods) {
                if (candidate.name.equals(method.name) && candidate.desc.equals(method.desc)) {
                    now = candidate;
                    break;
                }
            }
            if (now == null || realCount(now) != realCount(method)) {
                return false;
            }
            for (int i = 0; i < method.instructions.size(); i++) {
                if (method.instructions.get(i)
                    .getOpcode() != now.instructions.get(i)
                        .getOpcode()) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Rewrites one float literal, standing in for Hodgepodge's @ModifyConstant. */
    private static byte[] rewrite(byte[] bytes, Float from, Float to) {
        ClassNode node = read(bytes);
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn instanceof LdcInsnNode && from.equals(((LdcInsnNode) insn).cst)) {
                    ((LdcInsnNode) insn).cst = to;
                }
            }
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] wrongShapeClass() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
        org.objectweb.asm.MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC,
            ChestplateFlightPatch.METHOD_NAME, ChestplateFlightPatch.METHOD_DESC, null, null);
        method.visitCode();
        method.visitInsn(Opcodes.ICONST_1);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    // ---- reading the jar ---------------------------------------------------

    private static byte[] readTree(String root, String internalName) throws IOException {
        File asDir = new File(root, internalName + ".class");
        if (asDir.isFile()) {
            return Files.readAllBytes(asDir.toPath());
        }
        File asJar = new File(root);
        try (ZipFile zip = new ZipFile(asJar)) {
            ZipEntry entry = zip.getEntry(internalName + ".class");
            InputStream in = zip.getInputStream(entry);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }
}
