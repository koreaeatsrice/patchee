package com.jointspaceforce.patchee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * The README renders straight onto the public site, so a feature line that grew a second sentence is a
 * user-visible defect. This guard parses the {@code ## Features} list and fails, naming the line, when
 * any bullet text is more than one sentence or stops using the house bullet shape.
 *
 * <p>
 * A sentence end is a {@code .}, {@code !} or {@code ?} followed, past any closing bracket or quote, by
 * whitespace or end of line. Three allowances keep that honest: a dot between two digits (a decimal
 * such as {@code 0.6}, a version such as {@code v1.8.0}); an abbreviation ({@code e.g.}, {@code i.e.},
 * {@code etc.}, {@code vs.}, {@code cf.}) when the next word continues the clause, which means lowercase,
 * a number, a short all-caps acronym, or a capitalised word followed by a comma (a capitalised word with
 * no comma after it reads as a new sentence, which is deliberately strict); and text with no terminal
 * punctuation, counted as one sentence rather than zero. The checker itself is unit-tested against
 * synthetic bullets so it cannot quietly pass everything.
 */
class ReadmeFeatureSentencesTest {

    /** Abbreviations whose final dot may be part of the word rather than a sentence end. */
    private static final List<String> ABBREVIATIONS = Arrays.asList("e.g.", "i.e.", "etc.", "vs.", "cf.");

    /** {@code - **Name** — text} with an em dash as the separator, exactly as the site renders it. */
    private static final Pattern FEATURE_BULLET = Pattern.compile("^- \\*\\*(.+?)\\*\\* — (.+)$");

    /** Any list marker, so an asterisk, plus or indented bullet is reported instead of skipped. */
    private static final Pattern BULLET_START = Pattern.compile("^\\s*[-*+]\\s");

    /** A bullet plus its indented continuation lines, with the 1-based line number it starts on. */
    private static final class FeatureLine {

        private final int number;
        private String text;

        FeatureLine(int number, String text) {
            this.number = number;
            this.text = text;
        }
    }

    @Test
    void everyFeatureBulletInTheReadmeIsOneSentence() throws IOException {
        Path readme = Paths.get("README.md");
        assertTrue(Files.isRegularFile(readme), "README.md must exist at the project root");
        List<String> lines = Files.readAllLines(readme, StandardCharsets.UTF_8);

        List<String> bullets = featureBullets(lines);
        assertFalse(bullets.isEmpty(), "README.md must carry a ## Features list with bullets");
        assertTrue(bullets.size() >= 5, "the ## Features list looks truncated: " + bullets.size() + " bullets");

        List<String> violations = violations(lines);
        assertTrue(
            violations.isEmpty(),
            "Every README feature bullet must be one sentence. Offending bullets:\n" + String.join("\n", violations));
    }

    @Test
    void countsSentencesAndAllowsCommonFalsePositives() {
        assertEquals(1, sentenceCount("moves bee housings with their bees."));
        assertEquals(1, sentenceCount("runs at 0.6 speed."));
        assertEquals(1, sentenceCount("needs v1.8.0 or newer."));
        assertEquals(1, sentenceCount("all mods, e.g. jetpacks, work."));
        assertEquals(1, sentenceCount("all mods, e.g. Patchee, work."));
        assertEquals(1, sentenceCount("all mods, i.e. every one of them, work."));
        assertEquals(1, sentenceCount("such as E.g. IC2 work."));
        assertEquals(1, sentenceCount("covers Forestry, IC2, etc. and more."));
        assertEquals(1, sentenceCount("the mod, cf. the docs, works."));
        assertEquals(1, sentenceCount("one thing; another thing."));
        assertEquals(1, sentenceCount("no terminal punctuation"));
        assertEquals(1, sentenceCount(""));
        assertEquals(2, sentenceCount("break in 10 ticks. Shears cut arctic fur."));
        assertEquals(2, sentenceCount("Really? Yes!"));
        assertEquals(3, sentenceCount("One. Two! Three?"));
        assertEquals(2, sentenceCount("it climbs (see docs.) Then it sinks."));
        assertEquals(2, sentenceCount("he said \"stop.\" Then left."));
        assertEquals(2, sentenceCount("works with IC2, etc. Users see the change."));
        assertEquals(2, sentenceCount("Ends in e.g. Next sentence here."));
        assertEquals(2, sentenceCount("one sentence.\u00A0Two sentences."));
    }

    @Test
    void rejectsBadShapedOrMultiSentenceFeatureLinesOnly() {
        List<String> lines = Arrays.asList(
            "# Title",
            "",
            "## Features",
            "",
            "- **Good** — one sentence only.",
            "- **Bad** — first sentence. second sentence.",
            "- **Wrong shape**: first sentence. second sentence.",
            "- **Wrapped** — first sentence here.",
            "  second sentence wraps.",
            "",
            "* **Star** — first sentence. second sentence.",
            "",
            "## Install",
            "",
            "- **Not a feature** — first sentence. second sentence.");

        assertEquals(3, featureBullets(lines).size());
        List<String> violations = violations(lines);
        assertEquals(4, violations.size(), String.join("\n", violations));
        assertTrue(
            violations.get(0)
                .contains("**Bad**"),
            violations.get(0));
        assertTrue(
            violations.get(1)
                .contains("not in"),
            violations.get(1));
        assertTrue(
            violations.get(2)
                .contains("2 sentences"),
            violations.get(2));
        assertTrue(
            violations.get(3)
                .contains("not in"),
            violations.get(3));
    }

    /** Bullets in the {@code ## Features} section, with indented continuation lines joined on. */
    static List<String> featureBullets(List<String> lines) {
        List<String> bullets = new ArrayList<>();
        for (FeatureLine line : featureLines(lines)) {
            if (FEATURE_BULLET.matcher(line.text)
                .matches()) {
                bullets.add(line.text);
            }
        }
        return bullets;
    }

    /** One message per bad feature line: more than one sentence, or not in the house bullet shape. */
    static List<String> violations(List<String> lines) {
        List<String> violations = new ArrayList<>();
        for (FeatureLine line : featureLines(lines)) {
            Matcher matcher = FEATURE_BULLET.matcher(line.text);
            if (!matcher.matches() || matcher.group(2)
                .trim()
                .isEmpty()) {
                violations.add("line " + line.number + ": not in `- **Name** — text` form: " + line.text);
                continue;
            }
            int sentences = sentenceCount(matcher.group(2));
            if (sentences > 1) {
                violations.add("line " + line.number + ": " + sentences + " sentences: " + line.text);
            }
        }
        return violations;
    }

    /**
     * Feature lines with continuation lines joined to their bullet. Every non-blank line in the section
     * becomes part of some entry, so a stray line is reported rather than silently skipped.
     */
    static List<FeatureLine> featureLines(List<String> lines) {
        List<FeatureLine> entries = new ArrayList<>();
        boolean inFeatures = false;
        FeatureLine current = null;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith("## ")) {
                inFeatures = line.trim()
                    .equals("## Features");
                current = null;
                continue;
            }
            if (!inFeatures) {
                continue;
            }
            if (line.trim()
                .isEmpty() || line.startsWith("#")) {
                current = null;
            } else if (BULLET_START.matcher(line)
                .lookingAt()) {
                    current = new FeatureLine(i + 1, line.trim());
                    entries.add(current);
                } else if (current != null) {
                    current.text = current.text + " " + line.trim();
                } else {
                    entries.add(new FeatureLine(i + 1, line.trim()));
                }
        }
        return entries;
    }

    /** Counts sentence ends, stepping over closing brackets and quotes; no end still means one. */
    static int sentenceCount(String text) {
        String masked = maskNonSentenceDots(text);
        int count = 0;
        for (int i = 0; i < masked.length(); i++) {
            char c = masked.charAt(i);
            if (c != '.' && c != '!' && c != '?') {
                continue;
            }
            int next = i + 1;
            while (next < masked.length() && isClosing(masked.charAt(next))) {
                next++;
            }
            boolean atEnd = next == masked.length();
            boolean followedBySpace = !atEnd && isSpace(masked.charAt(next));
            if (atEnd || followedBySpace) {
                count++;
            }
        }
        return Math.max(count, 1);
    }

    private static boolean isClosing(char c) {
        return c == ')' || c == ']' || c == '}' || c == '"' || c == '\'' || c == '\u201d' || c == '\u2019';
    }

    /** Whitespace as it arrives from a web page: NBSP and zero-width space count too. */
    private static boolean isSpace(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c) || c == '\u200b';
    }

    /** Blanks the dots that cannot end a sentence, keeping the length so indexes stay put. */
    private static String maskNonSentenceDots(String text) {
        StringBuilder out = new StringBuilder(text);
        for (int i = 0; i < out.length(); i++) {
            String abbreviation = abbreviationAt(out, i);
            if (abbreviation == null) {
                continue;
            }
            int last = i + abbreviation.length() - 1;
            for (int dot = i; dot < last; dot++) {
                if (out.charAt(dot) == '.') {
                    out.setCharAt(dot, '#');
                }
            }
            if (!startsNewSentence(out, last + 1)) {
                out.setCharAt(last, '#');
            }
            i = last;
        }
        for (int i = 1; i < out.length() - 1; i++) {
            boolean betweenDigits = Character.isDigit(out.charAt(i - 1)) && Character.isDigit(out.charAt(i + 1));
            if (out.charAt(i) == '.' && betweenDigits) {
                out.setCharAt(i, '#');
            }
        }
        return out.toString();
    }

    private static String abbreviationAt(CharSequence text, int at) {
        if (at > 0 && Character.isLetter(text.charAt(at - 1))) {
            return null;
        }
        for (String abbreviation : ABBREVIATIONS) {
            if (at + abbreviation.length() <= text.length() && regionMatchesIgnoreCase(text, at, abbreviation)) {
                return abbreviation;
            }
        }
        return null;
    }

    private static boolean regionMatchesIgnoreCase(CharSequence text, int at, String word) {
        for (int i = 0; i < word.length(); i++) {
            if (Character.toLowerCase(text.charAt(at + i)) != Character.toLowerCase(word.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * True when the word after an abbreviation reads as a new sentence. Nothing left, or a capitalised
     * ordinary word without a comma after it, starts a sentence; everything else continues the clause.
     */
    private static boolean startsNewSentence(CharSequence text, int from) {
        int start = skipSpaces(text, from);
        if (start >= text.length()) {
            return true;
        }
        if (!Character.isLetter(text.charAt(start))) {
            return false;
        }
        int end = start;
        boolean hasDigit = false;
        boolean hasLower = false;
        while (end < text.length() && Character.isLetterOrDigit(text.charAt(end))) {
            hasDigit = hasDigit || Character.isDigit(text.charAt(end));
            hasLower = hasLower || Character.isLowerCase(text.charAt(end));
            end++;
        }
        String word = text.subSequence(start, end)
            .toString();
        if (hasDigit || !Character.isUpperCase(word.charAt(0))) {
            return false;
        }
        if (!hasLower) {
            return word.length() == 1;
        }
        int after = skipSpaces(text, end);
        return after >= text.length() || text.charAt(after) != ',';
    }

    private static int skipSpaces(CharSequence text, int from) {
        int at = from;
        while (at < text.length() && isSpace(text.charAt(at))) {
            at++;
        }
        return at;
    }
}
