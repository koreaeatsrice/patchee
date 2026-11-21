package com.jointspaceforce.patchee.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/**
 * Proves, against a real GregTech jar, the facts the Super Tank docs rest on:
 * GregTech's {@code ItemMachines.onUpdate} applies the four debuff potions, uses
 * the {@code 64000} mB / {@code mItemCount} carry rule, checks for the Super Tank
 * and Super Chest types, and uses the durations the docs quote.
 *
 * <p>
 * GregTech is not on the test classpath, so this reads the jar's class files
 * with ASM instead of loading them. Set {@code PATCHEE_GREGTECH_JAR} to a real
 * GregTech jar to run it; unset, the test is skipped with an assumption, so a
 * plain build never needs the jar.
 */
class SuperTankRealJarTest {

    /** Set to a real GregTech jar to run these checks; skipped otherwise. */
    private static final String REAL_JAR_ENV = "PATCHEE_GREGTECH_JAR";

    private static final String ITEM_MACHINES_CLASS = "gregtech/common/blocks/ItemMachines";

    private static final String ON_UPDATE_DESC = "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;IZ)V";

    private static final String POTION_DESC = "Lnet/minecraft/potion/Potion;";

    private static final String SUPER_TANK = "gregtech/common/tileentities/storage/MTESuperTank";
    private static final String SUPER_CHEST = "gregtech/common/tileentities/storage/MTESuperChest";

    /** GregTech applies four debuffs: hunger, slowness, mining fatigue, weakness. */
    private static final int DEBUFF_CALLS = 4;

    /** And loads four Potion constants to do it. */
    private static final int POTION_FIELDS = 4;

    /** The methods a 1.7.10 mob can be asked for a potion effect on, all living-base. */
    private static final Set<String> LIVING_BASE_RECEIVERS = new HashSet<String>(
        Arrays.asList(
            "net/minecraft/entity/EntityLivingBase",
            "net/minecraft/entity/player/EntityPlayer",
            "net/minecraft/entity/player/EntityPlayerMP"));

    @Test
    void theRealJarCarriesTheTankShapeTheFixDocuments() throws Exception {
        String jar = System.getenv(REAL_JAR_ENV);
        Assumptions.assumeTrue(
            jar != null && Files.isRegularFile(Paths.get(jar)),
            "set " + REAL_JAR_ENV + " to a real GregTech jar to run the Super Tank real-jar checks");

        MethodNode onUpdate = onUpdate(readClass(jar, ITEM_MACHINES_CLASS), jar);
        Facts facts = Facts.of(onUpdate);

        assertEquals(
            DEBUFF_CALLS,
            facts.potionCalls.size(),
            jar + ": "
                + ITEM_MACHINES_CLASS
                + ".onUpdate must call addPotionEffect "
                + DEBUFF_CALLS
                + " times, found "
                + facts.potionCalls.size()
                + " ("
                + facts.potionCalls
                + ")");
        for (String owner : facts.potionCalls) {
            assertTrue(
                LIVING_BASE_RECEIVERS.contains(owner),
                jar + ": onUpdate calls addPotionEffect on "
                    + owner
                    + " - expected EntityLivingBase or a subclass, so the fix can mirror it");
        }

        assertTrue(
            facts.ints.contains(Integer.valueOf(64000)),
            jar + ": "
                + ITEM_MACHINES_CLASS
                + ".onUpdate has no 64000 constant - the mB carry threshold "
                + "the fix and the docs name is gone");
        assertTrue(
            facts.ints.contains(Integer.valueOf(1200)),
            jar + ": "
                + ITEM_MACHINES_CLASS
                + ".onUpdate has no 1200 constant - a debuff duration the docs quote "
                + "is gone");
        assertTrue(
            facts.ints.contains(Integer.valueOf(12000)),
            jar + ": "
                + ITEM_MACHINES_CLASS
                + ".onUpdate has no 12000 constant - a debuff duration the docs quote "
                + "is gone");
        assertTrue(
            facts.doubles.contains(Double.valueOf(4096000.0)),
            jar + ": "
                + ITEM_MACHINES_CLASS
                + ".onUpdate has no 4096000.0 constant - a debuff duration the docs "
                + "quote is gone");
        assertTrue(
            facts.doubles.contains(Double.valueOf(2000000.0)),
            jar + ": "
                + ITEM_MACHINES_CLASS
                + ".onUpdate has no 2000000.0 constant - a debuff duration the docs "
                + "quote is gone");

        assertTrue(
            facts.strings.contains("mFluid"),
            jar + ": "
                + ITEM_MACHINES_CLASS
                + ".onUpdate never loads \"mFluid\" - the fluid carry rule the fix "
                + "mirrors is gone");
        assertTrue(
            facts.strings.contains("mItemCount"),
            jar + ": "
                + ITEM_MACHINES_CLASS
                + ".onUpdate never loads \"mItemCount\" - the item carry rule the fix "
                + "mirrors is gone");

        assertTrue(
            facts.instanceOf.contains(SUPER_TANK),
            jar + ": "
                + ITEM_MACHINES_CLASS
                + ".onUpdate has no instanceof "
                + SUPER_TANK
                + " - the fix's tank gate no longer matches GregTech");
        assertTrue(
            facts.instanceOf.contains(SUPER_CHEST),
            jar + ": "
                + ITEM_MACHINES_CLASS
                + ".onUpdate has no instanceof "
                + SUPER_CHEST
                + " - the fix's chest gate no longer matches GregTech");

        assertEquals(
            POTION_FIELDS,
            facts.potionFields.size(),
            jar + ": "
                + ITEM_MACHINES_CLASS
                + ".onUpdate must load "
                + POTION_FIELDS
                + " GETSTATIC "
                + POTION_DESC
                + " fields, found "
                + facts.potionFields.size()
                + " ("
                + facts.potionFields
                + ")");
    }

    /** The jar's {@code ItemMachines.onUpdate}, or a failure naming the jar and the missing method. */
    private static MethodNode onUpdate(ClassNode node, String jar) {
        for (MethodNode method : node.methods) {
            if ("onUpdate".equals(method.name) && ON_UPDATE_DESC.equals(method.desc)) {
                return method;
            }
        }
        return fail(
            jar + ": "
                + ITEM_MACHINES_CLASS
                + " has no onUpdate"
                + ON_UPDATE_DESC
                + " method - the Super Tank fix would find nothing to mirror");
    }

    /** The class node from the jar, reading code (only debug info is skipped). */
    private static ClassNode readClass(String jar, String internalName) throws IOException {
        try (ZipFile zip = new ZipFile(jar)) {
            ZipEntry entry = zip.getEntry(internalName + ".class");
            if (entry == null) {
                return fail(jar + ": no " + internalName + ".class - the Super Tank fix's class name is stale");
            }
            try (InputStream in = zip.getInputStream(entry)) {
                ClassNode node = new ClassNode();
                new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG);
                return node;
            }
        }
    }

    /** Everything the assertions read, collected in one pass over the method. */
    private static final class Facts {

        /** One entry per addPotionEffect call, holding the receiver's owner class. */
        final List<String> potionCalls = new ArrayList<String>();

        /** Integer constants from SIPUSH/BIPUSH/LDC and LDC'd longs that fit an int. */
        final Set<Integer> ints = new LinkedHashSet<Integer>();

        /** Double constants from LDC. */
        final Set<Double> doubles = new LinkedHashSet<Double>();

        /** String constants from LDC. */
        final Set<String> strings = new LinkedHashSet<String>();

        /** The type of every instanceof check. */
        final Set<String> instanceOf = new LinkedHashSet<String>();

        /** owner.name of every GETSTATIC whose type is a Potion. */
        final List<String> potionFields = new ArrayList<String>();

        static Facts of(MethodNode method) {
            Facts facts = new Facts();
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn instanceof IntInsnNode) {
                    facts.ints.add(Integer.valueOf(((IntInsnNode) insn).operand));
                } else if (insn instanceof LdcInsnNode) {
                    Object constant = ((LdcInsnNode) insn).cst;
                    if (constant instanceof Integer) {
                        facts.ints.add((Integer) constant);
                    } else if (constant instanceof Long) {
                        facts.ints.add(Integer.valueOf(((Long) constant).intValue()));
                    } else if (constant instanceof Double) {
                        facts.doubles.add((Double) constant);
                    } else if (constant instanceof String) {
                        facts.strings.add((String) constant);
                    }
                } else if (insn instanceof TypeInsnNode && insn.getOpcode() == Opcodes.INSTANCEOF) {
                    facts.instanceOf.add(((TypeInsnNode) insn).desc);
                } else if (insn instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) insn;
                    if ("addPotionEffect".equals(call.name)) {
                        facts.potionCalls.add(call.owner);
                    }
                } else if (insn instanceof FieldInsnNode) {
                    FieldInsnNode field = (FieldInsnNode) insn;
                    if (field.getOpcode() == Opcodes.GETSTATIC && POTION_DESC.equals(field.desc)) {
                        facts.potionFields.add(field.owner + "." + field.name);
                    }
                }
            }
            return facts;
        }
    }
}
