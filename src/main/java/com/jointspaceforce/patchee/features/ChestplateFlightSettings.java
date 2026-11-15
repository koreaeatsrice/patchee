package com.jointspaceforce.patchee.features;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The three ChestplateFlight switches, read straight from {@code config/patchee.cfg}.
 *
 * <p>
 * IC2 loads {@code ItemArmorQuantumSuit} before Forge's config system exists, so
 * the transformer cannot ask Forge for the switches: it parses the file itself.
 * The format understood is the one Forge writes ({@code B:key=true} inside a
 * {@code general { ... }} block); comments and unknown keys are ignored. A
 * missing file or a missing key uses the documented default; an unreadable
 * boolean reads as {@code false}, the same thing Forge's own boolean read does
 * and the safe direction (toward stock IC2 behaviour).
 *
 * <p>
 * The key names live in {@link ChestplateFlight} and are reused by
 * {@link com.jointspaceforce.patchee.Config}, so the early reader and the normal
 * config cannot drift apart.
 *
 * <p>
 * Pure text in, plain values out: no Minecraft, no Forge, no file system in the
 * parsing path, so it is unit-testable.
 */
public final class ChestplateFlightSettings {

    /** The values a unit test pins {@link #current()} to; normally {@code null}. */
    private static volatile ChestplateFlightSettings injected;

    private final boolean masterEnabled;
    private final boolean liftEnabled;
    private final boolean holdEnabled;
    private final boolean guardEnabled;
    private final boolean fromFile;
    private final String path;

    private ChestplateFlightSettings(boolean masterEnabled, boolean liftEnabled, boolean holdEnabled,
        boolean guardEnabled, boolean fromFile, String path) {
        this.masterEnabled = masterEnabled;
        this.liftEnabled = liftEnabled;
        this.holdEnabled = holdEnabled;
        this.guardEnabled = guardEnabled;
        this.fromFile = fromFile;
        this.path = path;
    }

    /** Read {@code config/patchee.cfg} relative to the game directory, or the defaults. */
    public static ChestplateFlightSettings read() {
        return read(new File(new File(System.getProperty("user.dir", "."), "config"), "patchee.cfg"));
    }

    /**
     * The settings the class-load patch uses: the real file in the game, or the
     * value a test injected through {@link #injectForTest(ChestplateFlightSettings)}.
     */
    public static ChestplateFlightSettings current() {
        ChestplateFlightSettings fixed = injected;
        return fixed != null ? fixed : read();
    }

    /** Test seam: make {@link #current()} return {@code settings} until cleared. */
    static void injectForTest(ChestplateFlightSettings settings) {
        injected = settings;
    }

    /** Test seam: go back to reading the real file. */
    static void clearInjectedForTest() {
        injected = null;
    }

    /** Package-private: read one file. {@link #read()} supplies the game's path. */
    static ChestplateFlightSettings read(File file) {
        String path = file.getPath();
        try {
            if (!file.isFile()) {
                return new ChestplateFlightSettings(
                    true,
                    ChestplateFlight.DEFAULT_ENABLED,
                    ChestplateFlight.DEFAULT_ENABLED,
                    ChestplateFlight.DEFAULT_ENABLED,
                    false,
                    path);
            }
            ChestplateFlightSettings parsed = parse(
                new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            return new ChestplateFlightSettings(
                parsed.masterEnabled,
                parsed.liftEnabled,
                parsed.holdEnabled,
                parsed.guardEnabled,
                true,
                path);
        } catch (IOException | RuntimeException unreadable) {
            return new ChestplateFlightSettings(
                true,
                ChestplateFlight.DEFAULT_ENABLED,
                ChestplateFlight.DEFAULT_ENABLED,
                ChestplateFlight.DEFAULT_ENABLED,
                false,
                path);
        }
    }

    /** Parse config text. Unknown keys and comments are ignored. */
    public static ChestplateFlightSettings parse(String configText) {
        boolean master = true;
        boolean lift = ChestplateFlight.DEFAULT_ENABLED;
        boolean hold = ChestplateFlight.DEFAULT_ENABLED;
        boolean guard = ChestplateFlight.DEFAULT_ENABLED;
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
                } else if (ChestplateFlight.LIFT_KEY.equals(key)) {
                    lift = Boolean.parseBoolean(value);
                } else if (ChestplateFlight.HOLD_KEY.equals(key)) {
                    hold = Boolean.parseBoolean(value);
                } else if (ChestplateFlight.GUARD_KEY.equals(key)) {
                    guard = Boolean.parseBoolean(value);
                }
            }
        }
        return new ChestplateFlightSettings(master, lift, hold, guard, false, "");
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

    /** Whether the hover vertical-speed fix may be applied. */
    public boolean liftEnabled() {
        return liftEnabled;
    }

    /** Whether the shift+space hold-altitude branch may be applied. */
    public boolean holdEnabled() {
        return holdEnabled;
    }

    /** Whether the ground guard may be applied. */
    public boolean guardEnabled() {
        return guardEnabled;
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
