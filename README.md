# Patchee

> [!WARNING]
> The majority of this project was written by a Large Language Model (LLM). While prompted, built, and tested for reliability, please take note of this before downloading and using this mod.

> [!NOTE]
> Constructive criticism is desired and greatly appreciated. I make no claims to be a bona fide Software Engineer nor pretend that this project makes me one.

A small add-on for my GTNH server that fixes a few things I personally find annoying.

## Do I need to install anything?

| I am… | Then… |
|---|---|
| Playing on a server running this | Nothing required to join. Patchee runs on the server; a client copy is optional (see "What you need" below) |
| Running the server | See [Install on the server](docs/INSTALL-SERVER.md) |
| Looking for optional client mods | See [Optional client mods](docs/CLIENT-MODS.md) |
| Building or changing the code | See [DEVELOPER.md](DEVELOPER.md) |

## What it fixes

Patchee fixes five things. Each has its own on/off switch, and all switches are in one settings file — see [docs/SETTINGS.md](docs/SETTINGS.md).

1. **Bee houses and the JABBA Dolly.** The Dolly would not pick up Forestry apiaries or bee houses, Gendustry industrial apiaries, or MagicBees magic apiaries. It now picks them up, with the bees inside.
2. **Tinkers' Construct mattock.** Sand and snow were dug slowly, and often dropped nothing. The mattock now treats them like a shovel.
3. **GregTech Super Tanks and Super Chests.** Carrying a filled one applies hunger, slowness, mining fatigue and weakness. Those effects no longer stick to the player carrying it.
4. **VeinMiner.** Not part of the pack; it is installed separately on the server. It allows mining a whole vein of the same block with one swing. Patchee limits it to sand (both variants), clay and gravel, with a maximum of 64 blocks per vein. If VeinMiner is not installed, this does nothing.
5. **NEI's F7 light overlay.** The overlay marks every block where monsters can spawn. Its light level is fixed, so it marks far more than the server actually allows. On a client that has Patchee installed, the marks now follow the server's rule: only pitch-dark blocks outside the Nether, and NEI's normal level in the Nether. Both levels are settings.

## What you need

- The GTNH pack for Minecraft 1.7.10.
- Patchee modifies mods the pack already has (JABBA, Tinkers' Construct, GregTech), plus VeinMiner if it is installed. If one of them is missing, that part of Patchee does nothing; the server still starts.
- The server must have the mod. Clients do not need it to join.
- The F7 overlay tweak is the one part that only works on the **client**: it changes nothing on a player who does not have Patchee installed. Everything else runs on the server.
- A client without it is out of step with the server wherever Patchee changes something: sand and snow break faster than that client expects, and a filled Super Tank still shows its effects on that client while the server does not apply them. Install the same jar on a client to have the changes there too.

## Install

1. On the [Releases page](https://github.com/koreaeatsrice/patchee/releases), open the newest release and download `patchee-<version>.jar` from the **Assets** list. Do not use files with `-dev` in the name; those are for development.
2. Put the file in the server's `mods` folder. Do not unzip it.
3. Restart the server normally — not "reload".

Full steps and what to look for in the log: [docs/INSTALL-SERVER.md](docs/INSTALL-SERVER.md).

## Word list

- **jar** — a mod file. Do not unzip it; put it in the folder.
- **mods folder** — the folder called `mods` inside the server folder.
- **settings file** — `config/patchee.cfg`, the text file with the on/off switches.
- **log** — `logs/latest.log`, the text file where the game writes what it is doing.

## If something is wrong

See [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) first. If that does not solve it, send: the Patchee jar name, your GTNH version, and the log lines around the word `patchee`.

## Details

<details>
<summary><b>Which bee houses are included?</b></summary>

| Mod | Block | Internal class |
|---|---|---|
| Forestry | Apiary | `forestry.apiculture.tiles.TileApiary` |
| Forestry | Bee House | `forestry.apiculture.tiles.TileBeehouse` |
| Gendustry | Industrial Apiary | `net.bdew.gendustry.machines.apiary.TileApiary` |
| MagicBees | Magic Apiary | `magicbees.tileentity.TileEntityMagicApiary` |

A moved apiary keeps the direction it was facing (cosmetic only). Natural world hives and the multi-block Alveary are not included — the Alveary is a large structure, and moving it one block at a time breaks it, the same as mining it. Jabba's 1 MB pickup size limit still applies.
</details>

<details>
<summary><b>What changes about the mattock?</b></summary>

The mattock's shovel list is missing sand and snow, so Patchee adds them (sand, snow layers, snow blocks). Existing mattocks are fixed by themselves. Other mods still see the mattock as an axe only — the same as always. The only other place inside Tinkers' Construct that reads the list is the "Omni" active modifier, which gains the same behaviour.
</details>

<details>
<summary><b>What changes about the tanks?</b></summary>

GregTech applies the four effects to whoever carries a filled Super Tank or Super Chest. It is hardcoded and has no off switch. Patchee clears the four effects once per player tick while such an item is carried — on the server, and on the client if the player has Patchee installed too. A client without Patchee keeps showing the effects (and the slow movement) while the server ignores them. While a filled tank or chest is carried, those four effects are cleared whatever their source; the pack's pollution can also apply them.
</details>

<details>
<summary><b>What changes about VeinMiner?</b></summary>

VeinMiner decides what it may chain-mine from its own two settings files. Patchee rewrites them at every server start, before VeinMiner reads them, so only sand (both data values), clay and gravel can be vein-mined, up to 64 blocks per vein. Block auto-detection and the "all blocks" / "all tools" overrides are forced off. VeinMiner stays a separate mod: Patchee does not bundle or edit it, and this does nothing when VeinMiner is not installed.
</details>

<details>
<summary><b>What changes about NEI's F7 overlay?</b></summary>

Not Enough Items can draw a red or yellow X on every block where a monster could spawn (the F7 key). It decides this from a light level that is written into the mod itself and cannot be changed from the server. Patchee changes it **on your client only**, and only if you have Patchee installed: outside the Nether an X is drawn only at light level 0 (pitch dark), and in the Nether at light level 7 — NEI's own level. Both numbers are settings (`lightOverlay.maxLightNormal` and `lightOverlay.maxLightNether`). Turn the feature off and the overlay is exactly as NEI ships it. The server is not involved and does not need NEI.
</details>

<details>
<summary><b>How does it work without editing any jars?</b></summary>

Patchee changes the other mods' live data at server start through reflection — the Dolly's movable-block list (JABBA), the mattock's material list (Tinkers' Construct) — rewrites VeinMiner's own settings files before the game reads them, and watches the player's inventory for the tank effects. No other mod's jar is modified, so a pack update does not silently undo the changes. If a targeted mod is missing, that part does nothing and writes a note to the log; if one has changed shape, Patchee writes an error and leaves it alone. The server starts either way.
</details>

## Licence

MIT — see [LICENSE](LICENSE). Built for the Joint Space Force server.
