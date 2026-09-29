# Settings — `config/patchee.cfg`

Patchee has **one** settings file. Everything can be turned on or off there.

It appears after the first start, at:

```
<server folder>/config/patchee.cfg
```

## How to change a setting

1. **Stop the server first.**
2. Open the file in Notepad (Windows) or any text editor.
3. Change `true` to `false` (or the other way around). **Only change the values, never the names.**
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
| `extraDollyClasses` (advanced) | Extra block types the Dolly should accept. One internal class name per line. | empty | Another mod adds a block you want to move **and** a dev told you the exact class name. Not sure? Leave it empty. |
| `runSelfTest` (advanced) | Runs a quick check at server start and writes PASS/FAIL per check to the log. Changes nothing in the game. | `false` (off) | You just installed or updated and want to be sure. Turn on, restart, read the log, turn off again. |

## Honest side notes

- **Dolly fix:** a moved apiary keeps the direction it was facing (cosmetic only). Natural world hives and the big multi-block Alveary are deliberately **not** included. Moving a block out of a finished Alveary still breaks it — same as mining it.
- **Mattock fix:** other mods still see the mattock as an axe only — same as the game always did. Nothing else about the tool changes.
- **Supertank fix:** while a filled tank/chest is carried, those four effects are cleared no matter where they came from (the pack's pollution can also use them). And for the *feeling* to disappear (screen icons, slow legs), **you** need the mod too — installing it on your client is optional: [CLIENT-MODS.md](CLIENT-MODS.md).
- All fixes fail soft: if one of the patched mods is missing or changed shape, that fix simply does nothing and writes a note in the log. The server always starts.

## Want the check output explained?

Set `runSelfTest=true` and look for lines like `[SelfTest] PASS …` and finally `[SelfTest] result: N passed, 0 failed`. That is Patchee proving to you that its changes work on **your** server. Then set it back to `false`.