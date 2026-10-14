package com.jointspaceforce.patchee.features;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The TeamChunkCap values, read straight from {@code config/patchee.cfg}.
 *
 * <p>
 * ServerUtilities' class is normally loaded by the class loader before Forge's
 * config system exists (its team data is built while the mods register), so the
 * transformer cannot ask Forge for the switches: it parses the file itself.
 * The format understood is the one Forge writes ({@code B:key=true} /
 * {@code I:key=1500} inside a {@code general { ... }} block); comments and
 * unknown keys are ignored and the documented defaults are used for a missing
 * file, a missing key or an unreadable value.
 *
 * <p>
 * The key names live in {@link TeamChunkCap} and are reused by
 * {@link com.jointspaceforce.patchee.Config}, so the early reader and the normal
 * config can never drift apart. Once Patchee's own pipeline runs, the
 * authoritative values from the config replace the seed.
 *
 * <p>
 * Pure text in, plain values out: no Minecraft, no Forge, no file system in the
 * parsing path, so it is unit-testable.
 */
public final class TeamChunkCapSettings {

    private final boolean masterEnabled;
    private final boolean enabled;
    private final int maxClaims;
    private final int maxLoads;
    private final boolean fromFile;
    private final String path;

    private TeamChunkCapSettings(boolean masterEnabled, boolean enabled, int maxClaims, int maxLoads, boolean fromFile,
        String path) {
        this.masterEnabled = masterEnabled;
        this.enabled = enabled;
        this.maxClaims = maxClaims;
        this.maxLoads = maxLoads;
        this.fromFile = fromFile;
        this.path = path;
    }

    /** Read {@code config/patchee.cfg} relative to the game directory, or the defaults. */
    public static TeamChunkCapSettings read() {
        File file = new File(new File(System.getProperty("user.dir", "."), "config"), "patchee.cfg");
        String path = file.getPath();
        try {
            if (!file.isFile()) {
                return new TeamChunkCapSettings(
                    true,
                    TeamChunkCap.DEFAULT_ENABLED,
                    TeamChunkCap.DEFAULT_MAX_CLAIMS,
                    TeamChunkCap.DEFAULT_MAX_LOADS,
                    false,
                    path);
            }
            TeamChunkCapSettings parsed = parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            return new TeamChunkCapSettings(
                parsed.masterEnabled,
                parsed.enabled,
                parsed.maxClaims,
                parsed.maxLoads,
                true,
                path);
        } catch (IOException | RuntimeException unreadable) {
            return new TeamChunkCapSettings(
                true,
                TeamChunkCap.DEFAULT_ENABLED,
                TeamChunkCap.DEFAULT_MAX_CLAIMS,
                TeamChunkCap.DEFAULT_MAX_LOADS,
                false,
                path);
        }
    }

    /** Parse config text. Unknown keys and comments are ignored. */
    public static TeamChunkCapSettings parse(String configText) {
        boolean master = true;
        boolean enabled = TeamChunkCap.DEFAULT_ENABLED;
        int maxClaims = TeamChunkCap.DEFAULT_MAX_CLAIMS;
        int maxLoads = TeamChunkCap.DEFAULT_MAX_LOADS;
        if (configText != null) {
            for (String raw : configText.split("\r?\n")) {
                int comment = raw.indexOf('#');
                String line = (comment < 0 ? raw : raw.substring(0, comment)).trim();
                int equals = line.indexOf('=');
                if (equals < 0) {
                    continue;
                }
                String key = stripTypePrefix(
                    line.substring(0, equals)
                        .trim());
                String value = line.substring(equals + 1)
                    .trim();
                if ("enabled".equals(key)) {
                    master = Boolean.parseBoolean(value);
                } else if (TeamChunkCap.ENABLE_KEY.equals(key)) {
                    enabled = Boolean.parseBoolean(value);
                } else if (TeamChunkCap.CLAIMS_KEY.equals(key)) {
                    maxClaims = readInt(value, maxClaims);
                } else if (TeamChunkCap.LOADS_KEY.equals(key)) {
                    maxLoads = readInt(value, maxLoads);
                }
            }
        }
        return new TeamChunkCapSettings(master, enabled, maxClaims, maxLoads, false, "");
    }

    /** A value we cannot read leaves the default in place. */
    private static int readInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    /** Forge writes the type in front of the key, e.g. {@code B:} or {@code I:}. */
    private static String stripTypePrefix(String key) {
        if (key.length() > 2 && key.charAt(1) == ':') {
            return key.substring(2)
                .trim();
        }
        return key;
    }

    public boolean masterEnabled() {
        return masterEnabled;
    }

    public boolean enabled() {
        return enabled;
    }

    public int maxClaims() {
        return maxClaims;
    }

    public int maxLoads() {
        return maxLoads;
    }

    /** True when the values came from the file rather than from the defaults. */
    public boolean fromFile() {
        return fromFile;
    }

    /** The path the values were read from, for the log line. */
    public String path() {
        return path;
    }
}
