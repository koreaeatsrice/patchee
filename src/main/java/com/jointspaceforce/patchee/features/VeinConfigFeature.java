package com.jointspaceforce.patchee.features;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.Logger;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.jointspaceforce.patchee.core.Feature;
import com.jointspaceforce.patchee.core.Outcome;
import com.jointspaceforce.patchee.core.Step;
import com.jointspaceforce.patchee.core.Toggles;

import cpw.mods.fml.common.Loader;

/**
 * Keeps VeinMiner under server control.
 *
 * <p>
 * On every server start Patchee rewrites VeinMiner's two config files,
 * {@code config/veinminer/general.cfg} and
 * {@code config/veinminer/tools-and-blocks.json}, so that only the blocks listed
 * in {@code veinConfig.blocks} can be vein-mined, with the cap and radius from
 * {@code config/patchee.cfg}; block autodetect and the two "override" switches
 * are forced off. VeinMiner reads both files in its <b>preInit</b>, so the write
 * happens at Patchee's own preInit — FML runs every mod's preInit before any
 * postInit, and Patchee declares {@code before:VeinMiner}, so its preInit comes
 * first. This is why the feature is {@link Feature#early() early}.
 *
 * <p>
 * After the server has started, {@link #verify(Toggles)} reads VeinMiner's live
 * state back by <b>fields only</b> reflection (method reflection on a class with
 * client-only signatures throws {@code NoClassDefFoundError} on a dedicated
 * server — see the repo's self-test notes) and reports a
 * {@code [VeinConfig] verified: ...} PASS or a {@code MISMATCH} ERROR.
 *
 * <p>
 * VeinMiner may be absent (a pack without it is normal): the write then logs
 * {@code [VeinConfig] VeinMiner not installed — skipped} and the feature does
 * nothing else. It never bundles or edits VeinMiner's jar.
 */
public final class VeinConfigFeature implements Feature {

    public static final String ID = "VeinConfig";

    /** VeinMiner's mod id — shared by the preInit probe, the verification and the self-test. */
    public static final String VEINMINER_MODID = "VeinMiner";
    private static final String VEINMINER_CLASS = "portablejim.veinminer.VeinMiner";
    private static final String INSTANCE_FIELD = "instance";
    private static final String SETTINGS_FIELD = "configurationSettings";
    private static final String BLOCK_LIMIT_FIELD = "blockLimit";
    private static final String RADIUS_LIMIT_FIELD = "radiusLimit";
    private static final String TOOLS_AND_BLOCKS_FIELD = "toolsAndBlocks";
    private static final String TOOL_BLOCKLIST_FIELD = "blocklist";

    private static final String DIR_NAME = "veinminer";
    private static final String GENERAL_CFG = "general.cfg";
    private static final String TOOLS_AND_BLOCKS_JSON = "tools-and-blocks.json";

    /** The five tool types VeinMiner knows. Missing types would regain built-in defaults. */
    public static final String[] TOOL_TYPES = { "axe", "hoe", "pickaxe", "shears", "shovel" };

    /** Every type except the shovel must end up with an empty block list. */
    private static final String[] EMPTY_TYPES = { "axe", "hoe", "pickaxe", "shears" };

    private static final String[] VANILLA_AXES = { "minecraft:wooden_axe", "minecraft:stone_axe",
        "minecraft:golden_axe", "minecraft:iron_axe", "minecraft:diamond_axe" };
    private static final String[] VANILLA_HOES = { "minecraft:wooden_hoe", "minecraft:stone_hoe",
        "minecraft:golden_hoe", "minecraft:iron_hoe", "minecraft:diamond_hoe" };
    private static final String[] VANILLA_PICKAXES = { "minecraft:wooden_pickaxe", "minecraft:stone_pickaxe",
        "minecraft:golden_pickaxe", "minecraft:iron_pickaxe", "minecraft:diamond_pickaxe" };
    private static final String[] VANILLA_SHEARS = { "minecraft:shears" };

    /**
     * The built-in shovel tools plus the cross-mod shovels VeinMiner's own "Mod
     * Support" child mod adds at runtime when those mods are present (GT, TiC,
     * IC2, AE2, BoP). Listed here so the written file does not flicker between
     * boots.
     */
    private static final String[] SHOVEL_TOOLS = { "minecraft:wooden_shovel", "minecraft:stone_shovel",
        "minecraft:golden_shovel", "minecraft:iron_shovel", "minecraft:diamond_shovel", "gregtech:gt.metatool.01",
        "TConstruct:shovel", "TConstruct:mattock", "IC2:itemToolBronzeSpade",
        "appliedenergistics2:item.ToolCertusQuartzSpade", "appliedenergistics2:item.ToolNetherQuartzSpade",
        "BiomesOPlenty:shovelMud", "BiomesOPlenty:shovelAmethyst" };

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String configKey() {
        return "enableVeinConfig";
    }

    @Override
    public boolean defaultEnabled() {
        return true;
    }

    @Override
    public String description() {
        return "Feature: keep VeinMiner under server control. true = Patchee rewrites VeinMiner's config "
            + "files at every server start, so only the blocks in veinConfig.blocks can be vein-mined, "
            + "with the cap and radius set below and autodetect off. false = VeinMiner's own files are "
            + "left alone.";
    }

    @Override
    public String disabledMessage() {
        return "[VeinConfig] disabled in config — VeinMiner's own config files left alone";
    }

    @Override
    public String failureMessage() {
        return "[VeinConfig] failed — VeinMiner's config files not written";
    }

    @Override
    public String targetClass() {
        return null;
    }

    @Override
    public String missingTargetMessage() {
        return null;
    }

    /** VeinMiner reads its config in preInit, so this feature runs at Patchee's preInit. */
    @Override
    public boolean early() {
        return true;
    }

    @Override
    public List<Step> steps() {
        return Collections.singletonList(writeStep());
    }

    /** VeinMiner's config directory inside the game's {@code config/} directory. */
    public static File directory(File gameConfigDirectory) {
        return new File(gameConfigDirectory, DIR_NAME);
    }

    // ---- the write step ---------------------------------------------------

    /** Writes both of VeinMiner's config files, always, so a boot is idempotent. */
    private Step writeStep() {
        return ctx -> {
            if (!Loader.isModLoaded(VEINMINER_MODID)) {
                ctx.log()
                    .info("[VeinConfig] VeinMiner not installed — skipped");
                return Outcome.SKIPPED;
            }

            Toggles config = ctx.toggles();
            File dir = directory(ctx.configDirectory());
            if (!dir.isDirectory() && !dir.mkdirs()) {
                ctx.log()
                    .error("[VeinConfig] could not create {} — VeinMiner config not written", dir);
                return Outcome.FAILED;
            }

            String[] blocks = config.veinBlocks();
            try {
                writeUtf8(new File(dir, GENERAL_CFG), generalConfig(config.veinBlockLimit(), config.veinRadius()));
                writeUtf8(new File(dir, TOOLS_AND_BLOCKS_JSON), toolsAndBlocksJson(blocks, config.veinExtraTools()));
            } catch (IOException failure) {
                ctx.log()
                    .error("[VeinConfig] could not write VeinMiner config: {}", failure.toString());
                return Outcome.FAILED;
            }

            ctx.log()
                .info(
                    "[VeinConfig] wrote config/veinminer/general.cfg + tools-and-blocks.json "
                        + "(blocks={}, cap={}, radius={}, autodetect=off)",
                    blocks.length,
                    config.veinBlockLimit(),
                    config.veinRadius());
            return Outcome.APPLIED;
        };
    }

    /** The literal {@code general.cfg} body, exactly as the feature spec fixes it. */
    static String generalConfig(int blockLimit, int radius) {
        return "# VeinMiner server configuration -- MANAGED BY PATCHEE (VeinConfig feature).\n"
            + "# Patchee rewrites this file at every server boot from config/patchee.cfg.\n"
            + "# Do not edit here -- edit the veinConfig.* options in config/patchee.cfg instead.\n"
            + "\n"
            + "autodetect {\n"
            + "    B:autodetect.blocks.axe.enable=false\n"
            + "    B:autodetect.blocks.hoe.enable=false\n"
            + "    B:autodetect.blocks.pickaxe.enable=false\n"
            + "    B:autodetect.blocks.shears.enable=false\n"
            + "    B:autodetect.blocks.shovel.enable=false\n"
            + "    S:autodetect.blocks.axe.prefixes=log,treeLeaves\n"
            + "    S:autodetect.blocks.hoe.prefixes=\n"
            + "    S:autodetect.blocks.pickaxe.prefixes=ore\n"
            + "    S:autodetect.blocks.shears.prefixes=treeLeaves\n"
            + "    S:autodetect.blocks.shovel.prefixes=\n"
            + "}\n"
            + "\n"
            + "limit {\n"
            + "    I:limit.blocks="
            + blockLimit
            + "\n"
            + "    I:limit.radius="
            + radius
            + "\n"
            + "    I:limit.blocksPerTick=10\n"
            + "}\n"
            + "\n"
            + "misc {\n"
            + "    S:equalBlocks=minecraft:redstone_ore=minecraft:lit_redstone_ore\n"
            + "    I:hungermodifier=0\n"
            + "    I:expmodifier=0\n"
            + "}\n"
            + "\n"
            + "overrides {\n"
            + "    B:override.allBlocks=false\n"
            + "    B:override.allTools=false\n"
            + "}\n"
            + "\n"
            + "client {\n"
            + "    S:client.preferredMode=pressed\n"
            + "}\n";
    }

    /** The {@code tools-and-blocks.json} body: all five types, shovel = the configured blocks. */
    static String toolsAndBlocksJson(String[] blocks, String[] extraTools) {
        JsonObject tools = new JsonObject();
        tools.add("axe", tool("Axe", "minecraft:diamond_axe", VANILLA_AXES, new String[0]));
        tools.add("hoe", tool("Hoe", "minecraft:diamond_hoe", VANILLA_HOES, new String[0]));
        tools.add("pickaxe", tool("Pickaxe", "minecraft:diamond_pickaxe", VANILLA_PICKAXES, new String[0]));
        tools.add("shears", tool("Shears", "minecraft:shears", VANILLA_SHEARS, new String[0]));
        tools.add("shovel", tool("Shovel", "minecraft:diamond_shovel", shovelTools(extraTools), blocks));

        JsonObject root = new JsonObject();
        root.add("tools", tools);
        return new GsonBuilder().setPrettyPrinting()
            .disableHtmlEscaping()
            .create()
            .toJson(root);
    }

    private static JsonObject tool(String name, String icon, String[] toollist, String[] blocklist) {
        JsonObject json = new JsonObject();
        json.addProperty("name", name);
        json.addProperty("icon", icon);
        json.add("toollist", stringArray(toollist));
        json.add("blocklist", stringArray(blocklist));
        return json;
    }

    private static JsonArray stringArray(String[] values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(new JsonPrimitive(value));
        }
        return array;
    }

    /** The built-in shovel tools plus the configured extras, each once, in order. */
    private static String[] shovelTools(String[] extraTools) {
        Set<String> tools = new LinkedHashSet<String>(Arrays.asList(SHOVEL_TOOLS));
        for (String extra : extraTools) {
            if (extra != null && !extra.trim()
                .isEmpty()) {
                tools.add(extra.trim());
            }
        }
        return tools.toArray(new String[0]);
    }

    private static void writeUtf8(File file, String content) throws IOException {
        Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
        try {
            writer.write(content);
        } finally {
            writer.close();
        }
    }

    // ---- the read-only verification --------------------------------------

    /** Type -&gt; blocklist as written in a {@code tools-and-blocks.json} body. */
    public static Map<String, List<String>> blocklistsInJson(String json) {
        Map<String, List<String>> blocklists = new LinkedHashMap<String, List<String>>();
        JsonObject root = new JsonParser().parse(json)
            .getAsJsonObject();
        JsonObject tools = root.getAsJsonObject("tools");
        for (Map.Entry<String, JsonElement> entry : tools.entrySet()) {
            List<String> blocks = new ArrayList<String>();
            JsonArray list = entry.getValue()
                .getAsJsonObject()
                .getAsJsonArray("blocklist");
            if (list != null) {
                for (JsonElement each : list) {
                    blocks.add(each.getAsString());
                }
            }
            blocklists.put(entry.getKey(), blocks);
        }
        return blocklists;
    }

    /** The result of one read-only check against the live VeinMiner state. */
    public static final class Verification {

        private final boolean applicable;
        private final boolean pass;
        private final String detail;
        private final int blockLimit;
        private final int radius;
        private final List<String> blocks;

        private Verification(boolean applicable, boolean pass, String detail, int blockLimit, int radius,
            List<String> blocks) {
            this.applicable = applicable;
            this.pass = pass;
            this.detail = detail;
            this.blockLimit = blockLimit;
            this.radius = radius;
            this.blocks = blocks;
        }

        /** False when VeinMiner is not installed, so there is nothing to check. */
        public boolean applicable() {
            return applicable;
        }

        public boolean pass() {
            return pass;
        }

        /** The mismatch description, empty on PASS. */
        public String detail() {
            return detail;
        }

        public int blockLimit() {
            return blockLimit;
        }

        public int radius() {
            return radius;
        }

        public List<String> blocks() {
            return blocks;
        }
    }

    /**
     * Reads VeinMiner's live settings back by <b>fields only</b> reflection and
     * compares them with the config. Never throws; a shape change or a crash
     * comes back as a failing {@link Verification}.
     */
    public static Verification verify(Toggles config) {
        if (!Loader.isModLoaded(VEINMINER_MODID)) {
            return new Verification(
                false,
                true,
                "",
                config.veinBlockLimit(),
                config.veinRadius(),
                sorted(Arrays.asList(config.veinBlocks())));
        }

        int blockLimit = config.veinBlockLimit();
        int radius = config.veinRadius();
        List<String> shovelBlocks = sorted(Arrays.asList(config.veinBlocks()));
        try {
            Class<?> veinMiner = Class.forName(VEINMINER_CLASS);
            Object instance = readStatic(veinMiner, INSTANCE_FIELD);
            if (instance == null) {
                return new Verification(true, false, "VeinMiner.instance is null", blockLimit, radius, shovelBlocks);
            }
            Object settings = read(veinMiner, instance, SETTINGS_FIELD);
            if (settings == null) {
                return new Verification(true, false, "configurationSettings is null", blockLimit, radius, shovelBlocks);
            }

            Class<?> settingsClass = settings.getClass();
            blockLimit = readInt(settingsClass, settings, BLOCK_LIMIT_FIELD);
            radius = readInt(settingsClass, settings, RADIUS_LIMIT_FIELD);

            Object rawMap = read(settingsClass, settings, TOOLS_AND_BLOCKS_FIELD);
            if (!(rawMap instanceof Map)) {
                return new Verification(
                    true,
                    false,
                    TOOLS_AND_BLOCKS_FIELD + " is not a Map",
                    blockLimit,
                    radius,
                    shovelBlocks);
            }
            Map<?, ?> tools = (Map<?, ?>) rawMap;
            shovelBlocks = blockIds(tools.get("shovel"));

            List<String> problems = new ArrayList<String>();
            if (blockLimit != config.veinBlockLimit()) {
                problems.add("cap=" + blockLimit + " (expected " + config.veinBlockLimit() + ")");
            }
            if (radius != config.veinRadius()) {
                problems.add("radius=" + radius + " (expected " + config.veinRadius() + ")");
            }
            Set<String> expected = new LinkedHashSet<String>();
            for (String entry : config.veinBlocks()) {
                expected.add(wildcardNormalized(entry));
            }
            if (!new LinkedHashSet<String>(shovelBlocks).equals(expected)) {
                problems.add("shovel blocks=" + shovelBlocks + " (expected " + sorted(expected) + ")");
            }
            for (String type : EMPTY_TYPES) {
                List<String> found = blockIds(tools.get(type));
                if (!found.isEmpty()) {
                    problems.add(type + " blocklist=" + found + " (expected none)");
                }
            }

            if (problems.isEmpty()) {
                return new Verification(true, true, "", blockLimit, radius, shovelBlocks);
            }
            return new Verification(true, false, problems.toString(), blockLimit, radius, shovelBlocks);
        } catch (Throwable failure) {
            return new Verification(true, false, "verification crashed: " + failure, blockLimit, radius, shovelBlocks);
        }
    }

    /** Logs the one PASS or MISMATCH line for the live VeinMiner state. */
    public static void verifyAndLog(Logger log, Toggles config) {
        try {
            Verification result = verify(config);
            if (!result.applicable()) {
                log.info("[VeinConfig] verify skipped — VeinMiner not installed");
                return;
            }
            if (result.pass()) {
                log.info(
                    "[VeinConfig] verified: cap={} radius={} blocks={} autodetect=off",
                    result.blockLimit(),
                    result.radius(),
                    result.blocks());
            } else {
                log.error("[VeinConfig] MISMATCH: {}", result.detail());
            }
        } catch (Throwable failure) {
            log.error("[VeinConfig] MISMATCH: verification crashed: {}", failure.toString());
        }
    }

    private static Object readStatic(Class<?> owner, String name) throws Exception {
        return read(owner, null, name);
    }

    private static Object read(Class<?> owner, Object instance, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    private static int readInt(Class<?> owner, Object instance, String name) throws Exception {
        return ((Integer) read(owner, instance, name)).intValue();
    }

    /** Every {@code BlockID} of one tool's block list, as strings, sorted. */
    private static List<String> blockIds(Object tool) throws Exception {
        List<String> ids = new ArrayList<String>();
        if (tool == null) {
            return ids;
        }
        Object raw = read(tool.getClass(), tool, TOOL_BLOCKLIST_FIELD);
        if (raw instanceof Set) {
            for (Object each : (Set<?>) raw) {
                ids.add(String.valueOf(each));
            }
        }
        return sorted(ids);
    }

    /** {@code minecraft:sand/-1} and {@code minecraft:sand} are the same wildcard entry to VeinMiner. */
    private static String wildcardNormalized(String blockId) {
        return blockId.endsWith("/-1") ? blockId.substring(0, blockId.length() - 3) : blockId;
    }

    private static List<String> sorted(Collection<String> values) {
        List<String> out = new ArrayList<String>(values);
        Collections.sort(out);
        return out;
    }
}
