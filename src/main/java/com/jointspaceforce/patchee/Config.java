package com.jointspaceforce.patchee;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class Config {

    public static boolean enabled = true;
    public static boolean enableDollyFix = true;
    public static boolean enableMattockFix = true;
    public static String[] extraDollyClasses = new String[0];
    public static boolean runSelfTest = false;

    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);

        try {
            configuration.load();

            enabled = configuration.getBoolean(
                "enabled",
                Configuration.CATEGORY_GENERAL,
                enabled,
                "MASTER SWITCH for the whole mod. true = the features below work. false = the mod does "
                    + "nothing at all. Every feature also has its own switch. After editing this file, "
                    + "restart the server.");

            enableDollyFix = configuration.getBoolean(
                "enableDollyFix",
                Configuration.CATEGORY_GENERAL,
                enableDollyFix,
                "Feature: move bee houses with the JABBA Dolly (keeps the bees inside). true = the Dolly "
                    + "can pick up Forestry's Apiary and Bee House, Gendustry's Industrial Apiary and "
                    + "MagicBees' Magic Apiary. false = vanilla behaviour.");

            enableMattockFix = configuration.getBoolean(
                "enableMattockFix",
                Configuration.CATEGORY_GENERAL,
                enableMattockFix,
                "Feature: the Tinkers' Construct mattock digs sand and snow like a shovel. true = fixed. "
                    + "false = vanilla behaviour.");

            extraDollyClasses = configuration.getStringList(
                "extraDollyClasses",
                Configuration.CATEGORY_GENERAL,
                extraDollyClasses,
                "Advanced: extra TileEntity class names (fully qualified) the JABBA Dolly should accept, "
                    + "one per entry. Entries are ignored when the class is not a TileEntity, is "
                    + "already blacklisted in Jabba's own config (exact class match), or does not exist.");

            runSelfTest = configuration.getBoolean(
                "runSelfTest",
                Configuration.CATEGORY_GENERAL,
                runSelfTest,
                "Advanced: run a one-shot check of every patched decision when the server starts, then "
                    + "log PASS/FAIL per check. Switch it on for one server start after installing or "
                    + "after a pack update, then set it back to false.");
        } finally {
            if (configuration.hasChanged()) {
                configuration.save();
            }
        }
    }
}
