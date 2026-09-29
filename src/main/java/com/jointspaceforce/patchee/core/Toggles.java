package com.jointspaceforce.patchee.core;

/**
 * Immutable snapshot of the mod config, taken once at preInit.
 *
 * <p>
 * Features and steps read their switches through this interface instead of
 * reaching for the static {@link com.jointspaceforce.patchee.Config} holder, so
 * they take their inputs as dependencies rather than globals.
 */
public interface Toggles {

    /** The master switch: when false the mod does nothing at all. */
    boolean masterEnabled();

    /** Whether one feature's own switch is on. Unknown ids are off. */
    boolean featureEnabled(String featureId);

    /** The advanced one-shot self-test switch. */
    boolean runSelfTest();

    /** Advanced: extra fully qualified class names the JABBA Dolly should accept. */
    String[] extraDollyClasses();
}
