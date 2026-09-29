package com.jointspaceforce.patchee;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;

/**
 * Patchee — a small server-side GTNH 1.7.10 addon that fixes two cross-mod
 * annoyances without editing any other mod's jar:
 *
 * <p>
 * 1. <b>JABBA Dolly × bee housings.</b> The Dolly refuses to pick up the
 * single-block crafted bee housings (Forestry Apiary, Gendustry Industrial
 * Apiary, MagicBees Magic Apiary) because Jabba's movable-class list is a
 * hardcoded static that is built once at class-init. Patchee appends those
 * classes to it at postInit ({@link com.jointspaceforce.patchee.features.DollyFeature}).
 *
 * <p>
 * 2. <b>Tinkers' Construct Mattock.</b> The mattock's shovel-material list
 * lacks sand/snow, so it digs them at hand speed. Patchee extends the static
 * array ({@link com.jointspaceforce.patchee.features.MattockFeature}). The Forge
 * "shovel" tool class is deliberately NOT claimed, so no other mod's
 * behaviour changes.
 *
 * <p>
 * Both fixes are reflection-based against verified runtime names — no ASM,
 * no bytecode edits, no dependencies. Clients need nothing installed
 * ({@code acceptableRemoteVersions = "*"}).
 */
@Mod(
    modid = Patchee.MODID,
    version = Tags.VERSION,
    name = "Patchee",
    acceptedMinecraftVersions = "[1.7.10]",
    acceptableRemoteVersions = "*")
public class Patchee {

    public static final String MODID = "patchee";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(
        clientSide = "com.jointspaceforce.patchee.ClientProxy",
        serverSide = "com.jointspaceforce.patchee.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        proxy.serverStarted(event);
    }
}
