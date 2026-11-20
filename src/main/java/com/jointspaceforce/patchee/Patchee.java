package com.jointspaceforce.patchee;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;

/**
 * Patchee — a small GTNH 1.7.10 addon that fixes ten cross-mod annoyances
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
 * 9. <b>Pam's HarvestCraft Fish Trap.</b> InventoryBogoSorter's hotkeys only
 * work in containers it knows by exact class, and the Fish Trap is not one of
 * them, so space/alt/ctrl-click and middle-click sort did nothing there; Patchee
 * registers the trap's output grid as one sort group and sends bait into slot 18
 * on a move ({@link com.jointspaceforce.patchee.features.FishTrapMovesFeature}).
 * The move hotkeys (space/alt/ctrl-click) work from the server jar alone;
 * sorting the trap's output with middle-click (and the sorter's sort button in
 * that GUI) needs Patchee on the client too. Plain shift-click already works.
 *
 * <p>
 * 10. <b>GregTech pollution visuals.</b> Pollution keeps running in full on the
 * server, but players see none of it: the single number GregTech sends each
 * client about a chunk is rewritten to 0 as the packet is built, so the client
 * draws no smog, no fog, no fog colour shift, no smoke particles and no
 * grass/leaf/water/flower tints
 * ({@link com.jointspaceforce.patchee.features.PollutionNoVisualsFeature}). Every
 * server-side read — damage, sour rain, vegetation, scanners, the pollution
 * detector, machines — keeps the real numbers; a client-side pollution readout
 * reads 0. A stock client needs nothing installed, but a client that also runs
 * Patchee applies the same rewrite to its own copy while the packet class loads,
 * so that player keeps the visuals hidden even when the switch is off.
 *
 * <p>
 * 11. <b>GregTech pollution settings.</b> Pollution stays on, but has no
 * downsides and no smog: Patchee writes {@code config/GregTech/Pollution.cfg}
 * before GregTech reads it ({@code Activate Pollution=true}, the four effect
 * limits at 2147483647) and forces those values in memory after load; on a
 * client it also turns off the Pollution Fog and Pollution Dirt render flags
 * ({@link com.jointspaceforce.patchee.features.PollutionConfigFeature}).
 *
 * <p>
 * Most fixes are reflection-based against verified runtime names. Six of them
 * cannot be, because the thing to change lives inside another mod's compiled
 * method with no hook and no config key: the F7 overlay comparison constant
 * (NEI), ServerUtilities' team-limit return value, GregTech's pollution packet
 * constructor, Twilight Forest's castle and thorn call sites, IC2's Quantum
 * Chestplate flight edits and GraviSuite's Advanced Jetpack flight edits. For
 * those six, Patchee's coremod rewrites the class bytes as they load — see
 * {@link com.jointspaceforce.patchee.asm.FMLPlugin} and
 * {@code tools/matcher-check}. Three fixes are partly seen on the client (the F7
 * overlay, the break speeds, and the Fish Trap's middle-click sort); the rest act
 * on the server, so clients need nothing installed to join
 * ({@code acceptableRemoteVersions = "*"}). The pollution rewrite is the exception
 * in the other direction: a client that also installs Patchee rewrites its own copy
 * and hides pollution visuals everywhere until that copy is removed or its own
 * switch is turned off.
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
