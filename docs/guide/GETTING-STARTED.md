# Install Patchee on the server

For a server owner installing Patchee for the first time. It covers the jar, the mods folder and the first boot.

**You only do this once.** It takes about two minutes.

Patchee runs on the server. The server needs the file; players do not need anything to join. Players who want the changes on their own game — and their client in step with the server — can install the same jar: [CLIENT.md](CLIENT.md).

---

## Step 1 — Get the file

1. Open the **[Releases page](https://github.com/koreaeatsrice/patchee/releases)**.
2. Click the **newest release** (top of the list).
3. Under **Assets**, click **`patchee-<version>.jar`** — for example `patchee-1.0.0.jar`.

> **Careful:** do **not** pick a file whose name contains **`-dev`**. That one is for programmers and can behave differently.

## Step 2 — Find your `mods` folder

The server folder contains a folder called **`mods`**.

- On a normal computer: it is next to `server.properties`, for example `C:\GTNH\mods` or `/home/gtnh/mods`.
- On a server panel (hosting website): open **File Manager**, then open the **`mods`** folder.

If there is no `mods` folder, create one with that exact name.

## Step 3 — Put the file in

Drag or upload the `.jar` file into the `mods` folder.

- **Do not unzip it.** A `.jar` already is the finished mod file. ([more info](TROUBLESHOOTING.md))
- If you see two files named `patchee-…` in there, delete the older one.

## Step 4 — Restart the server

Stop the server, then start it again **the normal way**.

> **Do not use "reload"** — Patchee loads at server start, so a reload will not pick it up.

## Step 5 — Check it worked

Open the log file (`logs/latest.log`) and search for the word **`patchee`**. You should see lines like:

```
[patchee] Patchee <version> — enabled=true dollyFix=true mattockFix=true superTankFix=true glassBreak=true veinConfig=true pollutionNoNegatives=true lightOverlay=true auroraBreak=true arcticFurBreak=true teamChunkCap=true pollutionVisuals=true finalCastleBreak=true enrichedFertilizerRecipe=true fishTrapMoves=true chestplateHoverLift=true chestplateHoldAltitude=true chestplateGroundGuard=true selfTest=false
[DollyFix] Dolly now accepts 3 bee-housing class(es)
[MattockFix] mattock shovel materials 3 -> 6 (+3)
[SuperTankFix] armed — carried GT Super Tanks/Chests will not keep their debuffs
[VeinConfig] wrote config/veinminer/general.cfg + tools-and-blocks.json (blocks=4, cap=64, radius=20, autodetect=off)
[TeamChunkCap] patched serverutils.data.ServerUtilitiesTeamData — team limits will be read from the members' per-rank settings (4 return sites; …)
[TeamChunkCap] armed — a team's limit is its best member's own per-rank limit (member limits are never added up …; switches from config/patchee.cfg)
[EnrichedFertilizerRecipe] armed - old recipe added: 4 fertilizer + 1000 mB fertilizer -> 1000 mB enriched fertilizer (Chemical Reactor and LCR, LV, 20 ticks) (server)
[FishTrapMoves] armed - Fish Trap is sortable (outputs 0-17, 6 per row); move hotkeys accept bait into slot 18
[GlassBreak] armed - a player wearing 1 or more armour piece(s) is not cut by glass, and Chisel's Transparent Technical Block never cuts; bleeding potion id 121
```

The pollution-visuals step is client-only. On a dedicated server it does nothing and logs `[PollutionVisuals] dedicated server - no client renderer, skipped`.

If the server's or a client's existing `config/patchee.cfg` already says `enablePollutionNoVisuals=false`, delete that line (or set it to `true`) to get the new default.

- The `[FishTrapMoves]` line appears only when **Pam's HarvestCraft + InventoryBogoSorter** are installed. Without HarvestCraft the gate logs `[FishTrapMoves] Pam's HarvestCraft is not installed - Fish Trap moves skipped`; with HarvestCraft but no InventoryBogoSorter the feature logs `[FishTrapMoves] InventoryBogoSorter is not installed - skipped`. It runs on both sides; the move hotkeys (space/alt/ctrl-click) work from the server jar alone, while sorting the trap's output with middle-click (and the sorter's sort button in that GUI) needs Patchee on the client too.
- The `[VeinConfig]` line appears only when **VeinMiner** is installed (`[VeinConfig] VeinMiner not installed — skipped` otherwise — see the optional part below).
- The two `[TeamChunkCap]` lines appear only when **ServerUtilities** is installed (`ServerUtilities is not installed — team chunk limits skipped` otherwise).
- The pollution visuals are hidden on **players' clients that install Patchee**, never on the server. A dedicated server logs only `[PollutionVisuals] dedicated server - no client renderer, skipped`. The server always sends the real pollution number, so a client without Patchee still sees GregTech's smog.
- The `[EnrichedFertilizerRecipe]` line appears only when **CropsNH + GregTech** are installed (`[EnrichedFertilizerRecipe] GregTech is not installed - old recipe skipped` or `[EnrichedFertilizerRecipe] CropsNH is not installed - old recipe skipped` otherwise). It ends with the side it ran on. On the **server** the recipe just works in the machine. On a **client that has Patchee** the line reads `(client)` and Patchee also asks GregTech to drop NEI's cached recipe pages, so the old recipe **shows up in NEI** too; a client without Patchee does not see it.
- The `[GlassBreak]` line appears only when **Blood Arsenal** is installed (`[GlassBreak] Blood Arsenal is not installed - skipped` otherwise). It runs on the server only — clients need nothing.

- A line that quotes a mod as not installed — for example **`[VeinConfig] VeinMiner not installed — skipped`** — is fine — it just means that mod is not in your pack.
- A line that says **`failed — … left unchanged`** means the other mod changed shape. See [TROUBLESHOOTING.md](TROUBLESHOOTING.md).

**Extra check (optional):** in the settings file set `runSelfTest=true`, restart once and look for:

```
[SelfTest] result: 27 passed, 0 failed
```

The exact total depends on which mods are installed. On this pack it is **27** with VeinMiner and **22** without it (with no VeinMiner there is nothing to check for it — those checks are skipped, which is normal). Seven of the checks are the Fish Trap: 3 need HarvestCraft, and 4 more need InventoryBogoSorter. Four check the ServerUtilities team cap and are skipped (with a note) when ServerUtilities is not installed. A missing mod is a **SKIP**, never a FAIL. One of them is the glass-cut fix, which is skipped when Blood Arsenal is not installed.

Then set it back to `false`. Settings explained: [SETTINGS.md](../SETTINGS.md).

## Optional: vein mining (VeinMiner)

Vein mining on this server is provided by **VeinMiner** — a mod the GTNH pack does not include. If you want it: download the **Minecraft 1.7.10** file (VeinMiner 0.36.0) and put it in the server's `mods` folder next to Patchee.

- Patchee then keeps it to **sand, clay and gravel, 64 blocks max per vein** (see [SETTINGS.md](../SETTINGS.md)).
- **Do not edit VeinMiner's own files in `config/veinminer/`** — Patchee rewrites them at every start.
- Players need nothing extra for this: without the client mod they type `/veinminer mode sneak` once (again after each server restart), then sneak while mining. The client copy only adds the hotkey and menu.

## How to undo it

Delete the `patchee` file from `mods` and restart. Your server is back to normal. Nothing else was changed.

## After a pack update

A pack update replaces the `mods` folder, so **Patchee disappears**. Install the newest Patchee again (same five steps). The full update checklist lives in the operations repo (`gtnh-server-ops` → `games/gtnh/UPDATE-PLAYBOOK.md`).