# Config and toggles

For an implementer adding a setting or reading one. It explains the registry-driven config, the immutable snapshot
a feature actually sees, and the second reader the transformer layer needs. The user-facing page is
[../guide/CONFIGURING.md](../guide/CONFIGURING.md); every key is in [../SETTINGS.md](../SETTINGS.md).

## One source, two readers

```
config/patchee.cfg
   ├── Config.synchronizeConfiguration()  -> Toggles snapshot  -> PatchContext -> Feature steps
   └── *Settings.read()                   -> early values      -> transformer (class load)
```

Both read the same file and, where they share a key, the same key constant. That is the only thing keeping the two
views from drifting. If you add a key that a transformer also needs, define the constant once and reference it from
both readers.

## The snapshot

`core/Toggles.java` is immutable: the master switch, a map of feature id to switch, the self-test switch, and the
typed advanced values (`extraDollyClasses`, `veinBlockLimit`, `veinRadius`, `veinBlocks`, `veinExtraTools`,
`lightOverlayMaxNormal`, `glassBreakMinArmourPieces`). An unknown feature id is off.

`Config.java` holds the mutable statics, but only because Forge config is conventionally static. After every load it
builds a new `Snapshot` and replaces the static `toggles`. Features never read the statics: the pipeline gets the
snapshot through `PatchContext` (`core/PatchContext.java:25`).

Why this matters: a feature cannot observe a half-loaded config, a test can construct a snapshot without Forge, and
a reload has no meaning unless the snapshot is rebuilt.

## How the file is generated

`Config.synchronizeConfiguration(configFile, registry)` (`Config.java:100-145`):

1. opens the `Configuration` and loads it;
2. reads `enabled` with `MASTER_COMMENT`;
3. for each registered feature, reads `feature.configKey()` with `feature.defaultEnabled()` and
   `feature.description()` as the comment;
4. reads the advanced values with their comment constants;
5. builds and publishes the snapshot.

One category: `Configuration.CATEGORY_GENERAL` (`Config.java:36`). Keys are flat (`enableDollyFix`,
`veinConfig.blockLimit`). Adding a feature adds its key with no edit to `Config.java`.

## The early reader

A transformer runs before Forge's `Configuration` exists. Its settings class opens the file directly:

```java
new File(new File(System.getProperty("user.dir", "."), "config"), "patchee.cfg")
```

`LightOverlaySettings.read()` (`features/LightOverlaySettings.java:52`) and `TeamChunkCapSettings.read()`
(`features/TeamChunkCapSettings.java:52`) use the `B:key=true` / `I:key=0` format, ignore comments and unknown
keys, and fall back to the documented defaults. `TeamChunkCapSettings` also accepts the old
`teamChunkCap.maxClaimChunks` / `maxLoadedChunks` keys and ignores them, so an old file does not fail.

The reader must not throw. A malformed file is a default value and, at most, one log line.

## Adding a setting

1. `core/Toggles` — the field and accessor on the snapshot.
2. `Config.Snapshot` — the field and constructor argument.
3. `Config` — a static default and a comment constant, then a read in `synchronizeConfiguration`.
4. The feature reads `ctx.toggles().yourValue()`.
5. `docs/SETTINGS.md` — key, default, "when to change it".
6. If a transformer needs it, `*Settings` with the same key constant.
7. A unit test for the parse and the clamp, if it has a range.

Never rename a key. Never change a default silently; say so in the PR and in `docs/SETTINGS.md`. A removed key
stays parseable and ignored.

## Where the values are clamped

Some keys have a range. `glassBreak.minArmourPieces` is clamped to `0..4` by the rule itself
(`GlassBreakPolicy.clampMinArmourPieces`, called from `GlassBreak.java:104`), not by the config reader, so a value
written into the file is stored as-is and the consumer clamps it. Clamp in one place, as close to the decision as
possible, and test the boundary. A value outside the range should not throw.

## See also

- [PIPELINE.md](PIPELINE.md) — who receives the snapshot.
- [TRANSFORMERS.md](TRANSFORMERS.md) — the early reader's context.
- [../guide/CONFIGURING.md](../guide/CONFIGURING.md) — the owner's view.
