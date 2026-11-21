# CI and workflows

For a maintainer who needs to know what runs on a push, a PR, a tag or a schedule, and which log lines a failure
means. The release process is [RELEASING.md](RELEASING.md); the tests are [../dev/TESTING.md](../dev/TESTING.md).

## The four workflows

| File | Trigger | Purpose |
|---|---|---|
| `ci.yml` | Push to `main`, PR to `main` | Build, smoke test, release-engine tests. |
| `release.yml` | Manual dispatch, tag `v*` | Prepare notes, build the tag, open a draft release. |
| `security.yml` | Push/PR to `main`, weekly cron (`0 6 * * 1`) | Submit the Gradle dependency graph for Dependabot alerts. |
| `dependabot-automerge.yml` | PR event | Enable squash auto-merge for a genuine Dependabot PR. |

## `ci.yml`

| Job | What it does |
|---|---|
| `Build & Format` | Validates the wrapper, sets up JDK 17 and JDK 25, runs `./gradlew build --no-daemon`, uploads `build/libs/*.jar` excluding `-dev` and `-sources`. |
| `Server smoke test (self-test must pass)` | Builds a throwaway server in `run/server`, writes `config/patchee.cfg` with `runSelfTest=true`, boots `./gradlew runServer` under a 900-second timeout, waits for `[SelfTest] result:`, then greps the expected lines and `0 failed`. |
| `Release Engine Tests` | `python3 -m unittest -v test_release` in `.github/scripts`. |

The smoke job asserts these lines:

- `[DollyFix] Dolly now accepts`
- `[MattockFix] mattock shovel materials`
- `[VeinConfig] wrote config/veinminer/`
- `[VeinConfig] verified:`
- `[AuroraBreak] armed`
- `[ArcticFurBreak] armed`
- `[TeamChunkCap] armed`
- `[FinalCastleBreak] armed`
- `[GlassBreak] Blood Arsenal is not installed - skipped`

Blood Arsenal is not on the dev runtime, so CI sees the GlassBreak skip line. The `[GlassBreak] armed ...` line only
appears on a server that has it. The FinalCastle line is asserted in its armed form only; read the workflow before
changing a target. The same job also asserts `[SelfTest] result: <n> passed, 0 failed`, so a feature that arms but
fails its check still fails the job.

`PollutionVisuals` is not in the grep list. CI runs a dedicated server, and the feature checks the side before it
looks for GregTech, so CI sees
`[PollutionVisuals] dedicated server - no client renderer, skipped`. Its client decision is proven by
`PollutionVisualsTest`.

A failure in the smoke job is usually one of:

1. the server did not reach the first tick (a mod crash, a missing dependency);
2. a feature armed differently because a dev-runtime pin changed;
3. a log line was edited without updating the workflow.

## `release.yml`

Three jobs: `prepare`, `build-release`, `publish`. The full behaviour, inputs and draft rule are in
[RELEASING.md](RELEASING.md). The one thing to remember here: `publish` uses `--draft`, so a green run does not
publish anything.

## `security.yml`

Submits the Gradle dependency graph with `gradle/actions/dependency-submission` so Dependabot alerts have data. It
is skipped on pull requests. CodeQL is deliberately absent: it needs GitHub Advanced Security on private
repositories, and the workflow comment says so.

## Dependabot

`.github/dependabot.yml` enables the `github-actions` ecosystem only, weekly, grouped, at most 5 open PRs. The
Gradle ecosystem is intentionally off: Dependabot cannot resolve the GTNH / RetroFuturaGradle toolchain and would
only produce failing runs.

`dependabot-automerge.yml` enables squash auto-merge when the actor, author and branch all identify Dependabot. It
does not bypass CI; auto-merge waits for the required checks.

## When a job fails

| Symptom | Likely cause |
|---|---|
| `Build & Format` fails at Spotless | Run `./gradlew spotlessApply` and commit. |
| `Build & Format` fails at Checkstyle | A wildcard import. |
| `Build & Format` fails to resolve a dependency | The GTNH maven is down or a pin moved. |
| Smoke job times out | The server never reached the first tick; download the `patchee-smoke-server-log` artifact and read `server.log`. |
| Smoke job misses an arm line | A feature skipped because a mod is absent or a shape changed. |
| Smoke job sees `[SelfTest] FAIL` | A real behavioural regression; the line names the check. |
| `Release Engine Tests` fails | A change to `release.py` without its test, or a changed section order. |

## See also

- [RELEASING.md](RELEASING.md) — tags and drafts.
- [../dev/TESTING.md](../dev/TESTING.md) — the four layers.
- [../internals/SELFTEST.md](../internals/SELFTEST.md) — what the checks do.
