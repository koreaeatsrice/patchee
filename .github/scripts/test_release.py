#!/usr/bin/env python3
"""
test_release.py - Unit tests for the release automation engine
"""

from pathlib import Path
import tempfile
import unittest

from release import (
    Commit,
    calculate_next_version,
    categorize_commits,
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
                "Uriel",
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
        self.assertIn("- Uriel", notes)
        self.assertIn("https://github.com/koreaeatsrice/patchee/compare/v1.3.0...v1.4.0", notes)

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