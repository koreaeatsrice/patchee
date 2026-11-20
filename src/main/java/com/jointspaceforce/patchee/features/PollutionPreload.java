package com.jointspaceforce.patchee.features;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Loader;

/**
 * The coremod (pre-load) half of PollutionNoNegatives.
 *
 * <p>
 * GregTech registers its configs in its coremod's static initializer, which FML
 * runs before any coremod's {@code injectData}, so a rewrite from here always
 * lands after GregTech has already read the file: the new values take effect on
 * the next boot. The postInit memory pass is what fixes the current one.
 *
 * <p>
 * Even so the file half runs from {@link com.jointspaceforce.patchee.asm.FMLPlugin#injectData},
 * as early as a coremod can run: {@code config/GregTech/Pollution.cfg} gets
 * {@code Activate Pollution=true} and the four effect limits set to 2147483647,
 * so every later boot reads the right values from disk.
 *
 * <p>
 * {@code config/GregTech/Client.cfg} is rewritten only when it already exists,
 * so the client render flags are off when GregTech next reads them; the
 * in-memory flags cover the current session, and a server-only install creates
 * nothing.
 *
 * <p>
 * Everything is fail-soft: a read-only file or directory just logs, and the
 * in-memory step at postInit still forces the same values on the live fields.
 */
public final class PollutionPreload {

    private static final String ID = "[PollutionNoNegatives]";

    private static final Logger LOG = LogManager.getLogger("patchee");

    /** GregTech's mod id, as registered with FML. */
    private static final String GT_MODID = "gregtech";

    private PollutionPreload() {}

    /**
     * Rewrites what GregTech is about to read. Called from the coremod, before
     * mod construction; never throws.
     */
    public static void run(File gameDirectory) {
        try {
            File gameDir = gameDirectory == null ? new File(".") : gameDirectory;
            File configDir = new File(gameDir, "config");

            PollutionSwitches switches = PollutionSwitches.read(new File(configDir, "patchee.cfg"));
            if (!switches.masterEnabled() || !switches.featureEnabled()) {
                LOG.info("{} disabled in config - GregTech's pollution files left as they ship", ID);
                return;
            }

            if (!gregTechPresent(gameDir, configDir)) {
                LOG.info("{} GregTech is not installed - skipped", ID);
                return;
            }

            String timestamp = timestamp();

            File pollution = new File(configDir, PollutionCfg.POLLUTION_CFG);
            logOutcome(
                PollutionCfg.POLLUTION_CFG,
                PollutionCfgWriter.rewrite(pollution, PollutionCfg.SERVER_KEYS, timestamp),
                timestamp);

            File client = new File(configDir, PollutionCfg.CLIENT_CFG);
            if (client.exists()) {
                logOutcome(
                    PollutionCfg.CLIENT_CFG,
                    PollutionCfgWriter.rewrite(client, PollutionCfg.CLIENT_KEYS, timestamp),
                    timestamp);
            }
        } catch (Throwable failure) {
            LOG.warn(
                "{} preload pass crashed ({} ) - GregTech's pollution files left as they are",
                ID,
                failure.toString());
        }
    }

    /**
     * Is GregTech here? FML fills its mod-id table later, in
     * {@code Loader.loadMods}, so {@link Loader#isModLoaded(String)} answers
     * {@code false} for every mod while a coremod's {@code injectData} runs. The
     * mod jar in {@code mods/} and GregTech's config folder are the signals that
     * work this early; with neither, there is nothing to rewrite on this boot
     * anyway.
     */
    private static boolean gregTechPresent(File gameDir, File configDir) {
        if (Loader.isModLoaded(GT_MODID) || new File(configDir, "GregTech").isDirectory()) {
            return true;
        }
        File[] jars = new File(gameDir, "mods").listFiles();
        if (jars == null) {
            return false;
        }
        for (File jar : jars) {
            if (jar.getName()
                .toLowerCase(Locale.ROOT)
                .contains("gregtech")) {
                return true;
            }
        }
        return false;
    }

    /** One honest line per file, whatever the rewrite did. */
    private static void logOutcome(String relativePath, PollutionCfgWriter.Result result, String timestamp) {
        switch (result.status) {
            case WRITTEN:
                LOG.info(
                    "{} wrote {} for the next boot ({} key(s): {}; backup {}.{}.bak - this boot is covered by the "
                        + "in-memory pass)",
                    ID,
                    relativePath,
                    result.changedKeys.size(),
                    join(result.changedKeys),
                    relativePath,
                    timestamp);
                break;
            case ALREADY_CORRECT:
                if (result.missingKeys.isEmpty()) {
                    LOG.info("{} {} already correct - not changed", ID, relativePath);
                } else {
                    LOG.info(
                        "{} {} already correct for every key found - not changed ({} key(s) not found: {})",
                        ID,
                        relativePath,
                        result.missingKeys.size(),
                        join(result.missingKeys));
                }
                break;
            case MISSING_FILE:
                LOG.info("{} {} not created by GregTech yet - left alone", ID, relativePath);
                break;
            default:
                LOG.warn(
                    "{} could not write {} ({}) - left as is; the in-memory step still applies",
                    ID,
                    relativePath,
                    result.message);
                break;
        }
    }

    private static String join(List<String> values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(value);
        }
        return out.toString();
    }

    /** UTC, sortable, safe in a file name. */
    private static String timestamp() {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }
}
