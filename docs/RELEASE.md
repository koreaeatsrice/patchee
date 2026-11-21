# Release

This page covers how a version is made, what the release engine does, what the workflows do, how the jar reaches
the GTNH server, and how to roll back. The engine is `.github/scripts/release.py`; its tests are
`.github/scripts/test_release.py`.

## 1. Where the version comes from

There is no version file. The jar's version is the git tag:

- `gradle.properties` sets `generateGradleTokenClass = com.jointspaceforce.patchee.Tags` and
  `gradleTokenVersion = VERSION`.
- `Patchee.java` uses `version = Tags.VERSION` in its `@Mod` annotation (`Patchee.java:117-121`).
- The GTNH build convention (`GitVersionModule`) derives that value from `git describe`, so the tag is the
  version.

A consequence: a local build without a tag still produces a jar, but its version is not a release version. The
release build checks out the tag (`release.yml`, `build-release` job, `ref: ${{ needs.prepare.outputs.tag }}`), so
the jar carries the tag's version.

Tags must be `vMAJOR.MINOR.PATCH`. `parse_semver` rejects anything else, and `last_tag` skips non-semver tags
instead of crashing (`release.py:125-146`). `v1.9.0` and `v1.9.1` are real tags in this repo.

## 2. The release engine

### 2.1 Two modes

```sh
python .github/scripts/release.py --bump auto|patch|minor|major [--dry-run] --repo owner/name
python .github/scripts/release.py --current-tag v1.2.3 --repo owner/name
```

- `--bump` computes the next version from the commits since the last tag, writes `RELEASE_NOTES.md`, updates
  `CHANGELOG.md`, commits, tags and pushes.
- `--current-tag` takes an existing tag (the `push: tags: v*` path), writes notes for it, and does **not** commit
  or tag. The tag already exists.

`--dry-run` writes the notes, prints them, sets the `dry_run` output to `true`, and returns without committing,
tagging or pushing. The workflow then skips the build and publish jobs.

### 2.2 How the next version is chosen

`calculate_next_version(current, bump, commits)` (`release.py:159-176`):

| Condition, when `bump` is `auto` | Result |
|---|---|
| Any commit is breaking (`!` after the type/scope, or `BREAKING CHANGE:` in the body) | major |
| Otherwise, any commit has type `feat` | minor |
| Otherwise | patch |

An explicit `patch`, `minor` or `major` overrides the detection. The current version is the last `v*` semver tag;
with no tag it is `0.0.0`. If there are no commits since that tag, the engine exits 1 with
`no commits since the last tag — nothing to release`.

### 2.3 Which commits are included

`collect_commits(since, cwd)` runs:

```sh
git log <since>..HEAD --no-merges --pretty=format:%H%x1f%s%x1f%b%x1f%an%x1f%ae%x1e
```

Merge commits are excluded. Each subject is parsed with `CONVENTIONAL_PATTERN`:
`^(type)(\((scope)\))?(!)?:\s*(desc)$`. A subject without a conventional type still appears, under
`Other Changes`, using the raw subject as the description. That is why the commit convention matters: an
unconventional subject is not lost, but it is not categorised and cannot trigger a minor or major bump.

### 2.4 Release notes

`generate_release_notes` groups commits into sections in this fixed order (`SECTION_ORDER`, `release.py:66-77`):

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

Each line is `- **scope**: description ([sha](commit url))`, or `- description (...)` without a scope. A breaking
commit appears in `Breaking Changes` **and** in its type section. A `Contributors` section follows, then a
`Full changelog` compare link. Contributors are derived from author identity; the logic deliberately folds only
retired identities with hard evidence (a legacy email domain or a retired GitHub noreply username) and keeps
everyone else, including bots and outside humans, under their own handle (`contributor_handle`,
`release.py:186-235`). The owner is always included once.

### 2.5 Changelog and tag

`update_changelog` inserts `## [x.y.z] - YYYY-MM-DD` plus the notes before the first existing release heading,
leaving an `[Unreleased]` section at the top if one exists. Then the engine runs:

```sh
git add CHANGELOG.md
git commit -m "chore(release): vX.Y.Z"
git tag -a vX.Y.Z -m "Release vX.Y.Z"
git push origin HEAD:main --tags
```

The push is to `main` and includes the tag. `main` is unprotected in this repo, so the push succeeds. The commit
subject is `chore(release): ...`, which the next release categorises as Maintenance & CI.

### 2.6 Engine tests

```sh
python3 .github/scripts/test_release.py
```

The suite covers semver parsing and rejection, explicit and automatic bumps, categorisation, note generation,
contributor handling (including the rule that an unrelated person is never credited), changelog insertion, and
`last_tag` skipping non-semver tags. CI runs the same file through
`python3 -m unittest -v test_release` in the `release-engine-tests` job.

## 3. The workflows

### 3.1 `ci.yml`

Triggers: push to `main`, pull request to `main`. Jobs:

| Job | What it does |
|---|---|
| `Build & Format` | Checks out, validates the Gradle wrapper, sets up JDK 17 and JDK 25, runs `./gradlew build --no-daemon`, uploads `build/libs/*.jar` minus `-dev` and `-sources`. |
| `Server smoke test (self-test must pass)` | Builds a throwaway server under `run/server`, writes `run/server/config/patchee.cfg` with `runSelfTest=true`, boots `./gradlew runServer` under a 900-second timeout, waits for `[SelfTest] result:`, then greps the arm lines for Dolly, Mattock, VeinConfig (write and verify), Aurora, ArcticFur, TeamChunkCap, FinalCastle, the GlassBreak missing-Blood-Arsenal skip, and `0 failed`. Any miss fails the job. |
| `Release Engine Tests` | Runs the Python unit tests in `.github/scripts`. |

The smoke job is the server-side proof. It does not test a client; a client-only claim is covered by the unit
tests and by the feature's side guard (`docs/ARCHITECTURE.md`, section 6).

### 3.2 `release.yml`

Triggers:

- `workflow_dispatch` with inputs `bump` (`auto`/`patch`/`minor`/`major`, default `auto`) and `dry_run`
  (boolean, default `false`).
- Push of a tag matching `v*`.

Jobs:

1. **`prepare`** — checks out with full history and a write token, sets up Python, runs the engine. On
   `workflow_dispatch` it calls `release.py --bump "$BUMP"` (plus `--dry-run` when asked); on a tag push it calls
   `release.py --current-tag "$REF_NAME"`. It writes the `tag`, `version` and `dry_run` outputs and uploads
   `RELEASE_NOTES.md`.
2. **`build-release`** — skipped on a dry run. Checks out the **tag**, sets up both JDKs, runs
   `./gradlew build --no-daemon`, uploads the plain jar as `release-jar`.
3. **`publish`** — skipped on a dry run. Downloads the artifacts and runs:
   ```sh
   gh release create "$TAG" release-assets/*.jar \
     --repo "$REPO" --title "$TAG" \
     --notes-file release-assets/RELEASE_NOTES.md \
     --draft --verify-tag
   ```

The release is created as a **draft**. The workflow never publishes it. A human reviews the notes and assets and
publishes it.

### 3.3 Draft and published releases

The rule in this repo: **delete drafts that are wrong, publish one correct release.** A draft is a staging area,
not a release.

A draft release is wrong when:

- the assets include a `-dev` or `-sources` jar, or more than one jar;
- the tag is not `vMAJOR.MINOR.PATCH`;
- the notes list a commit that is not in the tag, or miss a section;
- the version does not match the tag in `CHANGELOG.md`.

To correct it: delete the draft (`gh release delete <tag> --yes`), delete the local and remote tag if the tag itself
is wrong (`git tag -d`, `git push origin :refs/tags/<tag>`), fix the cause, and run the workflow again. Do not
edit a draft's notes by hand and publish it; the engine is the source of truth and the next release would disagree
with the changelog.

To publish: open the draft, confirm the single plain jar and the notes, and press **Publish release**. The release
page is what `docs/INSTALL-SERVER.md` and the README link to.

### 3.4 `security.yml`

Triggers: push to `main`, pull request to `main`, and a weekly cron (`0 6 * * 1`). It submits the Gradle
dependency graph (`gradle/actions/dependency-submission`) so Dependabot alerts have data. The job is skipped on
pull requests. CodeQL is deliberately absent: it needs GitHub Advanced Security on private repositories, and the
note in the workflow says so.

### 3.5 Dependabot

`.github/dependabot.yml` enables the `github-actions` ecosystem only, weekly, grouped, at most 5 open PRs. The
Gradle ecosystem is intentionally not enabled: Dependabot cannot resolve the GTNH / RetroFuturaGradle toolchain
and would only produce failing runs. `dependabot-automerge.yml` enables squash auto-merge for a PR whose actor,
author and branch all identify Dependabot. It does not bypass CI; auto-merge waits for the required checks.

## 4. Releasing, step by step

1. Confirm `main` is green: the latest `ci.yml` run, all three jobs.
2. Confirm the changelog and docs describe the release. `docs/FEATURES.md`, `docs/SETTINGS.md` and the README
   bullet are user-facing; a feature with no row is not ready.
3. Run the engine locally in dry-run mode if you want to preview:
   ```sh
   python3 .github/scripts/release.py --bump auto --dry-run --repo koreaeatsrice/patchee
   ```
   Read `RELEASE_NOTES.md`. Confirm the bump type matches the commits.
4. Run the GitHub Actions workflow **Release** with `bump = auto` (or the explicit type) and `dry_run = false`.
5. Let `prepare` push the release commit and tag, `build-release` build the tagged jar, and `publish` open the
   draft.
6. Inspect the draft: one plain `patchee-<version>.jar`, the right tag, the notes you expect.
7. Publish it. Delete any earlier wrong draft.
8. Stage the jar on the GTNH server (section 5).
9. Verify the server: the banner version, the arm lines, and `[SelfTest] result: N passed, 0 failed` if you run
   the self-test.

Do not tag by hand and do not edit `CHANGELOG.md` by hand for a release; the engine owns both.

## 5. Staging the jar on the GTNH server

The artifact is the plain jar from the release page. It is not the `-dev` jar and it is not built on the server.

1. Download `patchee-<version>.jar` from the published release.
2. Stop the server. Patchee loads at boot; a reload does not pick up a new jar.
3. Keep the jar being replaced. The convention used with this project is to move the old
   `mods/patchee-*.jar` into `mods/backup/` before copying the new one in.
4. Put exactly one `patchee-<version>.jar` in `mods/`. If two Patchee jars are present, FML sees the same modid
   twice and aborts the boot. Delete the older one.
5. Start the server and read `logs/latest.log`. The banner should name the new version:
   `[patchee] Patchee <version> — enabled=...`.
6. Run the self-test once after an install or a pack update: set `runSelfTest=true` in
   `config/patchee.cfg`, restart, confirm `[SelfTest] result: N passed, 0 failed`, then set it back to `false`.

A GTNH pack update replaces the `mods` folder, so Patchee disappears. Reinstall the newest jar after every pack
update. The server's `config/patchee.cfg` and the GregTech files Patchee writes survive in `config/`, but a pack
update can replace GregTech's config too; re-check the pollution lines after an update.

## 6. Rollback

The jar is the only thing that has to change.

1. Stop the server.
2. Move the new `mods/patchee-<version>.jar` out of `mods/` (or into `mods/backup/`).
3. Move the previous jar from `mods/backup/` back into `mods/`.
4. Start the server. Confirm the banner names the restored version, or that no `[patchee]` banner appears if you
   rolled back to "not installed".

If there is no backup jar, delete Patchee from `mods/` and restart. The server returns to stock behaviour; no world
data is migrated and no block or item is removed, because Patchee registers none. `config/patchee.cfg` can stay; it
is ignored when the jar is absent.

What a rollback does **not** undo:

- `config/GregTech/Pollution.cfg` and `config/GregTech/Client.cfg` were rewritten by Patchee. Each rewrite kept a
  timestamped `.bak` next to the file. Restore the `.bak` if you want the previous values, then restart.
- `config/veinminer/` was rewritten by VeinConfig. Patchee does not keep a `.bak` for those files; VeinMiner will
  regenerate or accept its own settings once Patchee stops rewriting them.
- `config/patchee.cfg` remains, with the last values. Harmless.

Never roll back by editing a released jar or by dropping a patched third-party jar into `mods/`. The rollback path
is the previous Patchee jar, or no Patchee jar.

## 7. What not to do

- Do not cut a release from a feature branch. Release from `main`, after the PR is merged and CI is green.
- Do not publish a draft with a `-dev` jar, a sources jar, or two jars.
- Do not hand-edit a tag or move a published tag. Delete the draft, delete the tag, fix the cause, run again.
- Do not edit `CHANGELOG.md` for a release by hand.
- Do not bump the version in a source file; there is no version file, and the tag is the version.
- Do not restart or update the game server as part of a docs or feature PR. Staging is a separate, deliberate
  operation.
