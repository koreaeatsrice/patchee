package com.jointspaceforce.patchee.core;

import java.util.List;

/**
 * A self-describing patchable feature.
 *
 * <p>
 * Adding a feature to Patchee means adding one instance of this to the registry
 * and nothing else: the config option (name, default, plain-language comment),
 * the boot banner field, the "switched off" line and the pipeline wiring all
 * follow from these methods.
 */
public interface Feature {

    /** Log id and lookup key, e.g. {@code DollyFix}; also names the banner field. */
    String id();

    /** Config option name, e.g. {@code enableDollyFix}. */
    String configKey();

    /** Value used when config/patchee.cfg does not have the option yet. */
    boolean defaultEnabled();

    /** Plain-language config comment, written into config/patchee.cfg. */
    String description();

    /** INFO line logged when this feature is switched off in the config. */
    String disabledMessage();

    /** ERROR line logged when one of this feature's steps throws. */
    String failureMessage();

    /**
     * The mod/class this feature patches, or {@code null} when the feature does
     * not depend on any external class at boot (e.g. a tick-event listener).
     * When non-null, the factory wraps the feature's steps in a missing-target
     * gate that logs this message and skips instead of failing.
     */
    String targetClass();

    /** The existing "not installed - skipped" INFO line for {@link #targetClass()}. */
    String missingTargetMessage();

    /** The work, in order. The factory wraps every step in the standard decorators. */
    List<Step> steps();
}
