package com.jointspaceforce.patchee.core;

/**
 * One unit of patch work.
 *
 * <p>
 * A step is always handed the {@link PatchContext} it needs, so it holds no
 * global state and can be composed and decorated in isolation. A step is allowed
 * to throw: the fail-soft decorator added by {@link PatchFactory} catches it,
 * reports it through the {@link com.jointspaceforce.patchee.core.policy.FailurePolicy}
 * and turns it into {@link Outcome#FAILED}. No feature needs a try/catch of its
 * own.
 */
@FunctionalInterface
public interface Step {

    Outcome run(PatchContext ctx) throws Exception;
}
