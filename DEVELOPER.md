# DEVELOPER

For the person building or changing Patchee. This page is a signpost: the detail is in `docs/`, and only the facts
unique to local development are kept here.

## Start here

| If you want to… | Read |
|---|---|
| Understand the design | [docs/internals/PIPELINE.md](docs/internals/PIPELINE.md) |
| Add a feature | [docs/dev/ADDING-A-FEATURE.md](docs/dev/ADDING-A-FEATURE.md) |
| Run the tests | [docs/dev/TESTING.md](docs/dev/TESTING.md) |
| Open a PR | [docs/dev/WORKFLOW.md](docs/dev/WORKFLOW.md) |
| Cut a release | [docs/ops/RELEASING.md](docs/ops/RELEASING.md) |
| Know the rules | [AGENTS.md](AGENTS.md) |
| See every page | [docs/README.md](docs/README.md) |

## Build

JDK 25, network access to the GTNH maven for the first build:

```sh
JAVA_HOME=/opt/jdk-25 ./gradlew spotlessApply build
# -> build/libs/patchee-<version>.jar   (the plain jar; -dev is for dev runs)
```

## Dev-runtime pins

- Twilight Forest is pinned to 2.7.41, the newest version published to a resolvable maven. The pack's newer build
  is not on that maven. The pin only affects `runServer`; it never reaches a published jar.
- Gendustry and MagicBees are not on the dev runtime: their jitpack artifacts drag in an unresolvable
  CodeChickenLib pin. The self-test skips them locally and checks them on a real pack.
- Blood Arsenal is absent, so the smoke job sees the GlassBreak skip line, not its armed line.

## What CI asserts

The smoke job repeats the list in [docs/ops/CI.md](docs/ops/CI.md). If a feature's log line changes, that file and
the workflow change in the same PR.

## Verified against

The builds the reflection targets and byte shapes were checked against. Re-verify with `javap` and
`tools/matcher-check/` before changing a target.

- JABBA (GTNH fork), `ItemBarrelMover` — static `classExtensions`, gate at
  `for (Class c : classExtensions) if (c.isInstance(te)) return true;`.
- Tinkers' Construct (GTNH fork) `1.14.108-GTNH` — package-private, non-final `Material[] shovelMaterials`.
- GT5-Unofficial `5.09.54.265` — `ItemMachines#onUpdate`, the four potions and the `mItemCount` / `mFluid > 64000`
  gates.
- ServerUtilities `2.4.9` — `ServerUtilitiesTeamData#getMaxClaimChunks` / `#getMaxChunkloaderChunks`; enforced at
  `ClaimedChunks#claimChunk` and `#loadChunk`.
- Twilight Forest — the nine `TFBlocks` fields used by `FinalCastleBreakFeature`, verified with `javap`. The source
  comment records 2.7.45; the dev runtime pins 2.7.41 and the matcher also checks the live 2.7.40 bytes.
- IC2 `ItemArmorQuantumSuit`, GraviSuite `ItemAdvancedJetPack` — closed source; the patch checks the method shape and
  refuses an unknown one.
- Forestry (GTNH fork), Gendustry, MagicBees — class names read from source.

## Licence

Twilight Forest code is LGPL-2.1-or-later, art CC BY-NC-SA 4.0, sounds all rights reserved. Patchee ships none of
it. The full position is [docs/ops/GTNH-INTERNALS.md](docs/ops/GTNH-INTERNALS.md).
