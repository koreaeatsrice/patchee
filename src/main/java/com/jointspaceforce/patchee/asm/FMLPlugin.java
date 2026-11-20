package com.jointspaceforce.patchee.asm;

import java.io.File;
import java.util.Map;

import org.apache.logging.log4j.LogManager;

import com.jointspaceforce.patchee.features.PollutionPreload;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

/**
 * The coremod entry point: registers Patchee's class transformers with Forge's
 * class loader.
 *
 * <p>
 * Six tweaks have to change another mod's class bytes while those bytes load,
 * which is a coremod's job: the F7 light overlay rewrites NEI's comparison
 * constants on the client, TeamChunkCap clamps ServerUtilities' team chunk
 * limits on the server, PollutionNoVisuals makes GregTech's pollution packet
 * carry 0 to every client so no smoke, fog or plant tint is drawn, FinalCastleBreak
 * redirects Twilight Forest's castle and thorn call sites on the server,
 * ChestplateFlight lifts IC2's Quantum Chestplate hover climb and keeps a
 * standing player from counting as flying, and GraviSuiteFlight applies those
 * same three flight edits to GraviSuite's Advanced Jetpack (which the Advanced
 * Nano Chestplate inherits).
 *
 * <p>
 * {@link #injectData(Map)} also runs Patchee's one config write as early as a coremod can:
 * {@link PollutionPreload} rewrites {@code config/GregTech/Pollution.cfg}
 * (and the client render flags in {@code config/GregTech/Client.cfg}) as early as
 * a coremod can run. GregTech reads that same file in its own coremod's static
 * initializer, which FML runs before any coremod's {@code injectData}, so the file
 * change lands for the next boot; the postInit memory pass covers this one.
 *
 * <p>
 * There is deliberately no coremod config and nothing else here: each
 * transformer ignores every class except its own target(s), so an absent NEI
 * (every server), an absent ServerUtilities, an absent GregTech, an absent
 * Twilight Forest or an absent IC2 costs one string comparison per class.
 */
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.TransformerExclusions({ "com.jointspaceforce.patchee." })
public class FMLPlugin implements IFMLLoadingPlugin {

    @Override
    public String[] getASMTransformerClass() {
        return new String[] { "com.jointspaceforce.patchee.asm.LightOverlayTransformer",
            "com.jointspaceforce.patchee.asm.TeamChunkCapTransformer",
            "com.jointspaceforce.patchee.asm.PollutionNoVisualsTransformer",
            "com.jointspaceforce.patchee.asm.FinalCastleBreakTransformer",
            "com.jointspaceforce.patchee.asm.ChestplateFlightTransformer",
            "com.jointspaceforce.patchee.asm.GraviSuiteFlightTransformer" };
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        Object location = data == null ? null : data.get("mcLocation");
        File gameDirectory;
        if (location instanceof File) {
            gameDirectory = (File) location;
        } else if (location instanceof String) {
            gameDirectory = new File((String) location);
        } else {
            gameDirectory = new File(".");
        }
        try {
            PollutionPreload.run(gameDirectory);
        } catch (Throwable failure) {
            LogManager.getLogger("patchee")
                .warn(
                    "[PollutionNoNegatives] preload pass crashed ({}) - GregTech's pollution files left as they are",
                    failure.toString());
        }
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
