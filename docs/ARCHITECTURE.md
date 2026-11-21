# Architecture

This page describes what the code actually does. Every claim cites a file, and where the detail matters, a line.
Read it before changing `core/`, `asm/` or the startup path.

## 1. Two entry points in one jar

Patchee is both a Forge coremod and an ordinary `@Mod`. They start at different times and must not be confused.

| Entry point | Class | When it runs | What it does |
|---|---|---|---|
| Coremod | `asm/FMLPlugin.java` | Before any mod is constructed, while the class loader is still loading mod classes | Registers five `IClassTransformer`s (`FMLPlugin.java:46-56`), and runs `PollutionPreload.run(gameDirectory)` from `injectData` (`FMLPlugin.java:65-82`). |
| Mod | `Patchee.java` | Normal FML lifecycle | `preInit` → `CommonProxy.preInit`, `postInit` → `CommonProxy.postInit`, `serverStarted` → `CommonProxy.serverStarted`. |

`FMLPlugin` declares `@IFMLLoadingPlugin.MCVersion("1.7.10")` and
`@IFMLLoadingPlugin.TransformerExclusions({ "com.jointspaceforce.patchee." })` (`FMLPlugin.java:41-42`). The
exclusion matters: Patchee's own classes are never handed to its own transformers. `getModContainerClass()`,
`getSetupClass()` and `getAccessTransformerClass()` all return `null` (`FMLPlugin.java:55-62`, `86-88`). There is
no coremod config file; each transformer rejects every class except its own target with a string comparison.

`Patchee` declares `acceptableRemoteVersions = "*"` and `dependencies = "before:VeinMiner"`
(`Patchee.java:120-121`). The first is the client-optional contract at the FML level. The second exists so
Patchee's `preInit` runs before VeinMiner's `preInit`, because VeinConfig writes files VeinMiner reads there.

## 2. Startup sequence

The order is fixed by FML and by the two entry points:

1. **Coremod construction.** `FMLPlugin` is constructed, `injectData` runs. `PollutionPreload.run` rewrites
   `config/GregTech/Pollution.cfg` and, if it already exists, `config/GregTech/Client.cfg`
   (`features/PollutionPreload.java`). This is as early as a coremod can run. GregTech reads its config in its own
   coremod's static initializer, which FML runs before any `injectData`, so this file write takes effect on the
   next boot. The in-memory step at postInit covers the current boot.
2. **Class loading.** Every class the loader sees passes through `FMLPlugin`'s five transformers in the order
   listed in `getASMTransformerClass()`. A transformer acts only when the class name matches; otherwise it returns
   the input array unchanged.
3. **`preInit`.** `CommonProxy.preInit` loads the config from the registry, takes a `Toggles` snapshot, builds the
   `PatchContext` and the two pipelines, logs the banner, and runs the **early** pipeline on a dedicated server
   when the master switch is on (`CommonProxy.java:43-70`).
4. **`postInit`.** If the master switch is off, one INFO line and no work (`CommonProxy.java:72-77`). Otherwise the
   normal pipeline runs (`CommonProxy.java:77`).
5. **`serverStarted`.** VeinConfig re-reads VeinMiner's live state and logs a verification line; if
   `runSelfTest=true`, `SelfTest.schedule` registers a one-shot first-tick listener
   (`CommonProxy.java:80-93`).
6. **First server tick.** The self-test runs and unregisters itself (`SelfTest.java:92-120`).

## 3. The feature pipeline

### 3.1 `Feature`

`core/Feature.java:14` is a self-describing descriptor. It answers:

- `id()` — the log id and lookup key, e.g. `DollyFix`. It also names the boot-banner field.
- `configKey()` — the key in `config/patchee.cfg`, e.g. `enableDollyFix`.
- `defaultEnabled()` — the value used when the key is absent.
- `description()` — the plain-language comment written into the config file.
- `disabledMessage()` — the INFO line when the switch is off.
- `failureMessage()` — the ERROR line when a step throws.
- `targetClass()` — the class whose presence gates the feature, or `null` when the feature does not depend on one
  external class at boot.
- `missingTargetMessage()` — the INFO line for an absent target.
- `steps()` — the work, in order (`Feature.java:46`).
- `early()` — default `false` (`Feature.java:59`). True means the work must run at Patchee's own `preInit`, before
  another mod's `preInit`.

Only `VeinConfigFeature` returns `early() == true` (`features/VeinConfigFeature.java:150`). It writes VeinMiner's
config files, and VeinMiner loads them in its own `preInit`; FML runs every mod's `preInit` before any `postInit`,
so a postInit step would be too late. `before:VeinMiner` in `Patchee.java:121` is what makes Patchee's `preInit`
come first.

### 3.2 `Step` and `Outcome`

`core/Step.java` is a functional interface: `Outcome run(PatchContext ctx) throws Exception`. A step may throw;
the decorators handle it. A step holds no global state: everything it needs is in the context.

`core/Outcome.java` has three values:

- `APPLIED` — the step changed the target.
- `SKIPPED` — it did nothing on purpose: target absent, feature off, already patched.
- `FAILED` — it could not do its job; the failure was already reported.

### 3.3 Composition helpers

`core/Steps.java` supplies:

- `sequence(Step...)` — runs steps in order and stops on `FAILED`, but not on `SKIPPED` (`Steps.java:21-34`). The
  asymmetry is deliberate: a later step usually depends on an earlier one, but a skip often still leaves something
  useful to report.
- `note(String)` — logs one line (`Steps.java:38`).
- `withStaticField(owner, field, action)` — resolves a declared static field through `ctx.reflect()` and hands it
  to the action; an absent class returns `SKIPPED` (`Steps.java:57-72`).
- `present(String)` — a `TargetCheck` that is true when the class is on the classpath (`Steps.java:74`).
- `noop()` — a do-nothing step, so a single-step feature is still a step list.

### 3.4 Decorators

`core/StepDecorators.java` adds one concern per wrapper:

- `failSoft(stepId, failureMessage, step)` — catches `Throwable`, calls
  `ctx.failurePolicy().onFailure(...)`, returns `FAILED` (`StepDecorators.java:23-36`). This is the normal failure
  path; a step does not need its own catch around the work it reports, and a step that must catch locally (an event
  handler) logs once and keeps going.
- `logged(stepId, step)` — logs step start and result at DEBUG only, so a production log is unchanged
  (`StepDecorators.java:39-55`).
- `requiresTarget(stepId, present, skipMessage, step)` — calls `ctx.missingTargetPolicy().onMissingTarget(...)`
  and returns `SKIPPED` when the probe is false (`StepDecorators.java:60-72`).

### 3.5 `PatchFactory`

`core/PatchFactory.java` turns a `FeatureRegistry` and a `Toggles` snapshot into a `FeaturePipeline`. For each
feature in registration order:

- If the feature is enabled, `enabled(feature)` (`PatchFactory.java:74`) builds the step list. The wrapper order
  is: `Steps.sequence(feature.steps())` → `failSoft` → `requiresTarget` (only when `targetClass() != null`) →
  `logged`. The target gate is outside fail-soft, so an absent class is a quiet skip rather than a caught
  exception. A feature with an empty step list gets `Steps.noop()`.
- If the feature is disabled, `disabled(feature)` (`PatchFactory.java:92`) contributes one step that logs
  `feature.disabledMessage()` and returns `SKIPPED`.

`build(toggles)` and `buildEarly(toggles)` are disjoint: a feature goes into exactly one pipeline, selected by
`feature.early()` (`PatchFactory.java:51-64`). The registry is not filtered by side; side decisions belong to the
steps.

### 3.6 `FeaturePipeline`

`core/FeaturePipeline.java:23` runs the decorated steps once. It has a last-resort `catch (Throwable)` that logs at
DEBUG (`FeaturePipeline.java:27`). The server boot continues whatever happens.

### 3.7 `FeatureRegistry`

`core/FeatureRegistry.java` is an ordered list with a freeze. `register` throws
`IllegalStateException("feature registry is frozen")` after `freeze()` (`FeatureRegistry.java:19-30`). The single
registration point is `features/PatcheeFeatures.standard()`, which registers 17 features and then freezes. Order is
run order.

### 3.8 `Toggles` and `Config`

`core/Toggles.java` is the immutable config view: master switch, per-feature switches, self-test switch, and the
typed advanced values (`extraDollyClasses`, `veinBlockLimit`, `veinRadius`, `veinBlocks`, `veinExtraTools`,
`lightOverlayMaxNormal`, `glassBreakMinArmourPieces`). Unknown feature ids are off.

`Config.java` is the only Forge-config holder. `synchronizeConfiguration(configFile, registry)` loads the file,
asks each registered feature for its key/default/comment, reads the advanced values, and replaces the static
`Toggles` snapshot. `Config.toggles()` returns it. The static fields exist because Forge config is conventionally
static, but features never read them: the pipeline gets the snapshot through `PatchContext`.

Adding a feature therefore adds a config key with no edit to `Config.java`: the registry supplies the name, the
default and the comment.

### 3.9 `PatchContext`

`core/PatchContext.java:25` carries everything a step may use: the logger, the `Toggles` snapshot, the failure
policy, the missing-target policy, the `Reflective` helper, and the game's `config/` directory
(`PatchContext.java:60`). It is constructed once in `CommonProxy.preInit`. A feature that writes another mod's
config builds the path from `ctx.configDirectory()` instead of reaching for a static.

### 3.10 Policies

`core/policy/` holds two interfaces and their standard instances in `Policies.java`:

- `FAIL_LOG_ERROR_CONTINUE` — `log.error(message, failure)`, then carry on.
- `QUIET_SKIP_INFO` — `log.info(message)`, then skip.

The composition root picks one of each (`CommonProxy.java:57-58`). Features never see the policies.

### 3.11 `Reflective`

`core/reflect/Reflective.java` is the shared reflection plumbing:

- `find(name)` returns the class or `null`, catching `ClassNotFoundException` and `LinkageError`
  (`Reflective.java:22-30`). A half-loaded pack is a normal condition, not an error.
- `staticField(owner, name)` uses `getDeclaredField` and `setAccessible(true)` (`Reflective.java:36`). A missing
  field throws `NoSuchFieldException`, which fail-soft turns into the feature's failure line.
- `staticClassList(field)` returns the value as `List<Class<?>>` or `null`.
- `appendIfAbsent(list, value)` is the idempotence primitive (`Reflective.java:63`).
- `extendMaterials(current, extras)` returns the same array when nothing is missing, so an already-patched array
  is left alone (`Reflective.java:76-84`).

## 4. What a feature looks like

A plain feature is one class in `features/` that implements `Feature` and returns a list of steps. The work is
usually one `Steps.withStaticField` call. `DollyFeature` is the clearest example: it resolves
`ItemBarrelMover.classExtensions`, rejects the value if it is not a `List`, appends each installed housing class
once, and logs the count (`features/DollyFeature.java:121-168`).

A feature that needs a runtime listener registers it from a step. `GlassBreakFeature` is server-only: its arm step
returns `SKIPPED` when the side is not `SERVER` (`features/GlassBreakFeature.java:126`), and its event handler
returns early when the world is null or remote (`GlassBreakFeature.java:219`). `AuroraBreakFeature` and
`ArcticFurBreakFeature` instead register a `PlayerEvent.BreakSpeed` listener on both sides, because in 1.7.10 the
client is the clock that accumulates break progress.

A feature that needs a config write computes the file from `ctx.configDirectory()`, writes only its own keys, keeps
a timestamped `.bak`, and re-reads the file to report what is actually there. `PollutionPreload`,
`PollutionConfigFeature`, `PollutionVisualsFeature` and `VeinConfigFeature` follow that shape.

## 5. Transformer-backed features

Five changes cannot be made by reflection because the thing to change is a constant, a return value or a
constructor argument inside another mod's compiled method, with no hook and no config key. Those are class-load
patches.

| Transformer | Target class | Patch class | Sites | What changes |
|---|---|---|---|---|
| `asm/LightOverlayTransformer.java` | `codechicken.nei.WorldOverlayRenderer` | `features/LightOverlayPatch.java` | `SITE_COUNT = 2` (`LightOverlayPatch.java:41`) | Two comparison constants in `getSpawnMode`, so the F7 X is drawn at the configured light level. No call injected; stack map frames stay valid. |
| `asm/TeamChunkCapTransformer.java` | `serverutils.data.ServerUtilitiesTeamData` | `features/TeamChunkCapPatch.java` | `SITES_PER_METHOD * 2` (`TeamChunkCapPatch.java:107`) | Return sites in `getMaxClaimChunks` and `getMaxChunkloaderChunks`; injects an `ALOAD_0 / SWAP / INVOKESTATIC` triple per site. The JVM has no `min` instruction, so this is the one patch that injects a call. |
| `asm/FinalCastleBreakTransformer.java` | `twilightforest.block.BlockTFCastleBlock`, `twilightforest.block.BlockTFThorns` | `features/FinalCastleBreakPatch.java` | `1 + 2` (`FinalCastleBreakPatch.java:142-148`) | Re-points the castle's `Item.onBlockDestroyed` call and the thorn burst call sites to Patchee hooks. |
| `asm/ChestplateFlightTransformer.java` | `ic2.core.item.armor.ItemArmorQuantumSuit` | `features/ChestplateFlightPatch.java` | `SITE_COUNT = 1`, `SNEAK_SITE_COUNT = 1` (`ChestplateFlightPatch.java:238-244`) | Rewrites the hover jump clamp, adds a descent store after IC2's own `-0.1` store, adds the hold-altitude branch and a ground guard. |
| `asm/GraviSuiteFlightTransformer.java` | `gravisuite.ItemAdvancedJetPack` | `features/GraviSuiteFlightPatch.java` | `SITE_COUNT = 1` (`GraviSuiteFlightPatch.java:269`) | The same three flight edits for GraviSuite's jetpack. |

### 5.1 The transformer contract

Every transformer follows the same shape, visible in `LightOverlayTransformer.java:37-70` and
`TeamChunkCapTransformer.java:43-71`:

1. If `basicClass == null`, return `null`.
2. If the name is not the target, return `basicClass` unchanged. Both `transformedName` and `name` are checked,
   because the loader can call with either.
3. Wrap the patch in `try { ... } catch (Throwable)`. Log at ERROR and return the original bytes. A transformer
   runs inside the class loader; throwing would take the game down. The logging call is itself wrapped, because
   logging must not be the reason a class fails to load.
4. Read the switches. A transformer runs before Forge's config system exists, so each transformer's settings class
   parses `config/patchee.cfg` itself. `LightOverlaySettings.read()` (`features/LightOverlaySettings.java:52`) and
   `TeamChunkCapSettings.read()` (`features/TeamChunkCapSettings.java:52`) both use
   `new File(new File(System.getProperty("user.dir", "."), "config"), "patchee.cfg")`, understand the
   `B:key=true` / `I:key=0` format, ignore comments and unknown keys, and fall back to the documented defaults.
5. Match the exact byte shape. On success, log the site count and return the patched bytes. On "already patched",
   return the original bytes. On any other shape, log ERROR, dump what was seen where the patch supports it, and
   return the original bytes. The target class is left exactly as it ships.
6. Be idempotent. A second pass over patched bytes must detect the patch and return the input unchanged.

A transformer never adds, removes or renames a method. `LightOverlay` changes operands only. `TeamChunkCap`
injects three instructions per return site and leaves the class's own stack map frames alone. `FinalCastleBreak`
re-points calls. `ChestplateFlight` and `GraviSuiteFlight` replace or append stores and add one guarded branch.
The tools in `tools/matcher-check/` assert the instruction sequences, the site counts, idempotence, the
pass-through of a non-target class and the fail-loud path.

### 5.2 Why a transformer-backed feature still has a `Feature`

The `Feature` cannot do the byte work; the transformer runs before the feature pipeline exists. The `Feature` owns:

- the config key (so the key appears in `patchee.cfg` and in the banner),
- the boot line that says what actually happened this boot,
- any runtime listener that belongs to the same behaviour,
- the self-test entry.

`LightOverlayFeature` is the minimal case: its only step logs the armed line on the client
(`features/LightOverlayFeature.java:93-106`) and returns `SKIPPED` on a server. The transformer is the work.

`TeamChunkCapFeature` shows the harder case. The injected hook cannot reach Forge, so it reads a small static
holder, `features/TeamChunkCap.java`. The transformer seeds that holder from the file while the class loads
(`TeamChunkCapSettings`), then the feature's arm step publishes the authoritative `Toggles` values into the same
holder (`TeamChunkCapFeature.java:128-163`). The arm line is gated on `TeamChunkCap.patchApplied()`, which the
transformer sets only when the return sites were really patched. A target class that was merely present is not
proof of a patch.

`GraviSuiteFlightTransformer` has no `Feature` class at all. It reads the same three chestplate switches through
`ChestplateFlightSettings.current()` (`asm/GraviSuiteFlightTransformer.java:74-85`), so the three registered
`Chestplate*Feature` classes are its config and banner surface. That is why the banner has no `graviSuite` field.

## 6. Proxies and the client/server split

There are two proxy classes and one composition root.

- `CommonProxy.java` is the composition root and the server proxy. It registers the features, loads the config,
  builds the context and pipelines, logs the banner, runs the early pipeline, the normal pipeline and the
  `serverStarted` work.
- `ClientProxy.java` is eight lines and extends `CommonProxy` with no override. Its comment says to call `super`
  if a client override is added. There is currently no client-only construction: client behaviour is decided
  inside feature steps by asking the side.

`CommonProxy` branches on the side in three places:

- the early pipeline runs only when `getSide() == Side.SERVER && toggles.masterEnabled()` (`CommonProxy.java:67`);
- VeinConfig's live verification runs under the same condition (`CommonProxy.java:84`);
- the self-test is scheduled whenever the master switch and `runSelfTest` are on (`CommonProxy.java:88`); the
  individual checks then guard themselves.

A feature that is server-only gates inside its own step. `GlassBreakFeature.java:126` returns `SKIPPED` when the
side is not `SERVER`. A feature that is client-only gates the other way: `LightOverlayFeature.java:99` and
`PollutionVisualsFeature.java:164` return `SKIPPED` when the side is not `CLIENT`.
`PollutionVisualsFeature.java:115` decides from the side before it looks for GregTech, so a dedicated server logs
one line and does nothing.

### 6.1 The client-optional contract

This is a hard product rule, not a slogan: **Patchee is installed on the server, and a player joins with a stock
client and nothing installed.** A PR that makes the jar required on the client is rejected by policy.

Every feature must choose one of three shapes:

| Shape | Meaning | Examples in this repo |
|---|---|---|
| **SERVER-ONLY** (default, preferred) | The server changes behaviour; the client needs nothing. A stock client joins and is unaffected, or is deliberately out of step in a way the server tolerates. | Dolly, Mattock, SuperTank, GlassBreak, VeinConfig, TeamChunkCap, EnrichedFertilizer recipe, FishTrap move hotkeys, the server half of pollution settings. |
| **BOTH-SIDES-OPTIONAL** | The server half is complete on its own. A client that also has Patchee sees or feels the same change locally; a stock client keeps the stock local behaviour. | The Twilight Forest break-speed floors, the IC2 and GraviSuite flight edits, the pollution visuals. |
| **CLIENT-ONLY** | Allowed only when the feature is cosmetic and inert without Patchee. The server is not involved and a stock client simply does not get it. | NEI's F7 overlay (`LightOverlayFeature`). |

The mechanics that keep this true:

1. **No network channel, no packet.** Patchee registers nothing with `NetworkRegistry` and defines no `IMessage`
   or `IPacket`. A maintained check is:
   ```sh
   grep -rn "SimpleNetworkWrapper\|NetworkRegistry\|IMessage\|IPacket" src/main/java
   ```
   It must return nothing. Today the only match for the word `network` is a comment naming IC2's own packet in
   `ChestplateHoverLiftFeature.java:35`. If a feature seems to need a packet, the design is wrong: either the
   server can do it alone, or it is a client-only cosmetic and needs no server data.
2. **Server gating in a step.** A server-only step checks the side and returns `SKIPPED` with a line:
   `FMLCommonHandler.instance().getSide() != Side.SERVER` (`GlassBreakFeature.java:126`,
   `TeamChunkCapFeature.java:145`). A client-only step checks `!= Side.CLIENT`
   (`LightOverlayFeature.java:99`, `PollutionConfigFeature.java:245`, `PollutionVisualsFeature.java:164`).
3. **Event handlers guard before touching server state.** `GlassBreakFeature.java:219` returns when
   `event.world == null || event.world.isRemote`. `GlacierGate.java:58` requires
   `player.worldObj != null && !player.worldObj.isRemote` before it reads progression. A handler that runs on the
   client must not mutate server-authoritative state.
4. **The composition root is side-aware, not side-split.** `CommonProxy` builds one feature set and branches at
   the three side points above. `ClientProxy` exists for client-only construction and must call `super`; today it
   adds nothing. This keeps the config, the banner and the registry identical on both sides, so a client and a
   server read the same keys.
5. **Every client-side improvement is optional by construction.** The pollution visuals case is the model: the
   server always sends the real number; only a Patchee client turns the render flags and tint tables off. A stock
   client still draws GregTech's smog, and that is accepted and documented. The server is complete without the
   client.

How each side is proven, rather than asserted:

- The server side is proven by the CI smoke job, `Server smoke test (self-test must pass)` in
  `.github/workflows/ci.yml`. It boots a real dev server with `runSelfTest=true`, waits for
  `[SelfTest] result:`, and fails the job on a missing arm line or on any `[SelfTest] FAIL`. The same job greps
  the boot lines for Dolly, Mattock, VeinConfig, Aurora, ArcticFur, TeamChunkCap, FinalCastle and the GlassBreak
  skip.
- A client-only claim is proven by knowing the class is client-only and by the feature's own side guard. The
  transformer name-gates on `codechicken.nei.WorldOverlayRenderer`; a dedicated server never loads it, so the
  transformer never fires and the feature logs its skip. `PollutionVisualsTest` proves the client decision logic
  without a game; CI only ever sees the dedicated-server skip line, which `DEVELOPER.md` records deliberately.
- A new feature must still let a stock client join. The proof is a real client connection to a server running the
  new jar, with no Patchee on the client, and a clean `latest.log` on both sides. There is no automated substitute
  for that check. In a dev environment, `./gradlew runServer` plus a second `./gradlew runClient` against it is
  the closest available; the client must not be given the jar.

What breaks client optionality, and is therefore rejected:

- registering a packet, a channel or a `NetworkRegistry` handler;
- requiring a client mod (a hard `@Mod` dependency, a class that only exists on the client without a side guard,
  or an item/block the client must have to render);
- changing the block or item registration set, which changes the registry the client syncs against;
- changing world data or metadata in a way a stock client must interpret;
- patching a class that exists only on one side without a side guard or a name gate;
- adding an event handler that runs on both sides and mutates state only the server owns.

A PR that does any of these is out of scope even if the feature "works" on a patched client.

## 7. The self-test

`SelfTest.java` is the behavioural proof that runs on a live server when `runSelfTest=true`.

- `schedule(toggles, configDirectory)` registers a `FirstTick` listener on the FML bus (`SelfTest.java:92-97`).
- `FirstTick.onServerTick` waits for `TickEvent.Phase.START`, unregisters itself, then calls `SelfTest.run`
  (`SelfTest.java:112-121`). The first tick is used because ServerUtilities builds its claim data while the
  universe loads, at the same moment `FMLServerStartedEvent` fires; the order of two mods' handlers for one event
  is arbitrary.
- `runChecks` calls one check per feature area: dolly gate, mattock, VeinConfig, aurora break, arctic fur break,
  TeamChunkCap, FinalCastleBreak, enriched fertilizer, Fish Trap, glass break (`SelfTest.java:127-140`).
- `check(what, ok)` logs `[SelfTest] PASS  ...` or `[SelfTest] FAIL  ...`, counts it, and the run ends with
  `[SelfTest] result: N passed, M failed` (`SelfTest.java:143-152` and the end of `runChecks`).
- A missing mod is a `SKIP` line, never a `FAIL`. A check that cannot set itself up is a `FAIL` with the reason.

The checks are stronger than "the class loaded". They invoke the patched decision with real instances and a
control group. The Dolly check runs JABBA's own final loop against the live `classExtensions` list and requires a
natural hive and a furnace to stay refused (`SelfTest.java:157-230`). The TeamChunkCap check builds a one-member
team and a multi-member team, compares the raw sums with the patched answers, and sweeps real claims until the
server refuses (`SelfTest.java:533-712`). Where a check cannot run on a dedicated server, it says so and skips.

A transformer-backed feature proves itself in three layers:

1. the transformer's own log line with the site count, or an ERROR line that says the shape was not recognised;
2. the feature's armed line, gated on the transformer's "patch applied" flag where a mere class presence would
   lie (`TeamChunkCapFeature.java:136-163`);
3. the self-test, which exercises the patched behaviour on real objects.

For byte-level proof there is `tools/matcher-check/`. It runs the real transformer class over real jar bytes and
asserts the result. `MatcherCheck.java` expects `RESULT: 8/8 PASS`, `CastleCheck.java` expects `10/10 PASS`,
`ChestplateCheck.java` expects `18/18 PASS`. Each tool also checks idempotence, a non-target pass-through and the
fail-loud path. A matcher that never fires is silent, which is why this layer exists.

`PollutionVisualsRealJarTest` is the reflection equivalent: when `PATCHEE_GREGTECH_JAR` points at a real GregTech
jar, it reads the class files with ASM and asserts every field name and descriptor the guard writes. Unset, the
test is skipped with a JUnit assumption, so a plain build needs no jar.

## 8. Invariants that hold the design together

- One registration point: `features/PatcheeFeatures.standard()`.
- Config keys, banner fields and pipeline order are derived from the registry; adding a feature edits one list.
- Features read settings through `PatchContext`, never through static `Config` fields.
- A missing target is a skip; a changed shape is an error and the target is left untouched; the server always
  starts.
- A transformer never throws and never changes a class it does not recognise.
- A transformer is idempotent and leaves every non-target class byte-identical.
- The server is complete without a client copy; no packet exists.
- The pollution number always reaches the client unchanged.

## 9. Where the detail lives

- `docs/CONTRIBUTING.md` — the step-by-step recipe for adding a feature and proving it.
- `docs/GTNH-INTERNALS.md` — obfuscated names, load order, reflection versus ASM, jar inspection.
- `docs/RELEASE.md` — the release engine and the artifact path.
- `tools/matcher-check/README.md` — the exact commands and expected results for each matcher.
- `DEVELOPER.md` — the short index and the facts unique to local development.
