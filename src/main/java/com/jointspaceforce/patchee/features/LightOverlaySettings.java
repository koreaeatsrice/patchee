package com.jointspaceforce.patchee.features;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The two LightOverlay values, read straight from {@code config/patchee.cfg}.
 *
 * <p>
 * The F7 tweak is applied while NEI's class is being loaded, which happens
 * before Forge's config system exists, so this class parses the file itself.
 * It understands the format Forge writes ({@code B:key=true} /
 * {@code I:key=0} inside a {@code general { ... }} block), ignores comments and
 * anything it does not know, and falls back to the documented defaults for a
 * missing file, a missing key or an unreadable value. The key names live here
 * and are reused by {@link com.jointspaceforce.patchee.Config}, so the early
 * reader and the normal config can never drift apart.
 *
 * <p>
 * Pure text in, plain values out: no Minecraft, no Forge, no file system in the
 * parsing path, so it is unit-testable.
 */
public final class LightOverlaySettings {

    /** The feature switch Forge writes, e.g. {@code B:enableLightOverlayTweak=true}. */
    public static final String MASTER_KEY = "enableLightOverlayTweak";

    /** The light level Forge writes, e.g. {@code I:lightOverlay.maxLightNormal=0}. */
    public static final String MAX_LIGHT_KEY = "lightOverlay.maxLightNormal";

    /** Used when the config file or the key is not there yet. */
    public static final boolean DEFAULT_ENABLED = true;

    /** Used when the config file or the key is not there yet. */
    public static final int DEFAULT_MAX_LIGHT = 0;

    private final boolean enabled;
    private final int maxLight;
    private final boolean fromFile;
    private final String path;

    private LightOverlaySettings(boolean enabled, int maxLight, boolean fromFile, String path) {
        this.enabled = enabled;
        this.maxLight = maxLight;
        this.fromFile = fromFile;
        this.path = path;
    }

    /** Read {@code config/patchee.cfg} relative to the game directory, or the defaults. */
    public static LightOverlaySettings read() {
        File file = new File(new File(System.getProperty("user.dir", "."), "config"), "patchee.cfg");
        String path = file.getPath();
        try {
            if (!file.isFile()) {
                return new LightOverlaySettings(DEFAULT_ENABLED, DEFAULT_MAX_LIGHT, false, path);
            }
            LightOverlaySettings parsed = parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            return new LightOverlaySettings(parsed.enabled, parsed.maxLight, true, path);
        } catch (IOException | RuntimeException unreadable) {
            return new LightOverlaySettings(DEFAULT_ENABLED, DEFAULT_MAX_LIGHT, false, path);
        }
    }

    /** Parse config text. Unknown keys and comments are ignored. */
    public static LightOverlaySettings parse(String configText) {
        boolean enabled = DEFAULT_ENABLED;
        int maxLight = DEFAULT_MAX_LIGHT;
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
                if (MASTER_KEY.equals(key)) {
                    enabled = Boolean.parseBoolean(value);
                } else if (MAX_LIGHT_KEY.equals(key)) {
                    try {
                        maxLight = Integer.parseInt(value);
                    } catch (NumberFormatException ignored) {
                        // a value we cannot read leaves the default in place
                    }
                }
            }
        }
        return new LightOverlaySettings(enabled, maxLight, false, "");
    }

    /** Forge writes the type in front of the key, e.g. {@code B:} or {@code I:}. */
    private static String stripTypePrefix(String key) {
        if (key.length() > 2 && key.charAt(1) == ':') {
            return key.substring(2)
                .trim();
        }
        return key;
    }

    /** Whether the feature switch is on. */
    public boolean enabled() {
        return enabled;
    }

    /** The owner's light level: an X is drawn at light levels up to and including this. */
    public int maxLight() {
        return maxLight;
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
