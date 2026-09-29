# Troubleshooting

Find what you see in the left column. Do what the right column says.

| What you see | What it means | What to do |
|---|---|---|
| Patchee is not in the in-game mod list | The game did not load the file. | 1) The jar is in the **server's** `mods` folder, not your own. 2) Its name has no `-dev`. 3) It is a `.jar` file, not inside a `.zip`. |
| The log says nothing about `patchee` at all | Same as above. | Check the file placement, then restart the server **fully** (not "reload"). |
| Log: `[DollyFix] … not installed — skipped` | Your pack does not have that mod. | Nothing. This is normal on smaller packs. |
| Log: `[MattockFix] … not installed — skipped` | Tinkers' Construct is not in the pack. | Nothing. |
| Log: `… failed — … left unchanged` | The other mod changed shape. Patchee stepped aside so the server keeps working. | Update Patchee to the newest release. Still failing? Send the log (below). |
| The mattock still digs sand slowly | The mattock fix is off — or Patchee is old. | Check `enableMattockFix=true` in `config/patchee.cfg`. Existing mattocks are fixed by themselves; no need to craft a new one. |
| The Dolly still refuses an apiary | The dolly fix is off — or it is not a supported block. | Check `enableDollyFix=true`. Only the small bee houses work; natural hives and the big Alveary are on purpose not included. |
| Carrying a big tank still makes me sick | You are on a normal client, so your screen still shows it — or the fix is off. | Check `enableSuperTankFix=true`. For the feeling to disappear too, install the same jar on your client: [CLIENT-MODS.md](CLIENT-MODS.md). |
| Two `patchee-…` files in `mods` | It got installed twice. | Delete the older one. Keep only the newest. |
| The server will not start after installing | Wrong file or wrong pack version. | Delete the `patchee` jar and start again — the server will come back. Then tell an admin what happened. |
| It worked, then a pack update broke it | A pack update replaces the `mods` folder. | Install the newest Patchee again. Still broken? Send the log. |

## How to ask for help

We want to help — send **these four things**. Copy them, do not retype:

1. Your **Patchee version** — the jar name in `mods`, e.g. `patchee-1.0.0.jar`.
2. Your **GTNH version** — shown on the game's main menu.
3. Your **log**: the file `logs/latest.log`. Search it for the word `patchee`, copy about 20 lines around it.
4. **What you did just before it broke.**

Send it to the [Issues page](https://github.com/koreaeatsrice/patchee/issues) — or hand it to an admin.
**Copy the log text; do not send a screenshot.**