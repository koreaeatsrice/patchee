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

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.stats.Achievement;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraftforge.common.config.Configuration;

import com.jointspaceforce.patchee.core.Toggles;
import com.jointspaceforce.patchee.features.ArcticFurBreakFeature;
import com.jointspaceforce.patchee.features.AuroraBreakFeature;
import com.jointspaceforce.patchee.features.DollyFeature;
import com.jointspaceforce.patchee.features.FinalCastleBreak;
import com.jointspaceforce.patchee.features.FinalCastleBreakFeature;
import com.jointspaceforce.patchee.features.TeamChunkCapFeature;
import com.jointspaceforce.patchee.features.VeinConfigFeature;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
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

    /**
     * Runs the checks once, on the first server tick.
     *
     * <p>
     * Not at {@code FMLServerStartedEvent} directly: some of the live state the
     * checks read is built by the mod being checked while the universe loads,
     * which happens at the same instant that event fires — ServerUtilities
     * creates its claimed-chunks state there, and the order of two mods' handlers
     * for one event is arbitrary. Waiting for the first tick means the world is
     * fully up, and every check sees the state a running server actually has.
     */
    public static void schedule(Toggles toggles, File configDirectory) {
        FMLCommonHandler.instance()
            .bus()
            .register(new FirstTick(toggles, configDirectory));
    }

    /** The one-shot listener that defers the checks to the first server tick. */
    public static final class FirstTick {

        private final Toggles toggles;
        private final File configDirectory;

        private FirstTick(Toggles toggles, File configDirectory) {
            this.toggles = toggles;
            this.configDirectory = configDirectory;
        }

        // public, and the class public: FML builds an ASM event handler in another
        // package, which cannot reach a private nested class.
        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.START) {
                return;
            }
            FMLCommonHandler.instance()
                .bus()
                .unregister(this);
            SelfTest.run(toggles, configDirectory);
        }
    }

    public static void run(Toggles toggles, File configDirectory) {
        new SelfTest().runChecks(toggles, configDirectory);
    }

    private void runChecks(Toggles toggles, File configDirectory) {
        Patchee.LOG.info("[SelfTest] running patched-decision checks (runSelfTest=true)");

        checkDollyGate();
        checkMattock();
        checkVeinConfig(toggles, configDirectory);
        checkAuroraBreak(toggles);
        checkArcticFurBreak(toggles);
        checkTeamChunkCap(toggles);
        checkFinalCastleBreak(toggles);

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

    // ---- 4. Twilight Forest aurora break speed (AuroraBreak) --------------

    private void checkAuroraBreak(Toggles toggles) {
        if (!toggles.featureEnabled(AuroraBreakFeature.ID)) {
            Patchee.LOG.info("[SelfTest] SKIP aurorabreak — feature disabled in config");
            return;
        }
        if (!Loader.isModLoaded("TwilightForest")) {
            Patchee.LOG.info("[SelfTest] SKIP aurorabreak — Twilight Forest is not installed");
            return;
        }
        try {
            Class<?> tfBlocks = Class.forName("twilightforest.block.TFBlocks");
            int found = 0;
            for (String name : AuroraBreakFeature.AURORA_FIELDS) {
                if (staticBlockOrNull(tfBlocks, name) != null) {
                    found++;
                }
            }
            Class<?> page = Class.forName("twilightforest.TFAchievementPage");
            Field field = page.getDeclaredField("twilightProgressGlacier");
            field.setAccessible(true);
            boolean gateFound = field.get(null) instanceof Achievement;
            check(
                "aurora break floor: " + found + "/4 blocks, Glacier achievement " + (gateFound ? "found" : "missing"),
                found == 4 && gateFound);
        } catch (Exception | LinkageError e) {
            Patchee.LOG.error("[SelfTest] FAIL aurora break floor setup crashed", e);
            failed++;
        }
    }

    // ---- 5. Twilight Forest arctic fur break speed (ArcticFurBreak) -------

    private void checkArcticFurBreak(Toggles toggles) {
        if (!toggles.featureEnabled(ArcticFurBreakFeature.ID)) {
            Patchee.LOG.info("[SelfTest] SKIP arcticfurbreak — feature disabled in config");
            return;
        }
        if (!Loader.isModLoaded("TwilightForest")) {
            Patchee.LOG.info("[SelfTest] SKIP arcticfurbreak — Twilight Forest is not installed");
            return;
        }
        try {
            Class<?> tfBlocks = Class.forName("twilightforest.block.TFBlocks");
            Block fur = staticBlockOrNull(tfBlocks, "arcticFurStorage");
            // The storage types are separate block instances, not metadata
            // variants: the field name is the arctic fur block and its class is
            // BlockTFCompressed. See ArcticFurBreakFeature for the evidence.
            check(
                "arctic fur break floor: arctic fur block found",
                fur != null && "twilightforest.block.BlockTFCompressed".equals(
                    fur.getClass()
                        .getName()));
        } catch (Exception | LinkageError e) {
            Patchee.LOG.error("[SelfTest] FAIL arctic fur break floor setup crashed", e);
            failed++;
        }
    }

    // ---- 6. FinalCastleBreak (Twilight Forest castle + thorn wall) --------

    /**
     * The castle/thorn feature: the three blocks and the Glacier achievement must
     * resolve, and the class-load behaviour patch must really have applied to
     * <b>both</b> target classes — a bare "the classes exist" probe is not enough,
     * because the transformer hands an unrecognised shape back untouched, and one
     * patched half must not stand in for the other. The break-speed handler is
     * registered by the same arm step the ARMED flag comes from.
     */
    private void checkFinalCastleBreak(Toggles toggles) {
        if (!toggles.featureEnabled(FinalCastleBreakFeature.ID)) {
            Patchee.LOG.info("[SelfTest] SKIP finalcastlebreak — feature disabled in config");
            return;
        }
        if (!Loader.isModLoaded("TwilightForest")) {
            Patchee.LOG.info("[SelfTest] SKIP finalcastlebreak — Twilight Forest is not installed");
            return;
        }
        try {
            Class<?> tfBlocks = Class.forName("twilightforest.block.TFBlocks");
            int found = 0;
            for (String name : FinalCastleBreakFeature.BLOCK_FIELDS) {
                if (staticBlockOrNull(tfBlocks, name) != null) {
                    found++;
                }
            }
            Class<?> page = Class.forName("twilightforest.TFAchievementPage");
            Field field = page.getDeclaredField("twilightProgressGlacier");
            field.setAccessible(true);
            boolean gateFound = field.get(null) instanceof Achievement;
            // Both halves are required: a boot where only one target class was
            // patched is not a working feature, so a bare patchApplied() would
            // hide the inert half.
            boolean castle = FinalCastleBreak.patchedCastle();
            boolean thorns = FinalCastleBreak.patchedThorns();
            check(
                "finalcastlebreak: " + found
                    + "/3 castle blocks, Glacier achievement "
                    + (gateFound ? "found" : "missing")
                    + ", class-load patch castle "
                    + (castle ? "applied" : "NOT applied")
                    + ", thorns "
                    + (thorns ? "applied" : "NOT applied"),
                found == 3 && gateFound && castle && thorns);
        } catch (Exception | LinkageError e) {
            Patchee.LOG.error("[SelfTest] FAIL finalcastlebreak setup crashed", e);
            failed++;
        }
    }

    // ---- 7. TeamChunkCap (ServerUtilities team chunk limits) --------------

    /** ServerUtilities' own team-limit class and the two per-rank permission nodes it sums. */
    private static final String SU_DATA_CLASS = "serverutils.data.ServerUtilitiesTeamData";
    private static final String SU_CLAIMS_NODE = "serverutilities.claims.max_chunks";
    private static final String SU_LOADS_NODE = "serverutilities.chunkloader.max_chunks";

    /** Enough members that their per-player limits sum past one member's own number. */
    private static final int CAP_TEST_MEMBERS = 16;

    /**
     * The team-limit rule, checked against ServerUtilities' own live objects: a
     * multi-member team must be answered its best member's own per-rank limit (not
     * the members' sum), a single-player team must be answered its own number, and
     * the same server-side code that refuses a claim must refuse claims past that
     * number — with the feature switched off, the raw per-member sum must come
     * back untouched.
     *
     * <p>
     * Everything is reached by reflection (Patchee keeps no dependency on
     * ServerUtilities), and only through fields and constructors where a method
     * lookup would resolve a signature list Patchee does not own.
     */
    private void checkTeamChunkCap(Toggles toggles) {
        if (FMLCommonHandler.instance()
            .getSide() != Side.SERVER) {
            Patchee.LOG.info("[SelfTest] SKIP teamchunkcap — server-side feature, this is a client");
            return;
        }
        if (!toggles.featureEnabled(TeamChunkCapFeature.ID)) {
            Patchee.LOG.info(
                "[SelfTest] teamchunkcap — feature disabled in config, so nothing applies; ServerUtilities' own totals stand (see the line below)");
        }
        Class<?> claimedChunks;
        try {
            claimedChunks = Class.forName("serverutils.data.ClaimedChunks");
        } catch (ClassNotFoundException | LinkageError absent) {
            Patchee.LOG.info("[SelfTest] SKIP teamchunkcap — ServerUtilities is not installed");
            return;
        }

        Object instance;
        try {
            instance = claimedChunks.getField("instance")
                .get(null);
        } catch (Throwable unreachable) {
            failed++;
            Patchee.LOG.error(
                "[SelfTest] FAIL teamchunkcap — could not reach ServerUtilities' claimed-chunks state",
                unreachable);
            return;
        }
        if (instance == null) {
            Patchee.LOG.info(
                "[SelfTest] SKIP teamchunkcap — chunk claiming is switched off on this server (ClaimedChunks.instance is null)");
            return;
        }

        try {
            Object universe = claimedChunks.getField("universe")
                .get(instance);
            Class<?> universeClass = universe.getClass();
            @SuppressWarnings("unchecked")
            Map<Object, Object> players = (Map<Object, Object>) universeClass.getField("players")
                .get(universe);

            Class<?> teamTypeClass = Class.forName("serverutils.lib.data.TeamType");
            Object serverType = enumValue(teamTypeClass, "SERVER_NO_SAVE");
            Class<?> teamClass = Class.forName("serverutils.lib.data.ForgeTeam");
            Class<?> playerClass = Class.forName("serverutils.lib.data.ForgePlayer");
            Class<?> dataClass = Class.forName(SU_DATA_CLASS);
            Class<?> posClass = Class.forName("serverutils.lib.math.ChunkDimPos");

            java.lang.reflect.Constructor<?> teamCtor = teamClass
                .getConstructor(universeClass, short.class, String.class, teamTypeClass);
            java.lang.reflect.Constructor<?> playerCtor = playerClass
                .getConstructor(universeClass, java.util.UUID.class, String.class);
            java.lang.reflect.Field playerTeam = playerClass.getField("team");
            java.lang.reflect.Field playerProfile = playerClass.getField("profile");
            java.lang.reflect.Method rankConfig = playerClass.getMethod("getRankConfig", String.class);
            java.lang.reflect.Method getData = dataClass.getMethod("get", teamClass);
            java.lang.reflect.Method maxClaim = dataClass.getMethod("getMaxClaimChunks");
            java.lang.reflect.Method maxLoad = dataClass.getMethod("getMaxChunkloaderChunks");
            java.lang.reflect.Constructor<?> posCtor = posClass.getConstructor(int.class, int.class, int.class);
            java.lang.reflect.Method claimChunk = claimedChunks
                .getMethod("claimChunk", playerClass, posClass, boolean.class);

            // a lone player, and a team big enough that ServerUtilities' per-member
            // sum is past one member's own number
            ForgeTeamProbe solo = team(
                teamClass,
                teamCtor,
                universe,
                serverType,
                "captest-solo",
                1,
                players,
                playerCtor,
                playerTeam,
                playerProfile);
            ForgeTeamProbe big = team(
                teamClass,
                teamCtor,
                universe,
                serverType,
                "captest-team",
                CAP_TEST_MEMBERS,
                players,
                playerCtor,
                playerTeam,
                playerProfile);

            int soloClaims = (Integer) maxClaim.invoke(getData.invoke(null, solo.team));
            int soloLoads = (Integer) maxLoad.invoke(getData.invoke(null, solo.team));
            int bigClaims = (Integer) maxClaim.invoke(getData.invoke(null, big.team));
            int bigLoads = (Integer) maxLoad.invoke(getData.invoke(null, big.team));

            int soloRawClaims = rankValue(rankConfig, solo.firstMember, SU_CLAIMS_NODE);
            int bigRawClaims = rankValue(rankConfig, big.firstMember, SU_CLAIMS_NODE) * CAP_TEST_MEMBERS;
            int bigRawLoads = rankValue(rankConfig, big.firstMember, SU_LOADS_NODE) * CAP_TEST_MEMBERS;
            // the rule's answer for the big team: its best member's own per-rank limit
            int bestClaims = rankValue(rankConfig, big.firstMember, SU_CLAIMS_NODE);
            int bestLoads = rankValue(rankConfig, big.firstMember, SU_LOADS_NODE);

            if (soloClaims < 0) {
                Patchee.LOG.info(
                    "[SelfTest] SKIP teamchunkcap — ServerUtilities answered {} (claimed chunks are not active in this world)",
                    soloClaims);
                return;
            }

            Patchee.LOG.info(
                "[SelfTest] teamchunkcap observed: 1-member team raw {} claims, {}-member team raw {} claims / {} loaded, best member {} / {}",
                soloRawClaims,
                CAP_TEST_MEMBERS,
                bigRawClaims,
                bigRawLoads,
                bestClaims,
                bestLoads);

            if (!toggles.featureEnabled(TeamChunkCapFeature.ID)) {
                // Switched off means the class was not patched at all: the raw,
                // per-member totals are what ServerUtilities serves.
                Patchee.LOG.info(
                    "[SelfTest] SKIP teamchunkcap checks — feature off: the {}-member team is served ServerUtilities' own {} claims / {} loaded (the sums stand)",
                    CAP_TEST_MEMBERS,
                    bigClaims,
                    bigLoads);
                return;
            }

            // (c) a solo team: the sum already is the member's own number
            check(
                "teamchunkcap solo team untouched: raw " + soloRawClaims + " claims -> " + soloClaims + " claims",
                soloClaims == soloRawClaims);
            check(
                "teamchunkcap solo team untouched: raw " + rankValue(rankConfig, solo.firstMember, SU_LOADS_NODE)
                    + " loaded -> "
                    + soloLoads,
                soloLoads == rankValue(rankConfig, solo.firstMember, SU_LOADS_NODE));

            // (b) the multi-member team is answered its best member's own number
            check(
                "teamchunkcap " + CAP_TEST_MEMBERS
                    + "-member team gets its best member: raw "
                    + bigRawClaims
                    + " claims -> "
                    + bigClaims
                    + " claims (best member "
                    + bestClaims
                    + ")",
                bigClaims == bestClaims);
            check(
                "teamchunkcap " + CAP_TEST_MEMBERS
                    + "-member team gets its best member: raw "
                    + bigRawLoads
                    + " loaded -> "
                    + bigLoads
                    + " loaded (best member "
                    + bestLoads
                    + ")",
                bigLoads == bestLoads);

            // (a) the server's own claim code refuses past the team's number
            ClaimSweep sweep = sweepClaims(claimChunk, posCtor, instance, big, bestClaims);
            if (sweep.dimensionBlocked) {
                Patchee.LOG.info(
                    "[SelfTest] SKIP teamchunkcap claim sweep — claiming is blocked in the dimensions this check tried");
            } else {
                check(
                    "teamchunkcap claim sweep: " + sweep.accepted
                        + " claim(s) accepted, then "
                        + sweep.refusal
                        + " (best member "
                        + bestClaims
                        + ")",
                    sweep.accepted == bestClaims && "NO_POWER".equals(sweep.refusal));
            }
        } catch (Exception | LinkageError broken) {
            failed++;
            Patchee.LOG.error("[SelfTest] FAIL teamchunkcap — the live check crashed", broken);
        }
    }

    /** One made team, with its members registered in ServerUtilities' player map. */
    private static final class ForgeTeamProbe {

        private final Object team;
        private final Object firstMember;

        private ForgeTeamProbe(Object team, Object firstMember) {
            this.team = team;
            this.firstMember = firstMember;
        }
    }

    /** What one claim sweep saw. */
    private static final class ClaimSweep {

        private int accepted;
        private String refusal = "none";
        private boolean dimensionBlocked;

        private ClaimSweep() {}
    }

    /** Builds a team of {@code members} plain (non-op) players. */
    private static ForgeTeamProbe team(Class<?> teamClass, java.lang.reflect.Constructor<?> teamCtor, Object universe,
        Object serverType, String name, int members, Map<Object, Object> players,
        java.lang.reflect.Constructor<?> playerCtor, java.lang.reflect.Field playerTeam,
        java.lang.reflect.Field playerProfile) throws Exception {
        short uid = (short) (20000 + Math.abs(name.hashCode() % 10000));
        Object team = teamCtor.newInstance(universe, uid, name, serverType);
        Object first = null;
        for (int i = 0; i < members; i++) {
            java.util.UUID id = java.util.UUID.randomUUID();
            Object player = playerCtor.newInstance(universe, id, name + "-m" + i);
            playerTeam.set(player, team);
            players.put(universeUuid(playerProfile, player), player);
            if (first == null) {
                first = player;
            }
        }
        return new ForgeTeamProbe(team, first);
    }

    /** The player's own UUID, so the player map key and the profile agree. */
    private static Object universeUuid(java.lang.reflect.Field playerProfile, Object player) throws Exception {
        Object profile = playerProfile.get(player);
        return Class.forName("com.mojang.authlib.GameProfile")
            .getMethod("getId")
            .invoke(profile);
    }

    /** A rank config value as an int, e.g. 100 for a plain player. */
    private static int rankValue(java.lang.reflect.Method rankConfig, Object player, String node) throws Exception {
        Object value = rankConfig.invoke(player, node);
        return (Integer) value.getClass()
            .getMethod("getInt")
            .invoke(value);
    }

    /**
     * Claims chunks with ServerUtilities' own method until it refuses, and reports
     * how many it accepted. Dimensions that are blocked are skipped, so the sweep
     * runs in a dimension where claiming is allowed.
     */
    private static ClaimSweep sweepClaims(java.lang.reflect.Method claimChunk, java.lang.reflect.Constructor<?> posCtor,
        Object instance, ForgeTeamProbe probe, int cap) throws Exception {
        ClaimSweep sweep = new ClaimSweep();
        int[] dims = { 0, 1, -1, 7 };
        Object dim = null;
        for (int candidate : dims) {
            Object pos = posCtor.newInstance(0, 0, candidate);
            Object result = claimChunk.invoke(instance, probe.firstMember, pos, Boolean.TRUE);
            if ("SUCCESS".equals(String.valueOf(result))) {
                sweep.accepted = 1;
                dim = candidate;
                break;
            }
            if (!"DIMENSION_BLOCKED".equals(String.valueOf(result))) {
                sweep.refusal = String.valueOf(result);
                return sweep;
            }
        }
        if (dim == null) {
            sweep.dimensionBlocked = true;
            return sweep;
        }
        int attempted = 0;
        // Keep going one attempt past the cap: the refusal is the point of the sweep.
        while (attempted < cap + 2) {
            attempted++;
            Object pos = posCtor.newInstance(attempted * 7, 100, (Integer) dim);
            Object result = claimChunk.invoke(instance, probe.firstMember, pos, Boolean.TRUE);
            String name = String.valueOf(result);
            if ("SUCCESS".equals(name)) {
                sweep.accepted++;
            } else {
                sweep.refusal = name;
                return sweep;
            }
        }
        return sweep;
    }

    /** One enum constant by name, or {@code null}. */
    private static Object enumValue(Class<?> type, String name) {
        for (Object constant : type.getEnumConstants()) {
            if (String.valueOf(constant)
                .equals(name)) {
                return constant;
            }
        }
        return null;
    }

    /** A declared static {@code Block} field of {@code owner}, or {@code null} when it is not one. */
    private static Block staticBlockOrNull(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(null);
            return value instanceof Block ? (Block) value : null;
        } catch (NoSuchFieldException | IllegalAccessException absent) {
            return null;
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
