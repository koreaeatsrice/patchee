# Patchee

A helper mod for GT:NH (1.7.10) that fixes your other mods on the server without editing them, so players join with nothing extra.

## What it changes

- **JABBA Dolly** — moves Forestry, Gendustry and MagicBees bee housings with their bees.
- **Tinkers' mattock** — digs sand and snow at shovel speed, with drops.
- **GregTech tanks and chests** — carrying a filled one applies no debuffs.
- **VeinMiner** — sand, clay and gravel only, 64 blocks per vein.
- **NEI F7 overlay** — lights pitch-dark blocks only. Client-side.
- **Twilight Forest** — kill the Snow Queen and aurora blocks, Weathered Deadrock and the Final Castle break in 10 ticks, bricks no longer wear the pickaxe down 16x as fast, the thorn wall stays broken, and shears cut arctic fur in 5 for anyone.
- **ServerUtilities** — a team gets its best member's per-rank chunk limit, never the sum. Server-side.
- **Enriched fertilizer** — the pack's old recipe still works in the Chemical Reactor and Large Chemical Reactor: 4 fertilizer + 1000 L fertilizer liquid makes 1000 L enriched fertilizer.

Switches live in `config/patchee.cfg`; defaults in [docs/SETTINGS.md](docs/SETTINGS.md).
Install, client notes and troubleshooting: [docs/](docs/).

## Build

```sh
./gradlew build
```

## Licence

MIT. Twilight Forest numbers were re-implemented from its published source; no
TF code, art or sound is shipped.
