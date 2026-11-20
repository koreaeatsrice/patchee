package com.jointspaceforce.patchee.features;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The PollutionNoVisuals switches, read straight from {@code config/patchee.cfg}.
 *
 * <p>
 * GregTech's {@code GTPacketPollution} is normally loaded by the class loader
 * before Forge's config system exists (GregTech registers its network packets
 * while the mods load), so the transformer cannot ask Forge for the switches: it
 * parses the file itself. The format understood is the one Forge writes
 * ({@code B:key=true} inside a {@code general { ... }} block); comments and
 * unknown keys are ignored and the documented defaults are used for a missing
 * file, a missing key or an unreadable value. A value that is not true/false is
 * ignored and the default stands, exactly like Forge's own
 * {@code Property.getBoolean}: a typo never quietly switches the feature off.
 *
 * <p>
 * The key names live in {@link PollutionNoVisuals} and are reused by
 * {@link com.jointspaceforce.patchee.Config}, so the early reader and the normal
 * config can never drift apart.
 *
 * <p>
 * Pure text in, plain values out: no Minecraft, no Forge, no file system in the
 * parsing path, so it is unit-testable.
 */
public final class PollutionNoVisualsSettings {

    private final boolean masterEnabled;
    private final boolean enabled;
    private final boolean fromFile;
    private final String path;

    private PollutionNoVisualsSettings(boolean masterEnabled, boolean enabled, boolean fromFile, String path) {
        this.masterEnabled = masterEnabled;
        this.enabled = enabled;
        this.fromFile = fromFile;
        this.path = path;
    }

    /** Read {@code config/patchee.cfg} relative to the game directory, or the defaults. */
    public static PollutionNoVisualsSettings read() {
        File file = new File(new File(System.getProperty("user.dir", "."), "config"), "patchee.cfg");
        String path = file.getPath();
        try {
            if (!file.isFile()) {
                return new PollutionNoVisualsSettings(true, PollutionNoVisuals.DEFAULT_ENABLED, false, path);
            }
            PollutionNoVisualsSettings parsed = parse(
                new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            return new PollutionNoVisualsSettings(parsed.masterEnabled, parsed.enabled, true, path);
        } catch (IOException | RuntimeException unreadable) {
            return new PollutionNoVisualsSettings(true, PollutionNoVisuals.DEFAULT_ENABLED, false, path);
        }
    }

    /** Parse config text. Unknown keys and comments are ignored. */
    public static PollutionNoVisualsSettings parse(String configText) {
        boolean master = true;
        boolean enabled = PollutionNoVisuals.DEFAULT_ENABLED;
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
                    Boolean parsed = readBoolean(value, master);
                    if (parsed != null) {
                        master = parsed.booleanValue();
                    }
                } else if (PollutionNoVisuals.ENABLE_KEY.equals(key)) {
                    Boolean parsed = readBoolean(value, enabled);
                    if (parsed != null) {
                        enabled = parsed.booleanValue();
                    }
                }
            }
        }
        return new PollutionNoVisualsSettings(master, enabled, false, "");
    }

    /** Forge writes the type in front of the key, e.g. {@code B:} or {@code I:}. */
    private static String stripTypePrefix(String key) {
        if (key.length() > 2 && key.charAt(1) == ':') {
            return key.substring(2)
                .trim();
        }
        return key;
    }

    /**
     * A strict boolean: only {@code true} or {@code false}, in any case, is a
     * value. Anything else ({@code maybe}, {@code yes}, an empty string) is
     * {@code null}, so the caller keeps what it already had — Forge's own
     * {@code Property.getBoolean} treats a malformed value the same way.
     *
     * @param value    the text after the {@code =}
     * @param fallback the value to report when there is no text at all
     * @return the parsed value, {@code fallback} for {@code null}, or
     *         {@code null} when the text is not true/false
     */
    private static Boolean readBoolean(String value, boolean fallback) {
        if (value == null) {
            return Boolean.valueOf(fallback);
        }
        if ("true".equalsIgnoreCase(value)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(value)) {
            return Boolean.FALSE;
        }
        return null;
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
