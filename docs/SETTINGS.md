# Settings — `config/patchee.cfg`

Patchee has **one** settings file. Everything can be turned on or off there.

It appears after the first start, at:

```
<server folder>/config/patchee.cfg
```

## How to change a setting

1. **Stop the server first.**
2. Open the file in Notepad (Windows) or any text editor.
3. Change the value — usually `true` to `false` (or the other way around); some settings are numbers or lists. **Only change the values, never the names.**
4. Save the file.
5. **Start the server again.** (Changes are read at startup.)

Everything after a `#` on a line is a note for humans — the game ignores it.

## The switches

| Setting (name in the file) | What it does | Default | Change it when… |
|---|---|---|---|
| `enabled` | **Master switch for the whole mod.** `false` = Patchee does nothing at all. | `true` (on) | You want to turn everything off at once while keeping the file. |
| `enableDollyFix` | Lets the JABBA Dolly pick up bee houses (Forestry apiary + bee house, Gendustry industrial apiary, MagicBees magic apiary), bees inside. | `true` (on) | You do not want players to move bee houses. |
| `enableMattockFix` | The Tinkers' Construct mattock digs sand and snow like a shovel. | `true` (on) | You want the old slow digging back. |
| `enableSuperTankFix` | No hunger / slow legs / slow mining / weakness from carrying a **filled** GregTech Super Tank or Super Chest. | `true` (on) | You want the vanilla GregTech behaviour back. |
| `enableVeinConfig` | Keeps **VeinMiner** under server control: rewrites its settings at every server start so only sand (both kinds), clay and gravel can be vein-mined, 64 blocks max. | `true` (on) | You want VeinMiner's own settings left alone. |
| `enableLightOverlayTweak` | **Client-side.** On a client that has Patchee installed, NEI's F7 light overlay marks blocks using the server's rule instead of NEI's fixed one. | `true` (on) | You want NEI's F7 overlay exactly as it ships. |
| `lightOverlay.maxLightNormal` | **Client-side.** The light level the F7 overlay marks at, in every dimension except the Nether. `0` = only pitch-dark blocks. | `0` | You want it stricter or looser outside the Nether. |
| `lightOverlay.maxLightNether` | **Client-side.** The light level the F7 overlay marks at in the Nether. | `7` | You want it different in the Nether. `7` is NEI's own value. |
| `veinConfig.blockLimit` | The most blocks **one vein** may have. | `64` | You want shorter or longer veins. |
| `veinConfig.radius` | How far from the first block VeinMiner looks for more of the same block. | `20` | You want a tighter or wider search. |
| `veinConfig.blocks` | The blocks that may be vein-mined, separated by commas. Format: `mod:block` or `mod:block/data` (data = exact variant; no data = all variants). | `minecraft:sand/0,minecraft:sand/1,minecraft:clay,minecraft:gravel` | You want to allow or remove blocks. |
| `veinConfig.extraTools` (advanced) | Extra item names treated as shovels for vein mining, separated by commas. | empty | Another mod adds a shovel-like tool you want to work. |
| `extraDollyClasses` (advanced) | Extra block types the Dolly should accept. One internal class name per line. | empty | Another mod adds a block you want to move **and** a dev told you the exact class name. Not sure? Leave it empty. |
| `runSelfTest` (advanced) | Runs a quick check at server start and writes PASS/FAIL per check to the log. Changes nothing in the game. | `false` (off) | You just installed or updated and want to be sure. Turn on, restart, read the log, turn off again. |

## Honest side notes

- **Dolly fix:** a moved apiary keeps the direction it was facing (cosmetic only). Natural world hives and the big multi-block Alveary are deliberately **not** included. Moving a block out of a finished Alveary still breaks it — same as mining it.
- **Mattock fix:** other mods still see the mattock as an axe only — same as the game always did. Nothing else about the tool changes.
- **Supertank fix:** while a filled tank/chest is carried, those four effects are cleared no matter where they came from (the pack's pollution can also use them). A client without Patchee still shows the effects and the slow movement (out of step with the server); installing the same jar on your client clears them there too — optional: [CLIENT-MODS.md](CLIENT-MODS.md).
- **VeinMiner fix:** it needs **VeinMiner installed on the server** — the pack does not include it ([INSTALL-SERVER.md](INSTALL-SERVER.md) has the optional part). **Never edit the files in `config/veinminer/` yourself** — Patchee rewrites them at every start. Players without the VeinMiner client mod can still vein-mine: type `/veinminer mode sneak` once (again after a server restart), then sneak while mining.
- **F7 overlay tweak:** this one is **client-side only**. It changes what the F7 overlay marks on a client that has Patchee installed; a player without Patchee sees NEI's normal overlay. The server does not need NEI. The numbers are the light level the X appears **at** — so `0` means only pitch-dark blocks outside the Nether.
- All fixes fail soft: if one of the patched mods is missing or changed shape, that fix simply does nothing and writes a note in the log. The server always starts.

## Want the check output explained?

Set `runSelfTest=true` and look for lines like `[SelfTest] PASS …` and finally `[SelfTest] result: N passed, 0 failed`. That is Patchee proving to you that its changes work on **your** server. Then set it back to `false`.