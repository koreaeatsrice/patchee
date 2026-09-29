package com.jointspaceforce.patchee.core;

import java.util.List;

/**
 * The ready-to-run patch pipeline built by {@link PatchFactory}: every enabled
 * feature's decorated steps, in registration order.
 *
 * <p>
 * The pipeline is the only place that touches a step it did not build itself, so
 * it keeps one last-resort guard: whatever happens, the server boot continues.
 * The guard is a DEBUG line, so a normal server log is unchanged.
 */
public final class FeaturePipeline {

    private final List<Step> steps;

    FeaturePipeline(List<Step> steps) {
        this.steps = steps;
    }

    /** Runs the pipeline once. Never throws. */
    public void run(PatchContext ctx) {
        for (Step step : steps) {
            try {
                step.run(ctx);
            } catch (Throwable failure) {
                ctx.log()
                    .debug("[patchee] pipeline step aborted", failure);
            }
        }
    }
}
