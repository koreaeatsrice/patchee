# AGENTS.md

Patchee is a server-side add-on for Minecraft 1.7.10 / GT:New Horizons. It is a Forge coremod that changes other
mods' behaviour at class-load time or through reflection, without editing their jars. This file is for an AI agent
or a human who is about to change the repo. Read it before you touch code. The deeper guides are linked at the end.

## What the repo is

| Path | What it is |
|---|---|
| `src/main/java/com/jointspaceforce/patchee/Patchee.java` | The `@Mod` entry point. `modid = "patchee"`, version from `Tags.VERSION`, `acceptableRemoteVersions = "*"`, `dependencies = "before:VeinMiner"`. |
| `src/main/java/com/jointspaceforce/patchee/asm/FMLPlugin.java` | The coremod entry point. Registers five `IClassTransformer`s and runs `PollutionPreload` from `injectData`. |
| `src/main/java/com/jointspaceforce/patchee/core/` | The feature pipeline: `Feature`, `Step`, `Outcome`, `Steps`, `StepDecorators`, `PatchFactory`, `FeaturePipeline`, `FeatureRegistry`, `Toggles`, `PatchContext`, `policy/`, `reflect/`. |
| `src/main/java/com/jointspaceforce/patchee/features/` | One file per feature. `PatcheeFeatures.java` is the single registration point. |
| `src/main/java/com/jointspaceforce/patchee/asm/` | The five class transformers. Each delegates the byte work to a `features/*Patch.java`. |
| `src/main/java/com/jointspaceforce/patchee/SelfTest.java` | The opt-in behavioural check (`runSelfTest=true`) that runs on the first server tick. |
| `src/test/java/...` | JUnit 5 tests. The pure decision classes are tested without a game. |
| `src/test/resources/` | Real and synthetic class bytes used by the transformer tests. Do not add third-party jars here. |
| `tools/matcher-check/` | Standalone tools that run a transformer over a real mod jar and assert the result. |
| `docs/` | User and contributor documentation. |
| `.github/workflows/` | `ci.yml`, `release.yml`, `security.yml`, `dependabot-automerge.yml`. |
| `.github/scripts/release.py` | The release engine. Its tests are `.github/scripts/test_release.py`. |

Read `docs/ARCHITECTURE.md` before changing `core/` or `asm/`. Read `docs/CONTRIBUTING.md` before opening a PR.
Read `docs/GTNH-INTERNALS.md` before touching obfuscated names or class transformers. Read `docs/RELEASE.md`
before touching a tag or a release.

## Build and test

```sh
./gradlew build --no-daemon        # compile, test, Spotless, Checkstyle; writes build/libs/patchee-<version>.jar
./gradlew test --rerun-tasks       # force the unit tests to run again
./gradlew spotlessApply            # format before committing
./gradlew runServer                # dev server (downloads the pinned dev mods on first run)
python3 .github/scripts/test_release.py   # release engine unit tests
```

Requires JDK 25 (`.java-version`). The GTNH convention plugin also resolves a JDK 17 toolchain for helper tasks;
CI installs both and exports `JAVA_HOME_17_X64`. The first build downloads the GTNH maven artifacts.

The jar you ship is the plain `patchee-<version>.jar`. The `-dev` jar is for a dev run and is not the release
artifact. CI uploads `build/libs/*.jar` while excluding `*-dev.jar` and `*-sources.jar`
(`.github/workflows/ci.yml`).

## House rules

These are not style preferences. A PR that breaks one is rejected.

1. **Pollution data is always sent to clients.** Never zero, clamp, delay or rewrite
   `gregtech.api.net.GTPacketPollution`. Every client must receive the real number, including a stock client that
   has never heard of Patchee. Pollution visuals are hidden on the client only, through GregTech's own
   `Pollution Fog` and `Pollution Dirt` flags in `config/GregTech/Client.cfg` and through the in-memory render
   flags and tint tables. Any change that edits the packet is out of scope, regardless of intent.
2. **Never commit agent artifacts.** No report, handover, audit, verdict or summary Markdown outside the docs the
   owner asked for. No `review/` or `reports/` folders. Long-form scratch and review notes stay in the PR
   conversation. The documentation set in this repo is the exception, because the owner ordered it. The history
   contains a commit that removed stale handover reports from `review/`; do not recreate that folder.
3. **Clean-room only.** Never copy Twilight Forest, IC2, GraviSuite, GregTech or Pam's code, art or sound into
   this repo. A patch may rewrite the bytecode of a mod the user already owns, or re-implement published
   behaviour. It may not vendor the original. The licence position is in `docs/GTNH-INTERNALS.md`.
4. **Client optional.** The server jar must be complete on its own. A player joins with a stock client and nothing
   installed. A PR that makes the jar required on the client is rejected by policy. The mechanics and the decision
   table are in `docs/ARCHITECTURE.md`; the short version is below.
5. **Fail soft.** A missing mod is an INFO skip. A mod that changed shape is an ERROR line and the class or field
   is left untouched. The server must always start. Feature steps are wrapped by the fail-soft decorator, which
   reports a throw through the failure policy. A step that catches for its own control flow (an event handler that
   must not fail a tick) says why in a comment.
6. **Log strings are API.** Operators and CI grep them. Existing INFO/ERROR lines stay byte-identical unless the
   change is the point of the PR. `DEVELOPER.md` keeps the tested list; CI asserts the ones in
   `.github/workflows/ci.yml`.
7. **Config keys are API.** Names, defaults and plain-language comments in `config/patchee.cfg` are user-facing.
   `docs/SETTINGS.md` is the authoritative list. Adding a feature adds a key; it does not rename one.
8. **Dry, factual voice.** No marketing, no adjectives, no emoji. If a fix has a tradeoff or a limit, write it in
   one sentence and move on. This applies to docs, PR text, code comments and commit messages.

### Client optionality in one paragraph

Patchee is installed on the server. A stock client joins and plays. For every new feature, choose one of three
shapes: SERVER-ONLY (preferred; the client needs nothing), BOTH-SIDES-OPTIONAL (the server half is complete and
the client copy only improves what that player sees or feels), or CLIENT-ONLY (allowed only when the feature is
cosmetic and inert without Patchee, such as the NEI F7 overlay). Patchee registers no network channel and no
packet; a grep for `SimpleNetworkWrapper`, `NetworkRegistry`, `IMessage` or `IPacket` in `src/main/java` must
return nothing. A step that only makes sense on one side checks
`FMLCommonHandler.instance().getSide()` and returns `Outcome.SKIPPED` with a log line. An event handler checks
`event.world == null || event.world.isRemote` before touching server state. If your feature needs a new packet,
a new client mod, a changed registration set or changed world data, it is the wrong shape. See
`docs/ARCHITECTURE.md` for the file and line references.

## Adding a feature

There are two shapes. Pick the smaller one that works.

**Plain feature (reflection, event listener or config write).** Create one class in `features/` that implements
`core/Feature`. Return an id, a config key, a default, a description, a disabled line, a failure line, a target
class (or `null`), a missing-target line, and a list of `Step`s. Add one `register(new YourFeature())` line to
`features/PatcheeFeatures.standard()`. The config option, the boot banner field and the pipeline wiring follow
from the registry. Nothing in `Config`, `CommonProxy` or `Patchee` needs an edit. Use
`ctx.toggles()` for settings and `ctx.reflect()` for reflection; never read `Config` statics from a feature.

**Transformer-backed feature.** If the thing to change is a constant, a return value or a constructor argument
with no hook and no config key, the change has to happen while the target class loads. Add a transformer in
`asm/`, register it in `FMLPlugin.getASMTransformerClass()`, and put the byte-level matching in a
`features/*Patch.java` class that the transformer calls. The `Feature` still exists, but only to own the config
switch, the boot line and any runtime listener. A transformer cannot read Forge's config, so its settings class
parses `config/patchee.cfg` itself. The full recipe, including the self-test and doc rows, is in
`docs/CONTRIBUTING.md`.

A feature is not done until it has: the code, the config key, the toggle path, a `SelfTest` entry (or a written
reason why it cannot be checked), a unit test for any pure decision, a real-jar or matcher check when it matches
bytecode, rows in `docs/FEATURES.md` and `docs/SETTINGS.md`, and a README bullet if it is user-visible.

## What a good PR looks like

- One feature or one fix per PR. The title is a Conventional Commit (`feat(scope): ...`, `fix(scope): ...`,
  `docs: ...`, `test: ...`, `chore: ...`).
- The body says what changed, why, how it was verified, and what the limits are. It opens with the
  `> [!NOTE]` / `> AI-GENERATED CONTENT` banner used in this repo.
- `./gradlew build --no-daemon` is green locally, and the CI smoke job is green.
- New behaviour is proven by output you actually saw: a unit test name, a `SelfTest` line, a matcher-check
  `RESULT: n/n PASS`, or a server log line. "Should work" is not proof.
- The diff contains no artifacts, no vendored mod code, no reformatting of unrelated files, and no renamed
  config keys.
- The client-optional question is answered in the PR: does a stock client still join and see no error?

## Forbidden

- Editing `gregtech.api.net.GTPacketPollution`, or suppressing pollution data in any way.
- Copying another mod's source, textures, models or sounds into the repo or its test resources.
- Committing reports, handovers, audits, verdicts or summary Markdown. Committing a `review/` or `reports/`
  directory.
- Adding a network channel, a packet class, `NetworkRegistry` registration or a client-only mod requirement.
- Throwing out of a transformer. A transformer runs inside the class loader; it must catch `Throwable`, log at
  ERROR and return the original bytes.
- Catching and swallowing an exception around a step's main work. Let the fail-soft decorator report it; a local
  catch is only for a handler that must not fail a tick, and it must log once.
- Renaming a config key, changing a default, or changing a log line operators quote without saying so in the PR.
- Committing a `-dev` jar, a built jar, `run/`, `build/`, a Gradle cache or a real mod jar.
- Guessing a class, field or method name. Verify it with `javap` against the real jar and cite the result.
- Using `#<number>` in PR comments unless it refers to a real GitHub issue or PR.

## Where to verify against the real jar

A matcher that never fires is silent, so never trust a transformer you have not run against real bytes.

```sh
# 1. Inspect a candidate jar without unpacking it.
unzip -l ServerUtilities-2.4.9.jar | grep -i teamdata
javap -p -c -classpath ServerUtilities-2.4.9.jar serverutils.data.ServerUtilitiesTeamData

# 2. Run the matching tool over the real jar (see tools/matcher-check/README.md).
#    TeamChunkCap: MatcherCheck.java   -> expected RESULT: 8/8 PASS
#    FinalCastleBreak: CastleCheck.java -> expected RESULT: 10/10 PASS
#    ChestplateFlight: ChestplateCheck.java -> expected RESULT: 18/18 PASS

# 3. Run the opt-in self-test against a real dev server.
#    Set runSelfTest=true in run/server/config/patchee.cfg, then:
./gradlew runServer
#    Look for "[SelfTest] result: N passed, 0 failed".
```

`tools/matcher-check/README.md` has the exact `javac`/`java` classpaths and the live-versus-dev byte caveat. The
GregTech field names are checked by `PollutionVisualsRealJarTest` when `PATCHEE_GREGTECH_JAR` points at a real
GregTech jar; without it the test is skipped.

## Where to go next

- `docs/ARCHITECTURE.md` — how the pipeline, the transformers, the proxies and the self-test actually work.
- `docs/CONTRIBUTING.md` — environment, commands, the full feature checklist, review process.
- `docs/RELEASE.md` — the release engine, version derivation and rollback.
- `docs/GTNH-INTERNALS.md` — 1.7.10 names, load order, reflection versus ASM, jar inspection.
- `docs/FEATURES.md` and `docs/SETTINGS.md` — the user-facing behaviour and every config key.
