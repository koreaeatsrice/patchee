# Testing

For a contributor who needs to prove a change. It lists the four layers, what each can prove, and how to test a
class transformer without risking a live server. The command set is [SETUP.md](SETUP.md).

## The four layers

| Layer | Runs where | Proves |
|---|---|---|
| JUnit 5 (`src/test/java`) | `./gradlew test`; no game | The pure decisions: thresholds, team-limit maths, config parsing, byte matching on synthetic classes. |
| Real-jar tests | `./gradlew test` with an env var | Field names and descriptors against a real mod jar. Third-party jars are skipped unless their var is set (`PATCHEE_GREGTECH_JAR`, `PATCHEE_IC2_JAR`, `PATCHEE_GRAVISUITE_JAR`); Forge's own classes are always on the classpath and their test never skips. |
| Matcher tools (`tools/matcher-check/`) | A shell, by hand | A transformer against the actual jar bytes: site count, instruction sequence, idempotence, pass-through, fail-loud. |
| Smoke job (`ci.yml`) | GitHub Actions, real dev server | The server boots, each feature arms, the self-test passes. |

A change is proven by the cheapest layer that can fail for the reason you changed. A docs-only change needs none of
them; a new transformer needs all four.

## Unit tests

```sh
./gradlew test --rerun-tasks
```

- JUnit 5, plain Java, no Minecraft bootstrap. If a class needs a `World` to be constructed, extract the decision
  into a static helper and test that, as `TeamChunkCap` and `BreakHardness` do.
- One behaviour per test; the name says the behaviour.
- Include the negative case: a value below the threshold, a class of the wrong shape, a config with the key absent.
- Do not relax an assertion to make a build pass. If the behaviour is wrong, say so in the PR.
- `ReadmeFeatureSentencesTest` guards the README feature bullets (one sentence, at most 18 words after the dash).
  A new user-visible feature needs a bullet in the same style.

### Transformer unit tests

Each patch has a test that builds synthetic bytes with ASM and asserts:

- the site count and the resulting instruction sequence;
- idempotence: a second pass returns the input unchanged;
- a non-target class comes back byte-identical;
- a right-name, wrong-shape class returns the original bytes and a failure state, not an exception.

Existing examples: `LightOverlayPatchTest`, `TeamChunkCapPatchTest`, `FinalCastleBreakPatchTest`,
`ChestplateFlightPatchTest`, `GraviSuiteFlightPatchTest`.

`HandshakeModListPatchTest` is the exception: it reads the real Forge class instead of synthetic bytes, because the
join path depends on the shape of FML's own code.

### The handshake test never skips

`HandshakeModListPatchTest` loads `cpw/mods/fml/common/network/handshake/FMLHandshakeServerState$2.class` from the
test runtime classpath — the build's recompiled Forge classes, which carry the same HELLO shape as the shipped
universal jar. When the class is absent it fails with a message saying so; it does not skip.

Skip-based green builds are banned for this path. A test that skips when the class is missing turns a broken patch
into a green suite. Any check whose subject is missing must fail, not pass quietly. The same rule applies to the
transformer-level checks: they assert the site count and the hook's position, never just "no exception".

## Real-jar tests

`PollutionVisualsRealJarTest` reads GregTech's class files with ASM and asserts every field name and descriptor the
visual guard writes. It runs only when `PATCHEE_GREGTECH_JAR` is set:

```sh
PATCHEE_GREGTECH_JAR=/path/to/gregtech.jar ./gradlew test --rerun-tasks
```

`SuperTankRealJarTest` uses the same jar and the same skip. It reads `gregtech/common/blocks/ItemMachines.class`
with ASM and asserts that `onUpdate` calls `addPotionEffect` four times, carries the 64000 / 1200 / 12000 /
4096000.0 / 2000000.0 constants, reads `mFluid` and `mItemCount`, checks `MTESuperTank` and `MTESuperChest`
instanceof, and loads four `Potion` fields.

Unset, both are skipped with a JUnit assumption, so a plain build needs no third-party jar. That exemption is for
**third-party** jars only, which we may not redistribute. Forge's own classes are part of the build and must never
be skipped: use `HandshakeModListPatchTest` as the pattern when the patch depends on FML's own shape. Do not
commit the jar.

## Matcher tools

`tools/matcher-check/` runs the real transformer class over the real jar and prints a result:

| Tool | Target | Expected |
|---|---|---|
| `MatcherCheck.java` | ServerUtilities `ServerUtilitiesTeamData` | `RESULT: 8/8 PASS` |
| `CastleCheck.java` | Twilight Forest `BlockTFCastleBlock`, `BlockTFThorns` | `RESULT: 10/10 PASS` |
| `ChestplateCheck.java` | IC2 `ItemArmorQuantumSuit` | `RESULT: 18/18 PASS` |

The README in that folder has the exact `javac`/`java` classpath. Read the live-versus-dev note: a production jar
carries SRG member names, the dev dependency carries MCP names, and the tools check both because the instruction
shape, not the member name, is what the patch matches.

Run the tool before and after a patch change. A matcher that never fires is silent, which is why the site count is
asserted rather than "no exception".

## Smoke and self-test

`ci.yml` boots `runServer` with `runSelfTest=true` and fails unless the log contains
`[SelfTest] result: N passed, 0 failed` and the expected arm lines. Locally:

```sh
# run/server/config/patchee.cfg
# runSelfTest=true
./gradlew runServer
```

Look for:

- the transformer's `patched ... <n> site(s)` line with the number you expect;
- the feature's `armed` line;
- `[SelfTest] PASS` per check and the final `result:` line.

A `SKIP` means the mod is absent from the dev runtime, not a failure. A `FAIL` is a failure even if the server
still boots.

## Testing a transformer safely

A transformer runs inside the class loader, on bytes you do not own, before the game is up. Work in this order:

1. **Synthetic bytes.** The patch's unit test. Fast, deterministic, and it can assert the wrong-shape path.
2. **Real bytes.** The matcher tool against the jar the pack ships. This is the only layer that catches a mod
   update changing the method shape.
3. **A dev server.** `./gradlew runServer`. This is the only layer that runs the JVM verifier on the patched class
   in a real class load.
4. **A second pass.** Confirm idempotence; the unit test and the matcher both assert it.
5. **Fail-loud.** Hand it a right-name, wrong-shape class and confirm it logs ERROR and returns the original bytes.

Never test a transformer by dropping an unreviewed jar on a live server. Never patch a third-party jar on disk and
commit it. If a licence does not allow redistributing a class file, gate the test on an environment variable
instead.

## What not to claim

- "It compiles" is not a test result.
- "The class loaded" does not prove the patch applied; `TeamChunkCapFeature` checks `patchApplied()` for exactly
  this reason.
- "Works on my client" does not prove the stock-client contract. Test with a client that does not have the jar.
- A green local build does not prove the smoke job; the smoke job runs a different pack.

## Where to go next

- [ADDING-A-FEATURE.md](ADDING-A-FEATURE.md) — where each test fits in the recipe.
- [../internals/SELFTEST.md](../internals/SELFTEST.md) — what the checks actually do.
- [../internals/TRANSFORMERS.md](../internals/TRANSFORMERS.md) — the patch contract.
