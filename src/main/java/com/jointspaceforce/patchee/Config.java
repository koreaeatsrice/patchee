package com.jointspaceforce.patchee;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraftforge.common.config.Configuration;

import com.jointspaceforce.patchee.core.Feature;
import com.jointspaceforce.patchee.core.FeatureRegistry;
import com.jointspaceforce.patchee.core.Toggles;

/**
 * Forge config holder, in the usual Forge style: one static
 * {@link Configuration} handed to the mod at preInit.
 *
 * <p>
 * The {@code general} category is generated from the {@link FeatureRegistry}:
 * every registered feature contributes its own boolean option, with the option
 * name, default value and plain-language comment it declares, plus the two
 * advanced options below. Adding a feature therefore adds its config option
 * automatically, with no edit here.
 *
 * <p>
 * The features themselves never read these statics. This class publishes an
 * immutable {@link Toggles} snapshot after every load, and the patch pipeline
 * receives that snapshot through the patch context.
 */
public class Config {

    private static final String CATEGORY = Configuration.CATEGORY_GENERAL;

    private static final String MASTER_COMMENT = "MASTER SWITCH for the whole mod. true = the features below work. false = the mod does "
        + "nothing at all. Every feature also has its own switch. After editing this file, "
        + "restart the server.";

    private static final String EXTRA_DOLLY_CLASSES_COMMENT = "Advanced: extra TileEntity class names (fully qualified) the JABBA Dolly should accept, "
        + "one per entry. Entries are ignored when the class is not a TileEntity, is "
        + "already blacklisted in Jabba's own config (exact class match), or does not exist.";

    private static final String RUN_SELF_TEST_COMMENT = "Advanced: run a one-shot check of every patched decision when the server starts, then "
        + "log PASS/FAIL per check. Switch it on for one server start after installing or "
        + "after a pack update, then set it back to false.";

    private static final String VEIN_BLOCK_LIMIT_COMMENT = "VeinConfig: the most blocks one vein may have (VeinMiner limit.blocks). "
        + "The default 64 keeps a vein small. Set with care.";

    private static final String VEIN_RADIUS_COMMENT = "VeinConfig: how far from the first block VeinMiner searches "
        + "(VeinMiner limit.radius). The default 20 is the whole reach of the mod.";

    private static final String VEIN_BLOCKS_COMMENT = "VeinConfig: the blocks that may be vein-mined, separated by commas. "
        + "Format is mod:block or mod:block/metadata (metadata = exact data value; no metadata = any). "
        + "Everything not listed here cannot be vein-mined. The default allows the three soft building "
        + "blocks: minecraft:sand/0,minecraft:sand/1,minecraft:clay,minecraft:gravel.";

    private static final String VEIN_EXTRA_TOOLS_COMMENT = "VeinConfig: extra item names (mod:item) to accept as shovels for vein mining, "
        + "separated by commas. Added on top of the built-in shovel list.";

    private static final String LIGHT_OVERLAY_NORMAL_COMMENT = "LightOverlay: the light level at which NEI's F7 overlay draws an X, in every dimension "
        + "except the Nether. 0 = only pitch-dark blocks get an X (the default). 7 = NEI's own default. "
        + "Client-side only: it changes anything only on a client that has Patchee installed.";

    private static final String LIGHT_OVERLAY_NETHER_COMMENT = "LightOverlay: the light level at which NEI's F7 overlay draws an X in the Nether. "
        + "7 = NEI's own default (unchanged).";

    public static boolean enabled = true;
    public static String[] extraDollyClasses = new String[0];
    public static boolean runSelfTest = false;
    public static int veinBlockLimit = 64;
    public static int veinRadius = 20;
    public static String veinBlocks = "minecraft:sand/0,minecraft:sand/1,minecraft:clay,minecraft:gravel";
    public static String veinExtraTools = "";
    public static int lightOverlayMaxNormal = 0;
    public static int lightOverlayMaxNether = 7;

    /** The snapshot the pipeline reads; replaced by every load. */
    private static Toggles toggles = new Snapshot(
        true,
        Collections.<String, Boolean>emptyMap(),
        false,
        new String[0],
        64,
        20,
        new String[] { "minecraft:sand/0", "minecraft:sand/1", "minecraft:clay", "minecraft:gravel" },
        new String[0],
        0,
        7);

    /** The immutable config snapshot the features read. */
    public static Toggles toggles() {
        return toggles;
    }

    public static void synchronizeConfiguration(File configFile, FeatureRegistry registry) {
        Configuration configuration = new Configuration(configFile);

        try {
            configuration.load();

            enabled = configuration.getBoolean("enabled", CATEGORY, enabled, MASTER_COMMENT);

            Map<String, Boolean> featureSwitches = new LinkedHashMap<String, Boolean>();
            for (Feature feature : registry.features()) {
                featureSwitches.put(
                    feature.id(),
                    configuration
                        .getBoolean(feature.configKey(), CATEGORY, feature.defaultEnabled(), feature.description()));
            }

            extraDollyClasses = configuration
                .getStringList("extraDollyClasses", CATEGORY, extraDollyClasses, EXTRA_DOLLY_CLASSES_COMMENT);

            runSelfTest = configuration.getBoolean("runSelfTest", CATEGORY, runSelfTest, RUN_SELF_TEST_COMMENT);

            veinBlockLimit = configuration
                .get(CATEGORY, "veinConfig.blockLimit", veinBlockLimit, VEIN_BLOCK_LIMIT_COMMENT)
                .getInt(veinBlockLimit);
            veinRadius = configuration.get(CATEGORY, "veinConfig.radius", veinRadius, VEIN_RADIUS_COMMENT)
                .getInt(veinRadius);
            veinBlocks = configuration.getString("veinConfig.blocks", CATEGORY, veinBlocks, VEIN_BLOCKS_COMMENT);
            veinExtraTools = configuration
                .getString("veinConfig.extraTools", CATEGORY, veinExtraTools, VEIN_EXTRA_TOOLS_COMMENT);

            lightOverlayMaxNormal = configuration
                .get(CATEGORY, "lightOverlay.maxLightNormal", lightOverlayMaxNormal, LIGHT_OVERLAY_NORMAL_COMMENT)
                .getInt(lightOverlayMaxNormal);
            lightOverlayMaxNether = configuration
                .get(CATEGORY, "lightOverlay.maxLightNether", lightOverlayMaxNether, LIGHT_OVERLAY_NETHER_COMMENT)
                .getInt(lightOverlayMaxNether);

            toggles = new Snapshot(
                enabled,
                featureSwitches,
                runSelfTest,
                extraDollyClasses.clone(),
                veinBlockLimit,
                veinRadius,
                splitList(veinBlocks),
                splitList(veinExtraTools),
                lightOverlayMaxNormal,
                lightOverlayMaxNether);
        } finally {
            if (configuration.hasChanged()) {
                configuration.save();
            }
        }
    }

    /** Immutable view of the switches, keyed by feature id. */
    private static final class Snapshot implements Toggles {

        private final boolean master;
        private final Map<String, Boolean> features;
        private final boolean selfTest;
        private final String[] extraDolly;
        private final int veinBlockLimit;
        private final int veinRadius;
        private final String[] veinBlocks;
        private final String[] veinExtraTools;
        private final int lightOverlayMaxNormal;
        private final int lightOverlayMaxNether;

        private Snapshot(boolean master, Map<String, Boolean> features, boolean selfTest, String[] extraDolly,
            int veinBlockLimit, int veinRadius, String[] veinBlocks, String[] veinExtraTools, int lightOverlayMaxNormal,
            int lightOverlayMaxNether) {
            this.master = master;
            this.features = Collections.unmodifiableMap(features);
            this.selfTest = selfTest;
            this.extraDolly = extraDolly;
            this.veinBlockLimit = veinBlockLimit;
            this.veinRadius = veinRadius;
            this.veinBlocks = veinBlocks;
            this.veinExtraTools = veinExtraTools;
            this.lightOverlayMaxNormal = lightOverlayMaxNormal;
            this.lightOverlayMaxNether = lightOverlayMaxNether;
        }

        @Override
        public boolean masterEnabled() {
            return master;
        }

        @Override
        public boolean featureEnabled(String featureId) {
            Boolean value = features.get(featureId);
            return value != null && value;
        }

        @Override
        public boolean runSelfTest() {
            return selfTest;
        }

        @Override
        public String[] extraDollyClasses() {
            return extraDolly.clone();
        }

        @Override
        public int veinBlockLimit() {
            return veinBlockLimit;
        }

        @Override
        public int veinRadius() {
            return veinRadius;
        }

        @Override
        public String[] veinBlocks() {
            return veinBlocks.clone();
        }

        @Override
        public String[] veinExtraTools() {
            return veinExtraTools.clone();
        }

        @Override
        public int lightOverlayMaxNormal() {
            return lightOverlayMaxNormal;
        }

        @Override
        public int lightOverlayMaxNether() {
            return lightOverlayMaxNether;
        }
    }

    /** A comma-separated config value as a trimmed list, blanks dropped. */
    private static String[] splitList(String value) {
        if (value == null || value.trim()
            .isEmpty()) {
            return new String[0];
        }
        List<String> parts = new ArrayList<String>();
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                parts.add(trimmed);
            }
        }
        return parts.toArray(new String[0]);
    }
}
