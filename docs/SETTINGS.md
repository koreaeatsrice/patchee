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
| `enableLightOverlayTweak` | **Client-side.** On a client that has Patchee installed, NEI's F7 light overlay marks only pitch-dark blocks instead of NEI's fixed levels. | `true` (on) | You want NEI's F7 overlay exactly as it ships. |
| `enableAuroraBreak` | The aurora blocks (block, pillar, slab and double slab) break in at most 2 ticks (a floor of 0.5 per tick) for a player who has killed the Snow Queen (earned the Glacier progression). The gate is read **per player on every break**, so a player without it breaks them at the plain 1.7.10 speed. The faster break is only **seen** on a client that has Patchee installed; the server half is what keeps the two in step (it stops the server refusing the faster break). A floor, never a slowdown. | `true` (on) | You want the plain 1.7.10 speed back. |
| `enableArcticFurBreak` | The arctic fur block gets the modern speed while shears are held: it breaks in at most 5 ticks. The faster break is only **seen** on a client that has Patchee installed; the server half is what keeps the two in step (it stops the server refusing the faster break). A floor, never a slowdown: without shears it breaks exactly as it does today. | `true` (on) | You want the plain 1.7.10 speed back. |
| `enableTeamChunkCap` | **ServerUtilities only.** A **team** gets its **best member's own per-rank limit** as its chunk limit. ServerUtilities normally adds the members' limits up, so a 4-player team would end up with 6000 / 2000. With this on, a team of ordinary players gets one player's total (the player rank's 1500 / 500) no matter how many members it has, and a member whose rank carries higher numbers — an admin — keeps them; nothing is ever capped below what a member's own rank grants. A player on their own is **not** affected (their sum already is their own number). Server-side only: clients need nothing. | `true` (on) | You want ServerUtilities' own per-member totals back. |
| `enableFinalCastleBreak` | For a player who has killed the Snow Queen (earned the Glacier progression): the castle bricks (`castleBlock`), the castle magic blocks (`castleMagic`) and the thorn wall (`thorns`) break in at most 10 ticks (a floor of 0.1 per tick), never a slowdown; the gate is read **per player on every break**, so a player without it breaks them at the plain 1.7.10 speed; the faster break is only **seen** on a client that has Patchee. **On the server**, the same progression also makes the castle bricks stop draining the pickaxe 16x and the thorn wall stay broken instead of growing back and multiplying. Players without the progression keep the stock behaviour. | `true` (on) | You want Twilight Forest's stock castle bricks and thorn wall back. |
| `enableEnrichedFertilizerRecipe` | Keeps the old **enriched fertilizer** recipe as an extra choice next to the pack's newer one: **4 fertilizer + 1000 L fertilizer liquid → 1000 L enriched fertilizer** in a **Chemical Reactor** or **Large Chemical Reactor** (LV, 1 second). Only adds a recipe; the pack's own recipe is untouched. Needs CropsNH and GregTech (both ship with GT:NH). Server-side only, so it does not show up in players' NEI; it still works in the machine. | `true` (on) | You want only the pack's own recipe. |
| `lightOverlay.maxLightNormal` | **Client-side.** The light level the F7 overlay marks at, in every dimension (the Nether included). `0` = only pitch-dark blocks. | `0` | You want it stricter or looser. |
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
- **F7 overlay tweak:** this one is **client-side only**. It changes what the F7 overlay marks on a client that has Patchee installed; a player without Patchee sees NEI's normal overlay. The server does not need NEI. The number is the light level the X appears **at** — so `0` means only pitch-dark blocks, in every dimension. It is applied while NEI loads, so change the value and restart the game for it to take effect.
- **Twilight Forest break speeds:** the faster break is only **seen** on a client that has Patchee installed. Unlike the F7 overlay, this rule runs on **both sides** — the server half is what stops the server refusing the faster break. The rules are a floor — nothing ever breaks slower than it did before, they only add the modern speed. The condition is checked **per player on every break**: the aurora blocks (2 ticks) and the castle blocks (10 ticks) need the Glacier progression (the Snow Queen), so a player who has not killed her breaks them at the plain 1.7.10 speed; the arctic fur block (5 ticks with shears) needs nothing but shears in hand. Patchee changes the game at runtime through reflection; it copies no Twilight Forest code and ships none of its art or sounds. If Twilight Forest is missing, the rules do nothing.
- **Team chunk limits (ServerUtilities):** ServerUtilities adds every member's own limit together for the whole team, so a team of four would be allowed 6000 claimed chunks and 2000 loaded ones. Patchee reads a team's limit from its **members' per-rank settings** instead: the best member's own limit becomes the team max, so a team of ordinary players gets one player's total (1500 claimed / 500 loaded on this pack) however many members it has — and a member whose rank carries higher numbers, such as an admin, keeps them; nothing is ever capped below what a member's own rank grants. A player on their own is left exactly as they were. The old `teamChunkCap.maxClaimChunks` / `teamChunkCap.maxLoadedChunks` number keys from earlier versions are retired — leave them in the file or delete them, they change nothing. This is a **server-side** rule; the numbers the claim map shows are computed on the server and sent to the client, so every player sees the real numbers without installing anything. It is read while the server starts, so restart after changing it. If ServerUtilities is not installed, the rule does nothing and the log says so.
- All fixes fail soft: if one of the patched mods is missing or changed shape, that fix simply does nothing and writes a note in the log. The server always starts.

## Want the check output explained?

Set `runSelfTest=true` and look for lines like `[SelfTest] PASS …` and finally `[SelfTest] result: N passed, 0 failed`. That is Patchee proving to you that its changes work on **your** server. Then set it back to `false`.
