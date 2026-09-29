package com.jointspaceforce.patchee;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class Config {

    public static boolean enableDollyFix = true;
    public static boolean enableMattockFix = true;
    public static String[] extraDollyClasses = new String[0];
    public static boolean runSelfTest = false;

    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);

        try {
            configuration.load();

            enableDollyFix = configuration.getBoolean(
                "enableDollyFix",
                Configuration.CATEGORY_GENERAL,
                enableDollyFix,
                "Let the JABBA Dolly pick up the single-block crafted bee housings "
                    + "(Forestry Apiary and Bee House, Gendustry Industrial Apiary, MagicBees Magic Apiary) "
                    + "plus any classes listed in extraDollyClasses.");

            enableMattockFix = configuration.getBoolean(
                "enableMattockFix",
                Configuration.CATEGORY_GENERAL,
                enableMattockFix,
                "Let the Tinkers' Construct Mattock dig sand, snow layers and snow blocks " + "at full shovel speed.");

            extraDollyClasses = configuration.getStringList(
                "extraDollyClasses",
                Configuration.CATEGORY_GENERAL,
                extraDollyClasses,
                "Extra TileEntity class names (fully qualified) the JABBA Dolly should accept, "
                    + "one per entry. Entries are ignored when the class is not a TileEntity, is "
                    + "already blacklisted in Jabba's own config (exact class match), or does not exist.");

            runSelfTest = configuration.getBoolean(
                "runSelfTest",
                Configuration.CATEGORY_GENERAL,
                runSelfTest,
                "Run a one-shot check of both patched decisions when the server starts, then log "
                    + "PASS/FAIL per check. Enable for one boot after installing or after a pack "
                    + "update, then set it back to false.");
        } finally {
            if (configuration.hasChanged()) {
                configuration.save();
            }
        }
    }
}
