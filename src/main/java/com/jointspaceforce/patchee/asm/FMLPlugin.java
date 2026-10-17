package com.jointspaceforce.patchee.asm;

import java.util.Map;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

/**
 * The coremod entry point: registers Patchee's class transformers with Forge's
 * class loader.
 *
 * <p>
 * Three tweaks have to change another mod's class bytes while those bytes load,
 * which is a coremod's job: the F7 light overlay rewrites NEI's comparison
 * constants on the client, TeamChunkCap clamps ServerUtilities' team chunk
 * limits on the server, and FinalCastleBreak redirects Twilight Forest's castle
 * and thorn call sites on the server. There is deliberately no coremod config
 * and nothing else here: each transformer ignores every class except its own
 * target(s), so an absent NEI (every server), an absent ServerUtilities or an
 * absent Twilight Forest costs one string comparison per class.
 */
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.TransformerExclusions({ "com.jointspaceforce.patchee." })
public class FMLPlugin implements IFMLLoadingPlugin {

    @Override
    public String[] getASMTransformerClass() {
        return new String[] { "com.jointspaceforce.patchee.asm.LightOverlayTransformer",
            "com.jointspaceforce.patchee.asm.TeamChunkCapTransformer",
            "com.jointspaceforce.patchee.asm.FinalCastleBreakTransformer" };
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
        // no setup data needed
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
