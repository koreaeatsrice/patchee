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

import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;

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

    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile(), features);
        toggles = Config.toggles();

        StepDecorators decorators = new StepDecorators();
        context = new PatchContext(
            Patchee.LOG,
            toggles,
            Policies.FAIL_LOG_ERROR_CONTINUE,
            Policies.QUIET_SKIP_INFO,
            new Reflective());
        pipeline = new PatchFactory(features, decorators, Patchee.LOG).build(toggles);

        Patchee.LOG.info(banner());
    }

    public void postInit(FMLPostInitializationEvent event) {
        if (!toggles.masterEnabled()) {
            Patchee.LOG.info("[patchee] master switch is off — all fixes skipped");
            return;
        }
        pipeline.run(context);
    }

    public void serverStarted(FMLServerStartedEvent event) {
        if (toggles.masterEnabled() && toggles.runSelfTest()) {
            SelfTest.run();
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
