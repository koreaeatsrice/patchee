package com.jointspaceforce.patchee.features;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.jointspaceforce.patchee.core.Feature;
import com.jointspaceforce.patchee.core.Outcome;
import com.jointspaceforce.patchee.core.PatchContext;
import com.jointspaceforce.patchee.core.Step;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.Side;

/**
 * Keeps GregTech's pollution on, with no downsides and no smog.
 *
 * <p>
 * Two halves. The <b>file half</b> runs from Patchee's coremod
 * ({@link com.jointspaceforce.patchee.asm.FMLPlugin}) before GregTech loads:
 * GregTech reads its pollution config while it is still constructing itself (its
 * coremod / static initializer), before any mod's preInit, so a postInit write
 * would be too late. That half sets {@code Activate Pollution=true} and the
 * four effect limits in {@code config/GregTech/Pollution.cfg} to 2147483647, and
 * keeps a timestamped {@code .bak} of the original.
 *
 * <p>
 * The four limits are the top of the int range, so no effect threshold can ever
 * be reached while pollution still accumulates and decays exactly as GregTech
 * wrote it. The <b>memory half</b> runs at postInit and writes both places
 * GregTech looks: the five {@code PollutionConfig} fields, and the copies on
 * {@code GTMod.proxy} that GregTech's pollution code actually reads - so a
 * read-only file or a hand edit cannot bring the effects back. On a client it
 * also turns off the {@code Pollution Fog} and {@code Pollution Dirt} render
 * flags in memory and in {@code config/GregTech/Client.cfg}, clears the same two
 * mirrors on {@code GTMod.proxy}, and turns the block-recolour tint off in memory
 * only - {@code Pollution.cfg} keeps exactly its five keys.
 *
 * <p>
 * Honest limits: the client half only helps players who install Patchee
 * themselves - a player without it still sees the smog the server reports. And
 * if the files cannot be written at all, no backup exists and the log says so;
 * the in-memory half still applies.
 */
public final class PollutionConfigFeature implements Feature {

    public static final String ID = "PollutionNoNegatives";

    private static final String CONFIG_KEY = "enablePollutionNoNegatives";

    /** The display name of each of the five server fields, in the order they are logged. */
    private static final String[] SERVER_LABELS = { "Activate Pollution", "pollutionSmogLimit", "pollutionPoisonLimit",
        "pollutionVegetationLimit", "pollutionSourRainLimit" };

    /** The live GregTech field behind each label. */
    private static final String[] SERVER_FIELDS = { "pollution", "pollutionSmogLimit", "pollutionPoisonLimit",
        "pollutionVegetationLimit", "pollutionSourRainLimit" };

    /**
     * The copies of those five values on {@code GTMod.proxy}; GregTech's pollution code reads these, not the
     * {@code PollutionConfig} statics.
     */
    private static final String[] SERVER_MIRRORS = { "mPollution", "mPollutionSmogLimit", "mPollutionPoisonLimit",
        "mPollutionVegetationLimit", "mPollutionSourRainLimit" };

    private static final String POLLUTION_CONFIG_CLASS = "gregtech.common.pollution.PollutionConfig";
    private static final String CLIENT_CONFIG_CLASS = "gregtech.common.config.Client";
    private static final String GT_MOD_CLASS = "gregtech.GTMod";
    private static final String RENDER_FIELD = "render";
    private static final String PROXY_FIELD = "proxy";

    /** The block-recolour tint: a client-side switch Patchee only ever clears in memory. */
    private static final String TINT_FIELD = "pollutionBlockRecolor";

    /** The two client render fields, in the order they are logged. */
    private static final String[] CLIENT_LABELS = { "Pollution Fog", "Pollution Dirt" };
    private static final String[] CLIENT_FIELDS = { "renderPollutionFog", "renderDirtParticles" };

    /** The same two flags on {@code GTMod.proxy}, which the renderer reads. */
    private static final String[] CLIENT_MIRRORS = { "mRenderPollutionFog", "mRenderDirtParticles" };

    /** GregTech's mod id, as registered with FML. */
    private static final String GT_MODID = "gregtech";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String configKey() {
        return CONFIG_KEY;
    }

    @Override
    public boolean defaultEnabled() {
        return true;
    }

    @Override
    public String description() {
        return "Feature: GregTech's pollution stays ON, with no downsides and no smog. true = at coremod load, "
            + "before any mod's preInit, Patchee rewrites config/GregTech/Pollution.cfg: Activate Pollution=true and "
            + "the four effect limits (poison, smog, sour rain, vegetation) at 2147483647, changing only those five "
            + "values and keeping every other byte, with a timestamped .bak. After GregTech loads it forces the same "
            + "five values in memory - in the five GregTech fields and in the copies on GTMod.proxy that GregTech's "
            + "pollution code actually reads - so a read-only file or a hand edit cannot bring the effects back; "
            + "that memory pass also covers the boot where GregTech read the file before Patchee could rewrite it. "
            + "On a client it turns the Pollution Fog and Pollution Dirt render flags off in memory, and in "
            + "config/GregTech/Client.cfg when that file exists (GregTech writes it on its first launch); the "
            + "block-recolour field pollutionBlockRecolor is cleared in memory only and no other line of that file "
            + "is touched. The client half only helps players who install Patchee themselves - a player without "
            + "Patchee still sees the smog the server reports. false = Patchee leaves GregTech's pollution files and "
            + "values exactly as they ship.";
    }

    @Override
    public String disabledMessage() {
        return "[PollutionNoNegatives] disabled in config - GregTech's pollution files and values left exactly as "
            + "they ship";
    }

    @Override
    public String failureMessage() {
        return "[PollutionNoNegatives] failed - GregTech's pollution values left as they were";
    }

    @Override
    public String targetClass() {
        return null;
    }

    @Override
    public String missingTargetMessage() {
        return null;
    }

    /** The file half runs from the coremod; these two steps force the loaded values. */
    @Override
    public boolean early() {
        return false;
    }

    @Override
    public List<Step> steps() {
        return Arrays.asList(forceServerStep(), forceClientStep());
    }

    /** Forces the five server fields in memory, whatever the file says. */
    private Step forceServerStep() {
        return ctx -> {
            if (!Loader.isModLoaded(GT_MODID)) {
                ctx.log()
                    .info("[PollutionNoNegatives] GregTech is not installed - skipped");
                return Outcome.SKIPPED;
            }
            try {
                Map<String, String> onDisk = fileValues(ctx, PollutionCfg.POLLUTION_CFG, PollutionCfg.SERVER_KEYS);
                Class<?> pollutionConfig = Class.forName(POLLUTION_CONFIG_CLASS);
                StringBuilder line = new StringBuilder("[PollutionNoNegatives] forced in memory: ");
                for (int i = 0; i < SERVER_FIELDS.length; i++) {
                    Field field = pollutionConfig.getDeclaredField(SERVER_FIELDS[i]);
                    field.setAccessible(true);
                    field.get(null); // read first, so a field of the wrong type fails before anything changes
                    if (i == 0) {
                        field.setBoolean(null, true);
                    } else {
                        field.setInt(null, PollutionCfg.MAX_LIMIT);
                    }
                    if (i > 0) {
                        line.append(", ");
                    }
                    line.append(SERVER_LABELS[i])
                        .append('=')
                        .append(i == 0 ? "true" : Integer.toString(PollutionCfg.MAX_LIMIT))
                        .append(" (file: ")
                        .append(fileSaid(onDisk, SERVER_LABELS[i]))
                        .append(')');
                }
                line.append(mirrorServer(ctx));
                ctx.log()
                    .info(line.toString());
                return Outcome.APPLIED;
            } catch (Throwable failure) {
                ctx.log()
                    .error(
                        "[PollutionNoNegatives] could not force GregTech's pollution fields ({}) - left as they were",
                        failure.toString());
                return Outcome.SKIPPED;
            }
        };
    }

    /**
     * Writes the five mirrors on {@code GTMod.proxy}, the copies GregTech's pollution code actually reads. Never
     * throws: an unreachable proxy logs one error line and the statics already set still count as applied.
     *
     * @return the tail to append to the success log line
     */
    private static String mirrorServer(PatchContext ctx) {
        Object proxy = null;
        String reason = "proxy is null";
        try {
            proxy = gtProxy();
        } catch (Throwable failure) {
            reason = failure.toString();
        }
        if (proxy == null) {
            ctx.log()
                .error(
                    "[PollutionNoNegatives] could not reach GTMod.proxy ({}) - only the config fields were forced",
                    reason);
            return "; GTMod.proxy mirrors not updated";
        }
        int updated = mirrorBoolean(ctx, proxy, SERVER_MIRRORS[0], true);
        for (int i = 1; i < SERVER_MIRRORS.length; i++) {
            updated += mirrorInt(ctx, proxy, SERVER_MIRRORS[i], PollutionCfg.MAX_LIMIT);
        }
        return mirrorNote(updated);
    }

    /** Turns off the two client render flags, in memory and in the file. */
    private Step forceClientStep() {
        return ctx -> {
            if (FMLCommonHandler.instance()
                .getSide() != Side.CLIENT) {
                return Outcome.SKIPPED;
            }
            try {
                Map<String, String> onDisk = fileValues(ctx, PollutionCfg.CLIENT_CFG, PollutionCfg.CLIENT_KEYS);
                Class<?> clientConfig = Class.forName(CLIENT_CONFIG_CLASS);
                Field renderField = clientConfig.getDeclaredField(RENDER_FIELD);
                renderField.setAccessible(true);
                Object render = renderField.get(null);
                if (render == null) {
                    ctx.log()
                        .error("[PollutionNoNegatives] GregTech's render settings are null - left alone");
                    return Outcome.SKIPPED;
                }

                StringBuilder line = new StringBuilder("[PollutionNoNegatives] client: ");
                boolean any = false;
                for (int i = 0; i < CLIENT_FIELDS.length; i++) {
                    Field field;
                    try {
                        field = render.getClass()
                            .getDeclaredField(CLIENT_FIELDS[i]);
                    } catch (NoSuchFieldException absent) {
                        ctx.log()
                            .info(
                                "[PollutionNoNegatives] {} is not present in this GregTech version - left alone",
                                CLIENT_LABELS[i]);
                        continue;
                    }
                    field.setAccessible(true);
                    field.setBoolean(render, false);
                    if (any) {
                        line.append(", ");
                    }
                    line.append(CLIENT_LABELS[i])
                        .append("=false (file: ")
                        .append(fileSaid(onDisk, CLIENT_LABELS[i]))
                        .append(')');
                    any = true;
                }
                line.append(mirrorClient(ctx));
                if (any) {
                    ctx.log()
                        .info(line.toString());
                }
                clearTint(ctx);
                return Outcome.APPLIED;
            } catch (Throwable failure) {
                ctx.log()
                    .error(
                        "[PollutionNoNegatives] could not turn off GregTech's pollution render flags ({}) - left as "
                            + "they were",
                        failure.toString());
                return Outcome.SKIPPED;
            }
        };
    }

    /**
     * Writes the two render mirrors on {@code GTMod.proxy}, which the renderer reads instead of the client config
     * object. Never throws.
     *
     * @return the tail to append to the client log line
     */
    private static String mirrorClient(PatchContext ctx) {
        Object proxy = null;
        String reason = "proxy is null";
        try {
            proxy = gtProxy();
        } catch (Throwable failure) {
            reason = failure.toString();
        }
        if (proxy == null) {
            ctx.log()
                .error(
                    "[PollutionNoNegatives] could not reach GTMod.proxy ({}) - only the config fields were forced",
                    reason);
            return "; GTMod.proxy mirrors not updated";
        }
        int updated = 0;
        for (String mirror : CLIENT_MIRRORS) {
            updated += mirrorBoolean(ctx, proxy, mirror, false);
        }
        return mirrorNote(updated);
    }

    /** Clears the block-recolour field in memory only; nothing is written into the file. */
    private static void clearTint(PatchContext ctx) {
        try {
            Class<?> pollutionConfig = Class.forName(POLLUTION_CONFIG_CLASS);
            Field tint = pollutionConfig.getDeclaredField(TINT_FIELD);
            tint.setAccessible(true);
            tint.setBoolean(null, false);
            ctx.log()
                .info(
                    "[PollutionNoNegatives] client tint: pollutionBlockRecolor=false (memory only - nothing is "
                        + "written into that file)");
        } catch (NoSuchFieldException absent) {
            ctx.log()
                .info(
                    "[PollutionNoNegatives] pollutionBlockRecolor is not present in this GregTech version - left alone");
        } catch (Throwable failure) {
            ctx.log()
                .error(
                    "[PollutionNoNegatives] could not clear the pollution block-recolour tint ({}) - left alone",
                    failure.toString());
        }
    }

    /** The live {@code GTMod.proxy} instance; {@code null} when the field itself is null. */
    private static Object gtProxy() throws ReflectiveOperationException {
        Class<?> gtMod = Class.forName(GT_MOD_CLASS);
        Field proxyField = gtMod.getDeclaredField(PROXY_FIELD);
        proxyField.setAccessible(true);
        return proxyField.get(null);
    }

    /** The success tail for the mirror count: {@code 0} means the mirrors already said what we wanted. */
    private static String mirrorNote(int updated) {
        return updated > 0 ? "; GTMod.proxy mirrors updated (" + updated + " field(s))"
            : "; GTMod.proxy mirrors already say this";
    }

    /** Sets one boolean mirror; {@code 1} when it actually changed, {@code 0} otherwise. Never throws. */
    private static int mirrorBoolean(PatchContext ctx, Object proxy, String name, boolean wanted) {
        try {
            Field field = findField(proxy.getClass(), name);
            field.setAccessible(true);
            if (field.getBoolean(proxy) == wanted) {
                return 0;
            }
            field.setBoolean(proxy, wanted);
            return 1;
        } catch (NoSuchFieldException absent) {
            ctx.log()
                .info("[PollutionNoNegatives] {} is not present on GregTech's proxy - skipped", name);
            return 0;
        } catch (Throwable failure) {
            ctx.log()
                .error(
                    "[PollutionNoNegatives] could not set {} on GregTech's proxy ({}) - skipped",
                    name,
                    failure.toString());
            return 0;
        }
    }

    /** Sets one int mirror; {@code 1} when it actually changed, {@code 0} otherwise. Never throws. */
    private static int mirrorInt(PatchContext ctx, Object proxy, String name, int wanted) {
        try {
            Field field = findField(proxy.getClass(), name);
            field.setAccessible(true);
            if (field.getInt(proxy) == wanted) {
                return 0;
            }
            field.setInt(proxy, wanted);
            return 1;
        } catch (NoSuchFieldException absent) {
            ctx.log()
                .info("[PollutionNoNegatives] {} is not present on GregTech's proxy - skipped", name);
            return 0;
        } catch (Throwable failure) {
            ctx.log()
                .error(
                    "[PollutionNoNegatives] could not set {} on GregTech's proxy ({}) - skipped",
                    name,
                    failure.toString());
            return 0;
        }
    }

    /** Where the field really lives: GregTech declares these on {@code GTProxy}, and the client proxy extends it. */
    private static Field findField(Class<?> start, String name) throws NoSuchFieldException {
        for (Class<?> current = start; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException absent) {
                // walk up: inherited fields are not getDeclaredField() on the subclass
            }
        }
        throw new NoSuchFieldException(name);
    }

    /** What one config file says today; an empty map when it cannot be read. */
    private static Map<String, String> fileValues(PatchContext ctx, String relativePath, List<PollutionCfg.Key> keys) {
        File file = new File(ctx.configDirectory(), relativePath);
        try {
            if (!file.isFile()) {
                return PollutionCfg.values("", keys);
            }
            return PollutionCfg.values(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8), keys);
        } catch (IOException | RuntimeException unreadable) {
            return PollutionCfg.values("", keys);
        }
    }

    /** The file's value for one key, or {@code unknown} when the file had no such line. */
    private static String fileSaid(Map<String, String> onDisk, String label) {
        String value = onDisk.get(label);
        return value == null ? "unknown" : value;
    }
}
