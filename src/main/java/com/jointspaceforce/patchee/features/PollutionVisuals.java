package com.jointspaceforce.patchee.features;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The client-side pollution-visuals decision: the switches, the log id and the
 * field names the guard touches. No Minecraft, no Forge, no GregTech classes.
 *
 * <p>
 * The server always sends the real pollution numbers. This feature only stops a
 * client from drawing them: GregTech's two render flags are turned off and its
 * block-tint tables are kept empty, so no smog, fog colour shift, smoke
 * particles or grass/leaf/water/flower tints are drawn on that client.
 *
 * <p>
 * {@link #decide(boolean, boolean, boolean)} is pure, so the truth table is
 * unit-tested without a game.
 */
public final class PollutionVisuals {

    /** Log id and lookup key, e.g. {@code [PollutionVisuals]} in the boot banner. */
    public static final String ID = "PollutionVisuals";

    /** The feature switch Forge writes, e.g. {@code B:enablePollutionNoVisuals=true}. */
    public static final String ENABLE_KEY = "enablePollutionNoVisuals";

    /** On unless the file says otherwise. */
    public static final boolean DEFAULT_ENABLED = true;

    /** GregTech's client file, relative to the game's {@code config/} directory. */
    public static final String CLIENT_CFG = PollutionCfg.CLIENT_CFG;

    /** The two Client.cfg labels, in the order the log line lists them. */
    public static final List<String> FLAG_LABELS = Collections
        .unmodifiableList(Arrays.asList("Pollution Fog", "Pollution Dirt"));

    /** The two booleans on {@code Client.render}, in the same order as {@link #FLAG_LABELS}. */
    public static final List<String> RENDER_FIELDS = Collections
        .unmodifiableList(Arrays.asList("renderPollutionFog", "renderDirtParticles"));

    /** The same two flags on {@code GTMod.proxy}, which the renderer reads every frame. */
    public static final List<String> PROXY_FIELDS = Collections
        .unmodifiableList(Arrays.asList("mRenderPollutionFog", "mRenderDirtParticles"));

    /** The five block-tint tables on {@code Pollution}, kept empty. */
    public static final List<String> MATCHER_FIELDS = Collections.unmodifiableList(
        Arrays.asList("standardBlocks", "liquidBlocks", "doublePlants", "crossedSquares", "blockVine"));

    /** What the boot step should do this run. */
    public enum Decision {
        /** A client with the switches on: arm the guard and write the file. */
        RUN,
        /** The master switch or this feature's switch is off. */
        DISABLED,
        /** A dedicated server: there is no client renderer to change. */
        SERVER
    }

    private PollutionVisuals() {}

    /**
     * The one decision the boot step makes.
     *
     * @param masterEnabled  the mod's master switch
     * @param featureEnabled this feature's own switch
     * @param client         true when FML says this is a client
     */
    public static Decision decide(boolean masterEnabled, boolean featureEnabled, boolean client) {
        if (!masterEnabled || !featureEnabled) {
            return Decision.DISABLED;
        }
        return client ? Decision.RUN : Decision.SERVER;
    }

    /**
     * True only when the guard really turned the visuals off: both proxy flags
     * read {@code false} and every tint table is empty.
     *
     * <p>
     * The dirt-particle flag matters even beyond the particles. The fog <b>colour</b>
     * shift has no flag of its own: GregTech's renderer reads the per-tick
     * pollution value to tint the fog, and that value is only refreshed while the
     * dirt particles run. With {@code Pollution Dirt} off the value stays at zero,
     * so the colour shift stays away. That is why both flags must really be off
     * before this feature may say it is armed.
     *
     * @param fogOff         {@code Pollution Fog} really reads {@code false}
     * @param dirtOff        {@code Pollution Dirt} really reads {@code false}
     * @param tablesEmpty    how many block-tint tables are empty now
     * @param tablesTotal    how many block-tint tables there are
     * @param tintPassFailed true when the tint pass threw
     */
    public static boolean armed(boolean fogOff, boolean dirtOff, int tablesEmpty, int tablesTotal,
        boolean tintPassFailed) {
        return fogOff && dirtOff && !tintPassFailed && tablesEmpty == tablesTotal;
    }
}
