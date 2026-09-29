package com.jointspaceforce.patchee.core;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.Logger;

/**
 * The factory: turns a {@link FeatureRegistry} plus a config snapshot into the
 * decorated, ready-to-run {@link FeaturePipeline}.
 *
 * <p>
 * For every enabled feature it takes the composed steps from
 * {@link Feature#steps()} and wraps each one in:
 * <ul>
 * <li>a missing-target gate, when the feature declares a {@link Feature#targetClass()
 * target class} — the gate logs the feature's own "not installed - skipped" INFO
 * line and returns {@link Outcome#SKIPPED} instead of failing;</li>
 * <li>fail-soft, so a step that throws is reported through the failure policy and
 * becomes {@link Outcome#FAILED};</li>
 * <li>trace logging, so the bootstrap of a step is visible at DEBUG only.</li>
 * </ul>
 * A feature that is switched off contributes its own single INFO line instead, so
 * the log of a disabled feature reads exactly as it did before.
 */
public final class PatchFactory {

    private final FeatureRegistry registry;
    private final StepDecorators decorators;
    private final Logger log;

    public PatchFactory(FeatureRegistry registry, StepDecorators decorators, Logger log) {
        this.registry = registry;
        this.decorators = decorators;
        this.log = log;
    }

    public FeaturePipeline build(Toggles toggles) {
        List<Step> pipeline = new ArrayList<Step>();
        for (Feature feature : registry.features()) {
            if (toggles.featureEnabled(feature.id())) {
                pipeline.add(enabled(feature));
            } else {
                pipeline.add(disabled(feature));
            }
        }
        if (log.isDebugEnabled()) {
            log.debug(
                "[patchee] pipeline built: {} step(s), {} feature(s) registered",
                pipeline.size(),
                registry.features()
                    .size());
        }
        return new FeaturePipeline(pipeline);
    }

    private Step enabled(Feature feature) {
        Step bare = feature.steps()
            .isEmpty() ? Steps.noop()
                : Steps.sequence(
                    feature.steps()
                        .toArray(new Step[0]));
        Step inner = decorators.failSoft(feature.id(), feature.failureMessage(), bare);
        if (feature.targetClass() != null) {
            inner = decorators.requiresTarget(
                feature.id(),
                Steps.present(feature.targetClass()),
                feature.missingTargetMessage(),
                inner);
        }
        return decorators.logged(feature.id(), inner);
    }

    /** A switched-off feature still reports itself, in its own words. */
    private Step disabled(final Feature feature) {
        return ctx -> {
            ctx.log()
                .info(feature.disabledMessage());
            return Outcome.SKIPPED;
        };
    }
}
