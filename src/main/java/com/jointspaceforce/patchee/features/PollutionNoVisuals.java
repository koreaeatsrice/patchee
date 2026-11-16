package com.jointspaceforce.patchee.features;

/**
 * The pollution-visuals switch, plus the flag the boot line is gated on.
 *
 * <p>
 * GregTech keeps pollution entirely on the server: production, accumulation,
 * decay, chunk storage, damage, sour rain, vegetation checks, scanners, the
 * pollution detector and every machine reading are all server-side numbers.
 * What a player
 * <i>sees</i> is a separate path: the server sends one number per chunk to each
 * nearby client through a single packet, {@code gregtech.api.net.GTPacketPollution},
 * and the client's {@code PollutionRenderer} turns that one number into fog, a
 * fog colour shift, smoke particles and grass/leaves/water/flower tints. The
 * server's own {@code pollutionSmogLimit} config cannot stop that, because the
 * renderer never asks the server — it reads only the number it was sent.
 *
 * <p>
 * So the channel is the packet: if the number in that packet is 0, the client
 * has nothing to draw with. {@link PollutionNoVisualsPatch} rewrites the
 * packet's {@code (chunk, int)} constructor to store the constant {@code 0}
 * instead of its {@code int} argument, which makes every value the server ever
 * sends 0 — whatever the real pollution is, and whichever of the two send sites
 * built the packet.
 *
 * <p>
 * The tradeoff is stated plainly: anything a <b>client</b> reads from that
 * number now reads 0, so a client-side pollution readout or debug display built
 * on GregTech's client map shows nothing. Every <b>server</b>-side read is
 * exact, because the real value is written to chunk storage before the packet
 * is built and the packet is never read back for mechanics.
 *
 * <p>
 * This holder carries no switches of its own: the transformer reads
 * {@link PollutionNoVisualsSettings} directly (Forge's config does not exist
 * when the packet class loads) and {@link #shouldPatch(boolean, boolean)} is
 * the one pure decision both of them share.
 */
public final class PollutionNoVisuals {

    /** Log id and lookup key, e.g. {@code [PollutionNoVisuals]} in the boot banner. */
    public static final String ID = "PollutionNoVisuals";

    /** The feature switch Forge writes, e.g. {@code B:enablePollutionNoVisuals=true}. */
    public static final String ENABLE_KEY = "enablePollutionNoVisuals";

    /** Used when the config file or the key is not there yet. */
    public static final boolean DEFAULT_ENABLED = true;

    /**
     * Whether the packet's constructor was really rewritten this boot.
     *
     * <p>
     * Written only by
     * {@link com.jointspaceforce.patchee.asm.PollutionNoVisualsTransformer} once
     * its constructor site has been rewritten (or found already patched), and
     * read by {@link PollutionNoVisualsFeature} before it claims the feature is
     * armed. A pure target-class <i>presence</i> probe is not enough: a
     * GregTech build whose shape the patch does not recognise is handed back
     * untouched, so the boot line must not say "armed" on it.
     */
    static volatile boolean patchApplied = false;

    /**
     * Whether the switches were off when the packet class loaded, so the class
     * was deliberately handed back untouched.
     *
     * <p>
     * Set by
     * {@link com.jointspaceforce.patchee.asm.PollutionNoVisualsTransformer}
     * when it sees its target class and decides not to patch it. Without this
     * flag the boot line cannot tell "you switched it off before the class
     * loaded" from "GregTech's class has a shape this patch does not know", and
     * would print a misleading error on a config change. Never un-set during a
     * boot: the class loads once.
     */
    static volatile boolean loadTimeDisabled = false;

    private PollutionNoVisuals() {}

    /**
     * Publish that the packet's constructor was patched. Called by
     * {@link com.jointspaceforce.patchee.asm.PollutionNoVisualsTransformer} the
     * moment the site is rewritten (or found already patched). Never un-set: the
     * patch only ever goes from "not applied" to "applied" during a boot.
     */
    public static void markPatchApplied() {
        patchApplied = true;
    }

    /** True when the packet class was really patched this boot (the ARMED gate). */
    public static boolean patchApplied() {
        return patchApplied;
    }

    /**
     * Publish that the packet class loaded while the switches were off, so it
     * was handed back untouched on purpose. Called by
     * {@link com.jointspaceforce.patchee.asm.PollutionNoVisualsTransformer}'s
     * switch branch, so the boot line can name the real reason instead of
     * reporting a shape mismatch.
     */
    public static void markLoadTimeDisabled() {
        loadTimeDisabled = true;
    }

    /** True when the class-load switch was off, so nothing was changed. */
    public static boolean loadTimeDisabled() {
        return loadTimeDisabled;
    }

    /**
     * Whether the patch may run at all: the master switch and the feature's own
     * switch. Pure, so a unit test can pin the truth table without a game.
     *
     * @param masterEnabled  the file's {@code enabled} switch
     * @param featureEnabled the file's {@code enablePollutionNoVisuals} switch
     * @return true only when both are on
     */
    public static boolean shouldPatch(boolean masterEnabled, boolean featureEnabled) {
        return masterEnabled && featureEnabled;
    }
}
