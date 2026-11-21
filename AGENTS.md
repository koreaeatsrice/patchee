# AGENTS.md

Patchee is a server-side add-on for Minecraft 1.7.10 / GT:New Horizons: a Forge coremod that changes other mods'
behaviour at class-load time or through reflection, without editing their jars. This page is the short rule set for
an AI agent or a human about to change the repo. The detail lives in `docs/`; the index is `docs/README.md`.

## What the repo is

| Path | What it is |
|---|---|
| `Patchee.java` | The `@Mod` entry: `modid = "patchee"`, `version = Tags.VERSION`, `acceptableRemoteVersions = "*"`, `dependencies = "before:VeinMiner"`. |
| `asm/FMLPlugin.java` | The coremod entry: registers five `IClassTransformer`s, runs `PollutionPreload` from `injectData`. |
| `core/` | The pipeline: `Feature`, `Step`, `Outcome`, `Steps`, `StepDecorators`, `PatchFactory`, `FeaturePipeline`, `FeatureRegistry`, `Toggles`, `PatchContext`, `policy/`, `reflect/`. |
| `features/` | One class per feature; `PatcheeFeatures.java` is the only registration point. |
| `asm/` | The five transformers; each delegates byte work to a `features/*Patch.java`. |
| `SelfTest.java` | The opt-in behavioural check (`runSelfTest=true`) on the first server tick. |
| `src/test/java/` | JUnit 5 tests. `src/test/resources/` holds test bytes, never a third-party jar. |
| `tools/matcher-check/` | Runs a transformer over a real jar and asserts the result. |
| `docs/` | All documentation. `.github/` holds the workflows and the release engine. |

All Java paths above are under `src/main/java/com/jointspaceforce/patchee/`.

## Build and test

```sh
./gradlew build --no-daemon        # compile, test, Spotless, Checkstyle; writes build/libs/patchee-<version>.jar
./gradlew test --rerun-tasks       # force the tests to run
./gradlew spotlessApply            # format before committing
./gradlew runServer                # dev server; downloads the pinned dev mods on first run
python3 .github/scripts/test_release.py   # release engine tests
```

JDK 25 is required (`.java-version`). The GTNH convention plugin also resolves a JDK 17 toolchain for helper
tasks. The plain `patchee-<version>.jar` is the release artifact; `-dev` is not.

## House rules

A PR that breaks one of these is rejected, regardless of intent.

1. **Pollution data is always sent to clients.** Never zero, clamp, delay or rewrite
   `gregtech.api.net.GTPacketPollution`. Every client receives the real number. Pollution visuals are hidden on the
   client only, through GregTech's `Pollution Fog` and `Pollution Dirt` flags in `config/GregTech/Client.cfg` and
   the in-memory render flags and tint tables.
2. **Never commit agent artifacts.** No report, handover, audit, verdict or summary Markdown outside the docs in
   this repo. No `review/` or `reports/` folder. Long-form scratch stays in the PR conversation. A commit in the
   history removed stale handover reports from `review/`; do not recreate that folder.
3. **Clean-room only.** Never copy Twilight Forest, IC2, GraviSuite, GregTech or Pam's source code, art or sound
   into this repo. A patch may rewrite the bytecode of a mod the user already owns, or re-implement published
   behaviour. It may not vendor the original. Extracted class files used as matcher fixtures under
   `src/test/resources/finalcastlebreak/` are test inputs, not a redistribution; never add a mod jar.
4. **Client optional.** The server jar is complete on its own. A player joins with a stock client and nothing
   installed. A PR that makes the jar required on the client is rejected by policy. See the next section.
5. **Fail soft.** A missing mod is an INFO skip. A changed shape is an ERROR line and the target is left untouched.
   The server always starts. Steps are wrapped by the fail-soft decorator; do not swallow an exception around a
   step's main work.
6. **Log strings are API.** Operators and CI grep them. Do not change an existing INFO/ERROR line unless the PR is
   about that line. `docs/ops/CI.md` lists the ones CI asserts.
7. **Config keys are API.** Names, defaults and comments in `config/patchee.cfg` are user-facing.
   `docs/SETTINGS.md` is authoritative. Add a key; do not rename one.
8. **Dry, factual voice.** No marketing, no adjectives, no emoji. A tradeoff or limit gets one sentence.

## Client optionality

Patchee is installed on the server. A player joins with a stock client and nothing installed. Every feature picks
one shape:

- **SERVER-ONLY** (default, preferred): the client needs nothing. Dolly, GlassBreak, VeinConfig, TeamChunkCap,
  the Fish Trap move hotkeys.
- **BOTH-SIDES-OPTIONAL**: the server half is complete; a client copy only improves what that player sees or
  feels. The Twilight Forest break-speed floors, the mattock's shovel speed (its drops are server-side), the tank
  effect clear, the fertilizer recipe's NEI refresh, the IC2/GraviSuite flight edits, the pollution visuals.
- **CLIENT-ONLY**: allowed only when cosmetic and inert without Patchee. NEI's F7 overlay.

Patchee registers no network channel and no packet. This must stay true:

```sh
grep -rn "SimpleNetworkWrapper\|NetworkRegistry\|IMessage\|IPacket" src/main/java   # must print nothing
```

A one-sided step checks `FMLCommonHandler.instance().getSide()` and returns `Outcome.SKIPPED` with a log line. An
event handler checks `event.world == null || event.world.isRemote` before touching server state. If a feature needs
a packet, a client mod, a changed registration set or changed world data, it is the wrong shape. The mechanics and
the proof for each shape are in `docs/internals/SIDES.md`.

## Adding a feature, in brief

A plain feature is one class in `features/` implementing `core.Feature`, plus one `register(new ...)` line in
`PatcheeFeatures.standard()`. Settings come from `ctx.toggles()`, reflection from `ctx.reflect()`, and the config
option, boot banner field and pipeline wiring follow from the registry. A transformer-backed feature adds an
`asm/*Transformer.java`, a registration line in `FMLPlugin`, a `features/*Patch.java`, and still keeps a `Feature`
for the switch, banner and self-test. The full recipe, with the doc rows and the tests, is
`docs/dev/ADDING-A-FEATURE.md`.

## Forbidden

- Editing `GTPacketPollution`, or suppressing pollution data in any way.
- Copying another mod's source, art, sound or jar into the repo (extracted matcher fixtures excepted).
- Committing reports, handovers, audits, verdicts, summaries or a `review/`/`reports/` directory.
- Adding a network channel, a packet class or a client-only mod requirement.
- Throwing out of a transformer; it must catch `Throwable`, log ERROR and return the original bytes.
- Renaming a config key or changing a documented log line without saying so in the PR.
- Committing `build/`, `run/`, a jar or a Gradle cache.
- Guessing a class, field or method name; verify it with `javap`.
- Writing `#<number>` in PR text unless it is a real issue or PR reference.

## Where to read next

- `docs/README.md` — the index, one line per page.
- `docs/dev/WORKFLOW.md` — branch, commit, review and merge.
- `docs/internals/PIPELINE.md` — how a feature actually runs.
- `docs/internals/TRANSFORMERS.md` — the five class patches and their safety rules.
- `docs/ops/RELEASING.md` — versions, tags and drafts.
- `docs/FEATURES.md`, `docs/SETTINGS.md` — what a player and an operator see.
