package com.jointspaceforce.patchee;

import com.jointspaceforce.patchee.fixes.DollyFix;
import com.jointspaceforce.patchee.fixes.MattockFix;

import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());

        Patchee.LOG.info(
            "Patchee " + Tags.VERSION
                + " — enabled="
                + Config.enabled
                + " dollyFix="
                + Config.enableDollyFix
                + " mattockFix="
                + Config.enableMattockFix
                + " selfTest="
                + Config.runSelfTest);
    }

    public void postInit(FMLPostInitializationEvent event) {
        if (!Config.enabled) {
            Patchee.LOG.info("[patchee] master switch is off — all fixes skipped");
            return;
        }
        DollyFix.apply();
        MattockFix.apply();
    }

    public void serverStarted(FMLServerStartedEvent event) {
        if (Config.enabled && Config.runSelfTest) {
            SelfTest.run();
        }
    }
}
