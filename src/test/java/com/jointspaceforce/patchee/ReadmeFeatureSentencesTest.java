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
 * user-visible defect. This guard parses the {@code ## Features} list and fails, naming the line and the
 * measured word count, when any bullet text is more than one sentence, runs past 18 words after the em
 * dash, or stops using the house bullet shape.
 *
 * <p>
 * A sentence end is a dot, {@code !}, {@code ?}, ellipsis or ideographic stop followed, past any closing
 * bracket or quote, by whitespace, end of line, or directly by a capital letter (a run-on). Three allowances
 * keep that honest: a dot between two digits (a decimal
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

    /** Hard cap on the words in one feature bullet's text, counted after the em dash. */
    private static final int MAX_WORDS = 18;

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
            "Every README feature bullet must be one sentence of at most 18 words. Offending bullets:\n"
                + String.join("\n", violations));
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
        assertEquals(2, sentenceCount("first thing.Second thing here."));
        assertEquals(1, sentenceCount("config/patchee.cfg is read."));
        assertEquals(1, sentenceCount("tile.chisel.technical2 is metal."));
        assertEquals(2, sentenceCount("caps at 64.Next it runs."));
        assertEquals(2, sentenceCount("caps at 64).Then it runs."));
        assertEquals(2, sentenceCount("digs sand\u2026 Then it drops items."));
        assertEquals(2, sentenceCount("digs sand\u3002 Then it drops items."));
    }

    @Test
    void wordCapRejectsRunOnFeatureLines() {
        String seventeen = "- **Seventeen** — " + words(17) + ".";
        String eighteen = "- **Eighteen** — " + words(18) + ".";
        String nineteen = "- **Nineteen** — " + words(19) + ".";
        List<String> lines = Arrays.asList("## Features", "", seventeen, eighteen, nineteen, "");

        assertEquals(3, featureBullets(lines).size());
        List<String> violations = violations(lines);
        assertEquals(1, violations.size(), String.join("\n", violations));
        assertTrue(
            violations.get(0)
                .contains("19 words"),
            violations.get(0));
        assertTrue(
            violations.get(0)
                .contains("**Nineteen**"),
            violations.get(0));
        assertEquals(17, wordCount(words(17) + "."));
        assertEquals(18, wordCount(words(18) + "."));
        assertEquals(0, wordCount("   "));
        assertEquals(19, wordCount(words(19).replace(" ", "\u00A0")));
        assertEquals(19, wordCount(words(19).replace(" ", "\ufeff")));
        assertEquals(19, wordCount(words(19).replace(" ", "\u2060")));
        assertEquals(19, wordCount(words(19).replace(" ", "\u180e")));
        String nbsp = "- **Nbsp** \u2014 " + words(19).replace(" ", "\u00A0") + ".";
        List<String> nbspViolations = violations(Arrays.asList("## Features", "", nbsp, ""));
        assertEquals(1, nbspViolations.size(), String.join("\n", nbspViolations));
        assertTrue(
            nbspViolations.get(0)
                .contains("19 words"),
            nbspViolations.get(0));
    }

    /** {@code n} distinct words, so the cap test never depends on real prose. */
    private static String words(int n) {
        StringBuilder out = new StringBuilder();
        for (int i = 1; i <= n; i++) {
            if (i > 1) {
                out.append(' ');
            }
            out.append('w')
                .append(i);
        }
        return out.toString();
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
            String text = matcher.group(2);
            int sentences = sentenceCount(text);
            if (sentences > 1) {
                violations.add("line " + line.number + ": " + sentences + " sentences: " + line.text);
            }
            int words = wordCount(text);
            if (words > MAX_WORDS) {
                violations.add("line " + line.number + ": " + words + " words (max " + MAX_WORDS + "): " + line.text);
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

    /** Words in the bullet text, using the same whitespace set as the sentence check. */
    static int wordCount(String text) {
        int words = 0;
        boolean inWord = false;
        for (int i = 0; i < text.length(); i++) {
            if (isSpace(text.charAt(i))) {
                inWord = false;
            } else if (!inWord) {
                inWord = true;
                words++;
            }
        }
        return words;
    }

    /** Counts sentence ends, stepping over brackets and quotes; a dot before a capital also ends one. */
    static int sentenceCount(String text) {
        String masked = maskNonSentenceDots(text);
        int count = 0;
        for (int i = 0; i < masked.length(); i++) {
            char c = masked.charAt(i);
            if (c != '.' && c != '!' && c != '?' && c != '\u2026' && c != '\u3002') {
                continue;
            }
            int next = i + 1;
            while (next < masked.length() && isClosing(masked.charAt(next))) {
                next++;
            }
            boolean atEnd = next == masked.length();
            boolean followedBySpace = !atEnd && isSpace(masked.charAt(next));
            boolean followedByCapital = !atEnd && Character.isUpperCase(masked.charAt(next));
            if (atEnd || followedBySpace || followedByCapital) {
                count++;
            }
        }
        return Math.max(count, 1);
    }

    private static boolean isClosing(char c) {
        return c == ')' || c == ']' || c == '}' || c == '"' || c == '\'' || c == '\u201d' || c == '\u2019';
    }

    /** Whitespace and invisible joiners as they arrive from a web page: any Z or Cf character counts. */
    private static boolean isSpace(char c) {
        int type = Character.getType(c);
        return Character.isWhitespace(c) || type == Character.SPACE_SEPARATOR
            || type == Character.LINE_SEPARATOR
            || type == Character.PARAGRAPH_SEPARATOR
            || type == Character.FORMAT;
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
