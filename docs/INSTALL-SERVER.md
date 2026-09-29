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
[patchee] Patchee <version> — enabled=true dollyFix=true mattockFix=true superTankFix=true selfTest=false
[DollyFix] Dolly now accepts 3 bee-housing class(es)
[MattockFix] mattock shovel materials 3 -> 6 (+3)
[SuperTankFix] armed — carried GT Super Tanks/Chests will not keep their debuffs
```

- A line that says **`not installed — skipped`** is fine — it just means that mod is not in your pack.
- A line that says **`failed — … left unchanged`** means the other mod changed shape. See [TROUBLESHOOTING.md](TROUBLESHOOTING.md).

**Extra check (optional):** in the settings file set `runSelfTest=true`, restart once and look for:

```
[SelfTest] result: 7 passed, 0 failed
```

Then set it back to `false`. Settings explained: [SETTINGS.md](SETTINGS.md).

## How to undo it

Delete the `patchee` file from `mods` and restart. Your server is back to normal. Nothing else was changed.

## After a pack update

A pack update replaces the `mods` folder, so **Patchee disappears**. Install the newest Patchee again (same five steps). The full update checklist lives in the operations repo (`gtnh-server-ops` → `games/gtnh/UPDATE-PLAYBOOK.md`).