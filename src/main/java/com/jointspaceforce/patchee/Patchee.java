package com.jointspaceforce.patchee;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;

/**
 * Patchee — a small GTNH 1.7.10 addon that fixes eight cross-mod annoyances
 * without editing any other mod's jar. The fixes are registered in one place,
 * {@link com.jointspaceforce.patchee.features.PatcheeFeatures}.
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
 * 3. <b>GregTech Super Tanks and Super Chests.</b> A filled one applies four
 * potion effects to whoever carries it; Patchee clears them
 * ({@link com.jointspaceforce.patchee.features.SuperTankFeature}).
 *
 * <p>
 * 4. <b>VeinMiner.</b> Patchee rewrites VeinMiner's own settings at every
 * server start so only sand, clay and gravel can be vein-mined, up to 64 blocks
 * ({@link com.jointspaceforce.patchee.features.VeinConfigFeature}).
 *
 * <p>
 * 5. <b>NEI's F7 light overlay.</b> On a client that has Patchee installed the
 * overlay marks only pitch-dark blocks
 * ({@link com.jointspaceforce.patchee.features.LightOverlayFeature}).
 *
 * <p>
 * 6. <b>Twilight Forest break speeds.</b> The aurora blocks and the arctic fur
 * block get the modern break speed as a floor
 * ({@link com.jointspaceforce.patchee.features.AuroraBreakFeature},
 * {@link com.jointspaceforce.patchee.features.ArcticFurBreakFeature}).
 *
 * <p>
 * 7. <b>ServerUtilities team chunk limits.</b> ServerUtilities adds a team's
 * members' limits together, so a bigger team would be allowed more land than
 * any one member; Patchee reads a team's limit from its members' per-rank
 * settings instead — the best member's own limit becomes the team max, member
 * limits are never added up — so a team of ordinary players gets one player's
 * total and a member whose rank carries higher numbers (the admin rank) keeps
 * them; a solo player is untouched
 * ({@link com.jointspaceforce.patchee.features.TeamChunkCapFeature}).
 *
 * <p>
 * 8. <b>CropsNH enriched fertilizer.</b> The old 4-fertilizer recipe for
 * enriched fertilizer was dropped from the pack; Patchee adds it back next to
 * the pack's newer one, in the Chemical Reactor and the Large Chemical Reactor
 * ({@link com.jointspaceforce.patchee.features.EnrichedFertilizerRecipeFeature}).
 *
 * <p>
 * Most fixes are reflection-based against verified runtime names. Two of them
 * cannot be: the F7 overlay comparison constant and ServerUtilities' team-limit
 * return value live inside another mod's compiled method with no hook and no
 * config key, so Patchee's coremod rewrites those class bytes as they load —
 * see {@link com.jointspaceforce.patchee.asm.FMLPlugin} and
 * {@code tools/matcher-check}. Two fixes are seen on the client (the F7 overlay
 * and the break speeds); the rest run on the server, so clients need nothing
 * installed to join ({@code acceptableRemoteVersions = "*"}).
 */
@Mod(
    modid = Patchee.MODID,
    version = Tags.VERSION,
    name = "Patchee",
    acceptedMinecraftVersions = "[1.7.10]",
    acceptableRemoteVersions = "*",
    dependencies = "before:VeinMiner")
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
