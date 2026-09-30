# Install Patchee on the server

**You only do this once.** It takes about two minutes.

Patchee is a **server-side** mod. Only the server needs the file. Players do nothing.

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
[patchee] Patchee <version> — enabled=true dollyFix=true mattockFix=true superTankFix=true veinConfig=true selfTest=false
[DollyFix] Dolly now accepts 3 bee-housing class(es)
[MattockFix] mattock shovel materials 3 -> 6 (+3)
[SuperTankFix] armed — carried GT Super Tanks/Chests will not keep their debuffs
[VeinConfig] wrote config/veinminer/general.cfg + tools-and-blocks.json (blocks=4, cap=64, radius=20, autodetect=off)
```

- The `[VeinConfig]` line appears only when **VeinMiner** is installed (`not installed — skipped` otherwise — see the optional part below).

- A line that says **`not installed — skipped`** is fine — it just means that mod is not in your pack.
- A line that says **`failed — … left unchanged`** means the other mod changed shape. See [TROUBLESHOOTING.md](TROUBLESHOOTING.md).

**Extra check (optional):** in the settings file set `runSelfTest=true`, restart once and look for:

```
[SelfTest] result: 12 passed, 0 failed
```

12 checks with VeinMiner installed; **7** without it (with no VeinMiner there is nothing to check for it — those checks are skipped, which is normal).

Then set it back to `false`. Settings explained: [SETTINGS.md](SETTINGS.md).

## Optional: vein mining (VeinMiner)

Vein mining on this server is provided by **VeinMiner** — a mod the GTNH pack does not include. If you want it: download the **Minecraft 1.7.10** file (VeinMiner 0.36.0) and put it in the server's `mods` folder next to Patchee.

- Patchee then keeps it to **sand, clay and gravel, 64 blocks max per vein** (see [SETTINGS.md](SETTINGS.md)).
- **Do not edit VeinMiner's own files in `config/veinminer/`** — Patchee rewrites them at every start.
- Players need nothing: without the client mod they type `/veinminer mode sneak` once (again after each server restart), then sneak while mining.

## How to undo it

Delete the `patchee` file from `mods` and restart. Your server is back to normal. Nothing else was changed.

## After a pack update

A pack update replaces the `mods` folder, so **Patchee disappears**. Install the newest Patchee again (same five steps). The full update checklist lives in the operations repo (`gtnh-server-ops` → `games/gtnh/UPDATE-PLAYBOOK.md`).