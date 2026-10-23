# Patchee

Server-side GT:NH (1.7.10) addon that fixes other mods at runtime. No jar edits;
clients need nothing to join.

## What it changes

- **JABBA Dolly** — moves Forestry, Gendustry and MagicBees bee housings with their bees.
- **Tinkers' mattock** — digs sand and snow at shovel speed, with drops.
- **GregTech tanks and chests** — carrying a filled one applies no debuffs.
- **VeinMiner** — sand, clay and gravel only, 64 blocks per vein.
- **NEI F7 overlay** — lights pitch-dark blocks only. Client-side.
- **Twilight Forest** — after the Snow Queen, aurora blocks and the Final Castle's bricks, magic blocks and thorn wall break in 10 ticks, and shears cut arctic fur in 5. Bricks no longer drain the pickaxe 16x and the thorn wall stays broken.
- **ServerUtilities** — a team gets its best member's per-rank chunk limit, never the sum. Server-side.

Switches live in `config/patchee.cfg`; defaults in [docs/SETTINGS.md](docs/SETTINGS.md).
Install, client notes and troubleshooting: [docs/](docs/).

## Build

```sh
./gradlew build
```

## Licence

MIT. Twilight Forest numbers were re-implemented from its published source; no
TF code, art or sound is shipped.
