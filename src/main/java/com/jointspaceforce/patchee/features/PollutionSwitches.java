package com.jointspaceforce.patchee.features;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The two PollutionNoNegatives switches, read straight from
 * {@code config/patchee.cfg}.
 *
 * <p>
 * GregTech reads its pollution config in its own static initializer, before any
 * mod's preInit, so the coremod half of this feature cannot ask Forge for the
 * switches: it parses the file itself. The format understood is the one Forge
 * writes ({@code B:key=true}); comments and unknown keys are ignored and the
 * documented defaults (both on) are used for a missing file, a missing key or
 * an unreadable value.
 *
 * <p>
 * Pure text in, plain values out: no Minecraft, no Forge, no file system in the
 * parsing path, so it is unit-testable.
 */
public final class PollutionSwitches {

    /** The master switch every feature shares. */
    public static final String MASTER_KEY = "enabled";

    /** This feature's own switch. */
    public static final String FEATURE_KEY = "enablePollutionNoNegatives";

    /** The value used when the file has no such key. */
    public static final boolean DEFAULT_ENABLED = true;

    private final boolean masterEnabled;
    private final boolean featureEnabled;
    private final boolean fromFile;
    private final String path;

    private PollutionSwitches(boolean masterEnabled, boolean featureEnabled, boolean fromFile, String path) {
        this.masterEnabled = masterEnabled;
        this.featureEnabled = featureEnabled;
        this.fromFile = fromFile;
        this.path = path;
    }

    /** Read one {@code patchee.cfg}, or the defaults for a missing or unreadable file. */
    public static PollutionSwitches read(File patcheeCfg) {
        String path = patcheeCfg == null ? "" : patcheeCfg.getPath();
        try {
            if (patcheeCfg == null || !patcheeCfg.isFile()) {
                return new PollutionSwitches(true, DEFAULT_ENABLED, false, path);
            }
            PollutionSwitches parsed = parse(
                new String(Files.readAllBytes(patcheeCfg.toPath()), StandardCharsets.UTF_8));
            return new PollutionSwitches(parsed.masterEnabled, parsed.featureEnabled, true, path);
        } catch (IOException | RuntimeException unreadable) {
            return new PollutionSwitches(true, DEFAULT_ENABLED, false, path);
        }
    }

    /** Parse config text. Unknown keys and comments are ignored. */
    public static PollutionSwitches parse(String configText) {
        boolean master = true;
        boolean feature = DEFAULT_ENABLED;
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
                    master = Boolean.parseBoolean(value);
                } else if (FEATURE_KEY.equals(key)) {
                    feature = Boolean.parseBoolean(value);
                }
            }
        }
        return new PollutionSwitches(master, feature, false, "");
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

    /** Whether the pollution fix may run at all. */
    public boolean featureEnabled() {
        return featureEnabled;
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
