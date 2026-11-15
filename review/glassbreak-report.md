# GlassBreak — evidence report

Branch `fix/chisel-glass-effects`, worktree
`/root/projects/patchee/.worktrees/chisel-glass`. No git writes were made; the
parent publishes.

## 1. What the bug is

Owner's words:

> breaking certain chisel blocks that are NOT glass based act as if you are
> breaking glass. Biggest example: technical blocks.

Break a Chisel Transparent Technical Block and you can start bleeding; while
bleeding is active you are also blinded. The block is a metal mesh, not glass.

## 2. The culprit

Blood Arsenal 1.5.12 (the build in the GT:NH 2.9.0 manifest),
`com.arc.bloodarsenal.common.BloodArsenalEventHooks`, method
`harvestEvent(BlockEvent.HarvestDropsEvent)`, decompiled source line 171:

```java
if (block != null && block instanceof BlockGlass)          // line 171
```

Line 170 guards `!event.world.isRemote && !event.isSilkTouching`. Then:

```java
if (BloodArsenalConfig.isGlassDangerous && random.nextInt(3) == 2) {   // line 186
    ...
    player.addPotionEffect(new PotionEffect(BloodArsenal.bleeding.id, 8 * 20, random.nextInt(3)));  // line 189
```

With flint in hand the odds change (`random.nextInt(5) > 3`, line 181; 4*20
ticks, line 183). Its living-tick handler (lines 90-97) adds
`new PotionEffect(Potion.blindness.id, duration, 1)` every tick while bleeding
is active and damages the player. There is **no armour check** anywhere in that
path — only an item whitelist `glassProtectiveItems` (default:
matter-manipulators). Bleeding's default config id is 121.

The method is registered on both `MinecraftForge.EVENT_BUS` and
`FMLCommonHandler.instance().bus()` at
`com/arc/bloodarsenal/common/BloodArsenal.java:139-140`.

javap of the method agrees with the source line, and the `LineNumberTable`
pins the `instanceof` to line 171 (offsets 66 and 71 both map to it):

```text
 71: instanceof  #294   // class net/minecraft/block/BlockGlass
...
LineNumberTable:
  line 171: 66
  line 171: 71
```

So the whole decision is "is this a `BlockGlass` (or subclass)" — nothing about
the block's actual material or name.

## 3. Why a non-glass block reaches it

Chisel 2.17.34-GTNH:

- `team.chisel.block.BlockCarvableGlass extends BlockGlass`, and its constructor
  calls `super(Material.glass, false)`.
- The Transparent Technical Block is `chisel:technical2`, built
  `new BlockCarvableGlass()` at `team/chisel/Features.java:3823-3829`.
- Its unlocalized name is `tile.chisel.technical2`, the exact string
  `BlockCarvableGlass.java:113` compares to pick the transparent texture set —
  proof on the block's own side that this instance *is* the technical block.
- The opaque Technical Block is `new BlockCarvable(Material.rock)` at
  `Features.java:3801`, so it is not a `BlockGlass` and never triggers.

`instanceof BlockGlass` therefore returns true for a metal block. That is the
whole bug: the check is on the class, and Chisel gave a non-glass block a
glass class.

## 4. Coverage

| Block | Is it a `BlockGlass`? | Hit by Blood Arsenal? | Patchee's verdict |
|---|---|---|---|
| `minecraft:glass` | yes | yes | real glass: cuts only an unarmoured player |
| Chisel glass / `glass2` / stained glass | yes | yes | real glass, same rule |
| `chisel:technical2` (Transparent Technical Block) | yes (`BlockCarvableGlass`) | yes — the bug | **never** cuts, whatever the armour |
| Chisel opaque `technical` | no (`BlockCarvable`) | no | untouched |
| Vanilla glass panes (`BlockPane`) | no | no | untouched |
| EFR `BlockTintedGlass` | yes (extends `BlockGlass`) | yes | real glass, same rule |

## 5. The armour rule

A player counts as protected when the number of filled armour slots is at least
`glassBreak.minArmourPieces`. "Filled" means a non-null stack in
`getCurrentArmor(0..3)` — any item counts, the slot does not have to match the
body part. The value is clamped into 0..4, so `0` = armour never protects and
`4` = a full set; a negative or oversized value from a hand-edited file cannot
invert the rule. Default is `1` (any single piece). Feature switch:
`enableGlassBreakFix`, default `true`.

## 6. How the fix works

One listener on `MinecraftForge.EVENT_BUS` at `EventPriority.LOWEST`, server
side only, on `BlockEvent.HarvestDropsEvent`.

The ordering is the argument:

- `HarvestDropsEvent` is a Forge event, and Blood Arsenal's handler is
  registered on the Forge bus at the default `NORMAL` priority.
- FML dispatches a single event through the bus in priority order, so `LOWEST`
  runs **after** `NORMAL` — after Blood Arsenal applied its potion, still
  inside the same dispatch.
- Nothing ticks between the two handlers, so the effect is removed before it
  can deal damage, and before the living-tick handler can add blindness.

That same-dispatch ordering is why no bytecode patch of Blood Arsenal is
needed: nothing is rewritten, and Blood Arsenal is never a compile dependency.
The listener reads Blood Arsenal's bleeding potion id once at boot by
reflection and fails loud if the field is missing or is not a `Potion`.

Order inside the listener: `world != null && !isRemote`, the fix is active, the
block is non-null and a `BlockGlass`, a harvester is present, then
`GlassBreak.suppresses(block, player)`. Anything unexpected is caught; the
harvest is never broken.

## 7. What is NOT covered

- **The potion exists briefly.** It is applied by Blood Arsenal and removed by
  us in the same dispatch — microseconds apart, with no tick in between. Its
  damage and blindness never happen, but the potion object really is added and
  removed. The server does send the effect packet and the removal packet, both
  inside the same tick, so the client never renders the effect.
- **Existing bleed and blindness are cleared too.** `removePotionEffect` removes
  by id, not by cause. Clearing `Potion.blindness` also clears blindness from any
  other source, and clearing the bleeding potion also clears a bleed the player
  already had from a fight. This is documented in `docs/SETTINGS.md`.
- **Only the named non-glass block is listed.** The rule matches
  `tile.chisel.technical2` (unlocalized) and `chisel:technical2` (registry
  name). A full scan of every `BlockGlass` subclass in the pack was **not**
  done — the owner named the technical block, and that is what is covered. A
  second offender would need its own entry.
- **Silk touch is not ours.** A silk-touch break never reaches Blood Arsenal's
  potion code (its own line 170 guard), so Patchee never sees it either.
- **No in-game run here.** See §8.
- If Blood Arsenal is not installed (or its class changed shape) the fix does
  nothing and says so in the log; the server still starts.

## 8. Verification

- Unit tests: `GlassBreakPolicyTest` — 12 tests, following the repo-wide total
  of 119 tests, 0 failures. They pin the clamp, both name comparisons (case and
  trailing space), the null/blank cases and every branch of `verdict`.
- Build: `./gradlew spotlessApply check` → BUILD SUCCESSFUL.
- Jar contents and the log lines a real boot prints: see PR body.
- Not proven: no live boot and no player broke a technical block here. All
  proof is source/javap-level plus the pure-rule unit tests.
