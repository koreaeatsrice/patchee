# DEVELOPER

For the person building, changing or releasing Patchee.

## Build

Requires JDK 25 and network access to the GTNH maven (first build downloads the
toolchain):

```sh
JAVA_HOME=/opt/jdk-25 ./gradlew spotlessApply build
# -> build/libs/patchee-<version>.jar   (the PLAIN jar; -dev is for dev runs)
```

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
  `[VeinConfig] wrote …` and
  `[SelfTest] result: N passed, 0 failed`. Ops docs quote them too.
- **The config file keys/comment text** (`enabled`, `enableDollyFix`,
  `enableMattockFix`, `enableSuperTankFix`, `extraDollyClasses`,
  `runSelfTest`) are user-facing; keep names, defaults and the plain-language
  comments stable. Config generation is registry-driven — the feature
  descriptor supplies the text.
- **Every fix stays fail-soft** (INFO skip on absent mods, ERROR + untouched on
  malformed targets) and **config-gated**.
- One jar must stay **client-optional** and **acceptableRemoteVersions = "*"**:
  players never need it.

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
- Forestry (GTNH fork), Gendustry, MagicBees — class names read from source.