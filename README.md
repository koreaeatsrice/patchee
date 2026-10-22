# Patchee

A server-side add-on for GT:NH (Minecraft 1.7.10) that fixes a handful of
cross-mod annoyances without editing a single third-party jar. Every change is
made to another mod's runtime state as it loads, so a pack update cannot quietly
revert it. Clients need nothing installed to join.

## What it changes

- **JABBA Dolly** — picks up Forestry apiaries and bee houses, Gendustry
  industrial apiaries and MagicBees magic apiaries, bees and all.
- **Tinkers' Construct mattock** — digs sand and snow at shovel speed, with drops.
- **GregTech Super Tanks and Super Chests** — carrying a filled one no longer
  applies hunger, slowness, mining fatigue and weakness.
- **VeinMiner** — limited to sand, clay and gravel, 64 blocks per vein.
- **NEI F7 light overlay** — marks only pitch-dark blocks instead of NEI's
  over-broad light levels. Client-side.
- **Twilight Forest** — once the Glacier progression (the Snow Queen) is earned,
  aurora blocks break in 10 ticks, shears break arctic fur blocks in 5 ticks, the
  castle bricks, the castle magic blocks and the thorn wall break in 10 ticks,
  the castle bricks no longer drain the pickaxe 16x, and the thorn wall can be
  broken for good instead of growing back and multiplying. The behaviour change
  takes effect on the server; the break speed is seen on a client that has
  Patchee.
- **ServerUtilities team chunk limits** — ServerUtilities adds a team's members'
  limits together; Patchee gives a team its best member's own per-rank limit
  instead — never the sum, so a team of ordinary players gets one player's total
  and a member whose rank carries higher numbers (an admin) is never capped away.
  Server-side.

Each feature has its own switch in `config/patchee.cfg`, the Twilight Forest
break speeds have one per rule, and a master switch turns the whole mod off.
Keys, defaults and caveats: [docs/SETTINGS.md](docs/SETTINGS.md).

## Requirements

GT:NH for Minecraft 1.7.10. Patchee targets JABBA, Tinkers' Construct, GregTech
and NEI, plus Twilight Forest and VeinMiner when they are installed. A target
that is missing or has changed shape disables that one feature and writes a line
to the log — the server starts either way.

## Install (server)

1. Download `patchee-<version>.jar` from the [latest release](https://github.com/koreaeatsrice/patchee/releases) — the plain jar, not the `-dev` build.
2. Put it in the server's `mods` folder.
3. Restart the server normally (not "reload").

Full steps and the log lines to expect: [docs/INSTALL-SERVER.md](docs/INSTALL-SERVER.md).

## Client (optional)

A client without Patchee is out of step with the server wherever a fix changes
something on the server only: the tank effects still show, and sand and snow
break at the old speed. The F7 overlay tweak is client-only and needs the jar
locally. The Twilight Forest break speeds are seen only on a client that has
Patchee; a client without it breaks those blocks at the normal 1.7.10 speed and
nothing looks wrong to that player. What each optional client mod does:
[docs/CLIENT-MODS.md](docs/CLIENT-MODS.md).

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
- Modern Twilight Forest speeds up the aurora block only. Patchee covers the
  aurora blocks (block, pillar, slab and double slab) on purpose — same
  material, one consistent speed while mining the palace.

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

The Twilight Forest break-speed rule and its numbers were read from the modern
mod's published source and re-written as Patchee's own code; no Twilight Forest
source file, asset or sound is copied or shipped. Twilight Forest code is GNU
LGPL v2.1-or-later, its art is CC BY-NC-SA 4.0, and its sounds are all rights
reserved.
