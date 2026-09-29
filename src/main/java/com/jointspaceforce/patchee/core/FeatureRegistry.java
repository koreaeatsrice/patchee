package com.jointspaceforce.patchee.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ordered registration of the features Patchee ships: register them in the order
 * they should run. The config file, the boot banner and the pipeline are all
 * built from this one list. Once the composition root has it, the registry is
 * frozen so nothing can change the feature set mid-run.
 */
public final class FeatureRegistry {

    private final List<Feature> features = new ArrayList<Feature>();
    private boolean frozen = false;

    /** Registers a feature and returns this registry, so calls can be chained. */
    public FeatureRegistry register(Feature feature) {
        if (frozen) {
            throw new IllegalStateException("feature registry is frozen");
        }
        features.add(feature);
        return this;
    }

    /** Locks the registry; the single registration point calls this last. */
    public void freeze() {
        frozen = true;
    }

    /** Every registered feature, in registration order. */
    public List<Feature> features() {
        return Collections.unmodifiableList(features);
    }
}
