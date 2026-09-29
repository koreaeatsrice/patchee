#!/usr/bin/env python3
"""
release.py - Single-Workflow Release Automation for the Patchee repo family

Parses Conventional Commits since the last release tag, calculates the next
SemVer version, formats grouped release notes, updates CHANGELOG.md, and
creates the release commit and annotated tag.

The mod's runtime version comes from the git tag (GTNH build convention:
GitVersionModule derives the version from `git describe`), so unlike a typical
project there is no version file to bump - the tag *is* the version.

Usage (as called by .github/workflows/release.yml):
    python .github/scripts/release.py --bump auto|patch|minor|major [--dry-run]
    python .github/scripts/release.py --current-tag v1.2.3

Outputs (also written to $GITHUB_OUTPUT when present): tag, version, dry_run.
"""

from __future__ import annotations

import argparse
import datetime
import os
from pathlib import Path
import re
import subprocess
import sys
from typing import Dict, List, NamedTuple, Optional, Tuple

CONVENTIONAL_PATTERN = re.compile(
    r"^(?P<type>[a-zA-Z]+)(?:\((?P<scope>[^)]+)\))?(?P<breaking>!)?:\s*(?P<desc>.+)$"
)

CATEGORY_MAPPING = {
    "feat": "Features",
    "fix": "Bug Fixes",
    "perf": "Performance Improvements",
    "refactor": "Refactoring & Code Quality",
    "docs": "Documentation & Wiki",
    "style": "Code Style & Formatting",
    "test": "Tests & Verification",
    "ci": "Maintenance & CI",
    "build": "Maintenance & CI",
    "chore": "Maintenance & CI",
}

SECTION_ORDER = [
    "Breaking Changes ⚠️",
    "Features",
    "Bug Fixes",
    "Performance Improvements",
    "Refactoring & Code Quality",
    "Documentation & Wiki",
    "Code Style & Formatting",
    "Tests & Verification",
    "Maintenance & CI",
    "Other Changes",
]

CHANGELOG_HEADER = (
    "# Changelog\n\n"
    "All notable changes to this project will be documented in this file.\n"
    "The format is based on [Keep a Changelog](https://keepachangelog.com/) and "
    "this project adheres to [Semantic Versioning](https://semver.org/).\n"
)


class Commit(NamedTuple):
    sha: str
    subject: str
    body: str
    type: Optional[str]
    scope: Optional[str]
    breaking: bool
    desc: str
    author_name: str = ""
    author_email: str = ""


def run(cmd: List[str], cwd: Optional[Path] = None, check: bool = True) -> str:
    proc = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True)
    if check and proc.returncode != 0:
        raise RuntimeError(f"command failed: {' '.join(cmd)}\n{proc.stderr}")
    return proc.stdout.strip()


def parse_semver(text: str) -> Tuple[int, int, int]:
    m = re.fullmatch(r"v?(\d+)\.(\d+)\.(\d+)", text.strip())
    if not m:
        raise ValueError(f"not a semantic version: {text!r}")
    return int(m.group(1)), int(m.group(2)), int(m.group(3))


def format_semver(version: Tuple[int, int, int]) -> str:
    return ".".join(str(p) for p in version)


def parse_commit(subject: str, body: str) -> Tuple[Optional[str], Optional[str], bool, str]:
    m = CONVENTIONAL_PATTERN.match(subject.strip())
    if not m:
        return None, None, False, subject.strip()
    breaking = bool(m.group("breaking")) or "BREAKING CHANGE:" in body
    return m.group("type").lower(), m.group("scope"), breaking, m.group("desc").strip()


def last_tag(cwd: Path) -> Optional[str]:
    out = run(["git", "tag", "--list", "v*", "--sort=-v:refname"], cwd=cwd)
    tags = [t for t in out.splitlines() if t.strip()]
    return tags[0].strip() if tags else None


def collect_commits(since: Optional[str], cwd: Path) -> List[Commit]:
    rng = f"{since}..HEAD" if since else "HEAD"
    out = run(
        ["git", "log", rng, "--no-merges", "--pretty=format:%H%x1f%s%x1f%b%x1f%an%x1f%ae%x1e"],
        cwd=cwd,
    )
    commits: List[Commit] = []
    for entry in out.split("\x1e"):
        entry = entry.strip("\n")
        if not entry:
            continue
        parts = entry.split("\x1f")
        if len(parts) < 5:
            continue
        sha, subject, body, author_name, author_email = parts[:5]
        ctype, scope, breaking, desc = parse_commit(subject, body)
        commits.append(Commit(sha, subject, body, ctype, scope, breaking, desc, author_name, author_email))
    return commits


def calculate_next_version(current: str, bump: str, commits: List[Commit]) -> str:
    major, minor, patch = parse_semver(current)
    if bump == "auto":
        if any(c.breaking for c in commits):
            bump = "major"
        elif any(c.type == "feat" for c in commits):
            bump = "minor"
        else:
            bump = "patch"
    if bump == "major":
        return format_semver((major + 1, 0, 0))
    if bump == "minor":
        return format_semver((major, minor + 1, 0))
    if bump == "patch":
        return format_semver((major, minor, patch + 1))
    raise ValueError(f"unknown bump: {bump}")


def categorize_commits(commits: List[Commit]) -> Dict[str, List[Commit]]:
    categories: Dict[str, List[Commit]] = {name: [] for name in SECTION_ORDER}
    for c in commits:
        if c.breaking:
            categories["Breaking Changes ⚠️"].append(c)
        categories[CATEGORY_MAPPING.get(c.type or "", "Other Changes")].append(c)
    return {k: v for k, v in categories.items() if v}


def _commit_line(c: Commit, repo: str) -> str:
    link = f"[`{c.sha[:7]}`](https://github.com/{repo}/commit/{c.sha})"
    if c.scope:
        return f"- **{c.scope}**: {c.desc} ({link})"
    return f"- {c.desc} ({link})"


def generate_release_notes(version: str, since: Optional[str], commits: List[Commit], repo: str) -> str:
    lines: List[str] = []
    for section, items in categorize_commits(commits).items():
        lines.append(f"### {section}")
        for c in items:
            lines.append(_commit_line(c, repo))
        lines.append("")

    contributors: List[str] = []
    for c in commits:
        handle = c.author_name
        if handle and handle not in contributors:
            contributors.append(handle)
    if contributors:
        lines.append("### Contributors")
        lines.append("")
        for name in contributors:
            lines.append(f"- {name}")
        lines.append("")

    if since:
        lines.append(f"**Full changelog**: https://github.com/{repo}/compare/{since}...v{version}")
    else:
        lines.append(f"**Full changelog**: https://github.com/{repo}/commits/v{version}")

    return "\n".join(lines).strip() + "\n"


def update_changelog(path: Path, version: str, body: str) -> None:
    date = datetime.date.today().isoformat()
    section = f"## [{version}] - {date}\n\n{body.strip()}\n"
    if not path.exists():
        path.write_text(CHANGELOG_HEADER + "\n" + section, encoding="utf-8")
        return
    text = path.read_text(encoding="utf-8")
    release_heading = re.compile(r"^## \[(?!Unreleased\])", re.MULTILINE)
    match = release_heading.search(text)
    if match:
        text = text[: match.start()] + section + "\n" + text[match.start() :]
    else:
        text = text.rstrip() + "\n\n" + section
    path.write_text(text, encoding="utf-8")


def write_outputs(tag: str, version: str, dry_run: bool) -> None:
    out = os.environ.get("GITHUB_OUTPUT")
    if out:
        with open(out, "a", encoding="utf-8") as fh:
            fh.write(f"tag={tag}\nversion={version}\ndry_run={'true' if dry_run else 'false'}\n")


def main() -> int:
    parser = argparse.ArgumentParser(description="Conventional-Commits release engine")
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--bump", choices=["auto", "patch", "minor", "major"])
    group.add_argument("--current-tag")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--repo", default=os.environ.get("GITHUB_REPOSITORY", ""))
    parser.add_argument("--remote", default="origin", help="git remote to push the release commit and tags to")
    parser.add_argument("--dir", default=".")
    args = parser.parse_args()

    root = Path(args.dir).resolve()
    if not args.repo:
        print("error: --repo (or GITHUB_REPOSITORY) is required", file=sys.stderr)
        return 2

    if args.current_tag:
        version = format_semver(parse_semver(args.current_tag))
        tag = f"v{version}"
        previous = run(["git", "tag", "--list", "v*", "--sort=-v:refname"], cwd=root).splitlines()
        previous = [t.strip() for t in previous if t.strip() and t.strip() != tag]
        since = previous[0] if previous else None
        commits = collect_commits(since, root)
        notes = generate_release_notes(version, since, commits, args.repo)
        Path(root / "RELEASE_NOTES.md").write_text(notes, encoding="utf-8")
        print(f"notes for existing tag {tag} ({len(commits)} commits since {since})")
        write_outputs(tag, version, False)
        return 0

    current = last_tag(root)
    current_version = format_semver(parse_semver(current)) if current else "0.0.0"
    commits = collect_commits(current, root)
    if not commits:
        print("error: no commits since the last tag — nothing to release", file=sys.stderr)
        return 1

    next_version = calculate_next_version(current_version, args.bump, commits)
    tag = f"v{next_version}"
    notes = generate_release_notes(next_version, current, commits, args.repo)
    Path(root / "RELEASE_NOTES.md").write_text(notes, encoding="utf-8")

    print(f"current: {current_version} (+{len(commits)} commits) -> next: {next_version} ({tag})")
    if args.dry_run:
        print("\n=== DRY RUN: Generated Release Notes ===\n")
        print(notes)
        write_outputs(tag, next_version, True)
        return 0

    update_changelog(root / "CHANGELOG.md", next_version, notes)
    run(["git", "add", "CHANGELOG.md"], cwd=root)
    run(["git", "commit", "-m", f"chore(release): {tag}"], cwd=root)
    run(["git", "tag", "-a", tag, "-m", f"Release {tag}"], cwd=root)
    # Push the release commit and the new tag so the later jobs can check the tag out.
    run(["git", "push", args.remote, "HEAD:main", "--tags"], cwd=root)
    print(f"committed, tagged and pushed {tag}")
    write_outputs(tag, next_version, False)
    return 0


if __name__ == "__main__":
    sys.exit(main())