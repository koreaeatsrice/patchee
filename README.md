# Patchee

> [!WARNING]
> The majority of this project was written by a Large Language Model (LLM). While prompted, built, and tested for reliability, please take note of this before downloading and using this mod.

> [!NOTE]
> Constructive criticism is desired and greatly appreciated. I make no claims to be a bona fide Software Engineer nor pretend that this project makes me one.

A small server-side add-on for GT:NH (Minecraft 1.7.10) that patches a handful of
cross-mod annoyances. It never edits another mod's jar — it adjusts their runtime
state at load, so a pack update cannot quietly revert the fixes. Clients need
nothing to join.

## What it changes

- **JABBA Dolly** — picks up Forestry apiaries and bee houses, Gendustry
  industrial apiaries and MagicBees magic apiaries, bees and all.
- **Tinkers' Construct mattock** — digs sand and snow at shovel speed, with drops.
- **GregTech Super Tanks and Super Chests** — carrying a filled one no longer
  applies hunger, slowness, mining fatigue and weakness.
- **VeinMiner** — limited to sand, clay and gravel, 64 blocks per vein.
- **NEI F7 light overlay** — marks only pitch-dark blocks instead of NEI's
  over-broad light levels. Client-side.

Each feature has its own switch in `config/patchee.cfg`. Keys, defaults and
caveats: [docs/SETTINGS.md](docs/SETTINGS.md).

## Requirements

GT:NH for Minecraft 1.7.10. Patchee targets JABBA, Tinkers' Construct and
GregTech, plus VeinMiner when it is installed. A target that is missing or has
changed shape disables that one feature and writes a line to the log — the server
starts either way.

## Install (server)

1. Download `patchee-<version>.jar` from the [latest release](https://github.com/koreaeatsrice/patchee/releases) — the plain jar, not the `-dev` build.
2. Put it in the server's `mods` folder.
3. Restart the server normally (not "reload").

Full steps and the log lines to expect: [docs/INSTALL-SERVER.md](docs/INSTALL-SERVER.md).

## Client (optional)

A client without Patchee is out of step with the server wherever a fix changes
something: the tank effects still show, and sand and snow break at the old speed.
The F7 overlay tweak is client-only and needs the jar locally. What each optional
client mod does: [docs/CLIENT-MODS.md](docs/CLIENT-MODS.md).

## Notes

- The Dolly accepts the four crafted bee housings (Forestry Apiary and Bee House,
  Gendustry Industrial Apiary, MagicBees Magic Apiary). Natural hives and the
  multi-block Alveary are out of scope; a moved apiary keeps its facing
  (cosmetic). Jabba's 1 MB pickup limit still applies.
- The mattock change adds sand, snow layers and snow blocks to its shovel list.
  Existing mattocks update on their own, and other mods still see the tool as an
  axe.
- The tank fix clears the four effects once per player tick while a filled tank
  or chest is carried — from any source, the pack's pollution included.
- VeinMiner stays a separate mod. Patchee rewrites its two config files at every
  server start and does nothing when it is absent; do not edit those files by
  hand.
- The F7 tweak is applied as NEI loads, so restart the game after changing
  `lightOverlay.maxLightNormal`.

## Troubleshooting

Start with [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md). If that does not
solve it, open an issue with the jar name, your GT:NH version, and the `patchee`
lines from `logs/latest.log`.

## Building

```sh
./gradlew build          # -> build/libs/patchee-<version>.jar
```

Pushing a `v*` tag builds and drafts a release. The code layout, invariants and
release flow are in [DEVELOPER.md](DEVELOPER.md).

## Licence

MIT — see [LICENSE](LICENSE). Built for the Joint Space Force server.