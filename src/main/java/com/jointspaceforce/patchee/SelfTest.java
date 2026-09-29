package com.jointspaceforce.patchee;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityFurnace;

import com.jointspaceforce.patchee.fixes.DollyFix;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * One-shot behavioural self-check for both patches, run at server start when
 * {@code runSelfTest=true} in the config.
 *
 * <p>
 * This is deliberately stronger than checking that the patches "loaded": it
 * invokes the patched decisions with real instances and a control group —
 *
 * <ul>
 * <li>JABBA's pickup gate {@code ItemBarrelMover.isTEMovable(TileEntity)} must
 * return {@code true} for each installed bee housing, {@code false} for a
 * natural hive ({@code TileSwarm}) and for a plain furnace.</li>
 * <li>Tinkers' Construct's {@code Mattock.isEffective(Material)} must return
 * {@code true} for sand and for wood (its axe behaviour, control) and
 * {@code false} for stone (control).</li>
 * </ul>
 *
 * Every check fails soft: results are logged, nothing is thrown. Enable it for
 * one boot after an install or a pack update, then turn it off again.
 */
public final class SelfTest {

    private int passed;
    private int failed;

    private SelfTest() {}

    public static void run() {
        new SelfTest().runChecks();
    }

    private void runChecks() {
        Patchee.LOG.info("[SelfTest] running patched-decision checks (runSelfTest=true)");

        checkDollyGate();
        checkMattock();

        Patchee.LOG.info("[SelfTest] result: {} passed, {} failed", passed, failed);
    }

    // ---- 1. JABBA dolly gate ----------------------------------------------

    private void checkDollyGate() {
        List<Class<?>> movable;
        try {
            Class<?> moverClass = Class.forName("mcp.mobius.betterbarrels.common.items.dolly.ItemBarrelMover");
            Field field = moverClass.getDeclaredField("classExtensions");
            field.setAccessible(true);
            Object raw = field.get(null);
            if (!(raw instanceof List)) {
                failed++;
                Patchee.LOG.error("[SelfTest] FAIL dolly gate — classExtensions is not a List");
                return;
            }
            @SuppressWarnings("unchecked")
            List<Class<?>> cast = (List<Class<?>>) raw;
            movable = cast;
        } catch (ClassNotFoundException | LinkageError e) {
            Patchee.LOG.info("[SelfTest] SKIP dolly gate — JABBA is not installed ({})", e.toString());
            return;
        } catch (Exception e) {
            failed++;
            Patchee.LOG.error("[SelfTest] FAIL dolly gate setup crashed", e);
            return;
        }

        // NOTE: isTEMovable() itself is not invoked here. It is a private instance
        // method, and building its Method handle was observed to die on a
        // dedicated server: Class.getDeclaredMethod() resolves every declared
        // signature, reaches the client-only class
        // net.minecraft.client.renderer.texture.IIconRegister, and throws
        // NoClassDefFoundError (hit live during development). Instead, the
        // check below runs the gate's own final loop against JABBA's live
        // classExtensions list:
        // `for (Class c : classExtensions) if (c.isInstance(te))`.
        for (String name : DollyFix.beeHousingClassNames()) {
            Class<?> clazz;
            try {
                clazz = Class.forName(name);
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                Patchee.LOG.info("[SelfTest] SKIP {} — mod not installed", name);
                continue;
            }
            if (Modifier.isAbstract(clazz.getModifiers())) {
                Patchee.LOG.info("[SelfTest] SKIP {} — abstract base, covered by its subclasses", name);
                continue;
            }
            TileEntity instance;
            try {
                instance = (TileEntity) clazz.getDeclaredConstructor()
                    .newInstance();
            } catch (Exception | LinkageError e) {
                Patchee.LOG.warn("[SelfTest] SKIP {} — cannot instantiate: {}", name, e.toString());
                continue;
            }
            expectGate(movable, instance, true);
        }

        // controls that must stay refused
        expectGate(movable, forestrySwarmOrNull(), false);
        expectGate(movable, new TileEntityFurnace(), false);
    }

    private void expectGate(List<Class<?>> movable, TileEntity candidate, boolean expected) {
        if (candidate == null) return;
        String what = candidate.getClass()
            .getName();
        boolean accepted = false;
        for (Class<?> c : movable) {
            if (c != null && c.isInstance(candidate)) {
                accepted = true;
                break;
            }
        }
        if (accepted == expected) {
            passed++;
            Patchee.LOG.info("[SelfTest] PASS  dolly gate {} {}", expected ? "accepts" : "refuses", what);
        } else {
            failed++;
            Patchee.LOG.error(
                "[SelfTest] FAIL  dolly gate {} {} (expected to be {})",
                accepted ? "accepts" : "refuses",
                what,
                expected ? "accepted" : "refused");
        }
    }

    /** The natural, world-generated hive — must stay out of scope. */
    private static TileEntity forestrySwarmOrNull() {
        try {
            Class<?> swarm = Class.forName("forestry.apiculture.tiles.TileSwarm");
            return (TileEntity) swarm.getDeclaredConstructor()
                .newInstance();
        } catch (Exception | LinkageError e) {
            return null;
        }
    }

    // ---- 2. Tinkers' Construct mattock ------------------------------------

    private void checkMattock() {
        Item mattock = GameRegistry.findItem("TConstruct", "mattock");
        if (mattock == null) {
            Patchee.LOG.info("[SelfTest] SKIP mattock — Tinkers' Construct is not installed");
            return;
        }
        try {
            Method isEffective = mattock.getClass()
                .getMethod("isEffective", Material.class);
            // sand: the patched material; wood: axe control (must be true);
            // rock: control (must be false — it is in neither list)
            checkEffective(isEffective, mattock, Material.sand, "sand", true);
            checkEffective(isEffective, mattock, Material.wood, "wood", true);
            checkEffective(isEffective, mattock, Material.rock, "rock", false);
        } catch (Exception | LinkageError e) {
            failed++;
            Patchee.LOG.error("[SelfTest] FAIL mattock check crashed", e);
        }
    }

    private void checkEffective(Method isEffective, Item item, Material material, String label, boolean expected)
        throws Exception {
        boolean actual = (Boolean) isEffective.invoke(item, material);
        if (actual == expected) {
            passed++;
            Patchee.LOG.info("[SelfTest] PASS  mattock isEffective({}) = {}", label, actual);
        } else {
            failed++;
            Patchee.LOG.error("[SelfTest] FAIL  mattock isEffective({}) = {} (expected {})", label, actual, expected);
        }
    }
}
