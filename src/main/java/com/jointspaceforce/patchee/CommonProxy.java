package com.jointspaceforce.patchee;

import com.jointspaceforce.patchee.core.Feature;
import com.jointspaceforce.patchee.core.FeaturePipeline;
import com.jointspaceforce.patchee.core.FeatureRegistry;
import com.jointspaceforce.patchee.core.PatchContext;
import com.jointspaceforce.patchee.core.PatchFactory;
import com.jointspaceforce.patchee.core.StepDecorators;
import com.jointspaceforce.patchee.core.Toggles;
import com.jointspaceforce.patchee.core.policy.Policies;
import com.jointspaceforce.patchee.core.reflect.Reflective;
import com.jointspaceforce.patchee.features.PatcheeFeatures;
import com.jointspaceforce.patchee.features.VeinConfigFeature;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.relauncher.Side;

/**
 * The composition root: the one place that knows about everything and wires it
 * together. It registers the features, loads the config, hands the policies,
 * logger, config snapshot and reflection helper to the factory, and runs the
 * pipeline the factory builds.
 *
 * <p>
 * Nothing below this class constructs its own dependencies, and adding a feature
 * needs no change here: it is registered in
 * {@link com.jointspaceforce.patchee.features.PatcheeFeatures}, and its config
 * option, banner field and pipeline steps follow from that.
 */
public class CommonProxy {

    /** Registered features: config options, banner fields and pipeline all come from this. */
    protected final FeatureRegistry features = PatcheeFeatures.standard();

    private Toggles toggles = Config.toggles();
    private PatchContext context;
    private FeaturePipeline pipeline;
    private FeaturePipeline earlyPipeline;

    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile(), features);
        toggles = Config.toggles();

        StepDecorators decorators = new StepDecorators();
        context = new PatchContext(
            Patchee.LOG,
            toggles,
            Policies.FAIL_LOG_ERROR_CONTINUE,
            Policies.QUIET_SKIP_INFO,
            new Reflective(),
            event.getModConfigurationDirectory());
        PatchFactory factory = new PatchFactory(features, decorators, Patchee.LOG);
        pipeline = factory.build(toggles);
        earlyPipeline = factory.buildEarly(toggles);

        Patchee.LOG.info(banner());

        // Features marked early (VeinConfig) must land BEFORE the mod they target
        // has run its own preInit: VeinMiner reads its config files there, and
        // FML runs every mod's preInit before any postInit. Patchee declares
        // before:VeinMiner in its @Mod, so this preInit comes first. Server only,
        // and gated by the master switch exactly like the postInit pipeline.
        if (FMLCommonHandler.instance()
            .getSide() == Side.SERVER && toggles.masterEnabled()) {
            earlyPipeline.run(context);
        }
    }

    public void postInit(FMLPostInitializationEvent event) {
        if (!toggles.masterEnabled()) {
            Patchee.LOG.info("[patchee] master switch is off — all fixes skipped");
            return;
        }
        pipeline.run(context);
    }

    public void serverStarted(FMLServerStartedEvent event) {
        // The VeinConfig check reads VeinMiner's live state and the files on
        // disk, both of which only exist on the server.
        if (FMLCommonHandler.instance()
            .getSide() == Side.SERVER && toggles.masterEnabled()
            && toggles.featureEnabled(VeinConfigFeature.ID)) {
            VeinConfigFeature.verifyAndLog(Patchee.LOG, toggles);
        }
        if (toggles.masterEnabled() && toggles.runSelfTest()) {
            SelfTest.run(toggles, context.configDirectory());
        }
    }

    /**
     * The boot banner: {@code Patchee <version> — enabled=... dollyFix=...
     * mattockFix=... superTankFix=... selfTest=...}. The per-feature fields come
     * from the registry, so a new feature shows up here by itself.
     */
    private String banner() {
        StringBuilder banner = new StringBuilder("Patchee ").append(Tags.VERSION)
            .append(" — enabled=")
            .append(toggles.masterEnabled());
        for (Feature feature : features.features()) {
            banner.append(' ')
                .append(bannerField(feature.id()))
                .append('=')
                .append(toggles.featureEnabled(feature.id()));
        }
        banner.append(" selfTest=")
            .append(toggles.runSelfTest());
        return banner.toString();
    }

    /** {@code DollyFix} -> {@code dollyFix}. */
    private static String bannerField(String featureId) {
        return Character.toLowerCase(featureId.charAt(0)) + featureId.substring(1);
    }
}
