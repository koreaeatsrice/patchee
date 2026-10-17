# FinalCastleBreak — completion & verification report

Branch `feat/final-castle-break`, worktree
`/root/projects/patchee/.worktrees/castlebreak`. No git writes were made
(add/commit/push/checkout); the parent publishes the branch.

## 1. What was on disk when this pass started (verified, not assumed)

New, untracked:

- `src/main/java/com/jointspaceforce/patchee/features/FinalCastleBreak.java`
- `.../features/FinalCastleBreakFeature.java`
- `.../features/FinalCastleBreakHooks.java`
- `.../features/FinalCastleBreakPatch.java`
- `.../features/FinalCastleBreakSettings.java`
- `src/main/java/com/jointspaceforce/patchee/asm/FinalCastleBreakTransformer.java`
- `src/test/java/com/jointspaceforce/patchee/features/FinalCastleBreakPatchTest.java`
- `src/test/java/com/jointspaceforce/patchee/features/FinalCastleBreakSettingsTest.java`
- `src/test/resources/finalcastlebreak/{live,dev}/*.class` (+ `README.md` with provenance hashes)
- `tools/matcher-check/CastleCheck.java`

Modified (already in the working tree): `asm/FMLPlugin.java`,
`features/PatcheeFeatures.java`.

Baseline build **passed** before any edit: `spotlessApply` + `clean build`
(and the bundled tests) were green — the previous agent's failure was in the
un-run `tools/matcher-check` harness, not in the Gradle build.

## 2. What was wrong / missing, and what was fixed

### 2a. Missing half of the agreed design: the 0.1 break-speed floor

The feature **did not register any `PlayerEvent.BreakSpeed` handler at all**, and
`BLOCK_FIELDS` listed only two blocks (`castleBlock`, `thorns`). The agreed
design (a) requires the floor on `castleBlock`, `castleMagic` **and** `thorns`,
on both sides, reusing `AuroraBreakFeature.raiseBreakFloor` / `floorSpeed` and
the `FLOOR = 0.1F` constant.

Fixed in `FinalCastleBreakFeature.java` (mirrors `AuroraBreakFeature`):

- `BLOCK_FIELDS = { "castleBlock", "castleMagic", "thorns" }`, count logged as
  `{}/3`; the three resolved blocks are collected into a `List<Block>`.
- New nested `Handler` (`@SubscribeEvent onBreakSpeed`) registered on
  `MinecraftForge.EVENT_BUS` in the arm step; it applies
  `AuroraBreakFeature.raiseBreakFloor(event, FLOOR)` where
  `FLOOR = AuroraBreakFeature.FLOOR` (0.1F), only for a player for whom
  `FinalCastleBreak.earnedGlacier(player)` is true. Same guards as Aurora
  (`player == null`, `y < 0`, `castle.contains(block)`), fail-soft with a
  single warning.
- The handler is registered on **both** sides (the 1.7.10 client is the clock),
  exactly like `AuroraBreakFeature`; the server-side `patchApplied()` gate and
  the literal `[FinalCastleBreak] armed` line stay server-only. The client gets
  a distinct `[FinalCastleBreak] client half ready …` line so the CI grep can
  only be satisfied by a real server arm.

`castleMagic` was confirmed to exist as `public static Block castleMagic` in
`TFBlocks` (javap of the 2.7.41 dev jar, and — added in review round 1, F8 —
javap of the live 2.7.40 `TFBlocks.class` pulled read-only from the server box):

```
public static net.minecraft.block.Block thorns;
public static net.minecraft.block.Block castleBlock;
public static net.minecraft.block.Block castleMagic;
```

### 2b. Missing SelfTest check (step 4a)

Added `checkFinalCastleBreak(toggles)` to `src/main/java/.../SelfTest.java`,
following the `checkAuroraBreak` / `checkTeamChunkCap` style. It:

- SKIPs cleanly when the feature is off in config, and when Twilight Forest is
  not installed;
- asserts the three `TFBlocks` blocks resolve, the
  `TFAchievementPage.twilightProgressGlacier` field is an `Achievement`, and
  `FinalCastleBreak.patchApplied()` is true (the real class-load patch, not a
  bare class-presence probe);
- reports `PASS finalcastlebreak: 3/3 castle blocks, Glacier achievement found,
  class-load patch applied`.

Called from `runChecks(...)` after `checkTeamChunkCap`.

### 2c. Missing CI smoke grep (step 4b)

Added to `.github/workflows/ci.yml` next to the existing greps:

```
grep -q "\[FinalCastleBreak\] armed" server.log || { echo "::error::FinalCastleBreak did not arm"; fail=1; }
```

### 2d. Missing docs (step 4c)

- `README.md`: new "Twilight Forest Final Castle" bullet under *What it changes*.
- `docs/SETTINGS.md`: new `enableFinalCastleBreak` row (`true` default), placed
  with the other feature switches; the text matches the code (three blocks, 0.1
  floor / 10 ticks, 16x pickaxe drain removed server-side, thorn wall removable
  server-side).

### 2e. Test harness bug (step 3) — the bug the previous agent died on

`tools/matcher-check/CastleCheck.java` looked for a flat
`<dir>/BlockTFCastleBlock.class` but nothing narrowed the path; the earlier run
looked for `…/live/twilightforest/block/BlockTFCastleBlock.class`. Rewrote
`readTree(...)` to accept **either a jar or a directory**, in **either** the flat
layout (`live/BlockX.class`) or the package-nested layout. It now reads the live
bytes from the flat resources dir and the dev bytes **straight out of the Gradle
cache jar**. Also made the wrong-shape case print the reported ERROR detail and
the instruction dump to the console (previously only asserted internally).

`tools/matcher-check/README.md` gained a "FinalCastleBreak variant" section
(what it checks + the exact run command).

## 3. Raw harness output (`CastleCheck`, step 3)

Command (live bytes from the pulled resources, dev bytes from the 2.7.41 dev
dependency in the Gradle cache):

```
java -cp "$OUT:$ASM:$ASMT:$LW:$LOG4J:$MC" CastleCheck \
  /root/projects/patchee/.worktrees/castlebreak/src/test/resources/finalcastlebreak/live \
  /root/.gradle/caches/modules-2/files-2.1/com.github.GTNewHorizons/TwilightForest/2.7.41/36d15b3d4f70cfbab5dfa6c614f68f248472a82f/TwilightForest-2.7.41-dev.jar \
  "$OUT"
```

```
live class dir : /root/projects/patchee/.worktrees/castlebreak/src/test/resources/finalcastlebreak/live
dev class dir  : /root/.gradle/.../TwilightForest-2.7.41-dev.jar
PASS — live BlockTFCastleBlock patched (hook sites=1 (expected 1), castle sites left=0, instruction count 46->46)
PASS — live BlockTFCastleBlock patched — second pass idempotent (unchanged=true)
PASS — live BlockTFThorns patched (hook sites=2 (expected 2), remaining setBlock=1 (the untouched growThorns one) burst=0, burst method 42->44 instructions)
PASS — live BlockTFThorns patched — second pass idempotent (unchanged=true)
PASS — dev BlockTFCastleBlock patched (hook sites=1 (expected 1), castle sites left=0, instruction count 46->46)
PASS — dev BlockTFCastleBlock patched — second pass idempotent (unchanged=true)
PASS — dev BlockTFThorns patched (hook sites=2 (expected 2), remaining setBlock=1 (the untouched growThorns one) burst=0, burst method 42->44 instructions)
PASS — dev BlockTFThorns patched — second pass idempotent (unchanged=true)
PASS — non-target class passes through untouched (unchanged=true (twilightforest.block.BlockTFCastleMagic))
ERROR patchee [FinalCastleBreak] twilightforest.block.BlockTFThorns does not have the shape this patch knows — Twilight Forest left exactly as it ships (expected either 1 Item.onBlockDestroyed site (twilightforest.block.BlockTFCastleBlock) or 1 burst method carrying 1 World.setBlock + 1 doThornBurst (twilightforest.block.BlockTFThorns); found 0 castle site(s) and 0 burst method(s)). Expected 1 site(s) in twilightforest.block.BlockTFCastleBlock, or 2 site(s) in twilightforest.block.BlockTFThorns.
ERROR patchee [FinalCastleBreak] observed shape: removedByPlayer(Lnet/minecraft/world/World;)Z:
PASS — wrong shape -> reported, bytes returned untouched (...)
  reported    : expected either 1 Item.onBlockDestroyed site (...) or 1 burst method carrying 1 World.setBlock + 1 doThornBurst (...); found 0 castle site(s) and 0 burst method(s)
  instruction dump: removedByPlayer(Lnet/minecraft/world/World;)Z:
RESULT: 10/10 PASS
```

(The fail-loud line now carries the **per-class** expectation — `Expected 1 site(s)
in twilightforest.block.BlockTFCastleBlock, or 2 site(s) in
twilightforest.block.BlockTFThorns` — not the old single-class `3 site(s)` count.)

Covered: (a) live-jar bytes of both targets, (b) dev bytes chosen by shape
(1 castle site; 2 thorn sites), (c) idempotence (second pass unchanged),
(d) non-target `BlockTFCastleMagic` untouched, (e) wrong shape → ERROR + dump,
bytes returned untouched.

## 4. Full verify (step 5)

Build + tests:

```
./gradlew spotlessApply --no-daemon -q
./gradlew clean build --no-daemon     -> BUILD SUCCESSFUL (checkstyle + tests run as part of build)
./gradlew test --rerun-tasks --no-daemon
```

Per-class results (from `build/test-results/test/*.xml`):

```
AuroraBreakFloorTest        tests=4  failures=0 errors=0 skipped=0
FinalCastleBreakPatchTest   tests=21 failures=0 errors=0 skipped=0
FinalCastleBreakSettingsTest tests=10 failures=0 errors=0 skipped=0
LightOverlayPatchTest       tests=6  failures=0 errors=0 skipped=0
LightOverlaySettingsTest    tests=7  failures=0 errors=0 skipped=0
LightOverlayThresholdTest   tests=5  failures=0 errors=0 skipped=0
TeamChunkCapPatchTest       tests=8  failures=0 errors=0 skipped=0
TeamChunkCapTest            tests=12 failures=0 errors=0 skipped=0
TOTAL: tests=73 failures=0 errors=0 skipped=0
```

FinalCastleBreakPatchTest test names (all PASSED, 21): patchesTheLiveCastleBytesWithExactlyOneSite,
patchesTheDevCastleBytesWithExactlyOneSite, patchesTheLiveThornsBytesWithExactlyTwoSites,
patchesTheDevThornsBytesWithExactlyTwoSites, everyOtherMethodOfBothClassesIsUnchanged,
theCastleMethodChangesOnlyByTheRepointeredCall, theThornsMethodGrowsByExactlyTheTwoALoads,
theGrowThornsSetBlockIsLeftAlone, aSecondPassChangesNothing,
theNonTargetClassPassesThroughByteIdentical, aClassWithoutTheTargetsIsRefusedUntouched,
aNullClassIsNotAPatch, theTransformerPublishesPatchSuccessPerTargetClass,
theTransformerLeavesTheHalfShutOnARefusedShape, theArmedLineGoesOutOnlyWhenBothHalvesArePatched,
theLiveTfBlocksHasTheThreePublicStaticBlockFields, anUnknownClassIsNotTouchedByTheTransformer,
theSeedAloneNeverArmsTheHooks, theHooksActOnlyAfterThePipelineConfirmsTheSwitches,
theGateFailsSafeWhenTheAchievementWasNotPublished, thePureGateDecisionNeedsBothTheSwitchAndTheEarn.

FinalCastleBreakSettingsTest names (all PASSED, 10): readsTheFileForgeWrites, readsTheSwitchBeingOff,
readsTheMasterSwitchBeingOff, fallsBackToTheDefaultsWhenKeysAreMissing,
ignoresCommentsBlanksAndUnknownKeys, garbageValuesFallBackToTheDefault,
acceptsKeysWithoutForgeTypePrefixes, nullTextIsTheDefaults,
readOfAMissingFileIsTheDefaultsAndNotFromFile, readOfARealFileIsFromFile(@TempDir).

Artifacts (this pass — the release jar sha changed with the edits):

```
jar (release): build/libs/patchee-v1.5.1-feat-final-castle-break+58d1a81006-dirty.jar
  sha256 (first 16): 51b1a64ba9fbd4e1
jar (dev):     build/libs/patchee-v1.5.1-feat-final-castle-break+58d1a81006-dirty-dev.jar
  sha256 (first 16): 4c70c6f6b64c3951
```

Jar contents:

```
unzip -l <jar> | grep -E "FinalCastleBreakTransformer|FinalCastleBreakHooks|FinalCastleBreakPatch"
  com/jointspaceforce/patchee/asm/FinalCastleBreakTransformer.class
  com/jointspaceforce/patchee/features/FinalCastleBreakHooks.class
  com/jointspaceforce/patchee/features/FinalCastleBreakPatch.class   (+ $Result/$State/$InsnMatch)
for f in asm/FinalCastleBreakTransformer features/FinalCastleBreakHooks features/FinalCastleBreakPatch; do
  unzip -p <jar> "com/jointspaceforce/patchee/$f.class" | strings | grep -c "FinalCastleBreakHooks|setThornBlock|thornBurst|onBlockDestroyed"
done
  asm/FinalCastleBreakTransformer.class  -> 1
  features/FinalCastleBreakHooks.class   -> 6
  features/FinalCastleBreakPatch.class   -> 5
unzip -l <jar> | grep -ci twilight       -> 0   (NO Twilight Forest classes shipped)
```

`javap -p -c` before/after (live SRG bytes; only the injected instructions move):

Castle `harvestBlock` (`func_149636_a`) — length 46 → 46; the single change:

```
-       73: invokevirtual #121  // Method net/minecraft/item/Item.func_150894_a:(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/block/Block;IIILnet/minecraft/entity/EntityLivingBase;)Z
+       73: invokestatic  #123  // Method com/jointspaceforce/patchee/features/FinalCastleBreakHooks.onBlockDestroyed:(Lnet/minecraft/item/Item;Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/block/Block;IIILnet/minecraft/entity/EntityLivingBase;)Z
```

Thorns `removedByPlayer` — length 42 → 44; the only changes are the two hooks,
each preceded by one appended `aload_2` (the `player` parameter; the original
`pop`s and control flow stay exactly where they were, just shifted by +2):

```
-       43: invokevirtual #165  // Method net/minecraft/world/World.func_147465_d:(IIILnet/minecraft/block/Block;II)Z
+       43: aload_2
+       44: invokestatic  #167  // Method ...FinalCastleBreakHooks.setThornBlock:(Lnet/minecraft/world/World;IIILnet/minecraft/block/Block;IILnet/minecraft/entity/player/EntityPlayer;)Z
-       56: invokespecial #169  // Method doThornBurst:(Lnet/minecraft/world/World;IIII)V
+       57: aload_2
+       58: invokestatic  #171  // Method ...FinalCastleBreakHooks.thornBurst:(Lnet/minecraft/block/Block;Lnet/minecraft/world/World;IIIILnet/minecraft/entity/player/EntityPlayer;)V
```

Everything else in both methods (and, per the unit tests, every other method of
both classes) is identical at the ASM opcode level. On the **dev** bytes ASM
widens four `ldc` constants to `ldc_w` and shifts an `if_icmpge` target inside
`BlockTFThorns.registerBlockIcons` once the patch adds constants (`ldc_w` count
0 → 4, `if_icmpge 123 → 127`); that is behaviour-identical, but **not**
byte-identical. The **live** 2.7.40 bytes show no such extra change — only the
expected re-pointed calls, the two appended `aload_2`s, and the branch offsets
those insertions shift.

## 5. Design conformance (step 2)

- Registered in `PatcheeFeatures` (last, after `TeamChunkCapFeature`). ✔
- Config key `enableFinalCastleBreak`, default `true`, generated from the
  registry by `Config` via `feature.configKey()` / `feature.defaultEnabled()`. ✔
- Achievement + `TFBlocks.castleBlock/castleMagic/thorns` resolved by reflection;
  a missing class/field is a loud `[FinalCastleBreak] … not arming` ERROR. ✔
- BreakSpeed handler on `MinecraftForge.EVENT_BUS`, both sides, only when the
  player earned the achievement via `FinalCastleBreak.earnedGlacier` (the
  two-sided idiom: `EntityPlayerMP.func_147099_x()` vs
  `EntityClientPlayerMP.getStatFileWriter()`, with the non-MP-logical-server
  guard before the client branch). ✔
- Patch/hook half gated on the raw config at class load
  (`FinalCastleBreakSettings.read()` → master + feature switch) **and** on the
  published runtime values (`seed`/`configure`). ✔
- Transformer registered in `FMLPlugin.getASMTransformerClass()`; the javadoc
  sentence updated ("Two tweaks" → "Three tweaks", FinalCastleBreak named). ✔
- Fail-soft everywhere: transformer catches `Throwable` and returns the original
  bytes; hooks catch `Throwable` and fall back to stock. ✔
- Boot line contains the literal `[FinalCastleBreak] armed`; transformer logs
  `[FinalCastleBreak] patched <class> — … (N site(s))`. ✔
- No new blocks/items; Twilight Forest is reflection-only (never a compile
  dependency) — the shipped jar contains zero `twilightforest` classes. ✔
- Existing reviewed features (`AuroraBreakFeature`, `ArcticFurBreakFeature`,
  `TeamChunkCap*`, `LightOverlay*`) were not edited. ✔

## 6. Uncertainties / not proven

- **In-game behaviour was not exercised.** All proof is bytecode-level
  (matcher + `javap` + unit tests). No live server boot, no player mined a
  castle brick, no thorn was broken, no break-speed floor was observed on a real
  client. The dev-server CI boot (`runServer` + the new grep + SelfTest) is the
  intended runtime proof and has not been run here.
- **`castleMagic` semantics.** `castleMagic` is confirmed a `Block` field and is
  now covered by the 0.1 floor, but the block class (`BlockTFCastleMagic`) has no
  harvest-side patch (by design — the design only patches `BlockTFCastleBlock`
  and `BlockTFThorns`); its only change is the break-speed floor. Not separately
  behaviour-tested.
- **Client path not tested.** The `client half ready` branch and the
  `EntityClientPlayerMP` branch of `earnedGlacier` are compiled but never
  executed in this environment (no client).
- **`doThornBurst` reflection.** `FinalCastleBreakHooks.thornBurst` reaches the
  private Twilight Forest burst by reflection; that path was not executed
  against a live `BlockTFThorns` instance here (it is exercised only by the pure
  `bypass` decision in unit tests, not the reflective invoke).
- **Transformer live-shape match.** Proven against the pulled 2.7.40 live bytes
  and the 2.7.41 dev bytes; the actual live server may run a different
  TwilightForest build, which is exactly what the fail-loud ERROR + the ARMED
  gate are for. Not verified against the live server's TF jar beyond the stored
  bytes.
- The jar name is a `-dirty` local build (`+58d1a81006-dirty`); the parent's
  publish step will produce the real release artifact.

## 7. Review round 1 fixes (this pass)

Nine findings from two hostile reviews were applied. No other feature was touched
(`AuroraBreakFeature`, `ArcticFurBreakFeature`, `TeamChunkCap*`, `LightOverlay*`
are untouched). Line numbers are from the final tree.

### F1 (MAJOR) — per-target patch tracking

- `features/FinalCastleBreak.java:96-135` — the single `patchApplied` boolean is
  replaced by `patchedCastle` / `patchedThorns`; `markPatched(String className)`
  (:113) sets only the matching flag; `patchApplied()` (:135) is true only when
  **both** are. `resetForTest()` (:201) is a package-private test seam.
- `asm/FinalCastleBreakTransformer.java:94,106` — both the PATCHED and the
  ALREADY_PATCHED branch call `FinalCastleBreak.markPatched(className)` (the old
  `markPatchApplied()` is gone).
- `features/FinalCastleBreakFeature.java:145-166` — `armReport()` returns ARMED
  only when both halves are patched; the new `missingHalf()` (:156) names the
  unpatched class, and the NOT-armed ERROR (`:267`) prints it.
- `SelfTest.java:436-437` — the self-test now requires `patchedCastle()` **and**
  `patchedThorns()`, and its line reports each half separately.
- Tests: `theTransformerPublishesPatchSuccessPerTargetClass` (:243) — castle
  patched → NOT_ARMED, both patched → ARMED;
  `theTransformerLeavesTheHalfShutOnARefusedShape` (:267) — castle patched +
  thorns refused → NOT_ARMED and `missingHalf()` names the thorn class;
  `theArmedLineGoesOutOnlyWhenBothHalvesArePatched` (:291).

Evidence: those three tests pass (names in §4). No armed line can go out on a
half-patched boot because `arm()` only logs `[FinalCastleBreak] armed` when
`armReport() == ARMED`; the not-armed branch returns `Outcome.FAILED` and logs
the ERROR naming the missing half.

### F2 (MINOR) — a feature switched OFF could leave the class-load half ON

- `features/FinalCastleBreak.java:163-179` — `active()` is now
  `configured && master && enabled`. `configured` is set only by
  `configure(master, enabled)`, which only the feature's arm step calls; the
  class-load seed still decides whether the **patch** is installed but no longer
  arms the hooks. A boot where the pipeline never confirms (feature off in config
  → `PatchFactory.disabled()` runs, `configure()` never runs) leaves the hooks on
  stock Twilight Forest behaviour — fail safe.
- The holder's `active()` javadoc documents the behaviour.
- Tests: `theSeedAloneNeverArmsTheHooks` (:348) — `seed(true,true)` leaves
  `active()==false`; `theHooksActOnlyAfterThePipelineConfirmsTheSwitches` (:359)
  — false before `configure`, false for `(true,false)`/`(false,true)`, true only
  for `(true,true)`.

### F3 (MINOR) — two tests depended on the working directory

Chosen seam: an injected settings value plus a package-private `read(File)`
overload — **not** a `System.getProperty("user.dir")` swap — because it mutates
no global JVM state and is therefore safe under parallel test execution.

- `features/FinalCastleBreakSettings.java:34,60-82` — `current()` returns the
  injected value if any, else `read()`; `injectForTest` / `clearInjectedForTest`
  and `read(File)` are package-private; `read()` delegates to `read(File)`.
- `asm/FinalCastleBreakTransformer.java:70` — the transformer now asks
  `FinalCastleBreakSettings.current()`.
- `FinalCastleBreakPatchTest.java:324-332` — `withEnabledSettings(...)` pins the
  seam to on and clears it in a `finally`.
- `FinalCastleBreakSettingsTest.java:98-122` — the defaults test uses
  `read(<a path that cannot exist>)`; a new `readOfARealFileIsFromFile` writes and
  reads its own `@TempDir` file (also covers the `fromFile()==true` branch that
  the old, path-dependent test never reached).

Evidence: with a decoy `config/patchee.cfg` (`B:enabled=false`,
`B:enableFinalCastleBreak=false`) placed in the working directory,
`./gradlew test --rerun-tasks --tests 'com.jointspaceforce.patchee.features.FinalCastleBreak*'`
still passed all 31 FinalCastleBreak tests:

```
FinalCastleBreakPatchTest … (21 tests) PASSED
FinalCastleBreakSettingsTest > readOfARealFileIsFromFile(Path) PASSED
FinalCastleBreakSettingsTest > readOfAMissingFileIsTheDefaultsAndNotFromFile() PASSED
FinalCastleBreakSettingsTest … (10 tests) PASSED
BUILD SUCCESSFUL
```

The decoy was deleted afterwards. Both tests pass with or without a real config
file in the working directory.

### F4 (NIT) — the fail-loud ERROR printed an impossible count

`asm/FinalCastleBreakTransformer.java:113-120` — the single-class `SITE_COUNT`
(=3) is gone; the message now prints the per-class counts: `Expected 1 site(s) in
twilightforest.block.BlockTFCastleBlock, or 2 site(s) in
twilightforest.block.BlockTFThorns.` (observed in §3's harness output).

### F5 (NIT) — settings javadoc contradicted the code

`features/FinalCastleBreakSettings.java:15-18` — now says a missing file or key
uses the documented default, and an unreadable boolean reads as `false` (Forge's
own behaviour, and the safe direction).

### F6 (NIT) — a javadoc named a test class that does not exist

`features/FinalCastleBreakHooks.java:50-51` — now names
`FinalCastleBreakPatchTest.thePureGateDecisionNeedsBothTheSwitchAndTheEarn`.

### F7 (NIT) — 'server-side only' overstated the class-load patch

Reworded to say the behaviour change **takes effect on the server** (the
transformer has no side guard; only the ARMED line is side-gated), keeping the
break-speed half honest about needing Patchee on the client, in
`README.md:31`, `docs/SETTINGS.md:34`, and
`features/FinalCastleBreakFeature.java:56` (class javadoc) and `:96`
(`description()`).

### F8 (NIT + MISSING EVIDENCE) — 'castleMagic exists on the live server' was unproven

- Pulled the live build's `TFBlocks.class` read-only:
  `ssh -i ~/.ssh/mainframe_key -p 2222 root@46.4.120.77 'unzip -p
  /home/amp/.ampdata/instances/GTNH/Minecraft/mods/TwilightForest-2.7.40.jar
  twilightforest/block/TFBlocks.class'`
  → sha256 `2c1e57b206d7fb69515aedc3d2d781e7d073043448339ccf8561fa67a2aef52a`.
- `javap -p` of those live bytes proves all three fields exist as public static
  `Block` fields on the live build:

```
public static net.minecraft.block.Block thorns;
public static net.minecraft.block.Block castleBlock;
public static net.minecraft.block.Block castleMagic;
```

- Bytes stored at `src/test/resources/finalcastlebreak/live/TFBlocks.class`; sha
  recorded in `src/test/resources/finalcastlebreak/README.md:15-40`.
- New test
  `FinalCastleBreakPatchTest.theLiveTfBlocksHasTheThreePublicStaticBlockFields`
  (:301) parses the stored live bytes with ASM and asserts the three names are
  public-static `Block` fields.
- All three fields are present on the live build, so the docs claim stands.

### F9 (NIT) — 'byte-for-byte identical' was false on the dev bytes

Reworded to "instruction-sequence identical at the ASM opcode level; ASM may
widen a constant's encoding when constants are added" in
`tools/matcher-check/README.md` (castle-variant section) and in §4 of this report;
the live-bytes statement is kept accurate. Measured on the dev bytes: `ldc_w`
count 0 → 4 and `if_icmpge 123 → 127` inside `BlockTFThorns.registerBlockIcons`.

### Re-verification after the fixes (this pass)

- `./gradlew spotlessApply --no-daemon -q` → clean (exit 0).
- `./gradlew clean build --no-daemon` → **BUILD SUCCESSFUL**.
- `./gradlew test --rerun-tasks --no-daemon` → **BUILD SUCCESSFUL**, 73 tests,
  0 failures / 0 errors (per-class table and the two FinalCastleBreak name lists
  in §4).
- CastleCheck harness → **RESULT: 10/10 PASS** (raw output in §3), covering the
  live + dev bytes, idempotence, the non-target pass-through and the wrong-shape
  ERROR + dump.
- Release jar sha256 (first 16): `51b1a64ba9fbd4e1`; `unzip -l … | grep -ci
  twilight` → 0; 12 `FinalCastleBreak*` entries in the jar.
- `javap -p -c` before/after on the live bytes: the two target methods change only
  by the expected re-pointed calls (plus two `aload_2`s in the thorn method) — §4.

### Still not proven (unchanged)

- **No in-game run.** All proof is bytecode-level (matcher + `javap` + unit tests)
  and the pure-decision tests. The CI smoke boot (`runServer` + the
  `[FinalCastleBreak] armed` grep + `runSelfTest`) is the intended runtime proof
  and has not been run here; JVM verification of the patched bytes is expected
  from that smoke boot.
- The client branches, the reflective `doThornBurst` invoke, and the live server's
  own TF jar beyond the stored bytes remain untested here (see §6).

## 8. Review round 3: CI-only failure (repository file-type bug, not a feature bug)

### The failure

The first CI run of this branch failed on a test that passed locally:

```
FinalCastleBreakPatchTest > theLiveTfBlocksHasTheThreePublicStaticBlockFields() FAILED
    java.lang.ArrayIndexOutOfBoundsException: Index 1119 out of bounds for length 1119
        at org.objectweb.asm.ClassReader.readClass(ClassReader.java:...)
        ...
```

That test only does `read(resource(LIVE + "TFBlocks.class"))` and then checks the
three `TFBlocks` fields. Locally it passed; in CI the stored bytes came back
short and ASM fell over at `readClass`.

### Root cause: git normalized a binary `.class` file as text

The stored live bytes at `src/test/resources/finalcastlebreak/live/TFBlocks.class`
are real binary class bytes. On disk in this worktree:

```
19272 bytes, sha256 2c1e57b206d7fb69515aedc3d2d781e7d073043448339ccf8561fa67a2aef52a
CR=37  CRLF=1  LF=240
```

`git hash-object --path=…TFBlocks.class` (i.e. the blob the publish path stores,
with the repo's attribute filters applied) produced a **19271-byte** blob —
one byte gone. In a fresh checkout of the published branch the same file is
19271 bytes, sha256 `bc12f2734dcb…`, CR=36, CRLF=0. The single CRLF pair had been
collapsed to LF, corrupting the class; ASM saw a 1119-byte fragment (the
"length 1119" in the stack trace) instead of a 19272-byte class.

The cause is `.gitattributes` line 1:

```
* text eol=lf
```

Every file is text by default and `.class` was **not** declared binary, so git
(eol=lf) rewrote line endings inside the class file on publish. The other five
stored `.class` files happen to contain no CRLF pair, so they survived — the same
latent risk, unproven until a class with a CRLF shows up.

Reproduced cheaply in the worktree: `git check-attr -a -- …/TFBlocks.class` said
`text: set`, and the normalized blob was 19271 bytes vs 19272 on disk.

### What changed (all inside the worktree; no git writes, not published)

1. **`.gitattributes`** — added one line, in the file's own bracketed
   case-insensitive / column-aligned style, next to the other JVM-archive rule:

   ```
   *.[jJ][aA][rR]          binary
   *.[cC][lL][aA][sS][sS]  binary
   ```

   `binary` sets `text` unset, so git stops converting line endings inside
   `.class` files (and the publish tool, which compares git-normalized blobs, now
   stores the exact bytes).

2. **`src/test/java/.../features/FinalCastleBreakPatchTest.java`** — the test no
   longer trusts the bytes blindly. A single `FIXTURE_SHA256` map (one obvious
   place, with a comment) records the sha256 of all six stored `.class` fixtures;
   `resource(...)` now verifies the fetched bytes against it before any parse, and
   fails with a message naming the file, the expected and actual sha, the byte
   count, and the `.gitattributes`/text-normalization pitfall. No other assertion
   changed; the live/dev bytes are untouched.

3. **`src/test/resources/finalcastlebreak/README.md`** — new "File-type pitfall"
   section: these are binary class bytes; `.gitattributes` must keep `.class`
   binary; a text/eol=lf rule silently drops a byte from a class carrying a CRLF
   pair (this happened to TFBlocks.class, 1 byte lost, CI failed); the sha guard;
   and how the bytes were captured (the read-only `ssh … unzip -p` command, the
   jar names/versions, and the recorded sha256s, which must agree with the test
   map).

4. This section.

### Re-verification (raw output)

Whether the fix works is proven by git's own attribute resolution, not by luck:

```
$ git check-attr -a -- src/test/resources/finalcastlebreak/live/TFBlocks.class
src/test/resources/finalcastlebreak/live/TFBlocks.class: binary: set
src/test/resources/finalcastlebreak/live/TFBlocks.class: diff: unset
src/test/resources/finalcastlebreak/live/TFBlocks.class: merge: unset
src/test/resources/finalcastlebreak/live/TFBlocks.class: text: unset
src/test/resources/finalcastlebreak/live/TFBlocks.class: eol: lf
```

`text: unset` is the point: git will no longer normalize this file. The `.java`
file is unaffected and still text:

```
$ git check-attr -a -- src/test/resources/finalcastlebreak/live/TFBlocks.class \
                       src/main/java/com/jointspaceforce/patchee/features/FinalCastleBreak.java
…TFBlocks.class: text: unset   (as above)
…/FinalCastleBreak.java: text: set
…/FinalCastleBreak.java: eol: lf
```

Additional check that the rule is what changed the outcome: with the new rule in
place, `git hash-object --path=…/TFBlocks.class …/TFBlocks.class` now yields the
same bytes — `git cat-file -s` reports **19272** (was 19271 before the fix).

The sha guard itself was proven to fire: substituting the git-normalized
19271-byte bytes (CR=36, CRLF=0) into the built resource dir and running the
single test produced

```
FinalCastleBreakPatchTest > theLiveTfBlocksHasTheThreePublicStaticBlockFields() FAILED
    org.opentest4j.AssertionFailedError at FinalCastleBreakPatchTest.java:442
```

— i.e. the loud sha-mismatch message, not an ASM stack trace. The built resource
was then restored to the exact 19272 bytes / `2c1e57b2…`.

Full build and tests after the fixes:

```
$ ./gradlew spotlessApply --no-daemon -q
SPOTLESS_OK exit=0
$ ./gradlew clean build --no-daemon
BUILD SUCCESSFUL

$ ./gradlew test --rerun-tasks --no-daemon
BUILD SUCCESSFUL
com.jointspaceforce.patchee.features.AuroraBreakFloorTest          tests=4  failures=0 errors=0 skipped=0
com.jointspaceforce.patchee.features.FinalCastleBreakPatchTest     tests=21 failures=0 errors=0 skipped=0
com.jointspaceforce.patchee.features.FinalCastleBreakSettingsTest  tests=10 failures=0 errors=0 skipped=0
com.jointspaceforce.patchee.features.LightOverlayPatchTest         tests=6  failures=0 errors=0 skipped=0
com.jointspaceforce.patchee.features.LightOverlaySettingsTest      tests=7  failures=0 errors=0 skipped=0
com.jointspaceforce.patchee.features.LightOverlayThresholdTest     tests=5  failures=0 errors=0 skipped=0
com.jointspaceforce.patchee.features.TeamChunkCapPatchTest         tests=8  failures=0 errors=0 skipped=0
com.jointspaceforce.patchee.features.TeamChunkCapTest              tests=12 failures=0 errors=0 skipped=0
TOTAL tests=73 failures=0 errors=0 skipped=0
```

Files changed in this round: `.gitattributes`,
`src/test/java/com/jointspaceforce/patchee/features/FinalCastleBreakPatchTest.java`,
`src/test/resources/finalcastlebreak/README.md`, and this report. No `.class`
byte was modified and no git write was made.

### Uncertainty

- The proof that the publish path no longer mangles the file rests on git's
  attribute resolution (`text: unset`) and on `git hash-object --path=` no longer
  changing the bytes. The actual publish tool was not run here (the parent
  publishes); if that tool does not honour `.gitattributes` at all, this fix would
  not help — but the evidence (the fresh-checkout file was git-normalized to
  19271 bytes) shows it does.
- Only `live/TFBlocks.class` actually contained a CRLF pair and so was corrupted;
  the other five were not damaged, and the sha guard now protects all six if a
  future TF build ships a class with a CRLF.
