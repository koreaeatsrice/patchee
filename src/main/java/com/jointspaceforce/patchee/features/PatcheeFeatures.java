package com.jointspaceforce.patchee.features;

import com.jointspaceforce.patchee.core.FeatureRegistry;

/**
 * The single registration point: every feature Patchee ships is listed here and
 * nowhere else. The config options, the boot banner fields and the patch
 * pipeline are all derived from this registry, so adding a feature means adding
 * one class and one line below.
 */
public final class PatcheeFeatures {

    private PatcheeFeatures() {}

    /** The registry, in run order. */
    public static FeatureRegistry standard() {
        FeatureRegistry registry = new FeatureRegistry();
        registry.register(new DollyFeature())
            .register(new MattockFeature())
            .register(new SuperTankFeature());
        registry.freeze();
        return registry;
    }
}
