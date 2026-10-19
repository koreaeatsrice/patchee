package com.jointspaceforce.patchee.features;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The TeamChunkCap switches, read straight from {@code config/patchee.cfg}.
 *
 * <p>
 * ServerUtilities' class is normally loaded by the class loader before Forge's
 * config system exists (its team data is built while the mods register), so the
 * transformer cannot ask Forge for the switches: it parses the file itself.
 * The format understood is the one Forge writes ({@code B:key=true} inside a
 * {@code general { ... }} block); comments and unknown keys are ignored and the
 * documented defaults are used for a missing file, a missing key or an
 * unreadable value. The old {@code teamChunkCap.maxClaimChunks} /
 * {@code teamChunkCap.maxLoadedChunks} number keys from earlier versions are
 * retired and deliberately ignored, so files that still carry them parse
 * cleanly.
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
    private final boolean fromFile;
    private final String path;

    private TeamChunkCapSettings(boolean masterEnabled, boolean enabled, boolean fromFile, String path) {
        this.masterEnabled = masterEnabled;
        this.enabled = enabled;
        this.fromFile = fromFile;
        this.path = path;
    }

    /** Read {@code config/patchee.cfg} relative to the game directory, or the defaults. */
    public static TeamChunkCapSettings read() {
        File file = new File(new File(System.getProperty("user.dir", "."), "config"), "patchee.cfg");
        String path = file.getPath();
        try {
            if (!file.isFile()) {
                return new TeamChunkCapSettings(true, TeamChunkCap.DEFAULT_ENABLED, false, path);
            }
            TeamChunkCapSettings parsed = parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            return new TeamChunkCapSettings(parsed.masterEnabled, parsed.enabled, true, path);
        } catch (IOException | RuntimeException unreadable) {
            return new TeamChunkCapSettings(true, TeamChunkCap.DEFAULT_ENABLED, false, path);
        }
    }

    /** Parse config text. Unknown keys, comments and the retired cap keys are ignored. */
    public static TeamChunkCapSettings parse(String configText) {
        boolean master = true;
        boolean enabled = TeamChunkCap.DEFAULT_ENABLED;
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
                }
            }
        }
        return new TeamChunkCapSettings(master, enabled, false, "");
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

    /** True when the values came from the file rather than from the defaults. */
    public boolean fromFile() {
        return fromFile;
    }

    /** The path the values were read from, for the log line. */
    public String path() {
        return path;
    }
}
