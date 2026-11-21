# Configuring Patchee

For a server owner who has the jar installed and wants to change what it does. It covers the config file, the
master switch, the restart rule, and the advanced keys; every key with its default is in
[SETTINGS.md](../SETTINGS.md).

## The file

`config/patchee.cfg` is a normal Forge `.cfg` file, written by Patchee at the first `preInit`. If it does not
exist, Patchee creates it with every key and a plain-language comment per key. If it exists, Patchee reads it and
keeps the values it finds; a key added by a new Patchee version is appended with its default.

The file has one category, `general`, and flat keys:

```ini
general {
    B:enabled=true
    B:enableDollyFix=true
    B:enableMattockFix=true
    ...
    I:veinConfig.blockLimit=64
}
```

A key's comment is the feature's own description (`Feature.description()`), so the file explains itself. Do not
hand-edit the comments; Patchee rewrites them from the registry on the next boot.

## The master switch

`enabled` is the master switch. `false` means Patchee does nothing at all: no pipeline, no feature step, no
pollution config write. The boot still prints one line so an operator can see the jar loaded and chose to stand
down.

Every feature also has its own `enable...` key. A feature with its switch off logs its `disabledMessage` at INFO and
returns `SKIPPED`. Turning a feature off does not undo an edit it already made in a previous boot (a config file it
rewrote, a list it extended); it stops it doing the work again.

## Restart rule

Change the file, restart the server. Patchee reads the config once at `preInit` and publishes an immutable snapshot;
there is no reload command and no config watcher. A `/reload` does not re-read it.

The one exception is the transformer layer: a transformer runs before Forge's config exists and parses
`config/patchee.cfg` itself. It reads the same keys, so the file stays the single source of truth, but it also only
reads at class-load time. A new value still needs a restart.

## What Patchee writes outside its own file

Three features rewrite another mod's config, because that mod has no API for the setting:

| File | Written by | Keeps a backup |
|---|---|---|
| `config/GregTech/Pollution.cfg` | `PollutionPreload` (coremod) and `PollutionConfigFeature` | Yes, a timestamped `.bak` |
| `config/GregTech/Client.cfg` | `PollutionPreload` (if the file exists) and the pollution features: `PollutionConfigFeature` at postInit, `PollutionVisualsFeature` on the client tick | Yes, a timestamped `.bak` |
| `config/veinminer/*.cfg` | `VeinConfigFeature` | No |

Each writer changes only its own keys and re-reads the file to log what is actually there. If a pack update replaces
one of these files, Patchee rewrites its keys on the next boot. If you want the previous values back, restore the
`.bak` next to the file and restart.

## Advanced keys, briefly

| Key | Default | What it does |
|---|---|---|
| `extraDollyClasses` | empty | Extra fully qualified `TileEntity` class names the JABBA Dolly should accept. Ignored for a non-`TileEntity`, a class JABBA blacklists, or a class that does not exist. |
| `runSelfTest` | `false` | Runs a one-shot check of every patched decision on the first server tick and logs PASS/FAIL per check. Turn it on for one boot after an install or pack update, then off. |
| `veinConfig.blockLimit` | `64` | The most blocks one vein may have. |
| `veinConfig.radius` | `20` | How far from the first block VeinMiner searches. |
| `veinConfig.blocks` | sand, red sand, clay, gravel | The blocks that may be vein-mined, `mod:block` or `mod:block/metadata`. |
| `veinConfig.extraTools` | empty | Extra items accepted as shovels for vein mining. |
| `lightOverlay.maxLightNormal` | `0` | The light level at which NEI's F7 overlay draws an X. `0` = pitch dark only, `7` = NEI's own default. Client-side only. |
| `glassBreak.minArmourPieces` | `1` | Filled armour slots that count as protected when mining real glass. `0`..`4`; values outside are clamped. |

## Pollution settings and the client

The pollution features do not reduce the pollution a server produces and do not change what is sent to clients.
They change what a client **draws**, and they always leave `gregtech.api.net.GTPacketPollution` alone. Hiding the
fog or the dirt on a client is a GregTech `Client.cfg` flag (`Pollution Fog`, `Pollution Dirt`); Patchee only sets
those flags for a client that has Patchee. A stock client still draws GregTech's smog, which is expected.

Do not try to "fix" pollution by editing GregTech's packet or by lowering the numbers in world data. That is
outside Patchee's contract and is rejected in review.

## Where to go next

- [SETTINGS.md](../SETTINGS.md) — every key, default, and when to change it.
- [FEATURES.md](../FEATURES.md) — what each switch turns on, and its limits.
- [TROUBLESHOOTING.md](TROUBLESHOOTING.md) — a setting that did not take effect.
