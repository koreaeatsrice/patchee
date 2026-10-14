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
