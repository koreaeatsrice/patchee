# Releasing

For the maintainer cutting a version. It covers where the version comes from, how the engine picks the next one,
what the workflow does, and the draft-versus-published rule. Staging the jar is
[SERVER-STAGING.md](SERVER-STAGING.md); the jobs are [CI.md](CI.md).

## The version is the tag

There is no version file. `gradle.properties` sets `generateGradleTokenClass = com.jointspaceforce.patchee.Tags`
and `gradleTokenVersion = VERSION`; `Patchee.java:117` uses `version = Tags.VERSION`. The GTNH build convention
derives `VERSION` from `git describe`, so the tag is the version.

Tags are `vMAJOR.MINOR.PATCH`. `parse_semver` rejects anything else, and `last_tag` skips a non-semver tag instead
of crashing (`release.py:125-136`). `v1.9.0` and `v1.9.1` are real tags.

A local build without a tag still produces a jar, but its version is not a release version. The release build checks
out the tag (`release.yml`, `build-release`), so the published jar carries the tag's version.

## The engine

`.github/scripts/release.py` has two modes:

```sh
python .github/scripts/release.py --bump auto|patch|minor|major [--dry-run] --repo owner/name
python .github/scripts/release.py --current-tag v1.2.3 --repo owner/name
```

- `--bump` computes the next version from the commits since the last tag, writes `RELEASE_NOTES.md`, updates
  `CHANGELOG.md`, commits, tags and pushes.
- `--current-tag` is the `push: tags: v*` path. The tag already exists; the engine writes notes and does not
  commit or tag.
- `--dry-run` writes and prints the notes, sets the `dry_run` output to `true`, and returns without committing,
  tagging or pushing.

### The bump

`calculate_next_version(current, bump, commits)` (`release.py:159-176`):

| Condition, when `bump` is `auto` | Result |
|---|---|
| Any commit is breaking (`!` after the type/scope, or `BREAKING CHANGE:` in the body) | major |
| Otherwise any commit has type `feat` | minor |
| Otherwise | patch |

An explicit `patch`, `minor` or `major` overrides detection. The current version is the last `v*` semver tag, or
`0.0.0` if there is none. With no commits since the tag, the engine exits 1 with
`no commits since the last tag — nothing to release`.

### The commits

`git log <since>..HEAD --no-merges --pretty=format:%H%x1f%s%x1f%b%x1f%an%x1f%ae%x1e`. Merge commits are excluded.
Each subject is parsed with `CONVENTIONAL_PATTERN`; a subject without a conventional type still appears under
`Other Changes`, using the raw subject. That is why the commit convention matters.

### The notes

`generate_release_notes` groups commits in a fixed order (`SECTION_ORDER`, `release.py:66-77`):

1. `Breaking Changes ⚠️`
2. `Features`
3. `Bug Fixes`
4. `Performance Improvements`
5. `Refactoring & Code Quality`
6. `Documentation & Wiki`
7. `Code Style & Formatting`
8. `Tests & Verification`
9. `Maintenance & CI`
10. `Other Changes`

Each line is `- **scope**: description ([sha](url))`. A breaking commit appears in `Breaking Changes` and in its
type section. A `Contributors` section follows, then a `Full changelog` compare link. Contributors are derived from
author identity; only retired identities with hard evidence are folded, and everyone else keeps their own handle
(`contributor_handle`, `release.py:186-226`).

### The commit and tag

`update_changelog` inserts `## [x.y.z] - YYYY-MM-DD` plus the notes before the first existing release heading,
leaving an `[Unreleased]` section at the top if one exists. Then:

```sh
git add CHANGELOG.md
git commit -m "chore(release): vX.Y.Z"
git tag -a vX.Y.Z -m "Release vX.Y.Z"
git push origin HEAD:main --tags
```

The push goes to `main` and includes the tag. `main` is unprotected, so it succeeds. The release commit is
`chore(release): ...`, which the next release files under Maintenance & CI.

## The workflow

`release.yml` triggers on:

- `workflow_dispatch` with `bump` (`auto`/`patch`/`minor`/`major`, default `auto`) and `dry_run` (boolean, default
  `false`);
- a push of a tag matching `v*`.

| Job | What it does |
|---|---|
| `prepare` | Full history, write token, Python; runs the engine; writes `tag`, `version`, `dry_run`; uploads `RELEASE_NOTES.md`. |
| `build-release` | Skipped on a dry run. Checks out the tag, sets up both JDKs, runs `./gradlew build --no-daemon`, uploads the plain jar as `release-jar`. |
| `publish` | Skipped on a dry run. `gh release create "$TAG" release-assets/*.jar --title "$TAG" --notes-file ... --draft --verify-tag`. |

The release is created as a **draft**. The workflow never publishes it.

## Drafts

**Delete drafts that are wrong; publish one correct release.** A draft is a staging area.

A draft is wrong when:

- it has a `-dev` or `-sources` jar, or more than one jar;
- the tag is not `vMAJOR.MINOR.PATCH`;
- the notes miss a section or list a commit outside the tag;
- the version disagrees with `CHANGELOG.md`.

Correct it by deleting the draft (`gh release delete <tag> --yes`), deleting the tag if the tag is wrong
(`git tag -d`, `git push origin :refs/tags/<tag>`), fixing the cause, and running the workflow again. Do not
hand-edit a draft's notes and publish it; the engine is the source of truth and the next changelog would disagree.

To publish: open the draft, confirm one plain jar and the notes, and press **Publish release**.

## Checklist

1. `main` is green: the latest `ci.yml` run, all three jobs.
2. `docs/FEATURES.md`, `docs/SETTINGS.md` and the README bullet describe what ships.
3. Preview if you want: `python3 .github/scripts/release.py --bump auto --dry-run --repo koreaeatsrice/patchee`.
4. Run the **Release** workflow with the bump type and `dry_run = false`.
5. Inspect the draft: one plain `patchee-<version>.jar`, the right tag, the expected notes.
6. Publish. Delete any earlier wrong draft.
7. Stage the jar: [SERVER-STAGING.md](SERVER-STAGING.md).
8. Verify the banner version and, if you run it, `[SelfTest] result: N passed, 0 failed`.

Do not tag by hand and do not edit `CHANGELOG.md` by hand for a release.

## Engine tests

```sh
python3 .github/scripts/test_release.py
```

Covers semver parsing, explicit and automatic bumps, categorisation, note generation, contributors, changelog
insertion and `last_tag`. CI runs the same file through `python3 -m unittest -v test_release` in the
`release-engine-tests` job.

## See also

- [CI.md](CI.md) — the four workflows.
- [SERVER-STAGING.md](SERVER-STAGING.md) — install and rollback.
- [../dev/WORKFLOW.md](../dev/WORKFLOW.md) — the commit types the engine parses.
