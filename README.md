# Patchee

> [!NOTE]
> **Disclaimer:** The majority of this project was written by a Large Language Model (LLM). While prompted, built, and tested for reliability, please take note of this before downloading and using this mod. Constructive criticism is desired and greatly appreciated. I make no claims to be a bona fide Software Engineer nor pretend that this project makes me one.

**A small add-on for our GTNH server that fixes a few annoying things — and never takes anyone else's mod apart to do it.**

## Do I need to install anything?

| I am… | Then… |
|---|---|
| Just playing on the server | **Nothing to do.** Patchee runs on the server; players need zero files. |
| Running the server | Go to **[Install on the server](docs/INSTALL-SERVER.md)** — takes about two minutes. |
| Curious about optional client mods | Go to **[Optional client mods](docs/CLIENT-MODS.md)**. |
| Building or changing the code | Go to **[DEVELOPER.md](DEVELOPER.md)**. |

## What it fixes, in plain words

1. **Moving bee houses.** You could not move an apiary with a JABBA Dolly — it refused. Now the Dolly picks up Forestry apiaries, Forestry bee houses, Gendustry industrial apiaries and MagicBees magic apiaries — **bees still inside**.
2. **Digging sand and snow with a Mattock.** The Tinkers' Construct mattock dug sand and snow slowly, and often dropped nothing. Now it digs them like a shovel.
3. **Big GregTech tanks that make you sick.** Carrying a filled Super Tank or Super Chest gave you hunger, slow legs, slow mining and weakness. Those effects now never stick.

Every fix can be turned off, and all switches live in **one settings file** — see **[docs/SETTINGS.md](docs/SETTINGS.md)**.

## What you need

- The GTNH pack on Minecraft 1.7.10. Nothing else.
- Patchee changes mods the pack already has (JABBA, Tinkers' Construct, GregTech). If one of them is not installed, that one fix simply does nothing — the server still starts fine.
- **Only the server needs the mod. Players need nothing**, and everything works for normal clients.

## Install in three steps

1. Open the **[Releases page](https://github.com/koreaeatsrice/patchee/releases)**, click the newest release, and under **Assets** download **`patchee-<version>.jar`**. Do **not** pick a file with `-dev` in the name — that one is for programmers only.
2. Put the file into the server's **`mods`** folder (do not unzip it).
3. **Restart the server the normal way** — not "reload".

Full steps, and what to look for in the log: **[docs/INSTALL-SERVER.md](docs/INSTALL-SERVER.md)**.

## Word list

- **jar** — a mod file. Do not unzip it; just drop it in the folder.
- **mods folder** — the folder where mod files go, called `mods` inside the server folder.
- **settings file** — a small text file with on/off switches: `config/patchee.cfg`.
- **log** — the text file where the game writes what it is doing: `logs/latest.log`.

## Something is wrong?

Look here first: **[docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md)** — find what you see, do what it says.
Still stuck? Send: your Patchee version (the jar name), your GTNH version, and the log lines around the word `patchee`.

## The details (for the curious)

<details>
<summary><b>Which bee houses exactly? (click)</b></summary>

| Mod | Block | Internal class |
|---|---|---|
| Forestry | Apiary | `forestry.apiculture.tiles.TileApiary` |
| Forestry | Bee House | `forestry.apiculture.tiles.TileBeehouse` |
| Gendustry | Industrial Apiary | `net.bdew.gendustry.machines.apiary.TileApiary` |
| MagicBees | Magic Apiary | `magicbees.tileentity.TileEntityMagicApiary` |

A moved apiary keeps the direction it was facing (cosmetic only). Natural world
hives and the big multi-block Alveary are deliberately **not** included — the
Alveary is a giant structure, and moving it one block at a time breaks it just
like mining it does. Jabba's own 1 MB size limit for a pickup still applies.
</details>

<details>
<summary><b>What changes about the mattock? (click)</b></summary>

The mattock's "shovel list" is missing sand and snow, so Patchee adds them
(`sand`, snow layers, snow blocks). Existing mattocks are fixed by themselves.
Other mods still see the mattock as an axe only — exactly like the game always
did. The only other place inside Tinkers' Construct that reads the list is the
"Omni" active modifier, which gains the same behaviour.
</details>

<details>
<summary><b>What changes about the tanks? (click)</b></summary>

GregTech's code gives the four effects to whoever **carries** a filled Super
Tank / Super Chest — it is hardcoded and has no off switch. Patchee clears the
four effects once per player tick while such an item is carried, on both the
server and the client (if the player has the mod too). While a filled tank is
carried, those four effects are cleared no matter where they came from — the
pack's pollution can also use them.
</details>

<details>
<summary><b>How does it work without editing any jars? (click)</b></summary>

Patchee changes the other mods' live data at server start through reflection —
a list of movable blocks (JABBA) and a list of materials (Tinkers' Construct) —
and watches the player's inventory for the tank fix. Nothing is ever patched
into anyone else's jar, so a pack update can't silently undo the fixes. If a
patched mod is missing, the fix skips itself with a note; if one changed shape,
the fix logs an error and leaves it alone. The server always starts.
</details>

## Licence

MIT — see [LICENSE](LICENSE). Built for the Joint Space Force server.