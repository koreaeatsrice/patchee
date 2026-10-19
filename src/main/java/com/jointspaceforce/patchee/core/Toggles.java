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

    /** VeinConfig: the most blocks one vein may have (VeinMiner {@code limit.blocks}). */
    int veinBlockLimit();

    /** VeinConfig: the search radius around the first block (VeinMiner {@code limit.radius}). */
    int veinRadius();

    /** VeinConfig: the block IDs that may be vein-mined, e.g. {@code minecraft:sand/0}. */
    String[] veinBlocks();

    /** VeinConfig: extra item names appended to the shovel tool list. */
    String[] veinExtraTools();

    /**
     * LightOverlay: the owner's max light level (X at light &le; this), in every
     * dimension. NEI draws an X below its comparison constant, so the constant
     * is this value + 1.
     */
    int lightOverlayMaxNormal();
}
