# Patchee

Server-side GT:NH (1.7.10) addon that fixes other mods at runtime. No jar edits;
clients need nothing to join.

## What it changes

- **JABBA Dolly** — moves Forestry, Gendustry and MagicBees bee housings with their bees.
- **Tinkers' mattock** — digs sand and snow at shovel speed, with drops.
- **GregTech tanks and chests** — carrying a filled one applies no debuffs.
- **VeinMiner** — sand, clay and gravel only, 64 blocks per vein.
- **NEI F7 overlay** — lights pitch-dark blocks only. Client-side.
- **Twilight Forest** — a player who has killed the Snow Queen (the Glacier achievement) breaks aurora blocks and Weathered Deadrock at the castle's speed (10 ticks), and the Final Castle's bricks, magic blocks and thorn wall in 10 ticks; the Tinkers' Construct hammer's 3x3 area-mine works on the aurora blocks too. A player without it breaks them at normal speed. Shears cut arctic fur in 5 ticks for anyone. Bricks no longer drain the pickaxe 16x and the thorn wall stays broken for a player who has the achievement.
- **ServerUtilities** — a team gets its best member's per-rank chunk limit, never the sum. Server-side.
- **Enriched fertilizer recipe** — the old recipe is kept alongside the pack's newer one: 4 fertilizer + 1000 L fertilizer liquid makes 1000 L enriched fertilizer. It works in the Chemical Reactor and the Large Chemical Reactor. On a client that has Patchee the NEI recipe cache is dropped, so the recipe also shows up in NEI; a client without Patchee does not see it.

Switches live in `config/patchee.cfg`; defaults in [docs/SETTINGS.md](docs/SETTINGS.md).
Install, client notes and troubleshooting: [docs/](docs/).

## Build

```sh
./gradlew build
```

## Licence

MIT. Twilight Forest numbers were re-implemented from its published source; no
TF code, art or sound is shipped.
