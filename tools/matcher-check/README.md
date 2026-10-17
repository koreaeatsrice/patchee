# matcher-check — verify the TeamChunkCap transform against REAL class bytes

Why this exists: a bytecode matcher that never fires is **silent**. The live
server runs a released ServerUtilities jar whose class bytes are whatever the
released build produced, and the transform has to recognise exactly the return
sites it expects in that file. This tool runs the REAL
`com.jointspaceforce.patchee.asm.TeamChunkCapTransformer` over the REAL bytes of
the ServerUtilities jar that is deployed (`ServerUtilities-2.4.9.jar`) and
asserts the outcome, so a shape mismatch shows up here instead of as a quiet
no-op on the server.

## What it checks

1. `serverutils/data/ServerUtilitiesTeamData.class` from the real jar → patched,
   with exactly 4 hook call sites (2 in `getMaxClaimChunks()` and 2 in
   `getMaxChunkloaderChunks()`).
2. Nothing else changed: same fields, every other method's opcode sequence
   identical, the `-1` / `-2` sentinel returns still return `-1` / `-2`, and the
   two target methods differ **only** by the injected
   `ALOAD_0` / `SWAP` / `INVOKESTATIC` triples.
3. Idempotence: a second pass skips and returns the bytes unchanged.
4. A non-target class of the same jar (`ClaimedChunks`) passes through
   byte-identical.
5. The fail-loud path: a class of any other shape is reported at ERROR (with an
   instruction dump) and handed back byte-identical.

## Run

```sh
WT=/path/to/patchee            # the worktree, after ./gradlew build
SU=/path/to/ServerUtilities-2.4.9.jar
OUT=/tmp/mcheck

ASM=.../org.ow2.asm/asm/9.8/.../asm-9.8.jar
ASMT=.../org.ow2.asm/asm-tree/9.8/.../asm-tree-9.8.jar
LW=.../net.minecraft/launchwrapper/1.12/.../launchwrapper-1.12.jar
LOG4J=.../org.apache.logging.log4j/log4j-api/2.0-beta9-fixed/.../log4j-api-2.0-beta9-fixed.jar
MC=$WT/build/classes/java/main

mkdir -p "$OUT"
javac -d "$OUT" -cp "$ASM:$ASMT:$LW:$LOG4J:$MC" "$WT/tools/matcher-check/MatcherCheck.java"
java -cp "$OUT:$ASM:$ASMT:$LW:$LOG4J:$MC" MatcherCheck "$SU" "$OUT"
```

Expected: `RESULT: 8/8 PASS`, with the patched class written next to the tool for
inspection. The jar comes from the game box:

```sh
scp -i ~/.ssh/mainframe_key -P 2222 \
  root@<box>:/home/amp/.ampdata/instances/GTNH/Minecraft/mods/ServerUtilities-2.4.9.jar .
```

After patching, `javap -p -c` on the patched class is the cheap eyeball check;
the JVM verifier is the real one, and the dev server (or the unit test that loads
the patched bytes) is what proves the patched class still verifies.

## The FinalCastleBreak variant (`CastleCheck.java`)

The same idea for the Twilight Forest castle/thorn patch: run the REAL
`com.jointspaceforce.patchee.asm.FinalCastleBreakTransformer` over the REAL bytes
of the two classes it must recognise, so a shape mismatch shows up here instead
of as a quiet no-op.

## What the castle variant checks

1. `twilightforest/block/BlockTFCastleBlock.class` → patched, exactly
   `FinalCastleBreakPatch.CASTLE_SITE_COUNT` (=1) `FinalCastleBreakHooks` call
   site, no `Item.onBlockDestroyed` virtual call left, the method the same length
   (the call is only re-pointed), and every other method identical at the ASM
   opcode level.
2. `twilightforest/block/BlockTFThorns.class` → patched, exactly
   `FinalCastleBreakPatch.THORNS_SITE_COUNT` (=2) hook call sites; the burst
   method grows by exactly the two `ALOAD 2`s; the second `World.setBlock` in
   `growThorns` is left alone; every other method identical at the ASM opcode
   level.

> On the **dev** bytes ASM widens a few 2-byte encodings inside
> `BlockTFThorns.registerBlockIcons` (4× `ldc` → `ldc_w`, an `if_icmpge` target
> shift) once the patch adds constants — the instruction sequence is identical at
> the ASM opcode level, but the method is not byte-identical. That is
> behaviour-identical; the **live** 2.7.40 bytes show no such extra change.
3. Both are checked over the **live** jar bytes (SRG member names) and over the
   2.7.41 **dev** dependency (MCP member names), because real deployments differ
   only in member names.
4. Idempotence: a second pass over the patched bytes skips and returns them
   unchanged.
5. A non-target class (`BlockTFCastleMagic`) passes through byte-identical.
6. The fail-loud path: a class of any other shape is reported at ERROR with the
   observed instruction dump and handed back byte-identical.

## Run (castle variant)

```sh
WT=/root/projects/patchee/.worktrees/castlebreak          # after ./gradlew build
LIVE=$WT/src/test/resources/finalcastlebreak/live          # the live-jar bytes
DEVJAR=/root/.gradle/caches/modules-2/files-2.1/com.github.GTNewHorizons/TwilightForest/2.7.41/36d15b3d4f70cfbab5dfa6c614f68f248472a82f/TwilightForest-2.7.41-dev.jar
OUT=$(mktemp -d)

ASM=/root/.gradle/caches/modules-2/files-2.1/org.ow2.asm/asm/9.8/dc19ecb3f7889b7860697215cae99c0f9b6f6b4b/asm-9.8.jar
ASMT=/root/.gradle/caches/modules-2/files-2.1/org.ow2.asm/asm-tree/9.8/18419ca5b77a2f81097c741e7872e6ab8d2f40d/asm-tree-9.8.jar
LW=/root/.gradle/caches/modules-2/files-2.1/net.minecraft/launchwrapper/1.12/111e7bea9c968cdb3d06ef4632bf7ff0824d0f36/launchwrapper-1.12.jar
LOG4J=/root/.gradle/caches/modules-2/files-2.1/org.apache.logging.log4j/log4j-api/2.0-beta9-fixed/b61eaf2e64d8b0277e188262a8b771bbfa1502b3/log4j-api-2.0-beta9-fixed.jar
MC=$WT/build/classes/java/main

javac -d "$OUT" -cp "$ASM:$ASMT:$LW:$LOG4J:$MC" "$WT/tools/matcher-check/CastleCheck.java"
java -cp "$OUT:$ASM:$ASMT:$LW:$LOG4J:$MC" CastleCheck "$LIVE" "$DEVJAR" "$OUT"
```

Expected: `RESULT: 10/10 PASS`. Both the flat layout
(`live/BlockTFCastleBlock.class`) and a package-nested tree are accepted for the
directory argument, and either a jar or a directory is accepted, so the same tool
runs over the pulled live bytes and straight out of the Gradle cache jar. The
patched classes are written next to the tool for `javap -p -c` inspection.
