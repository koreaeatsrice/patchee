# Contributing

This page is the working manual: environment, commands, the complete recipe for a new feature, how to test a
class transformer, and the review process this repo uses. `AGENTS.md` is the short rule set; this page is the
detail.

## 1. Environment

| Requirement | Detail |
|---|---|
| JDK | 25. `.java-version` contains `25`. |
| Second JDK | 17. The GTNH convention plugin resolves a JDK 17 toolchain for helper tasks. CI installs Zulu 17, then Temurin 25, and exports `JAVA_HOME_17_X64` (`gradle.properties`, `org.gradle.java.installations.fromEnv`). |
| Build tool | The wrapper. Use `./gradlew`, never a system Gradle. |
| Network | The first build downloads the GTNH maven (`https://nexus.gtnewhorizons.com/repository/public/`), Forge, and the dev-runtime mods. |
| Python | 3.x, for `.github/scripts/release.py` and its tests. No third-party packages. |
| OS | Linux or macOS. Windows works through `gradlew.bat`, but the scripts and docs assume a POSIX shell. |

```sh
git clone https://github.com/koreaeatsrice/patchee.git
cd patchee
./gradlew build --no-daemon
```

The first build is slow. Later builds are incremental. `org.gradle.configuration-cache` and
`org.gradle.parallel` are on in `gradle.properties`.

## 2. Commands

| Command | What it does |
|---|---|
| `./gradlew build --no-daemon` | Compile, run the tests, run Spotless and Checkstyle, write `build/libs/patchee-<version>.jar`. This is the command CI runs. |
| `./gradlew test --rerun-tasks` | Force the JUnit 5 tests to execute even when Gradle thinks they are up to date. |
| `./gradlew spotlessApply` | Apply the formatter. Run it before every commit. |
| `./gradlew spotlessCheck` | Check formatting without writing. |
| `./gradlew checkstyleMain` | The Checkstyle pass (the build enables the GTNH wildcard-import check). |
| `./gradlew runServer` | Boot the dev server in `run/server`. The dev runtime includes the pinned mods in `dependencies.gradle`. |
| `./gradlew runClient` | Boot a dev client. Useful for a two-sided manual check. |
| `python3 .github/scripts/test_release.py` | Unit tests for the release engine. |
| `python3 -m unittest -v test_release` | The same tests as CI runs them, from `.github/scripts/`. |

Notes that save time:

- `./gradlew build` writes both a plain jar and a `-dev` jar. The plain jar is the release artifact; `-dev` is for
  a development run. CI excludes `*-dev.jar` and `*-sources.jar` when it uploads.
- The dev runtime pins Twilight Forest 2.7.41, the newest version published to a resolvable maven. The pack's
  2.7.44 is not resolvable there. The pin affects `runServer` only; it never reaches a published build.
- Gendustry and MagicBees are not on the dev runtime: their jitpack artifacts drag in an unresolvable
  CodeChickenLib pin. The self-test skips them locally and checks them on a real pack.
- `run/` is gitignored. A dev server's config, world and logs stay out of the repo.

## 3. Formatting and style

- Java is formatted by Spotless, configured by the GTNH convention plugin with
  `gtnh.settings.blowdryerTag = 0.2.2` (`gradle.properties`). Do not fight it; run `spotlessApply`.
- Checkstyle currently enforces the wildcard-import rule. No `import foo.*;`.
- `.editorconfig` is authoritative for whitespace: UTF-8, LF, 4-space Java indent, 2-space indent for
  `*.{json,md,yml,yaml,...}`, final newline, trailing whitespace trimmed except in `*.md` and `*.lang`.
- The house voice is dry and factual. No marketing adjectives, no emoji. A limit or tradeoff gets one sentence.
- Log lines are contracts. Keep the existing text unless the PR is about changing it. CI greps specific lines
  (`.github/workflows/ci.yml`, the smoke job), and `docs/INSTALL-SERVER.md`, `docs/SETTINGS.md` and
  `docs/TROUBLESHOOTING.md` quote others.

## 4. Branches and commits

Branch from `main`, one topic per branch:

```
feat/<slug>      new behaviour
fix/<slug>       a correction
docs/<slug>      documentation only
test/<slug>      tests only
chore/<slug>     build, CI, release plumbing
```

Commits use Conventional Commits, because the release engine parses them (`.github/scripts/release.py`,
`CONVENTIONAL_PATTERN`):

```
<type>(<scope>): <description>

[optional body]
```

| Type | Release section |
|---|---|
| `feat` | Features; forces a minor bump in `auto` mode |
| `fix` | Bug Fixes |
| `perf` | Performance Improvements |
| `refactor` | Refactoring & Code Quality |
| `docs` | Documentation & Wiki |
| `style` | Code Style & Formatting |
| `test` | Tests & Verification |
| `ci`, `build`, `chore` | Maintenance & CI |

A `!` after the type or scope, or a `BREAKING CHANGE:` line in the body, marks a breaking change and forces a
major bump in `auto` mode. Examples from this repo: `feat(pollution): ...`, `fix(glassbreak): ...`,
`test(fishtrap): ...`, `docs(readme): ...`, `chore(release): v1.9.0`.

Rules:

- The subject is imperative and lower-case after the colon.
- One logical change per commit. A release commit is made by the engine, not by hand.
- Do not put a GitHub issue number in a commit subject unless it is a real reference. The release notes link
  commits by SHA; a bare `#12` in prose is ambiguous.
- Do not amend or force-push a branch that has a PR under review unless the reviewers asked for it. A new commit
  is easier to review than a rewritten one.

## 5. The complete feature recipe

Work through this list in order. A feature is not done until every applicable box is true.

### 5.1 Decide the shape

- [ ] Is the change possible with reflection, an event listener, a registry change or a config write? Use a plain
      feature. This is the default.
- [ ] Is the thing to change a constant, a return value or a constructor argument inside another mod's compiled
      method, with no hook and no config key? Use a transformer-backed feature.
- [ ] Choose the client shape: SERVER-ONLY (preferred), BOTH-SIDES-OPTIONAL, or CLIENT-ONLY (cosmetic and inert
      without Patchee only). Write the choice in the PR.

### 5.2 Write the feature class

- [ ] Create `src/main/java/com/jointspaceforce/patchee/features/YourFeature.java`, implementing `core.Feature`.
- [ ] Return a stable `id()` (the log prefix and the banner field) and a stable `configKey()`
      (`enableYourThing`).
- [ ] Write `description()` as plain language for the config file. It is shown to server owners, not developers.
- [ ] Write `disabledMessage()`, `failureMessage()`, `targetClass()` and `missingTargetMessage()`. `targetClass()`
      is `null` only when the feature does not depend on one external class at boot; then handle a missing mod
      inside the step and log the skip yourself.
- [ ] Implement `steps()` as small units. Use `Steps.sequence`, `Steps.withStaticField`, `Steps.present` and
      `Steps.note`. Do not wrap the step's main work in a `try/catch`; the factory's `failSoft` decorator owns failure
      handling. A local catch is acceptable only in a handler that must not fail a tick, and it must log once.
- [ ] Read settings through `ctx.toggles()` and the file system through `ctx.configDirectory()`. Never read
      `Config` statics from a feature.
- [ ] If the work must run before another mod's `preInit`, override `early()` and explain why in the Javadoc. Only
      do this when the target reads the file you write in its own `preInit`; `before:<modid>` must then be in
      `Patchee.java`'s `dependencies`.
- [ ] If the feature is server-only, gate the step with
      `FMLCommonHandler.instance().getSide() != Side.SERVER` and return `Outcome.SKIPPED` with a line.
- [ ] If it registers an event handler, guard it: `event.world == null || event.world.isRemote` before touching
      server state.

### 5.3 Register it

- [ ] Add one `register(new YourFeature())` line to `features/PatcheeFeatures.standard()`, in run order.
- [ ] Do not edit `Config.java` for the switch. The registry supplies the key, default and comment.
- [ ] Check the boot banner: a new field appears automatically as `yourFeature=<bool>`. If the banner line is
      quoted in an ops doc, update that doc.

### 5.4 Add the settings

- [ ] If the feature needs a value beyond on/off, add it to `core/Toggles`, to the `Config.Snapshot`, to
      `Config.synchronizeConfiguration`, and to the static defaults. Keep the comment text plain.
- [ ] If a transformer needs the same value before Forge config exists, add a small `*Settings` class that parses
      `config/patchee.cfg` itself, following `LightOverlaySettings` or `TeamChunkCapSettings`. Reuse the key
      constants so the early reader and the normal config cannot drift.
- [ ] Add the key to `docs/SETTINGS.md`, with the default and the "change it when" column filled in.
- [ ] If the key replaced an old one, keep the old key parseable and ignored, as `TeamChunkCapSettings` does with
      `teamChunkCap.maxClaimChunks` and `teamChunkCap.maxLoadedChunks`.

### 5.5 Prove it

- [ ] Add a `SelfTest` check when the behaviour can be exercised on a server. Follow the existing style: one
      `check("what was observed", condition)` call, a control case that must stay unchanged, and a `SKIP` line
      when the mod is absent. Add the call to `runChecks`.
- [ ] If a self-test is impossible, say why in the PR and add the strongest available test instead.
- [ ] Add a JUnit 5 test for every pure decision class (the threshold maths, the team-limit rule, the config
      parser, the patch matcher). These run without a game.
- [ ] For a transformer, add or extend a matcher check in `tools/matcher-check/` and run it against real bytes.
- [ ] For a reflection target, verify the class, field and descriptor with `javap` against the real jar the pack
      ships. Paste the command and its output in the PR.
- [ ] Add a real-jar test when a field name is the risk, following `PollutionVisualsRealJarTest` and its
      `PATCHEE_GREGTECH_JAR` gate.

### 5.6 Document it

- [ ] Add a row to `docs/FEATURES.md`: what it does, the switch, the honest limits.
- [ ] Add a row to `docs/SETTINGS.md`: key, effect, default, when to change it.
- [ ] Add one README bullet if the feature is user-visible. The bullet must be one sentence of at most 18 words
      after the em dash; `ReadmeFeatureSentencesTest` enforces that and will fail the build.
- [ ] If the boot log gained a line operators should look for, add it to `docs/INSTALL-SERVER.md` and, if it has a
      failure mode, to `docs/TROUBLESHOOTING.md`.
- [ ] If the feature has a client half, add it to `docs/CLIENT-MODS.md`.

### 5.7 Check client optionality

- [ ] Does a stock client still join and see no error? Test it if the change touches registration, world data or a
      class that exists on both sides.
- [ ] Does the server jar alone do the whole job? If not, the feature is BOTH-SIDES-OPTIONAL, and the docs must
      say what a stock client sees instead.
- [ ] Did you add a packet, a channel, a client-only mod requirement, a registration change or a one-sided class
      patch without a guard? If yes, stop and redesign. The forbidden list is in `AGENTS.md`.
- [ ] Run the no-packet check:
      `grep -rn "SimpleNetworkWrapper\|NetworkRegistry\|IMessage\|IPacket" src/main/java` must return nothing.

### 5.8 Build and review

- [ ] `./gradlew spotlessApply` then `./gradlew build --no-daemon`, green.
- [ ] `./gradlew test --rerun-tasks`, green.
- [ ] `python3 .github/scripts/test_release.py`, green if you touched the release engine.
- [ ] Commit with a Conventional Commit subject, push, open the PR with the banner and the three sections.
- [ ] Answer the two hostile reviews with fixes, not arguments. Loop until both approve.

## 6. Testing a class transformer safely

A transformer is the riskiest code in the repo: it runs inside the class loader, on bytes you do not own, before
the game is up. Test it in layers, cheapest first.

1. **Pure matcher unit test.** Feed synthetic bytes built with ASM to the `*Patch` class and assert the state, the
   site count and the instruction sequence. Assert idempotence: a second pass returns the input unchanged. Assert
   a non-target class passes through byte-identical. Assert a wrong-shape class returns the original bytes and a
   `MISMATCH`/failure state. Existing examples: `LightOverlayPatchTest`, `TeamChunkCapPatchTest`,
   `FinalCastleBreakPatchTest`, `ChestplateFlightPatchTest`, `GraviSuiteFlightPatchTest`.
2. **Real-byte matcher tool.** Run the real transformer class over the real jar with `tools/matcher-check/`.
   `MatcherCheck` for ServerUtilities (`RESULT: 8/8 PASS`), `CastleCheck` for Twilight Forest
   (`RESULT: 10/10 PASS`), `ChestplateCheck` for IC2 (`RESULT: 18/18 PASS`). The tools check the patched class,
   the untouched methods, idempotence, the non-target pass-through and the fail-loud path. Read the tool's
   `README.md` for the classpath; it tells you which bytes are live (SRG names) and which are dev (MCP names).
3. **Dev server.** `./gradlew runServer` with `runSelfTest=true`. Look for the transformer's `patched ...` line
   with the expected site count and for `[SelfTest] result: N passed, 0 failed`.
4. **A second pass.** A transformer must survive being applied twice. The matcher tools and the unit tests both
   assert this; do not rely on the loader only calling once.
5. **Fail-loud.** Hand the transformer a class of the right name but the wrong shape and confirm it logs ERROR with
   the observed shape and returns the original bytes. A silent no-op is the failure mode this repo guards against.

Never test a transformer by dropping an unreviewed jar on a live server. Never edit a third-party jar and commit
it. `src/test/resources/` holds only small extracted class files and synthetic bytes; if a licence does not allow
redistribution, use the environment-variable gate instead, as `PollutionVisualsRealJarTest` does.

## 7. The pull request

Every PR body starts with the banner used in this repo:

```md
> [!NOTE]
> AI-GENERATED CONTENT

### 1. What Was Done
### 2. Why & Context
### 3. Overall Workflow & Project Status
```

Then:

- **What Was Done** — the files and behaviour, in plain English. No raw diff dump.
- **Why & Context** — the problem, the owner's rule it satisfies, the alternatives rejected.
- **Overall Workflow & Project Status** — what stage the work is in, what was verified, what is left.
- **Verification** — the exact commands and the output you saw. A test name, a matcher `RESULT`, a log line.
- **Limits** — one sentence each. A stock client's experience, a mod version that is not covered, a performance
  cost.

Do not write `#<number>` for anything except a real issue or PR reference. Write `Step 2`, `commit 3`,
`Topic 4`.

### 7.1 Review

This repo uses adversarial review: two independent reviewers, a fresh pair each round, both hostile to the change
and read-only.

- Reviewers check the technical claims against the source. A wrong signature, an invented class name, a stale
  command, a line number that does not match, or a doc that describes an API nobody opened is a rejection.
- Reviewers check that no artifact-style document crept in beyond the requested set, and that every command in a
  doc exists in the repo (Gradle task, workflow name, script path).
- A finding is fixed, not argued. The loop repeats with a brand-new pair until both return APPROVE on the current
  head commit.
- The author may not merge on one approval or on a stale approval. The approval must be for the live head SHA.

### 7.2 Merge

`main` is unprotected, but the local git guard blocks `gh pr merge`. Merge through the REST API with the reviewed
head SHA pinned:

```sh
gh api -X PUT repos/koreaeatsrice/patchee/pulls/<n>/merge \
  -f merge_method=squash -f sha=<head-sha>
```

Squash only. Then read the response back and confirm `merged: true` and a `merge_commit_sha`. Delete the branch.
Do not cut a release as part of a feature PR; the release is a separate workflow (`docs/RELEASE.md`).

## 8. Common mistakes

| Mistake | Why it fails |
|---|---|
| Reading `Config.enabled` from a feature | It is a static bypass of the `Toggles` snapshot; the pipeline and the tests cannot see it. |
| Swallowing an exception around a step's main work | Duplicates `failSoft`; hides the failure policy and changes the log. |
| Catching only `Exception` in a transformer | `LinkageError`, `NoClassDefFoundError` and `VerifyError` are `Error`s. Catch `Throwable`. |
| Forgetting the name gate | The transformer runs for every class; a missing gate is a performance and correctness bug. |
| Changing a config key name | Breaks every existing server's file and the docs. Add a new key, keep the old one parseable. |
| Editing `docs/SETTINGS.md` only | `docs/FEATURES.md` and the README bullet are also user-facing. |
| Adding a report Markdown file | Forbidden. Put it in the PR conversation. |
| Copying a mod's source "for reference" | Licence contamination. Verify with `javap`, cite the command. |
| Committing `build/`, `run/`, a jar | `.gitignore` covers most of it; a forced add is a rejection. |
| Testing only on a patched client | The contract is a stock client. Test the stock case. |
