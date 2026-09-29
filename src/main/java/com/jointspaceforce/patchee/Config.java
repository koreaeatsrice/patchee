package com.jointspaceforce.patchee;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
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

    public static boolean enabled = true;
    public static String[] extraDollyClasses = new String[0];
    public static boolean runSelfTest = false;

    /** The snapshot the pipeline reads; replaced by every load. */
    private static Toggles toggles = new Snapshot(true, Collections.<String, Boolean>emptyMap(), false, new String[0]);

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

            toggles = new Snapshot(enabled, featureSwitches, runSelfTest, extraDollyClasses.clone());
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

        private Snapshot(boolean master, Map<String, Boolean> features, boolean selfTest, String[] extraDolly) {
            this.master = master;
            this.features = Collections.unmodifiableMap(features);
            this.selfTest = selfTest;
            this.extraDolly = extraDolly;
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
    }
}
