# DEVELOPER

For the person building, changing or releasing Patchee.

## Build

Requires JDK 25 and network access to the GTNH maven (first build downloads the
toolchain):

```sh
JAVA_HOME=/opt/jdk-25 ./gradlew spotlessApply build
# -> build/libs/patchee-<version>.jar   (the PLAIN jar; -dev is for dev runs)
```

The dev runtime pins Twilight Forest 2.7.41: the newest version published to a
resolvable maven (the pack's 2.7.44 is not). The pin only affects a local
`runServer`; it never reaches a build.

## Architecture (design patterns)

The code is deliberately small and pattern-based. Adding a feature = **one
registration** in `features/PatcheeFeatures.standard()`; its config option,
banner field and pipeline steps all follow from it.

| Pattern | Where |
|---|---|
| **Artifact registry** | `core/FeatureRegistry.java` + `core/Feature.java` (descriptor: id, config key, default, description, target class, steps, `early()` for work that must run at preInit — see `features/VeinConfigFeature.java`) + `features/PatcheeFeatures.java` |
| **Composition** | `core/Step.java` (`Outcome run(PatchContext)`), `core/Steps.java` (`sequence`, `present`, `noop`) — features are composed steps, not monoliths |
| **Decorators** | `core/StepDecorators.java`: `failSoft` (Throwable → FAILED, policy-logged; the server never breaks), `logged` (DEBUG-only, so prod logs are unchanged), `requiresTarget` (absent target → SKIPPED with the feature's INFO line) |
| **Policies** | `core/policy/` — `FailurePolicy` (`FAIL_LOG_ERROR_CONTINUE`) and `MissingTargetPolicy` (`QUIET_SKIP_INFO`); decorators consult them, no feature contains a raw catch |
| **Factory** | `core/PatchFactory.java` builds the decorated pipeline of enabled features; `core/FeaturePipeline.java` runs it |
| **Dependency injection** (manual) | `core/PatchContext.java` carries logger, `Toggles` snapshot, policies and `core/reflect/Reflective` into every step; `CommonProxy` is the composition root. Features never touch static `Config` fields. |

`fixes/` no longer exists — the old monolithic classes were replaced by the
`features/` (one file per feature) + `core/` split.

## Invariants — do not break these

- **Log strings are API.** The CI smoke test greps for `[DollyFix] Dolly now
  accepts …`, `[MattockFix] mattock shovel materials …`, `[SuperTankFix] …`,
  `[VeinConfig] wrote …`, `[AuroraBreak] armed …`, `[ArcticFurBreak] armed …`,
  `[TeamChunkCap] armed …` and
  `[SelfTest] result: N passed, 0 failed`. Ops docs quote them too.
- **The config file keys/comment text** (`enabled`, `enableDollyFix`,
  `enableMattockFix`, `enableSuperTankFix`, `enableVeinConfig`,
  `enableLightOverlayTweak`, `enableAuroraBreak`, `enableArcticFurBreak`,
  `extraDollyClasses`, `runSelfTest`, the `veinConfig.*` section keys
  (`veinConfig.blockLimit`, `veinConfig.radius`, `veinConfig.blocks`,
  `veinConfig.extraTools`) and `lightOverlay.maxLightNormal`) are user-facing;
  keep names, defaults and the plain-language comments stable. Config
  generation is registry-driven — the feature descriptor supplies the text.
  `docs/SETTINGS.md` is the complete, authoritative list of every key; keep
  this bullet as the rule plus that pointer, never a hand-maintained list
  that can rot.
- **Every fix stays fail-soft** (INFO skip on absent mods, ERROR + untouched on
  malformed targets) and **config-gated**.
- **The two class-load patches are the exception to "no bytecode edits".**
  `LightOverlay` (client, NEI's F7 overlay) and `TeamChunkCap` (server,
  ServerUtilities' team limits) rewrite another mod's class bytes as those bytes
  load, because the thing to change is a comparison constant and a return value
  with no hook and no config key. Both live in `asm/` as `IClassTransformer`s
  registered by `asm/FMLPlugin`, both are name-gated to one class, both report
  their site count, both leave a class of any other shape **untouched** with an
  ERROR line, and both are idempotent. `TeamChunkCap` is the one patch that
  injects a call (the JVM has no "min" instruction) — three instructions per
  return site, with the class's own stack map frames left alone. Verify a
  transformer against the REAL bytes with `tools/matcher-check` (see its README)
  before believing it works: a matcher that never fires is silent.
- **A class-load patch cannot reach Forge's config.** Its switches are parsed
  from `config/patchee.cfg` by the patch's own `*Settings` class at load time and
  seeded into a small holder (`TeamChunkCap`); the feature's arm step then
  publishes the authoritative `Toggles` values into that same holder, which is
  what the hook reads on every call. Publish to the exact object the hot path
  reads, and log the effective values in the armed line.
- One jar must stay **client-optional** and **acceptableRemoteVersions = "*"**:
  clients never need it to join. Without a client copy, a client is out of step
  where a fix changes the same thing on both sides (e.g. the server clears the
  tank effects while the client still applies them, and the mattock's sand/snow
  speed differs). The Twilight Forest break speeds are different: they are seen
  only on a client that has Patchee, and a client without it just keeps the plain
  1.7.10 speed — no mismatch. All of that is accepted; the docs describe it.
- **The break-speed features are two-sided — never server-only.** In 1.7.10 the
  client is the clock: `PlayerControllerMP` accumulates the break progress and
  sends `C07PacketPlayerDigging(2)` when it reaches `1.0F`, and the server only
  validates a `0.7F` minimum (`ItemInWorldManager.uncheckedTryHarvestBlock`), so
  a floor applied only on the server changes nothing a real player feels. Apply
  `AuroraBreak` and `ArcticFurBreak` on both sides; the faster break is what a
  player sees on a client that has Patchee installed.

## CI / CD

- `ci.yml` — build + formatter/checkstyle, then a **real server smoke test**:
  boots `runServer` with `runSelfTest=true` and fails unless the self-test
  reports `0 failed`.
- `security.yml` — dependency-graph submission (weekly + on push; feeds
  Dependabot alerts). CodeQL is intentionally absent: it needs GitHub Advanced
  Security on private repos.
- `dependabot.yml` + `dependabot-automerge.yml` — weekly `github-actions`
  updates, auto-merged when green. The Gradle ecosystem is not enabled there
  because Dependabot cannot resolve the GTNH toolchain.
- `release.yml` — run from the Actions tab (`workflow_dispatch`, pick
  `auto`/`patch`/`minor`/`major`, `dry_run` to preview) or push a `v*` tag. The
  engine (`.github/scripts/release.py`) builds grouped notes from Conventional
  Commits, updates `CHANGELOG.md`, commits + tags + pushes, builds the jar and
  opens a **draft** GitHub release for review. Unit tests:
  `python3 .github/scripts/test_release.py`.

## Licence position

Twilight Forest code — the modern 1.20.1 mod and the old 1.7.10 mod — is GNU
LGPL v2.1-or-later, "©2012-2017 Ben Mazur / Benimatic"; the 1.7.10 code was
re-licensed to LGPL-2.1 in 2017, commit 98b88bd on the Twilight Forest 1.7.10
branch. The modern mod splits its assets: code LGPL-2.1, non-code art (textures
and models) CC BY-NC-SA 4.0, sounds all rights reserved. The 1.7.10 Twilight
Forest on the server is the GTNewHorizons fork
(LGPL-2.1 code; textures CC BY-NC-SA 4.0; sounds all rights reserved; GTNH
modifications LGPL-2.1, 2021-2024).

**Rule for future work:** Twilight Forest source must never be copied into
Patchee — copying its code verbatim would drag LGPL-2.1 copyleft onto those
files and break Patchee's pure-MIT release, and its art and sounds are barred
outright. A feature may only **re-implement the behaviour**; Patchee ships none
of Twilight Forest's code, art or sound and changes runtime behaviour only.

## Deeper notes

The technical research behind each fix (exact classes, bytecode evidence,
alternatives considered) lives in the operations notes on the server-ops side
(`gtnh-server-ops` playbook) and in the commit messages — read those before
changing a fix's reflection targets.

**Verified against:**
- JABBA (GTNH fork), `ItemBarrelMover` — static list at class-init, gate at
  `for (Class c : classExtensions) if (c.isInstance(te)) return true;`.
- Tinkers' Construct (GTNH fork) `1.14.108-GTNH` — javap-verified:
  `static Material[] shovelMaterials` (package-private, not final) is read
  per call by `getEffectiveSecondaryMaterials()`.
- GT5-Unofficial — `gregtech.common.blocks.ItemMachines#onUpdate` (the four
  potions, `mItemCount` / `mFluid > 64000` gates).
- ServerUtilities 2.4.9 — `serverutils.data.ServerUtilitiesTeamData`
  (`getMaxClaimChunks` / `getMaxChunkloaderChunks` sum
  `serverutilities.claims.max_chunks` / `serverutilities.chunkloader.max_chunks`
  over `team.getMembers()`, cache into `cachedMaxClaimChunks` /
  `cachedMaxChunkloaderChunks`, and answer `-1`/`-2` for the inactive and
  invalid-team cases). Enforced at `serverutils.data.ClaimedChunks#claimChunk`
  (`max == 0 || getTeamChunks(...).size() >= max` → `NO_POWER`) and
  `#loadChunk`; the numbers the claim-map GUI shows are computed server-side in
  `serverutils.net.MessageClaimedChunksUpdate`, so no client change is needed.
  Bytecode and matcher proof: `tools/matcher-check`.
- Forestry (GTNH fork), Gendustry, MagicBees — class names read from source.