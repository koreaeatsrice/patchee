# Patchee

> [!WARNING]
> The majority of this project was written by a Large Language Model (LLM). While prompted, built, and tested for reliability, please take note of this before downloading and using this mod.

> [!NOTE]
> Constructive criticism is desired and greatly appreciated. I make no claims to be a bona fide Software Engineer nor pretend that this project makes me one.

[![Release](https://img.shields.io/github/v/release/koreaeatsrice/patchee?label=release)](https://github.com/koreaeatsrice/patchee/releases)
[![Build](https://img.shields.io/github/actions/workflow/status/koreaeatsrice/patchee/ci.yml?branch=main&label=build)](https://github.com/koreaeatsrice/patchee/actions/workflows/ci.yml)
[![License](https://img.shields.io/github/license/koreaeatsrice/patchee)](LICENSE)
![Minecraft](https://img.shields.io/badge/Minecraft-1.7.10-3f9d3f)
![GTNH](https://img.shields.io/badge/GT%3ANH-server--side-2f6f9f)

A server-side GT:NH (1.7.10) mod that fixes other mods at load time without touching their jars, so clients need nothing to join.

## Features

- **JABBA Dolly** — moves Forestry, Gendustry and MagicBees bee housings with their bees.
- **Tinkers' mattock** — digs sand and snow at shovel speed, with drops.
- **GregTech tanks and chests** — carrying a filled one applies no debuffs.
- **VeinMiner** — sand, clay and gravel only, 64 blocks per vein.
- **NEI F7 overlay** — lights pitch-dark blocks only (client-side).
- **Twilight Forest** — after the Snow Queen: aurora blocks, Weathered Deadrock and the Final Castle break in 10 ticks, bricks stop draining pickaxes 16x as fast, the thorn wall stays broken, and shears cut arctic fur in 5 ticks for anyone.
- **ServerUtilities** — a team gets its best member's per-rank chunk limit, never the sum (server-side).
- **Enriched fertilizer** — the pack's old recipe still works in the Chemical Reactor and Large Chemical Reactor: 4 fertilizer + 1000 L fertilizer liquid makes 1000 L enriched fertilizer.
- **Fish Trap** — the HarvestCraft trap takes the inventory sorter's move and sort hotkeys. The move hotkeys (space/alt/ctrl-click) work from the server jar alone; sorting the trap's output with middle-click (and the sorter's sort button in that GUI) needs Patchee on the client too. Plain shift-click already works.

## Install

1. Download `patchee-<version>.jar` from [Releases](https://github.com/koreaeatsrice/patchee/releases). Take the plain jar, not `-dev`.
2. Drop it into the server's `mods` folder.
3. Restart the server.

Full steps and the log lines to expect: [docs/INSTALL-SERVER.md](docs/INSTALL-SERVER.md).

## Client (optional)

Players join with nothing. Without the jar a client still sees the tank debuffs and the old Twilight Forest break speed; the F7 overlay only works with the jar installed. Per-mod detail: [docs/CLIENT-MODS.md](docs/CLIENT-MODS.md).

## Config

Each feature has its own switch in `config/patchee.cfg`, plus a master switch. Keys, defaults and caveats: [docs/SETTINGS.md](docs/SETTINGS.md).

## Docs

- [Settings](docs/SETTINGS.md)
- [Server install](docs/INSTALL-SERVER.md)
- [Client mods](docs/CLIENT-MODS.md)
- [Troubleshooting](docs/TROUBLESHOOTING.md)

## Build

```sh
./gradlew build    # -> build/libs/patchee-<version>.jar
```

## License

MIT — see [LICENSE](LICENSE).

Twilight Forest break speeds were re-implemented from its published source; no Twilight Forest code, art or sound is shipped.
