#!/usr/bin/env python3
"""
test_release.py - Unit tests for the release automation engine
"""

from pathlib import Path
import tempfile
import unittest

from release import (
    DEFAULT_CONTRIBUTOR,
    Commit,
    calculate_next_version,
    categorize_commits,
    contributor_handle,
    generate_release_notes,
    last_tag,
    parse_semver,
    update_changelog,
)


class TestReleaseEngine(unittest.TestCase):
    def test_parse_semver_valid(self):
        self.assertEqual(parse_semver("1.2.3"), (1, 2, 3))
        self.assertEqual(parse_semver("v1.2.3"), (1, 2, 3))
        self.assertEqual(parse_semver("  0.10.4  "), (0, 10, 4))

    def test_parse_semver_invalid(self):
        with self.assertRaises(ValueError):
            parse_semver("invalid")
        with self.assertRaises(ValueError):
            parse_semver("1.2")

    def test_calculate_next_version_explicit(self):
        self.assertEqual(calculate_next_version("1.3.0", "patch", []), "1.3.1")
        self.assertEqual(calculate_next_version("1.3.0", "minor", []), "1.4.0")
        self.assertEqual(calculate_next_version("1.3.0", "major", []), "2.0.0")

    def test_calculate_next_version_auto(self):
        patch_only = [
            Commit("1111111", "fix: resolve edge case", "", "fix", None, False, "resolve edge case"),
            Commit("2222222", "docs: update readme", "", "docs", None, False, "update readme"),
        ]
        self.assertEqual(calculate_next_version("1.3.0", "auto", patch_only), "1.3.1")

        with_feat = patch_only + [
            Commit("3333333", "feat: add new rule", "", "feat", None, False, "add new rule"),
        ]
        self.assertEqual(calculate_next_version("1.3.0", "auto", with_feat), "1.4.0")

        with_breaking_bang = [
            Commit("4444444", "feat!: overhaul config format", "", "feat", None, True, "overhaul config format"),
        ]
        self.assertEqual(calculate_next_version("1.3.0", "auto", with_breaking_bang), "2.0.0")

        with_breaking_body = [
            Commit(
                "5555555",
                "fix: rework arguments",
                "BREAKING CHANGE: changes flag syntax",
                "fix",
                None,
                True,
                "rework arguments",
            ),
        ]
        self.assertEqual(calculate_next_version("1.3.0", "auto", with_breaking_body), "2.0.0")

    def test_categorize_commits(self):
        commits = [
            Commit("aaa1111", "feat(config): add toggle", "", "feat", "config", False, "add toggle"),
            Commit("bbb2222", "fix: null guard", "", "fix", None, False, "null guard"),
            Commit("ccc3333", "perf: faster loop", "", "perf", None, False, "faster loop"),
            Commit("ddd4444", "chore(deps): update actions", "", "chore", "deps", False, "update actions"),
            Commit("eee5555", "random non-conventional commit", "", None, None, False, "random non-conventional commit"),
        ]
        cat = categorize_commits(commits)
        self.assertEqual(len(cat["Features"]), 1)
        self.assertEqual(len(cat["Bug Fixes"]), 1)
        self.assertEqual(len(cat["Performance Improvements"]), 1)
        self.assertEqual(len(cat["Maintenance & CI"]), 1)
        self.assertEqual(len(cat["Other Changes"]), 1)

    def test_generate_release_notes(self):
        commits = [
            Commit(
                "1234567890abcdef",
                "feat(dolly): support a fourth housing",
                "",
                "feat",
                "dolly",
                False,
                "support a fourth housing",
                "uriel-runner[bot]",
                "306633960+uriel-runner[bot]@users.noreply.github.com",
            ),
            Commit(
                "abcdef1234567890",
                "fix: restyle the config comment",
                "",
                "fix",
                None,
                False,
                "restyle the config comment",
                "Uriel",
                "uriel@users.noreply.github.com",
            ),
        ]
        notes = generate_release_notes("1.4.0", "v1.3.0", commits, repo="koreaeatsrice/patchee")
        self.assertIn("### Features", notes)
        self.assertIn(
            "- **dolly**: support a fourth housing ([`1234567`](https://github.com/koreaeatsrice/patchee/commit/1234567890abcdef))",
            notes,
        )
        self.assertIn("### Bug Fixes", notes)
        self.assertIn(
            "- restyle the config comment ([`abcdef1`](https://github.com/koreaeatsrice/patchee/commit/abcdef1234567890))",
            notes,
        )
        self.assertIn("### Contributors", notes)
        # The bot and the stranger identity are ours, so the owner is the only credit.
        self.assertEqual(notes.count(f"- @{DEFAULT_CONTRIBUTOR}"), 1)
        self.assertNotIn("uriel", notes.lower())
        self.assertNotIn("Uriel", notes)
        self.assertIn("https://github.com/koreaeatsrice/patchee/compare/v1.3.0...v1.4.0", notes)

    def test_contributor_handle_never_credits_unrelated_identities(self):
        # A real, unrelated GitHub user must never be credited. Only the owner's
        # own retired accounts fold onto the owner; every other account -- bot or
        # human -- keeps its own handle or display name.
        owner = f"@{DEFAULT_CONTRIBUTOR}"
        cases = [
            # The release bot, with and without the numeric ID prefix.
            ("uriel-runner[bot]", "306633960+uriel-runner[bot]@users.noreply.github.com", owner),
            ("uriel-runner[bot]", "uriel-runner[bot]@users.noreply.github.com", owner),
            # Both of our real retired accounts fold on their ADDRESS alone,
            # with no help from the display name.
            ("", "uriel@users.noreply.github.com", owner),
            ("", "uriel@jointspaceforce.local", owner),
            # The retired uriel identities: these are the addresses the notes
            # must never credit. A numeric ID prefix does not change the user.
            ("Uriel", "3283635+uriel@users.noreply.github.com", owner),
            ("Uriel", "uriel@users.noreply.github.com", owner),
            ("Uriel", "uriel@jointspaceforce.local", owner),
            ("Uriel", "uriel@jointspaceforce.com", owner),
            # A display name is not proof: a real person named Uriel with an
            # ordinary mailbox keeps their own name, not the owner's.
            ("Uriel", "uriel@example.com", "@Uriel"),
            ("Uriel", "uriel@gmail.com", "@Uriel"),
            # An outside bot keeps its own handle: only OUR retired accounts
            # fold onto the owner, so dependabot must never be credited to him.
            ("dependabot[bot]", "49699333+dependabot[bot]@users.noreply.github.com", "@dependabot[bot]"),
            ("dependabot[bot]", "dependabot[bot]@users.noreply.github.com", "@dependabot[bot]"),
            # The owner stays the owner.
            (DEFAULT_CONTRIBUTOR, f"228802507+{DEFAULT_CONTRIBUTOR}@users.noreply.github.com", owner),
            # A genuine outside GitHub user keeps THEIR OWN handle, not the
            # owner's: a noreply address is not proof of ownership.
            ("Alice", "1234567+alice@users.noreply.github.com", "@alice"),
            ("Alice", "alice@users.noreply.github.com", "@alice"),
            # Plain outside humans keep their display name.
            ("Jane Doe", "jane@example.org", "@Jane Doe"),
            # A mailbox local part is not an identity: an outside human whose
            # ordinary mailbox happens to be `ops@corp.example` must stay
            # themselves, never fold onto the repository owner.
            ("Bob", "ops@corp.example", "@Bob"),
            # Empty identity yields a bare "@" the caller skips.
            ("", "", "@"),
            # An empty display name must not leak a mailbox local part as a
            # handle; with nothing to print the caller skips the bare "@".
            ("", "uriel@example.com", "@"),
            ("", "someone@example.com", "@"),
            # Outside bots and humans whose display name merely ends in [bot]
            # are not ours and keep their own name.
            ("Renovate[bot]", "renovate@example.com", "@Renovate[bot]"),
            ("Agent Smith", "smith@example.com", "@Agent Smith"),
            # `ops` and `agent` are generic names, not owned accounts: only the
            # legacy DOMAIN rule covers those mailboxes, never a bare username.
            ("Ops", "ops@users.noreply.github.com", "@ops"),
            ("Ops Bot", "12345+ops@users.noreply.github.com", "@ops"),
            ("agent", "agent@users.noreply.github.com", "@agent"),
            ("agent", "agent@jointspaceforce.com", owner),
        ]
        for name, email, expected in cases:
            with self.subTest(name=name, email=email):
                self.assertEqual(contributor_handle(name, email), expected)

    def test_genuine_outside_noreply_keeps_own_handle(self):
        # Anti-regression for the reviewer finding that every
        # users.noreply.github.com address was sent to the owner: a real
        # outside contributor would have been credited as the owner instead of
        # themselves. Alice must stay @alice.
        self.assertEqual(contributor_handle("Alice", "1234567+alice@users.noreply.github.com"), "@alice")
        self.assertEqual(contributor_handle("Alice", "alice@users.noreply.github.com"), "@alice")

    def test_generate_release_notes_has_no_uriel_line(self):
        # Commits mixing the bot, a stranger's noreply, a legacy agent domain and
        # the owner must never emit a line naming the unrelated user.
        commits = [
            Commit(
                "1111111",
                "chore(ci): bump the release action",
                "",
                "chore",
                "ci",
                False,
                "bump the release action",
                "uriel-runner[bot]",
                "uriel-runner[bot]@users.noreply.github.com",
            ),
            Commit(
                "2222222",
                "fix: guard the null case",
                "",
                "fix",
                None,
                False,
                "guard the null case",
                "Uriel",
                "3283635+uriel@users.noreply.github.com",
            ),
            Commit(
                "3333333",
                "docs: tidy the readme",
                "",
                "docs",
                None,
                False,
                "tidy the readme",
                "Uriel",
                "uriel@jointspaceforce.local",
            ),
            Commit(
                "4444444",
                "feat: add the thing",
                "",
                "feat",
                None,
                False,
                "add the thing",
                DEFAULT_CONTRIBUTOR,
                f"228802507+{DEFAULT_CONTRIBUTOR}@users.noreply.github.com",
            ),
        ]
        notes = generate_release_notes("1.4.0", "v1.3.0", commits, repo="koreaeatsrice/patchee")
        credit_lines = [
            line
            for line in notes.splitlines()
            if line.startswith("- @") or line.startswith("- Uriel")
        ]
        self.assertNotIn("@uriel", notes)
        self.assertEqual(credit_lines, [f"- @{DEFAULT_CONTRIBUTOR}"])

    def test_generate_release_notes_quotes_a_subject_that_names_the_person(self):
        # Notes quote commit subjects verbatim, so a subject that describes this
        # fix still appears as a bullet. What must never appear is a credit line
        # for the unrelated account.
        commits = [
            Commit(
                "5555555",
                "fix(credits): stop crediting the unrelated uriel account",
                "",
                "fix",
                "credits",
                False,
                "stop crediting the unrelated uriel account",
                "uriel-runner[bot]",
                "306633960+uriel-runner[bot]@users.noreply.github.com",
            ),
        ]
        notes = generate_release_notes("1.9.0", "v1.8.0", commits, repo="koreaeatsrice/patchee")
        credit_lines = [
            line
            for line in notes.splitlines()
            if line.startswith("- @") or line.startswith("- Uriel")
        ]
        self.assertNotIn("@uriel", notes)
        self.assertEqual(credit_lines, [f"- @{DEFAULT_CONTRIBUTOR}"])
        self.assertIn("stop crediting the unrelated uriel account", notes)

    def test_generate_release_notes_always_includes_owner(self):
        # No commit is authored by the owner — he must still be credited, once.
        commits = [
            Commit(
                "1111111",
                "fix: guard the null case",
                "",
                "fix",
                None,
                False,
                "guard the null case",
                "uriel-runner[bot]",
                "uriel-runner[bot]@users.noreply.github.com",
            ),
        ]
        notes = generate_release_notes("1.4.0", "v1.3.0", commits, repo="koreaeatsrice/patchee")
        self.assertEqual(notes.count(f"- @{DEFAULT_CONTRIBUTOR}"), 1)

    def test_generate_release_notes_owner_not_duplicated(self):
        # The owner IS the author — he must appear exactly once.
        commits = [
            Commit(
                "2222222",
                "feat: add the thing",
                "",
                "feat",
                None,
                False,
                "add the thing",
                DEFAULT_CONTRIBUTOR,
                f"{DEFAULT_CONTRIBUTOR}@users.noreply.github.com",
            ),
        ]
        notes = generate_release_notes("1.4.0", "v1.3.0", commits, repo="koreaeatsrice/patchee")
        self.assertEqual(notes.count(f"- @{DEFAULT_CONTRIBUTOR}"), 1)

    def test_update_changelog(self):
        sample = """# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

- Pending changes here.

## [1.3.0] - 2026-09-18

### Features
- Initial feature.
"""
        with tempfile.TemporaryDirectory() as tmpdir:
            p = Path(tmpdir) / "CHANGELOG.md"
            p.write_text(sample, encoding="utf-8")
            body = "### Features\n- New capability added."
            update_changelog(p, "1.4.0", body)
            updated = p.read_text(encoding="utf-8")
            self.assertIn("## [1.4.0] - ", updated)
            self.assertIn("### Features\n- New capability added.", updated)
            pos_140 = updated.find("## [1.4.0]")
            pos_130 = updated.find("## [1.3.0]")
            self.assertTrue(pos_140 < pos_130)
            self.assertTrue(updated.find("## [Unreleased]") < pos_140)

    def test_update_changelog_creates_missing_file(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            p = Path(tmpdir) / "CHANGELOG.md"
            update_changelog(p, "1.0.0", "### Features\n- First release.")
            updated = p.read_text(encoding="utf-8")
            self.assertIn("# Changelog", updated)
            self.assertIn("## [1.0.0] - ", updated)

    def test_last_tag_skips_non_semver(self):
        import subprocess

        with tempfile.TemporaryDirectory() as tmpdir:
            subprocess.run(["git", "init", "-q"], cwd=tmpdir, check=True)
            subprocess.run(["git", "config", "user.email", "test@example.invalid"], cwd=tmpdir, check=True)
            subprocess.run(["git", "config", "user.name", "test"], cwd=tmpdir, check=True)
            subprocess.run(["git", "commit", "--allow-empty", "-m", "init", "-q"], cwd=tmpdir, check=True)
            for tag in ["vnot-a-version", "v2", "v1.4.0", "v1.3.0"]:
                subprocess.run(["git", "tag", tag], cwd=tmpdir, check=True)
            self.assertEqual(last_tag(Path(tmpdir)), "v1.4.0")


if __name__ == "__main__":
    unittest.main()