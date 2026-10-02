package com.jointspaceforce.patchee.asm;

import java.util.Map;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

/**
 * The coremod entry point: registers {@link LightOverlayTransformer} with Forge's
 * class loader.
 *
 * <p>
 * The F7 tweak has to change NEI's class bytes while they load, which is a
 * coremod's job. There is deliberately no coremod config and nothing else here:
 * the single transformer ignores every class except
 * {@code codechicken.nei.WorldOverlayRenderer}, so an absent NEI (every server,
 * and any client without it) costs one string comparison per class.
 */
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.TransformerExclusions({ "com.jointspaceforce.patchee." })
public class FMLPlugin implements IFMLLoadingPlugin {

    @Override
    public String[] getASMTransformerClass() {
        return new String[] { "com.jointspaceforce.patchee.asm.LightOverlayTransformer" };
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
