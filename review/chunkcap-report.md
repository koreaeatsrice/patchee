# TeamChunkCap — handover report

Branch: `feat/team-chunk-cap` (worktree `/root/projects/patchee/.worktrees/chunkcap`)
Lane: **finished — built, unit-tested, real-jar matcher re-verified, A/B dev boots on file.**
Nothing was pushed, deployed, restarted, or written outside the worktree's `run/` dir.

The feature (already written by the previous agent) caps a **ServerUtilities team's**
chunk limits at one player's numbers — 1500 claimed / 500 loaded chunks **in total**,
however many members the team has. A solo player, whose own sum already equals those
numbers, is left untouched. Server-side only; clients need nothing installed.

---

## 1. What changed (files)

Modified (tracked):

- `src/main/java/com/jointspaceforce/patchee/Config.java`
- `src/main/java/com/jointspaceforce/patchee/core/Toggles.java`
- `src/main/java/com/jointspaceforce/patchee/features/PatcheeFeatures.java`
- `src/main/java/com/jointspaceforce/patchee/Patchee.java`
- `src/main/java/com/jointspaceforce/patchee/CommonProxy.java` (SelfTest moved to the first server tick)
- `src/main/java/com/jointspaceforce/patchee/SelfTest.java` (new TeamChunkCap checks)
- `src/main/java/com/jointspaceforce/patchee/asm/FMLPlugin.java` (registers the 2nd transformer)
- `src/main/resources/mcmod.info`
- `README.md`, `DEVELOPER.md`, `docs/SETTINGS.md`, `docs/INSTALL-SERVER.md`, `docs/TROUBLESHOOTING.md`
- `dependencies.gradle` (dev-runtime-only ServerUtilities dep)
- `.github/workflows/ci.yml` (smoke grep for `[TeamChunkCap] armed`)

New (untracked):

- `src/main/java/com/jointspaceforce/patchee/asm/TeamChunkCapTransformer.java`
- `src/main/java/com/jointspaceforce/patchee/features/TeamChunkCap.java`
- `src/main/java/com/jointspaceforce/patchee/features/TeamChunkCapFeature.java`
- `src/main/java/com/jointspaceforce/patchee/features/TeamChunkCapHooks.java`
- `src/main/java/com/jointspaceforce/patchee/features/TeamChunkCapPatch.java`
- `src/main/java/com/jointspaceforce/patchee/features/TeamChunkCapSettings.java`
- `src/test/java/com/jointspaceforce/patchee/features/TeamChunkCapTest.java`
- `src/test/java/com/jointspaceforce/patchee/features/TeamChunkCapPatchTest.java`
- `tools/matcher-check/` (harness + README)

## 2. New config keys + ARMED line

Keys in `config/patchee.cfg` (generated from the frozen registry):

```
B:enableTeamChunkCap=true
I:teamChunkCap.maxClaimChunks=1500
I:teamChunkCap.maxLoadedChunks=500
```

(`0` in either number = Patchee does not cap that one; negative falls back to the default.)

Patched line (boot A, feature on):

```
[TeamChunkCap] patched serverutils.data.ServerUtilitiesTeamData — team limits clamped to 1500 claimed / 500 loaded chunks (4 return sites; seed values from …/run/server/config/patchee.cfg)
[TeamChunkCap] armed — every team is capped at 1500 claimed / 500 loaded chunks in total (0 = that one is not capped; single-player teams already have exactly these numbers, so nothing changes for them; values from config/patchee.cfg)
```

Feature off → `[TeamChunkCap] disabled in config — ServerUtilities' team limits left exactly as they ship`.
ServerUtilities absent → `[TeamChunkCap] ServerUtilities is not installed — team chunk limits skipped`.
Wrong build shape → `[TeamChunkCap] … does not have the shape this patch knows — ServerUtilities left exactly as it ships`.

## 3. Tests

`./gradlew test --no-daemon --rerun-tasks` → **BUILD SUCCESSFUL**, **42 tests, 0 failures, 0 skipped**:

| suite | tests |
|---|---|
| AuroraBreakFloorTest | 4 |
| LightOverlayPatchTest | 6 |
| LightOverlaySettingsTest | 7 |
| LightOverlayThresholdTest | 5 |
| TeamChunkCapPatchTest | 8 |
| TeamChunkCapTest | 12 |

Two new tests were added since the first pass:
`TeamChunkCapTest.theArmedLineIsGatedOnARealPatch` and
`TeamChunkCapPatchTest.theTransformerPublishesPatchSuccessOnlyOnARealPatch`.

No test-compile error remained — the tree compiles and every test passes with no assertions weakened.

## 4. Matcher counts (REAL ServerUtilities-2.4.9.jar bytes)

Previous agent's recorded claim (`tools/matcher-check/README.md`): **exactly 4 hook call
sites — 2 in `getMaxClaimChunks()` and 2 in `getMaxChunkloaderChunks()`**, run over the
real jar bytes via `TeamChunkCapPatch.SITE_COUNT = SITES_PER_METHOD(2) * 2 = 4`.

Re-ran here, this time against the real jar
`/root/.hermes/cache/scratch/su/ServerUtilities-2.4.9.jar` (class
`serverutils/data/ServerUtilitiesTeamData.class`, 18134 bytes):

```
PASS — real ServerUtilities bytes patched (hook call sites=4 (expected 4))
PASS — class shape (fields) unchanged
PASS — every other method byte-for-byte identical
PASS — -1 and -2 sentinel returns untouched (iconst_m1+ireturn=2 (was 2), bipush+ireturn=2 (was 2))
PASS — target methods differ only by the injected calls (four ALOAD_0/SWAP/INVOKESTATIC triples)
PASS — second pass is idempotent (skip, unchanged) (unchanged=true sites=4)
PASS — non-target class passes through untouched (unchanged=true (serverutils/data/ClaimedChunks.class))
PASS — wrong shape -> reported, bytes returned untouched
RESULT: 8/8 PASS
```

The fail-loud line was emitted for the wrong-shape class with an instruction dump
(`getMaxClaimChunks() has 1 of the 2 expected return site(s) …`) and the bytes were
returned untouched. Counts restated: **4 sites**, idempotent skip on the 2nd pass,
non-target `ClaimedChunks` byte-identical, wrong shape reported and untouched.

## 5. A/B dev-server boot results (quoted)

Evidence on disk: `/root/.hermes/cache/scratch/su/boot-A.log` (feature ON),
`/root/.hermes/cache/scratch/su/boot-B.log` (feature OFF).

**Boot A — feature ON** (`boot-A.log`):

```
[TeamChunkCap] patched serverutils.data.ServerUtilitiesTeamData — team limits clamped to 1500 claimed / 500 loaded chunks (4 return sites; …)
[TeamChunkCap] armed — every team is capped at 1500 claimed / 500 loaded chunks in total (…)
[SelfTest] teamchunkcap observed: 1-member team raw 100 claims, 16-member team raw 1600 claims / 800 loaded, caps 1500 / 500
[SelfTest] PASS  teamchunkcap solo team not clamped: raw 100 claims -> 100 claims
[SelfTest] PASS  teamchunkcap solo team not clamped: raw 50 loaded -> 50
[SelfTest] PASS  teamchunkcap 16-member team capped: raw 1600 claims -> 1500 claims (cap 1500)
[SelfTest] PASS  teamchunkcap 16-member team capped: raw 800 loaded -> 500 loaded (cap 500)
[SelfTest] PASS  teamchunkcap claim sweep: 1500 claim(s) accepted, then NO_POWER (cap 1500)
[SelfTest] result: 19 passed, 0 failed
```

**Boot B — feature OFF** (`boot-B.log`):

```
[TeamChunkCap] disabled in config — ServerUtilities' team limits left exactly as they ship
[TeamChunkCap] switched off in …/run/server/config/patchee.cfg — ServerUtilities' team limits left exactly as they ship
[SelfTest] teamchunkcap — feature disabled in config, so nothing clamps; ServerUtilities' own totals stand (see the line below)
[SelfTest] SKIP teamchunkcap checks — feature off: the 16-member team is served ServerUtilities' own 1600 claims / 800 loaded (cap 1500 / 500 not applied)
[SelfTest] result: 14 passed, 0 failed
```

Solo team untouched (100→100 claims, 50→50 loaded); 16-member team clamped
(1600→1500 claims, 800→500 loaded); claim sweep accepts 1500 then NO_POWER.
Feature off: skip line present, nothing clamps, ServerUtilities defaults stand.

## 6. Release jar

- Path: `build/libs/patchee-v1.3.1-feat-team-chunk-cap.3+be1f1a907a-dirty.jar`
- sha256: `d83cb9f43fd110d59f633ca94a2e1d01bc566fea60d4fc4d2f2a61641d6fbd9c`
- Includes the armed-line gating fix (the ARMED log line is now gated on a real patch success).
- `mcmod.info` version: `v1.3.1-feat-team-chunk-cap.3+be1f1a907a-dirty`
- Manifest: `FMLCorePlugin: com.jointspaceforce.patchee.asm.FMLPlugin`, `FMLCorePluginContainsFMLMod: true`
- `Tags.class` present. All `TeamChunkCap*` classes present.
- **ServerUtilities NOT bundled** — `unzip -l` shows no `serverutils` / `ServerUtilities` entry.
  No Twilight Forest / NEI / CodeChickenLib / VeinMiner entry either. The ServerUtilities
  dep is `runtimeOnlyNonPublishable` (dev-run only).

Note: this is a **worktree build**, so the git-derived version carries `-dirty` and the
branch suffix. The tag-driven release workflow builds the clean `vX.Y.Z` asset; players
must get that jar, not this one.

## 7. Staging instructions (owner runs these — do NOT deploy)

The mod is **dormant until the owner's next natural server restart**. No restart needed to
place the file.

1. Take the **release** jar built by the tag workflow (not the `-dirty` worktree jar).
2. This is **not** a same-name replace: the new jar's filename differs from the deployed
   `patchee-v1.4.0.jar`, so an operator cannot overwrite it in place. Check what is there,
   then move the old jar OUT of `mods/` into a backup directory (two `patchee` jars in
   `mods/` abort the boot with `DuplicateModsFoundException`):

   ```
   ls -1 mods/ | grep -i patchee          # expect exactly 1
   mkdir -p /home/amp/patchee-backup
   mv mods/patchee-v1.4.0.jar /home/amp/patchee-backup/   # backup OUTSIDE mods/
   ```

3. Copy the release jar into `mods/` and confirm exactly one Patchee jar remains. It stays
   **dormant until the owner's next restart**:

   ```
   cp <release-jar> mods/
   ls -1 mods/ | grep -ci patchee         # MUST be 1
   ```

4. On the next boot, confirm in `logs/fml-server-latest.log`:
   `[TeamChunkCap] armed — every team is capped at …` (or the `not installed — skipped` /
   `disabled in config` line, which are fine). A `[TeamChunkCap] NOT armed — …shape…`
   ERROR instead means the installed ServerUtilities build is not the one this patch knows
   and nothing is capped — do not treat that boot as armed.
5. Optional proof: set `runSelfTest=true`, restart once, look for
   `[SelfTest] result: 19 passed, 0 failed`, then set it back to `false`.
6. **Rollback:** move the old jar back into `mods/` and restart, or set
   `enableTeamChunkCap=false` and restart.

Default config after first boot: `enableTeamChunkCap=true`,
`teamChunkCap.maxClaimChunks=1500`, `teamChunkCap.maxLoadedChunks=500`.
