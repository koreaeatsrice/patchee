# Bytecode transformers

For an implementer changing `asm/` or adding a class-load patch. It covers the coremod entry, the six transformers,
the contract every one follows, and the class-load-order rules that decide what a transformer can know. The runtime
pipeline is [PIPELINE.md](PIPELINE.md); the 1.7.10 name rules are [../ops/GTNH-INTERNALS.md](../ops/GTNH-INTERNALS.md).

## The coremod entry

`asm/FMLPlugin.java` is the `IFMLLoadingPlugin`. It:

- declares `@IFMLLoadingPlugin.MCVersion("1.7.10")` and
  `@IFMLLoadingPlugin.TransformerExclusions({ "com.jointspaceforce.patchee." })` (`FMLPlugin.java:43-44`), so
  Patchee's own classes never pass through its transformers;
- returns six class names from `getASMTransformerClass()` (`FMLPlugin.java:48-55`);
- returns `null` from `getModContainerClass()`, `getSetupClass()` and `getAccessTransformerClass()`
  (`FMLPlugin.java:58-65`, `89-91`);
- runs `PollutionPreload.run(gameDirectory)` from `injectData` (`FMLPlugin.java:68-86`).

There is no coremod config file. Each transformer rejects every class except its target with a string comparison.
The built jar declares the entry in its manifest as `FMLCorePlugin: com.jointspaceforce.patchee.asm.FMLPlugin`,
written by the GTNH build from `coreModClass` in `gradle.properties`.

## The six

| Transformer | Target | Patch class | Sites | Change |
|---|---|---|---|---|
| `LightOverlayTransformer` | `codechicken.nei.WorldOverlayRenderer` | `features/LightOverlayPatch` | `SITE_COUNT = 2` (`LightOverlayPatch.java:41`) | Two comparison constants in `getSpawnMode`. No call injected; stack map frames stay valid. |
| `TeamChunkCapTransformer` | `serverutils.data.ServerUtilitiesTeamData` | `features/TeamChunkCapPatch` | `SITES_PER_METHOD * 2` (`TeamChunkCapPatch.java:107`) | Return sites in `getMaxClaimChunks` and `getMaxChunkloaderChunks`; injects `ALOAD_0 / SWAP / INVOKESTATIC` per site. The JVM has no `min` instruction, so this is the one patch that injects a call. |
| `FinalCastleBreakTransformer` | `twilightforest.block.BlockTFCastleBlock`, `twilightforest.block.BlockTFThorns` | `features/FinalCastleBreakPatch` | `1 + 2` (`FinalCastleBreakPatch.java:142-148`) | Re-points the castle's `Item.onBlockDestroyed` call and the thorn burst call sites to Patchee hooks. |
| `ChestplateFlightTransformer` | `ic2.core.item.armor.ItemArmorQuantumSuit` | `features/ChestplateFlightPatch` | `SITE_COUNT = 1`, `SNEAK_SITE_COUNT = 1` (`ChestplateFlightPatch.java:238-244`) | Rewrites the hover jump clamp, adds a descent store after IC2's own `-0.1` store, adds the hold-altitude branch and a ground guard. |
| `GraviSuiteFlightTransformer` | `gravisuite.ItemAdvancedJetPack` | `features/GraviSuiteFlightPatch` | `SITE_COUNT = 1` (`GraviSuiteFlightPatch.java:269`) | The same three flight edits for GraviSuite's jetpack. |
| `HandshakeModListTransformer` | `cpw.mods.fml.common.network.handshake.FMLHandshakeServerState$2` | `features/HandshakeModListPatch` | 1 | Inserts `ALOAD 1 / ALOAD 2 / INVOKESTATIC SuperTankHandshake.observe` at the entry of `accept`, before the first frame. Frames kept; `COMPUTE_MAXS`. |

`GraviSuiteFlightTransformer` has no `Feature` class. It reads the three chestplate switches through
`ChestplateFlightSettings.current()` (`GraviSuiteFlightTransformer.java:74-85`), so the three registered
`Chestplate*Feature` classes are its config and banner surface. That is why the banner has no `graviSuite` field.

## The contract

Every transformer follows the shape in `LightOverlayTransformer.java:37-70` and
`TeamChunkCapTransformer.java:43-71`:

1. `basicClass == null` → return `null`.
2. Name is not the target → return the input unchanged. Check both `transformedName` and `name`; the loader can
   pass either.
3. Wrap the work in `try { ... } catch (Throwable)`. Log ERROR and return the original bytes. A transformer runs
   inside the class loader; a throw would take the game down. The logging call is itself wrapped, because logging
   must not be the reason a class fails to load.
4. Read the switches. Forge's config does not exist yet, so the transformer's `*Settings` class parses
   `config/patchee.cfg` itself (`LightOverlaySettings.read()`, `TeamChunkCapSettings.read()`), using
   `new File(new File(System.getProperty("user.dir", "."), "config"), "patchee.cfg")`, the `B:key=true` /
   `I:key=0` format, and the documented defaults.
5. Match the exact byte shape. Success: log the site count and return the patched bytes. Already patched: return
   the input. Any other shape: log ERROR, dump what was seen where supported, return the original bytes. The target
   class is left exactly as it ships.
6. Be idempotent. A second pass over patched bytes must detect the patch and return the input unchanged.

A transformer never adds, removes or renames a method. `LightOverlay` changes operands only. `TeamChunkCap` injects
three instructions per return site and leaves the class's own frames alone. `FinalCastleBreak` re-points calls.
`ChestplateFlight` and `GraviSuiteFlight` replace or append stores and add one guarded branch.

## What a transformer cannot know

- **Forge config.** `Configuration` is created in `preInit`, long after the class loads. Hence the `*Settings`
  readers. They and `Config` must read the same key constants or the two views drift.
- **`Toggles`.** The snapshot is built in `preInit`. A transformer may read a static holder that a feature later
  republishes; that is exactly what `TeamChunkCap` does. The transformer seeds the holder from the file, and the
  feature's arm step overwrites it with the authoritative values (`TeamChunkCapFeature.java:143-163`).
- **The side, reliably.** A transformer may run on either side; the target class's presence is the practical
  signal. Do not put a side policy in a transformer; put it in the feature step.
- **`isArmed()` at patch time.** `HandshakeModListTransformer` reads `SuperTankFeature.isArmed()`; its target
  class only loads at the first login, after `preInit`, so an unarmed run silently means "every client is
  stock" — the Super Tank clear is then off, and nothing breaks.
- **That it ran.** A class can be present and unpatched. Log a site count, set a `patchApplied` flag only on a real
  match, and let the feature's arm line and the self-test confirm.

## Load order

1. FML discovers coremods before it constructs mods.
2. `injectData` runs during loading.
3. The transformers are installed and fire as classes load, not at a lifecycle event.
4. Every mod's `preInit`, then every mod's `postInit`, then server start.

The order **between two coremods is not a contract**. GregTech reads `config/GregTech/Pollution.cfg` in its own
coremod's static initializer, and FML runs that before Patchee's `injectData`, so Patchee's rewrite lands after the
read and takes effect on the next boot. The postInit step covers the current boot. Do not try to move the write
earlier; there is no earlier hook.

Two jars with the same `modid` abort the boot with a duplicate-mod error. There is no "last one wins".

## Testing a patch

| Layer | Proves |
|---|---|
| Synthetic bytes (`*PatchTest`) | Site count, instruction sequence, idempotence, non-target pass-through, wrong-shape failure. |
| `tools/matcher-check/` | The same against the jar the pack ships (`8/8`, `10/10`, `18/18`). |
| `./gradlew runServer` | The JVM verifier accepts the patched class in a real load. |
| `SelfTest` | The patched behaviour on real objects. |

A matcher that never fires is silent. That is why every layer asserts a count rather than "no exception". The exact
commands are in [../dev/TESTING.md](../dev/TESTING.md).

## Adding one

1. `asm/YourTransformer.java` — gate, catch, settings, patch, log, original bytes on doubt.
2. One line in `FMLPlugin.getASMTransformerClass()`.
3. `features/YourPatch.java` — match, edit, return a state and a site count; never throw.
4. `features/YourSettings.java` — only if the transformer needs a value.
5. A `Feature` for the switch, banner, runtime listener and self-test.
6. `*PatchTest`, a matcher tool if the jar is available, a `SelfTest` check.

The worked version is [../dev/ADDING-A-FEATURE.md](../dev/ADDING-A-FEATURE.md).

## See also

- [PIPELINE.md](PIPELINE.md) — where the `Feature` half runs.
- [SIDES.md](SIDES.md) — which side a patch affects.
- [../ops/GTNH-INTERNALS.md](../ops/GTNH-INTERNALS.md) — names and `javap`.
