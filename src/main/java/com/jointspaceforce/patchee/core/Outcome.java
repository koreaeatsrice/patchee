package com.jointspaceforce.patchee.core;

/**
 * What a {@link Step} did. Decorators and the pipeline report on it; the log
 * lines of the patches themselves are unchanged by it.
 */
public enum Outcome {

    /** The step changed the target — the patch is in place. */
    APPLIED,

    /** The step did nothing on purpose: target absent, feature switched off, already patched. */
    SKIPPED,

    /** The step could not do its job. The failure was already reported. */
    FAILED
}
