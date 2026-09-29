package com.jointspaceforce.patchee.core.policy;

import org.apache.logging.log4j.Logger;

/**
 * What happens when the mod, class or field a feature wants to patch is simply
 * not there.
 *
 * <p>
 * That is normal on a pack that does not ship that mod, so the standard
 * implementation logs one INFO line and skips — no stack trace, no ERROR.
 */
public interface MissingTargetPolicy {

    /**
     * @param log     the mod logger
     * @param stepId  the feature that is skipping, e.g. {@code DollyFix}
     * @param message the feature's own "not installed - skipped" line
     */
    void onMissingTarget(Logger log, String stepId, String message);
}
