package com.jointspaceforce.patchee;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraftforge.common.config.Configuration;

import com.jointspaceforce.patchee.core.Toggles;
import com.jointspaceforce.patchee.features.DollyFeature;
import com.jointspaceforce.patchee.features.VeinConfigFeature;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;

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

    public static void run(Toggles toggles, File configDirectory) {
        new SelfTest().runChecks(toggles, configDirectory);
    }

    private void runChecks(Toggles toggles, File configDirectory) {
        Patchee.LOG.info("[SelfTest] running patched-decision checks (runSelfTest=true)");

        checkDollyGate();
        checkMattock();
        checkVeinConfig(toggles, configDirectory);

        Patchee.LOG.info("[SelfTest] result: {} passed, {} failed", passed, failed);
    }

    /** One PASS/FAIL line, counted. */
    private void check(String what, boolean ok) {
        if (ok) {
            passed++;
            Patchee.LOG.info("[SelfTest] PASS  {}", what);
        } else {
            failed++;
            Patchee.LOG.error("[SelfTest] FAIL  {}", what);
        }
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
        for (String name : DollyFeature.beeHousingClassNames()) {
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

    // ---- 3. Patchee's VeinMiner config (VeinConfig) ------------------------

    /**
     * The VeinConfig files must have been written and must read back as
     * configured; when VeinMiner is present the live state must verify PASS, and
     * an ordinary ore must be vein-mineable with no tool type (negative
     * control). Server side only, because the files are only written there.
     */
    private void checkVeinConfig(Toggles toggles, File configDirectory) {
        if (FMLCommonHandler.instance()
            .getSide() != Side.SERVER) {
            Patchee.LOG.info("[SelfTest] SKIP veinconfig — server-side feature, this is a client");
            return;
        }
        if (!toggles.featureEnabled(VeinConfigFeature.ID)) {
            Patchee.LOG.info("[SelfTest] SKIP veinconfig — feature disabled in config");
            return;
        }
        if (!Loader.isModLoaded(VeinConfigFeature.VEINMINER_MODID)) {
            Patchee.LOG.info("[SelfTest] SKIP veinconfig — VeinMiner is not installed");
            return;
        }

        File dir = VeinConfigFeature.directory(configDirectory);
        File general = new File(dir, "general.cfg");
        File json = new File(dir, "tools-and-blocks.json");
        if (!general.isFile()) {
            check("veinconfig general.cfg written", false);
            return;
        }
        if (!json.isFile()) {
            check("veinconfig tools-and-blocks.json written", false);
            return;
        }

        try {
            Configuration generalCfg = new Configuration(general);
            generalCfg.load();
            int cap = generalCfg.get("limit", "limit.blocks", -1, "")
                .getInt(-1);
            int radius = generalCfg.get("limit", "limit.radius", -1, "")
                .getInt(-1);
            check("veinconfig general.cfg limit.blocks = " + cap, cap == toggles.veinBlockLimit());
            check("veinconfig general.cfg limit.radius = " + radius, radius == toggles.veinRadius());

            Map<String, List<String>> blocklists = VeinConfigFeature.blocklistsInJson(readUtf8(json));
            check(
                "veinconfig tools-and-blocks.json has all five tool types",
                blocklists.keySet()
                    .containsAll(Arrays.asList(VeinConfigFeature.TOOL_TYPES)));

            VeinConfigFeature.Verification live = VeinConfigFeature.verify(toggles);
            if (live.applicable()) {
                check("veinconfig live verification PASS", live.pass());
            } else {
                Patchee.LOG.info("[SelfTest] SKIP veinconfig — VeinMiner is not installed");
            }

            boolean oreFound = false;
            for (List<String> blocks : blocklists.values()) {
                if (blocks.contains("minecraft:iron_ore")) {
                    oreFound = true;
                    break;
                }
            }
            check("veinconfig minecraft:iron_ore is not vein-mineable", !oreFound);
        } catch (Exception | LinkageError e) {
            Patchee.LOG.error("[SelfTest] FAIL  veinconfig — could not read the written files", e);
            failed++;
        }
    }

    private static String readUtf8(File file) throws IOException {
        StringBuilder text = new StringBuilder();
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line)
                    .append('\n');
            }
        } finally {
            reader.close();
        }
        return text.toString();
    }
}
