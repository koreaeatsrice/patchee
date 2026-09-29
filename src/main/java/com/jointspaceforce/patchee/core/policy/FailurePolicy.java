package com.jointspaceforce.patchee.core.policy;

import org.apache.logging.log4j.Logger;

/**
 * What happens when a step blows up.
 *
 * <p>
 * A patched mod changing shape must never take the server down with it, so the
 * standard implementation logs the original message with its stack trace and
 * lets the pipeline carry on.
 */
public interface FailurePolicy {

    /**
     * @param log     the mod logger
     * @param stepId  the feature that failed, e.g. {@code DollyFix}
     * @param message the feature's own error line
     * @param failure what went wrong
     */
    void onFailure(Logger log, String stepId, String message, Throwable failure);
}
