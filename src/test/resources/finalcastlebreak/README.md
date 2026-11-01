# Real Twilight Forest class bytes for the FinalCastleBreak tests

These `.class` files are **not** written by hand: they are the real bytes of the
Twilight Forest classes Patchee patches, pulled from the two jars the feature has
to work against. They exist so `FinalCastleBreakPatchTest` runs the REAL patch
over the REAL shapes, not over a synthetic look-alike.

## `live/` — the jar the live server runs (SRG member names)

- Source jar: `TwilightForest-2.7.40.jar`
- From: `/home/amp/.ampdata/instances/GTNH/Minecraft/mods/TwilightForest-2.7.40.jar`
  on `46.4.120.77` (the GTNH server box), pulled read-only with
  `unzip -p <jar> twilightforest/block/<Class>.class`.

| file | sha256 |
| --- | --- |
| `live/BlockTFCastleBlock.class` | `332ac1774a3d7808cbe1def4fddd4fafb1b1e218012da9a30e3537f2498af1b8` |
| `live/BlockTFThorns.class` | `5af1ce7c4f7b50204e188f79e70872557e2aa3da0ba81991396279e48d01d375` |
| `live/BlockTFCastleMagic.class` | `3717ecf1336ecf2f82fa6e60f67822db6a94cc45b06c209c3d13e92116b1ab65` |
| `live/TFBlocks.class` | `2c1e57b206d7fb69515aedc3d2d781e7d073043448339ccf8561fa67a2aef52a` |

`BlockTFCastleMagic` is the **non-target** control: the transformer must hand it
back byte-identical.

`TFBlocks.class` is the **field-presence** evidence: `javap -p` of these live
bytes shows all four fields the docs promise exist on the live build as
`public static net.minecraft.block.Block` fields —

```
public static net.minecraft.block.Block thorns;
public static net.minecraft.block.Block castleBlock;
public static net.minecraft.block.Block castleMagic;
public static net.minecraft.block.Block deadrock;
```

and `FinalCastleBreakPatchTest.theLiveTfBlocksHasTheFourPublicStaticBlockFields`
parses these stored bytes and asserts the same, so the four-block claim is not
just a warn-if-missing hope.

In this jar the vanilla member names are SRG (`func_150894_a`,
`func_147465_d`, ...) while the classes keep their MCP names
(`net/minecraft/item/Item`, `net/minecraft/world/World`).

## `dev/` — the Twilight Forest 2.7.41 dev dependency (MCP member names)

- Source jar: `TwilightForest-2.7.41-dev.jar` from the Gradle cache
  (`com.github.GTNewHorizons:TwilightForest:2.7.41:dev`, the
  `runtimeOnlyNonPublishable` dependency in `dependencies.gradle`).

| file | sha256 |
| --- | --- |
| `dev/BlockTFCastleBlock.class` | `4b72c0f60a305f091945dfca12ac29858e107ea39f511a73510450ec35bb7127` |
| `dev/BlockTFThorns.class` | `af9ea6a07bb32028453c952652571689ad3af8503a98abbfa755beacf5e9c09d` |

Here the same members carry their MCP names (`onBlockDestroyed`, `setBlock`).
The two jars differ **only** in those member names — never in class names or
descriptors — which is exactly why `FinalCastleBreakPatch` matches by
descriptor + instruction shape and never by member name.

## Note on versions

The live server jar is **2.7.40**; the dev dependency is **2.7.41**. Both were
checked and the two patched methods have the identical instruction shape in
each, so the one patch covers both.

## File-type pitfall (these are BINARY, and git must treat them so)

These `.class` files are **real binary class bytes**, not text. The repo's
`.gitattributes` line 1 is `* text eol=lf`, so **every** file is text by default;
a `.class` file is binary only because of the explicit rule

```
*.[cC][lL][aA][sS][sS]  binary
```

That rule must stay. If `.class` is ever left as text (or the rule is dropped),
git rewrites the line endings inside these files on publish and **silently drops
bytes**: a class file that happens to contain a CRLF pair loses the CR.

**This already happened.** On a fresh checkout of the published branch
`live/TFBlocks.class` came back 19271 bytes (sha256 `bc12f2734dcb…`, 36 CR, 0
CRLF) instead of the stored 19272 bytes (sha256
`2c1e57b206d7fb69515aedc3d2d781e7d073043448339ccf8561fa67a2aef52a`, 37 CR, 1
CRLF) — one byte gone, the class corrupted. ASM then threw
`ArrayIndexOutOfBoundsException … length 1119` at `ClassReader.readClass` in
`FinalCastleBreakPatchTest.theLiveTfBlocksHasTheThreePublicStaticBlockFields`,
and CI failed. The other five fixtures happen to contain no CRLF pair, so they
survived that time — the same latent risk applies to all of them.

`FinalCastleBreakPatchTest` now guards against this: it verifies each stored
fixture's sha256 (recorded once, in the test's `FIXTURE_SHA256` map) before ASM
parses it, and fails with the file, both shas and a pointer to this pitfall. The
sha values in that map and in the tables above must agree.

### How the bytes were captured

The live bytes were pulled read-only (no change to the server) with:

```
ssh -i ~/.ssh/mainframe_key -p 2222 root@46.4.120.77 \
  'unzip -p /home/amp/.ampdata/instances/GTNH/Minecraft/mods/TwilightForest-2.7.40.jar \
   twilightforest/block/<Class>.class'
```

for `BlockTFCastleBlock`, `BlockTFThorns`, `BlockTFCastleMagic` and `TFBlocks`
from `TwilightForest-2.7.40.jar` (the jar the live GTNH server runs). The dev
bytes were unzipped the same way from `TwilightForest-2.7.41-dev.jar` in the
Gradle cache (`com.github.GTNewHorizons:TwilightForest:2.7.41:dev`). The
sha256s of all six stored files are recorded in the two tables above.
