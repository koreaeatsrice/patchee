package com.jointspaceforce.patchee.core.policy;

/**
 * The standard policy instances. The composition root picks one of each and
 * hands them to the factory; features never see them, and never throw.
 */
public final class Policies {

    /**
     * Default step-failure policy: one ERROR line with the feature's own message
     * and the stack trace, then carry on. A patched mod that changed shape must
     * never break the server.
     */
    public static final FailurePolicy FAIL_LOG_ERROR_CONTINUE = (log, stepId, message, failure) -> log
        .error(message, failure);

    /**
     * Default missing-target policy: one INFO line, then skip. A pack that does
     * not ship the patched mod is a normal situation, not an error.
     */
    public static final MissingTargetPolicy QUIET_SKIP_INFO = (log, stepId, message) -> log.info(message);

    private Policies() {}
}
