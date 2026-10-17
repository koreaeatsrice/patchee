package com.jointspaceforce.patchee.features;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The FinalCastleBreak switches, read straight from {@code config/patchee.cfg}.
 *
 * <p>
 * Twilight Forest's block classes are normally loaded by the class loader before
 * Forge's config system exists, so the transformer cannot ask Forge for the
 * switches: it parses the file itself. The format understood is the one Forge
 * writes ({@code B:key=true} inside a {@code general { ... }} block); comments
 * and unknown keys are ignored. A missing file or a missing key uses the
 * documented default, while an unreadable value for a boolean reads as
 * {@code false} — the same thing Forge's own boolean config read does, and the
 * safe direction (toward stock Twilight Forest behaviour).
 *
 * <p>
 * The key names live in {@link FinalCastleBreak} and are reused by
 * {@link com.jointspaceforce.patchee.Config}, so the early reader and the normal
 * config can never drift apart. Once Patchee's own pipeline runs, the
 * authoritative values from the config replace the seed.
 *
 * <p>
 * Pure text in, plain values out: no Minecraft, no Forge, no file system in the
 * parsing path, so it is unit-testable.
 */
public final class FinalCastleBreakSettings {

    /** The values a unit test pins {@link #current()} to; normally {@code null}. */
    private static volatile FinalCastleBreakSettings injected;

    private final boolean masterEnabled;
    private final boolean enabled;
    private final boolean fromFile;
    private final String path;

    private FinalCastleBreakSettings(boolean masterEnabled, boolean enabled, boolean fromFile, String path) {
        this.masterEnabled = masterEnabled;
        this.enabled = enabled;
        this.fromFile = fromFile;
        this.path = path;
    }

    /** Read {@code config/patchee.cfg} relative to the game directory, or the defaults. */
    public static FinalCastleBreakSettings read() {
        return read(new File(new File(System.getProperty("user.dir", "."), "config"), "patchee.cfg"));
    }

    /**
     * The settings the class-load patch uses. In the game this reads the real
     * file ({@link #read()}); a unit test injects fixed values through
     * {@link #injectForTest(FinalCastleBreakSettings)} so the test does not depend
     * on whatever {@code config/patchee.cfg} happens to sit in the working
     * directory.
     */
    public static FinalCastleBreakSettings current() {
        FinalCastleBreakSettings fixed = injected;
        return fixed != null ? fixed : read();
    }

    /** Test seam: make {@link #current()} return {@code settings} until cleared. */
    static void injectForTest(FinalCastleBreakSettings settings) {
        injected = settings;
    }

    /** Test seam: go back to reading the real file. */
    static void clearInjectedForTest() {
        injected = null;
    }

    /** Package-private: read one file. {@link #read()} supplies the game's path. */
    static FinalCastleBreakSettings read(File file) {
        String path = file.getPath();
        try {
            if (!file.isFile()) {
                return new FinalCastleBreakSettings(true, FinalCastleBreak.DEFAULT_ENABLED, false, path);
            }
            FinalCastleBreakSettings parsed = parse(
                new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            return new FinalCastleBreakSettings(parsed.masterEnabled, parsed.enabled, true, path);
        } catch (IOException | RuntimeException unreadable) {
            return new FinalCastleBreakSettings(true, FinalCastleBreak.DEFAULT_ENABLED, false, path);
        }
    }

    /** Parse config text. Unknown keys and comments are ignored. */
    public static FinalCastleBreakSettings parse(String configText) {
        boolean master = true;
        boolean enabled = FinalCastleBreak.DEFAULT_ENABLED;
        if (configText != null) {
            for (String raw : configText.split("\\r?\\n")) {
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
                } else if (FinalCastleBreak.ENABLE_KEY.equals(key)) {
                    enabled = Boolean.parseBoolean(value);
                }
            }
        }
        return new FinalCastleBreakSettings(master, enabled, false, "");
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
