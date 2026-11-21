# DEVELOPER

For the person building, changing or releasing Patchee. This page is an index: the detail lives in the docs below,
and this page keeps only the facts that are unique to local development and CI.

## Start here

| If you want to… | Read |
|---|---|
| Understand the design before changing it | [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) |
| Add a feature, run the tests, open a PR | [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md) |
| Cut a release or roll one back | [docs/RELEASE.md](docs/RELEASE.md) |
| Deal with 1.7.10 names, load order, ASM or a mod jar | [docs/GTNH-INTERNALS.md](docs/GTNH-INTERNALS.md) |
| Know the house rules before touching anything | [AGENTS.md](AGENTS.md) |
| Change what a player sees or a config key does | [docs/FEATURES.md](docs/FEATURES.md), [docs/SETTINGS.md](docs/SETTINGS.md) |

## Build

Requires JDK 25 and network access to the GTNH maven (the first build downloads the toolchain):

```sh
JAVA_HOME=/opt/jdk-25 ./gradlew spotlessApply build
# -> build/libs/patchee-<version>.jar   (the PLAIN jar; -dev is for dev runs)
```

The dev runtime pins Twilight Forest 2.7.41: the newest version published to a resolvable maven (the pack's 2.7.44
is not). The pin only affects a local `runServer`; it never reaches a build. Gendustry and MagicBees are not on the
dev runtime because their jitpack artifacts drag in an unresolvable CodeChickenLib pin; the self-test skips them
locally and checks them on a real pack.

## CI smoke contract

`ci.yml` boots `runServer` with `runSelfTest=true` and fails unless the log contains
`[SelfTest] result: N passed, 0 failed`. It also greps these lines:

- `[DollyFix] Dolly now accepts`
- `[MattockFix] mattock shovel materials`
- `[VeinConfig] wrote config/veinminer/`
- `[VeinConfig] verified:`
- `[AuroraBreak] armed`
- `[ArcticFurBreak] armed`
- `[TeamChunkCap] armed`
- `[FinalCastleBreak] armed`
- `[GlassBreak] Blood Arsenal is not installed - skipped`

Blood Arsenal is not on the dev runtime, so CI sees the GlassBreak skip line; the `[GlassBreak] armed ...` line
only appears on a server that has it. The ops docs quote other lines too, so treat every existing log line as a
contract.

`PollutionVisuals` is not in the CI grep list. CI runs the dev pack as a dedicated server, and the feature checks
the side before it looks for GregTech, so CI only ever sees
`[PollutionVisuals] dedicated server - no client renderer, skipped`. Its client decision logic is proven by
`PollutionVisualsTest`. Setting `PATCHEE_GREGTECH_JAR` also makes `PollutionVisualsRealJarTest` check the real
jar's field names and descriptors; unset, that test is skipped.

## Verified against

These are the builds the reflection targets and bytecode shapes were checked against. Re-verify with `javap` and
`tools/matcher-check/` before changing a target.

- JABBA (GTNH fork), `ItemBarrelMover` — static `classExtensions` list built at class-init, gate at
  `for (Class c : classExtensions) if (c.isInstance(te)) return true;`.
- Tinkers' Construct (GTNH fork) `1.14.108-GTNH` — `static Material[] shovelMaterials` (package-private, not
  final) read per call by `getEffectiveSecondaryMaterials()`.
- GT5-Unofficial `5.09.54.265` — `gregtech.common.blocks.ItemMachines#onUpdate` (the four potions, `mItemCount` /
  `mFluid > 64000` gates).
- ServerUtilities `2.4.9` — `serverutils.data.ServerUtilitiesTeamData` (`getMaxClaimChunks` /
  `getMaxChunkloaderChunks` sum `serverutilities.claims.max_chunks` / `serverutilities.chunkloader.max_chunks`
  over `team.getMembers()`, cache into `cachedMaxClaimChunks` / `cachedMaxChunkloaderChunks`, and answer `-1`/`-2`
  for the inactive and invalid-team cases). Enforced at `serverutils.data.ClaimedChunks#claimChunk` and
  `#loadChunk`. Bytecode proof: `tools/matcher-check`.
- Twilight Forest — the nine `TFBlocks` fields used by `FinalCastleBreakFeature`, verified with `javap`. The source
  comment records 2.7.45; the dev runtime pins 2.7.41 and `tools/matcher-check` also checks the live 2.7.40 bytes.
  The live jar carries SRG names. Proof: `tools/matcher-check/CastleCheck.java`.
- IC2 `ItemArmorQuantumSuit` and GraviSuite `ItemAdvancedJetPack` — closed source; the patch checks the method
  shape and refuses an unknown one. Proof: `tools/matcher-check/ChestplateCheck.java`.
- Forestry (GTNH fork), Gendustry, MagicBees — class names read from source.

## Licence position

Twilight Forest code is LGPL-2.1-or-later, its art is CC BY-NC-SA 4.0 and its sounds are all rights reserved.
Patchee ships none of its code, art or sound and re-implements behaviour only. The same rule applies to every
other patched mod. Full text: [docs/GTNH-INTERNALS.md](docs/GTNH-INTERNALS.md), section 7.
