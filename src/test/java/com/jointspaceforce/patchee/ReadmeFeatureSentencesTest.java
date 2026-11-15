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
 * any bullet text is more than one sentence.
 *
 * <p>
 * A sentence end is a {@code .}, {@code !} or {@code ?} followed by whitespace or end of line. Three
 * allowances keep that honest: a dot between two digits (a decimal such as {@code 0.6}, a version such as
 * {@code v1.8.0}), a listed abbreviation ({@code e.g.}, {@code i.e.}), and text with no terminal
 * punctuation (counted as one sentence, not zero). The checker itself is unit-tested against synthetic
 * bullets so it cannot quietly pass everything.
 */
class ReadmeFeatureSentencesTest {

    /** Dots inside one of these are not sentence ends. */
    private static final List<String> ABBREVIATIONS = Arrays.asList("e.g.", "i.e.", "etc.", "vs.", "cf.");

    /** {@code - **Name** — text} with either an em dash or a plain hyphen as the separator. */
    private static final Pattern FEATURE_BULLET = Pattern.compile("^- \\*\\*(.+?)\\*\\*\\s*[—-]\\s*(.+)$");

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
        assertEquals(1, sentenceCount("all mods, e.g. IC2 and GraviSuite, work."));
        assertEquals(1, sentenceCount("all mods, i.e. every one of them, work."));
        assertEquals(1, sentenceCount("one thing; another thing."));
        assertEquals(1, sentenceCount("no terminal punctuation"));
        assertEquals(1, sentenceCount(""));
        assertEquals(2, sentenceCount("break in 10 ticks. Shears cut arctic fur."));
        assertEquals(2, sentenceCount("Really? Yes!"));
        assertEquals(3, sentenceCount("One. Two! Three?"));
    }

    @Test
    void rejectsASyntheticMultiSentenceBulletAndIgnoresOtherSections() {
        List<String> lines = Arrays.asList(
            "# Title",
            "",
            "## Features",
            "",
            "- **Good** — one sentence only.",
            "- **Bad** — first sentence. second sentence.",
            "",
            "## Install",
            "",
            "- **Not a feature** — first sentence. second sentence.");

        assertEquals(2, featureBullets(lines).size());
        List<String> violations = violations(lines);
        assertEquals(1, violations.size(), "exactly the feature bullet under ## Features must be flagged");
        assertTrue(
            violations.get(0)
                .contains("**Bad**"),
            violations.get(0));
    }

    /** Bullets inside the {@code ## Features} section, up to the next {@code ## } heading. */
    static List<String> featureBullets(List<String> lines) {
        List<String> bullets = new ArrayList<>();
        boolean inFeatures = false;
        for (String line : lines) {
            if (line.startsWith("## ")) {
                inFeatures = line.trim()
                    .equals("## Features");
                continue;
            }
            if (inFeatures && FEATURE_BULLET.matcher(line)
                .matches()) {
                bullets.add(line);
            }
        }
        return bullets;
    }

    /** One message per bullet whose text after the bold name is more than one sentence. */
    static List<String> violations(List<String> lines) {
        List<String> violations = new ArrayList<>();
        boolean inFeatures = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith("## ")) {
                inFeatures = line.trim()
                    .equals("## Features");
                continue;
            }
            if (!inFeatures) {
                continue;
            }
            Matcher matcher = FEATURE_BULLET.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            int sentences = sentenceCount(matcher.group(2));
            if (sentences > 1) {
                violations.add("line " + (i + 1) + ": " + sentences + " sentences: " + line);
            }
        }
        return violations;
    }

    /** Counts sentence ends; text with none still counts as one sentence. */
    static int sentenceCount(String text) {
        String masked = maskNonSentenceDots(text);
        int count = 0;
        for (int i = 0; i < masked.length(); i++) {
            char c = masked.charAt(i);
            if (c != '.' && c != '!' && c != '?') {
                continue;
            }
            boolean atEnd = i == masked.length() - 1;
            boolean followedBySpace = !atEnd && Character.isWhitespace(masked.charAt(i + 1));
            if (atEnd || followedBySpace) {
                count++;
            }
        }
        return Math.max(count, 1);
    }

    /** Blanks the dots that cannot end a sentence, keeping the length so indexes stay put. */
    private static String maskNonSentenceDots(String text) {
        StringBuilder out = new StringBuilder(text);
        for (String abbreviation : ABBREVIATIONS) {
            int from = 0;
            while (true) {
                int at = out.indexOf(abbreviation, from);
                if (at < 0) {
                    break;
                }
                for (int i = at; i < at + abbreviation.length(); i++) {
                    if (out.charAt(i) == '.') {
                        out.setCharAt(i, '#');
                    }
                }
                from = at + abbreviation.length();
            }
        }
        for (int i = 1; i < out.length() - 1; i++) {
            boolean betweenDigits = Character.isDigit(out.charAt(i - 1)) && Character.isDigit(out.charAt(i + 1));
            if (out.charAt(i) == '.' && betweenDigits) {
                out.setCharAt(i, '#');
            }
        }
        return out.toString();
    }
}
