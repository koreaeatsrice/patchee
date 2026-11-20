package com.jointspaceforce.patchee.features;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The GregTech pollution keys Patchee rewrites, and a line-preserving editor for
 * the Forge config files that carry them.
 *
 * <p>
 * Two files are involved. {@code config/GregTech/Pollution.cfg} holds the master
 * switch {@code Activate Pollution} and the four effect limits (smog, poison,
 * vegetation, sour rain); {@code config/GregTech/Client.cfg} holds the two
 * render switches, {@code Pollution Fog} and {@code Pollution Dirt}, inside its
 * {@code render} section.
 *
 * <p>
 * GregTech writes these files through GTNHLib's {@code @Config}, which is plain
 * Forge 1.7.10 {@code Configuration} on disk: {@code #} comments, a section
 * block {@code pollution { ... }} and key lines such as
 * {@code B:"Activate Pollution"=true} or {@code I:pollutionSmogLimit=550000}.
 * Reading that back into Forge's {@code Configuration} and saving it would
 * reorder keys and drop user comments, so this class edits the text instead: a
 * line is touched only when it is one of the listed keys, only the value after
 * {@code =} changes, and every other byte - comments, blank lines, line endings,
 * order - is copied through untouched.
 *
 * <p>
 * Older risk: a hand edit can make a limit negative or a file read-only, so the
 * feature also forces the values in memory; this class is only the file half.
 *
 * <p>
 * Pure text in, text out: no Minecraft, no Forge, no file system, so it is
 * unit-testable.
 */
public final class PollutionCfg {

    /** The top of the int range: no pollution effect threshold can ever be reached. */
    public static final int MAX_LIMIT = Integer.MAX_VALUE;

    /** Pollution.cfg, relative to the game's {@code config/} directory. */
    public static final String POLLUTION_CFG = "GregTech/Pollution.cfg";

    /** Client.cfg, relative to the game's {@code config/} directory. */
    public static final String CLIENT_CFG = "GregTech/Client.cfg";

    /** The limit values as text, the form Forge writes. */
    private static final String MAX_TEXT = Integer.toString(MAX_LIMIT);

    private static final String POLLUTION_SECTION = "pollution";
    private static final String RENDER_SECTION = "render";

    private PollutionCfg() {}

    /** One config key: its Forge name, the names older builds used, and the wanted value. */
    public static final class Key {

        /** The name Forge writes today, also the key used in logs and result maps. */
        public final String name;

        /** Every spelling accepted on disk, the current one first, matched case-insensitively. */
        public final List<String> aliases;

        /** The {@code section { ... }} block the key belongs to, lowercased. */
        public final String section;

        /** The Forge type letter: {@code 'B'} for a boolean, {@code 'I'} for an int. */
        public final char type;

        /** The value to write, as text. */
        public final String value;

        private Key(String name, List<String> aliases, String section, char type, String value) {
            this.name = name;
            this.aliases = Collections.unmodifiableList(new ArrayList<String>(aliases));
            this.section = section;
            this.type = type;
            this.value = value;
        }

        /** A boolean key. The first alias is normally {@code name} itself. */
        public static Key bool(String name, String section, String value, String... aliases) {
            return new Key(name, Arrays.asList(aliases), section, 'B', value);
        }

        /** An integer key. The first alias is normally {@code name} itself. */
        public static Key integer(String name, String section, String value, String... aliases) {
            return new Key(name, Arrays.asList(aliases), section, 'I', value);
        }
    }

    /**
     * The five server-side keys, in the order the log line lists them:
     * pollution on, and every effect limit at the top of the int range.
     */
    public static final List<Key> SERVER_KEYS = Collections.unmodifiableList(
        Arrays.asList(
            Key.bool("Activate Pollution", POLLUTION_SECTION, "true", "Activate Pollution", "pollution"),
            Key.integer("pollutionSmogLimit", POLLUTION_SECTION, MAX_TEXT, "pollutionSmogLimit"),
            Key.integer("pollutionPoisonLimit", POLLUTION_SECTION, MAX_TEXT, "pollutionPoisonLimit"),
            Key.integer("pollutionVegetationLimit", POLLUTION_SECTION, MAX_TEXT, "pollutionVegetationLimit"),
            Key.integer("pollutionSourRainLimit", POLLUTION_SECTION, MAX_TEXT, "pollutionSourRainLimit")));

    /** The two client render keys: no smog fog, no dirt particles. */
    public static final List<Key> CLIENT_KEYS = Collections.unmodifiableList(
        Arrays.asList(
            Key.bool(
                "Pollution Fog",
                RENDER_SECTION,
                "false",
                "Pollution Fog",
                "renderPollutionFog",
                "RenderPollutionFog_true"),
            Key.bool(
                "Pollution Dirt",
                RENDER_SECTION,
                "false",
                "Pollution Dirt",
                "renderDirtParticles",
                "RenderDirtParticles_true")));

    /** What one {@link #apply(String, List)} pass did. */
    public static final class Edit {

        /** The whole file, with only the matched key values changed. */
        public final String text;

        /** How many key lines got a different value. */
        public final int changed;

        /** The keys no line matched, by their current name. */
        public final List<String> missing;

        /** What the file said for every key a line matched, by current name. */
        public final Map<String, String> found;

        private Edit(String text, int changed, List<String> missing, Map<String, String> found) {
            this.text = text;
            this.changed = changed;
            this.missing = Collections.unmodifiableList(new ArrayList<String>(missing));
            this.found = Collections.unmodifiableMap(new LinkedHashMap<String, String>(found));
        }

        /** True when at least one value differs, so the file must be written. */
        public boolean needsWrite() {
            return changed > 0;
        }

        /** True when every key is present and already at its wanted value. */
        public boolean alreadyCorrect() {
            return changed == 0 && missing.isEmpty();
        }
    }

    /**
     * Rewrites the listed keys in {@code text}, keeping every other byte.
     *
     * <p>
     * Each line keeps its own terminator, so CRLF files stay CRLF. A key matches
     * only inside its own section (or anywhere, in a file with no section at all
     * - a flat file); name matching is case-insensitive and accepts the older
     * spellings listed in {@link Key#aliases}. An inline {@code #} comment after
     * the value is kept verbatim after the new value.
     */
    public static Edit apply(String text, List<Key> keys) {
        String source = text == null ? "" : text;
        List<Key> wanted = keys == null ? Collections.<Key>emptyList() : keys;
        boolean flatFile = source.indexOf('{') < 0;

        Map<String, String> found = new LinkedHashMap<String, String>();
        List<String> missing = new ArrayList<String>();
        List<String> matched = new ArrayList<String>();
        StringBuilder out = new StringBuilder(source.length() + 32);
        String section = null;
        int changed = 0;

        for (String line : source.split("(?<=\\n)", -1)) {
            String content = contentOf(line);
            String ending = line.substring(content.length());
            String trimmed = content.trim();

            // A '#' line is a comment, never a key and never a section header, so it stays byte-identical.
            if (trimmed.startsWith("#")) {
                out.append(line);
                continue;
            }

            if (trimmed.endsWith("{")) {
                section = trimmed.substring(0, trimmed.length() - 1)
                    .trim()
                    .toLowerCase(Locale.ROOT);
                out.append(line);
                continue;
            }
            if ("}".equals(trimmed)) {
                section = null;
                out.append(line);
                continue;
            }

            int equals = content.indexOf('=');
            if (equals < 0) {
                out.append(line);
                continue;
            }

            String keyName = unquote(
                stripTypePrefix(
                    content.substring(0, equals)
                        .trim()));
            Key key = match(keyName, section, flatFile, wanted, matched);
            if (key == null) {
                out.append(line);
                continue;
            }
            matched.add(key.name);

            String valueRegion = content.substring(equals + 1);
            int comment = valueRegion.indexOf('#');
            String beforeComment = comment < 0 ? valueRegion : valueRegion.substring(0, comment);
            String commentPart = comment < 0 ? "" : valueRegion.substring(comment);

            int leadEnd = 0;
            while (leadEnd < beforeComment.length() && Character.isWhitespace(beforeComment.charAt(leadEnd))) {
                leadEnd++;
            }
            String lead = beforeComment.substring(0, leadEnd);
            String coreAndTrail = beforeComment.substring(leadEnd);
            int coreEnd = coreAndTrail.length();
            while (coreEnd > 0 && Character.isWhitespace(coreAndTrail.charAt(coreEnd - 1))) {
                coreEnd--;
            }
            String core = coreAndTrail.substring(0, coreEnd);
            String trail = coreAndTrail.substring(coreEnd);

            found.put(key.name, unquote(core.trim()));
            if (unquote(core.trim()).equals(key.value)) {
                out.append(line);
            } else {
                changed++;
                out.append(content, 0, equals + 1);
                out.append(lead);
                out.append(key.value);
                out.append(trail);
                out.append(commentPart);
                out.append(ending);
            }
        }

        for (Key key : wanted) {
            if (!matched.contains(key.name)) {
                missing.add(key.name);
            }
        }
        return new Edit(out.toString(), changed, missing, found);
    }

    /** What the file said for each key, by current name. Missing keys are absent from the map. */
    public static Map<String, String> values(String text, List<Key> keys) {
        return apply(text == null ? "" : text, keys).found;
    }

    /** The key whose alias matches {@code name} in this section, or {@code null}. */
    private static Key match(String name, String section, boolean flatFile, List<Key> keys,
        List<String> alreadyMatched) {
        for (Key key : keys) {
            if (alreadyMatched.contains(key.name)) {
                continue;
            }
            if (!flatFile && (section == null || !section.equalsIgnoreCase(key.section))) {
                continue;
            }
            for (String alias : key.aliases) {
                if (alias.equalsIgnoreCase(name)) {
                    return key;
                }
            }
        }
        return null;
    }

    /** One line without its {@code \n} or {@code \r\n} terminator. */
    private static String contentOf(String line) {
        int end = line.length();
        if (end > 0 && line.charAt(end - 1) == '\n') {
            end--;
        }
        if (end > 0 && line.charAt(end - 1) == '\r') {
            end--;
        }
        return line.substring(0, end);
    }

    /** Forge writes the type in front of the key, e.g. {@code B:} or {@code I:}. */
    private static String stripTypePrefix(String key) {
        if (key.length() > 2 && key.charAt(1) == ':') {
            return key.substring(2)
                .trim();
        }
        return key;
    }

    /** {@code "Pollution Fog"} and {@code Pollution Fog} are the same key name. */
    private static String unquote(String name) {
        if (name.length() >= 2 && name.startsWith("\"") && name.endsWith("\"")) {
            return name.substring(1, name.length() - 1);
        }
        return name;
    }
}
