# Patchee

> [!NOTE]
> **Disclaimer:** The majority of this project was written by a Large Language Model (LLM). While prompted, built, and tested for reliability, please take note of this before downloading and using this mod. Constructive criticism is desired and greatly appreciated. I make no claims to be a bona fide Software Engineer nor pretend that this project makes me one.

**A small server-side GTNH 1.7.10 addon that fixes two cross-mod annoyances — without touching anyone else's jar.**

Patchee is a *patcher addon*: it changes the behaviour of other mods (JABBA,
Tinkers' Construct) at runtime through reflection, so pack updates never
silently revert the fixes and no third-party jar is ever edited. Clients need
nothing installed (`acceptableRemoteVersions = "*"`).

## What it fixes

### 1. JABBA Dolly × bee housings

The Dolly picks up barrels and chests, plus a hardcoded list of other block
types — which does **not** include the bee housings, so you cannot move a
working apiary with its bees.

Patchee adds the **single-block crafted bee housings** to that list:

| Mod | Block | TileEntity class |
|---|---|---|
| Forestry | Apiary | `forestry.apiculture.tiles.TileApiary` |
| Forestry | Bee House | `forestry.apiculture.tiles.TileBeehouse` |
| Gendustry | Industrial Apiary | `net.bdew.gendustry.machines.apiary.TileApiary` |
| MagicBees | Magic Apiary | `magicbees.tileentity.TileEntityMagicApiary` |

(Two Forestry blocks share one block id and differ by metadata; Jabba's pickup
stores that metadata, and the self-test verifies both housing classes are
accepted. Forestry's housing base class `TileBeeHousingBase` is also
registered — its only concrete subclasses are exactly the Apiary and the Bee
House.)

**Out of scope by design:** natural/world-generation hives (Forestry
`TileSwarm`, Binnie, MagicBees hives) and the multi-block Alveary (moving one
block of a multiblock at a time leaves the structure temporarily broken —
same as mining it).

Small honest caveats: a moved apiary keeps the facing it had (Jabba has no
placement hook for bee housings — cosmetic only), and meta 0/2 of Forestry's
block are both covered. `extraDollyClasses` should only list blocks whose
tile entity is always created (Jabba restores NBT without a null check).
Jabba's own 1 MB "too complex" limit still applies to any pickup, and — for
completeness — Binnie's alveary parts are already movable in stock Jabba
(`binnie.core.machines.TileEntityMachine` is on Jabba's list), independent of
Patchee.

### 2. Tinkers' Construct Mattock × sand/snow

The mattock is an axe+shovel hybrid, but its shovel-material list is missing
sand and snow, so it digs them at hand speed. Patchee extends the mattock's
static `shovelMaterials` array with `sand`, `snow` (layers) and `craftedSnow`
(blocks). Existing mattocks benefit immediately — the game reads the array on
every dig.

The Forge "shovel" tool class is deliberately **not** claimed (`getToolClasses`
still answers `{"axe"}`), so other mods' harvest-tool checks are untouched.
The one visible side effect is the intended one: the mattock now answers
`canHarvestBlock`/`isEffective` as **true** for those three materials — that
is what makes the blocks drop (snow layer → snowball, snow block → 4
snowballs). The only other reader of that answer inside Tinkers' Construct is
the "Omni" active modifier, which gains the same behaviour. This is the same
result the older bytecode patch produced, now delivered as a proper addon.

## How it works (and why the obvious route doesn't)

- **Dolly:** Jabba's pickup gate (`ItemBarrelMover`) matches the target
  TileEntity against a static `classExtensions` list that is built once at
  class-init from `classExtensionsNames`. Patchee appends the bee-housing
  classes to that list at `postInit`. (Jabba ships a
  `MovableRegistrar`/`IDollyHandler` API package, but nothing in Jabba ever
  calls it — it is dead code and cannot be used.)
- **Mattock:** `DualHarvestTool.getDigSpeed`/`isEffective` fetch the arrays
  through `getEffectiveMaterials()`/`getEffectiveSecondaryMaterials()` on
  every call, so replacing the static field works for existing items.

Both fixes fail soft. When the target mod is simply **not installed**, the fix
is skipped with an INFO line (normal on smaller packs). If the target mod is
present but has changed shape (field renamed, class moved), Patchee logs an
ERROR with the details and leaves that mod untouched instead of crashing.

## Config (`config/patchee.cfg`)

```
general {
    B:enableDollyFix=true      # bee-housing dolly support
    B:enableMattockFix=true    # mattock digs sand/snow at shovel speed
    S:extraDollyClasses=       # extra TileEntity class names the Dolly should accept
    B:runSelfTest=false        # one-shot PASS/FAIL check at server start (see below)
}
```

`extraDollyClasses` notes: entries must be TileEntity classes (anything else is
ignored with a warning); a class that Jabba's own blacklist already lists
(exact class match, in Jabba's config) stays blocked; a class that cannot be
loaded is skipped and reported in the log line.

## Install

Server only: drop `patchee-<version>.jar` into the pack's `mods/` folder and
restart. Use the plain release jar — **not** the `-dev` one (that one is only
for development environments). It needs, but does not bundle, the mods it
patches (JABBA, Tinkers' Construct, and whichever bee mods you run).

## Verify it armed (recommended after install or a pack update)

Set `B:runSelfTest=true`, restart once, and read the log:

- `[SelfTest] PASS  dolly accepts …` for each installed bee housing,
- `[SelfTest] PASS  dolly refuses …` for a natural hive and a furnace,
- `[SelfTest] PASS  mattock isEffective(sand) = true`,
- a final `[SelfTest] result: N passed, 0 failed`.

Then set it back to `false`. The same lines to look for after a normal boot:

- `[DollyFix] Dolly now accepts N bee-housing class(es)`
- `[MattockFix] mattock shovel materials 3 -> 6 (+3)`

If a line says "not installed — skipped", that mod is absent from the pack
(fine). If it says "failed — … left unchanged", the target mod changed shape:
check `patchee` on GitHub and re-verify the field/class names.

## Build

Standard GTNH toolchain (the repo is the official
[ExampleMod1.7.10 starter](https://github.com/GTNewHorizons/ExampleMod1.7.10)):

```sh
./gradlew spotlessApply   # formatting
./gradlew build           # -> build/libs/patchee-<version>.jar
```

CI (`.github/workflows/ci.yml`) builds the mod on every push to `main` and then
boots a throwaway dev server with the self-test enabled, failing the run unless
it logs `[SelfTest] result: N passed, 0 failed`. Dependency-graph submission
runs weekly and on pushes (`security.yml`), feeding Dependabot alerts
(enabled); weekly dependency PRs are auto-merged when green.

Releases are prepared by the `Release` workflow: dispatch it from the Actions
tab (choose `auto`/`patch`/`minor`/`major`, plus an optional dry run), or push a
`v*` tag. It generates grouped release notes from Conventional Commits, updates
`CHANGELOG.md`, tags the release, builds the jar, and opens a **draft** GitHub
release for review. `release.py` and its unit tests live in `.github/scripts/`.

## Verified against

- JABBA (GTNH fork), `ItemBarrelMover` — static list at class-init, gate at
  `for (Class c : classExtensions) if (c.isInstance(te)) return true;`.
- Tinkers' Construct (GTNH fork) `1.14.108-GTNH` — javap-verified:
  `static Material[] shovelMaterials` (package-private, not final) is read
  per call by `getEffectiveSecondaryMaterials()`.
- Forestry (GTNH fork), Gendustry, MagicBees — class names read from source.

## Licence

MIT — see `LICENSE`.