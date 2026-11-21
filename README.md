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
- **Glass cuts** — Chisel's Transparent Technical Block never cuts, but real glass still does.
- **VeinMiner** — mines sand, clay and gravel only, 64 blocks per vein.
- **NEI F7 overlay** — marks only pitch-dark blocks, on the client.
- **Twilight Forest** — after the Snow Queen, aurora, deadrock and castle blocks break in 10 ticks.
- **ServerUtilities** — a team gets its best member's per-rank chunk limit, never the sum.
- **GregTech pollution visuals** — the server sends real pollution numbers; clients running Patchee hide the smog, fog, particles and plant tints.
- **Enriched fertilizer** — the old Chemical Reactor recipe works again, making 1000 L enriched fertilizer.
- **Fish Trap** — the sorter's move hotkeys work in the trap; plain shift-click already moves bait in and fish out.
- **GregTech pollution settings** — stays on, but the four effect limits are maxed, so no downsides.
- **IC2 and GraviSuite jetpacks** — hover mode rises and sinks at the speed it already flies sideways.

## Install

1. Download `patchee-<version>.jar` from [Releases](https://github.com/koreaeatsrice/patchee/releases). Take the plain jar, not `-dev`.
2. Drop it into the server's `mods` folder.
3. Restart the server.

Full steps and the log lines to expect: [docs/INSTALL-SERVER.md](docs/INSTALL-SERVER.md).

## Client (optional)

Players join with nothing. Without the jar a client still sees the tank debuffs and the old Twilight Forest break speed; the F7 overlay only works with the jar installed. A client without the jar still sees GregTech's pollution smog when pollution is high enough, because the server sends the real number. Per-mod detail: [docs/CLIENT-MODS.md](docs/CLIENT-MODS.md).

## Config

Each feature has its own switch in `config/patchee.cfg`, plus a master switch. Keys, defaults and caveats: [docs/SETTINGS.md](docs/SETTINGS.md).

## Docs

- [Feature detail](docs/FEATURES.md)
- [Settings](docs/SETTINGS.md)
- [Server install](docs/INSTALL-SERVER.md)
- [Client mods](docs/CLIENT-MODS.md)
- [Troubleshooting](docs/TROUBLESHOOTING.md)

Contributing: [AGENTS.md](AGENTS.md) for AI agents, [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md) for people.

## Build

```sh
./gradlew build    # -> build/libs/patchee-<version>.jar
```

## License

MIT — see [LICENSE](LICENSE).

Twilight Forest break speeds were re-implemented from its published source; no Twilight Forest code, art or sound is shipped.
