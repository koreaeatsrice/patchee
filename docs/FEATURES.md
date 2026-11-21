# Features, in detail

The README shows one short line per feature. This page keeps the detail that used to live in those lines. Every switch below is in `config/patchee.cfg`; the full setting descriptions are in [SETTINGS.md](SETTINGS.md).

## JABBA Dolly

The JABBA Dolly picks up Forestry apiaries and bee houses, Gendustry industrial apiaries and MagicBees magic apiaries, with the bees still inside. Switch: `enableDollyFix`.

## Tinkers' mattock

The Tinkers' Construct mattock digs sand and snow at shovel speed, and the drops come with it. Switch: `enableMattockFix`. The speed half is measured by the client, so a stock client keeps the old speed.

## GregTech tanks and chests

Carrying a filled GregTech Super Tank or Super Chest no longer gives hunger, slow legs, slow mining or weakness. An empty one never did. Switch: `enableSuperTankFix`.

## Glass cuts

Chisel's Transparent Technical Block (the metal-mesh `technical2` block) is metal, not glass, so breaking it no longer triggers Blood Arsenal's Bleeding and Blindness. Real glass (vanilla, Chisel glass, stained glass, EFR tinted glass) still cuts a player wearing no armour, and armour protects; the threshold is `glassBreak.minArmourPieces`. Server-side only: clients need nothing. Switch: `enableGlassBreakFix`.

## VeinMiner

Patchee rewrites VeinMiner's settings at every server start, so only sand (both kinds), clay and gravel can be vein-mined, 64 blocks at most. The client-side hotkey is optional: without it, type `/veinminer mode sneak` once after each server start and sneak while mining. Switch: `enableVeinConfig`.

## NEI F7 overlay

Client-side only. On a client that has Patchee, NEI's F7 overlay marks only pitch-dark blocks instead of NEI's fixed light levels. Without the jar, F7 marks more blocks than can really spawn. Switch: `enableLightOverlayTweak`.

## Twilight Forest

After a player kills the Snow Queen (the Glacier progression), checked per player on every break:

- Aurora blocks (block, pillar, slab, double slab), Weathered Deadrock (metas 0-2) and the Final Castle set break in at most 10 ticks (a floor of 0.1 per tick, never a slowdown).
- Castle bricks stop draining pickaxes 16x as fast, and the thorn wall stays broken instead of growing back.
- Shears cut arctic fur in 5 ticks, for anyone, progression or not.
- The same 10-tick rate applies inside Forge Multipart microblocks, ArchitectureCraft shapes, Carpenter's Blocks covers and LittleTiles tiles. ArchitectureCraft shapes also area-mine with the Tinkers' hammer; the other three keep their own tool.

The faster break is only seen on a client that has Patchee; the server half is what keeps the two in step. Switches: `enableAuroraBreak`, `enableFinalCastleBreak`, `enableArcticFurBreak`.

## ServerUtilities

Server-side only. A team gets its best member's own per-rank chunk limit, never the sum: four ordinary players get one player's 1500 / 500 instead of 6000 / 2000. A member whose rank carries higher numbers (an admin) keeps them, and a player on their own is not affected. Switch: `enableTeamChunkCap`.

## GregTech pollution visuals

The server always sends every client GregTech's real pollution number. Patchee never changes that packet, in any setting.

Hiding the visuals is a job for the client. On a client that runs Patchee, the default-on switch `enablePollutionNoVisuals` turns GregTech's own `Pollution Fog` and `Pollution Dirt` render flags off in memory every tick. When `config/GregTech/Client.cfg` already exists, it also sets those two lines to `false` there, with a timestamped `.bak`, and touches only those two lines. The keys are `Pollution Fog` and `Pollution Dirt`; the writer keeps whichever spelling the line already has — `B:"Pollution Fog"` / `B:"Pollution Dirt"` on newer builds, or the older `B:renderPollutionFog` / `B:renderDirtParticles` — and does not rename anything.

It also keeps GregTech's five block-tint tables empty: standard blocks, liquids, double plants, crossed squares and vines. So grass, leaf, water and flower tints are not drawn. GregTech can rebuild its tint tables when a world loads, so the guard checks them on every client tick and empties them again.

The fog colour shift has no flag of its own: GregTech tints the fog from a per-tick pollution value, that value is only refreshed while the dirt particles run, and so the `Pollution Dirt` flag being off is what keeps the colour shift away too.

A dedicated server does nothing here and logs `[PollutionVisuals] dedicated server - no client renderer, skipped`.

The one honest caveat: a client without Patchee still draws GregTech's own smog when its pollution is high enough, because the real number really is sent to it. These flags only help players who install Patchee. With `enablePollutionNoVisuals=false`, the plant and water tints return on that client, but fog and particles stay off while the separate default-on `enablePollutionNoNegatives` is on.

Pollution itself keeps running in full on the server: production, accumulation, decay, chunk storage, damage, sour rain, vegetation, scanners, the pollution detector and every machine reading it keep their exact numbers. It does not edit `Pollution.cfg` and does not touch the world. Switch: `enablePollutionNoVisuals` (default `true`).

## Enriched fertilizer

4 fertilizer + 1000 L fertilizer liquid makes 1000 L enriched fertilizer, in a Chemical Reactor or Large Chemical Reactor (LV, 1 second). The pack's own recipe is untouched. A server-only install still crafts it; a client that has Patchee also drops the NEI recipe cache so the recipe shows up in NEI. Switch: `enableEnrichedFertilizerRecipe`.

## Fish Trap

The HarvestCraft Fish Trap works with InventoryBogoSorter: space+click moves all, alt+click moves the same item, ctrl+click moves one, and middle-click sorts the 18 output slots. Bait from your inventory goes into the trap's bait slot. The move hotkeys work from the server jar alone; middle-click sorting and the sorter's sort button in that GUI need Patchee on the client too. Plain shift-click already works. Switch: `enableFishTrapMoves`.

## GregTech pollution settings

GregTech pollution stays on, but nothing comes of it. Patchee rewrites `config/GregTech/Pollution.cfg` with `Activate Pollution=true` and the four effect limits at 2147483647, so pollution still builds up and fades but no effect threshold is ever reached. Only those five lines change, and the original is kept next to it as a timestamped `.bak`. GregTech reads that file in its own coremod before Patchee can run, so the file change takes effect on the next boot; the same five values are forced in memory after GregTech loads, which is what fixes the current boot, so a read-only file or a hand edit cannot bring the effects back. On a client it also turns the pollution fog and dirt particle flags off, in memory and in `config/GregTech/Client.cfg` once that file exists, but it does not empty the block-tint tables: plant, grass and water tints need `enablePollutionNoVisuals`. Switch: `enablePollutionNoNegatives`.

## IC2 and GraviSuite jetpacks

In hover mode both jetpacks rise and sink at the speed they already fly sideways: 0.089 blocks a tick on IC2, 0.1156 on GraviSuite. Up matches down, and both match sideways. On IC2, hover with no key held still sinks gently at 0.025 (or 0.0 when Hodgepodge's hover fix is on). GraviSuite keeps its own fall speed with no key held. Sneak plus jump holds your height, and standing on the ground with hover on is not counted as flying. Both mods send key state to the server, so both sides apply it; the same jar everywhere keeps them in step. Switches: `enableChestplateHoverLift`, `enableChestplateGroundGuard`, `enableChestplateHoldAltitude`.
