package com.jointspaceforce.patchee.core;

import org.apache.logging.log4j.Logger;

/**
 * Decorators: each one wraps a {@link Step} and adds exactly one concern. The
 * step itself stays a plain function over the {@link PatchContext}, and no
 * feature contains a raw catch or a target check. The policies the decorators
 * consult are carried by the {@link PatchContext}, so they are injected once
 * at the composition root.
 */
public final class StepDecorators {

    public StepDecorators() {}

    /**
     * The server must never break over a patch: any Throwable thrown by the step
     * is reported through the failure policy and becomes {@link Outcome#FAILED}.
     *
     * @param stepId         the feature that owns the step, e.g. {@code DollyFix}
     * @param failureMessage the feature's own "failed - ... left unchanged" line
     */
    public Step failSoft(String stepId, String failureMessage, Step step) {
        return ctx -> {
            try {
                return step.run(ctx);
            } catch (Throwable failure) {
                ctx.failurePolicy()
                    .onFailure(ctx.log(), stepId, failureMessage, failure);
                return Outcome.FAILED;
            }
        };
    }

    /**
     * Traces the step's start and result at DEBUG only, so a normal server log
     * stays exactly as it was before the pipeline existed.
     */
    public Step logged(String stepId, Step step) {
        return ctx -> {
            Logger log = ctx.log();
            if (log.isDebugEnabled()) {
                log.debug("[{}] step start", stepId);
            }
            Outcome outcome = step.run(ctx);
            if (log.isDebugEnabled()) {
                log.debug("[{}] step result: {}", stepId, outcome);
            }
            return outcome;
        };
    }

    /**
     * Skips the step when the thing it patches is not installed, using the
     * feature's own INFO line instead of a failure.
     *
     * @param present     probe: is the target mod/class/field there right now?
     * @param skipMessage the existing "not installed - skipped" line
     */
    public Step requiresTarget(String stepId, TargetCheck present, String skipMessage, Step step) {
        return ctx -> {
            if (!present.isPresent(ctx)) {
                ctx.missingTargetPolicy()
                    .onMissingTarget(ctx.log(), stepId, skipMessage);
                return Outcome.SKIPPED;
            }
            return step.run(ctx);
        };
    }

    /** A cheap presence probe for {@link #requiresTarget}. */
    @FunctionalInterface
    public interface TargetCheck {

        boolean isPresent(PatchContext ctx);
    }
}
