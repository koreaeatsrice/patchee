package com.jointspaceforce.patchee.features;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import com.jointspaceforce.patchee.core.Feature;
import com.jointspaceforce.patchee.core.Outcome;
import com.jointspaceforce.patchee.core.Step;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;

/**
 * Stops a Patchee client from drawing GregTech's pollution, without changing a
 * single number the server sends.
 *
 * <p>
 * GregTech's packet still carries the real pollution value, so damage, sour
 * rain, vegetation, scanners, the pollution detector and every machine reading
 * pollution keep working exactly as before. The feature only turns off what the
 * client draws: the two render flags on {@code Client.render} and their mirrors
 * on {@code GTMod.proxy}, plus the five block-tint tables. GregTech rebuilds the
 * tint tables on world load, so {@link PollutionVisualsGuard} repeats the writes
 * every client tick.
 *
 * <p>
 * The first step decides, then arms the guard and writes the current session.
 * The second step writes the two {@code Pollution Fog} / {@code Pollution Dirt}
 * lines in {@code config/GregTech/Client.cfg} when that file exists, with a
 * timestamped {@code .bak}; only those two lines change. A dedicated server
 * skips both steps and says so once.
 */
public final class PollutionVisualsFeature implements Feature {

    public static final String ID = PollutionVisuals.ID;

    private static final String TARGET_CLASS = "gregtech.common.pollution.PollutionRenderer";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String configKey() {
        return PollutionVisuals.ENABLE_KEY;
    }

    @Override
    public boolean defaultEnabled() {
        return PollutionVisuals.DEFAULT_ENABLED;
    }

    @Override
    public String description() {
        return "Feature (ON by default, client-side): true = on a client, GregTech's Pollution Fog and Pollution "
            + "Dirt are turned off in memory every tick and, when config/GregTech/Client.cfg exists, written false "
            + "there with a timestamped .bak (only those two lines change); the five block-tint tables are kept "
            + "empty, so no smog, fog colour shift, smoke particles or grass/leaf/water/flower tints are drawn. A "
            + "dedicated server does nothing and logs that it skipped. A client without Patchee still draws "
            + "GregTech's own smog when pollution is high enough, because the real number is sent to it. false = "
            + "GregTech's client visuals left as they ship.";
    }

    @Override
    public String disabledMessage() {
        return "[PollutionVisuals] disabled in config - GregTech's client pollution visuals left as they ship";
    }

    @Override
    public String failureMessage() {
        return "[PollutionVisuals] failed - GregTech's client pollution visuals left as they were";
    }

    @Override
    public String targetClass() {
        return TARGET_CLASS;
    }

    @Override
    public String missingTargetMessage() {
        return "[PollutionVisuals] GregTech is not installed - skipped";
    }

    @Override
    public List<Step> steps() {
        return Arrays.asList(applyStep(), fileStep());
    }

    /** Decides, then arms the per-tick guard and does the first pass at once. */
    private Step applyStep() {
        return ctx -> {
            PollutionVisuals.Decision decision = PollutionVisuals.decide(
                ctx.toggles()
                    .masterEnabled(),
                ctx.toggles()
                    .featureEnabled(ID),
                FMLCommonHandler.instance()
                    .getSide() == Side.CLIENT);
            if (decision == PollutionVisuals.Decision.SERVER) {
                ctx.log()
                    .info("[PollutionVisuals] dedicated server - no client renderer, skipped");
                return Outcome.SKIPPED;
            }
            if (decision == PollutionVisuals.Decision.DISABLED) {
                ctx.log()
                    .info(disabledMessage());
                return Outcome.SKIPPED;
            }
            int cleared = PollutionVisualsGuard.enforce(ctx.log());
            PollutionVisualsGuard.install(ctx.log());
            if (cleared < 0) {
                ctx.log()
                    .error("[PollutionVisuals] the block-tint pass threw - tint tables left as they are");
                return Outcome.FAILED;
            }
            ctx.log()
                .info(
                    "[PollutionVisuals] armed - client: Pollution Fog=false, Pollution Dirt=false, {} tint table(s) "
                        + "emptied; pollution numbers still arrive unchanged",
                    cleared);
            return Outcome.APPLIED;
        };
    }

    /** Writes the two render flags into GregTech's Client.cfg when it already exists. */
    private Step fileStep() {
        return ctx -> {
            if (FMLCommonHandler.instance()
                .getSide() != Side.CLIENT) {
                return Outcome.SKIPPED;
            }
            File cfg = new File(ctx.configDirectory(), PollutionVisuals.CLIENT_CFG);
            if (!cfg.isFile()) {
                ctx.log()
                    .info("[PollutionVisuals] {} not there yet - nothing written", PollutionVisuals.CLIENT_CFG);
                return Outcome.SKIPPED;
            }
            PollutionCfgWriter.Result result = PollutionCfgWriter.rewrite(cfg, PollutionCfg.CLIENT_KEYS, timestamp());
            String line = "[PollutionVisuals] " + PollutionVisuals.CLIENT_CFG
                + " "
                + result.status
                + " - changed: "
                + join(result.changedKeys)
                + (result.backupPath.isEmpty() ? "" : "; backup " + result.backupPath);
            if (result.status == PollutionCfgWriter.Status.FAILED) {
                ctx.log()
                    .warn(line);
                return Outcome.FAILED;
            }
            ctx.log()
                .info(line);
            return Outcome.APPLIED;
        };
    }

    private static String join(List<String> values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(value);
        }
        return out.length() == 0 ? "none" : out.toString();
    }

    /** UTC, sortable, safe in a file name - the same shape PollutionPreload writes. */
    private static String timestamp() {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }
}
