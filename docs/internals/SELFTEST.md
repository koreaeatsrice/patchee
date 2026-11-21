# Self-test

For an implementer who needs to know what `runSelfTest=true` actually checks. It covers when the test runs, what a
PASS/FAIL/SKIP means, and what each check exercises. The CI wiring is [../ops/CI.md](../ops/CI.md); the test layers
are [../dev/TESTING.md](../dev/TESTING.md).

## When it runs

`SelfTest.schedule(toggles, configDirectory)` registers a one-shot `FirstTick` listener on the FML bus
(`SelfTest.java:92-97`). The listener:

1. waits for `TickEvent.Phase.START`;
2. unregisters itself;
3. calls `SelfTest.run` (`SelfTest.java:112-121`).

The first tick is used on purpose. ServerUtilities builds its claim data while the universe loads, at the same
moment `FMLServerStartedEvent` fires, and the order of two mods' handlers for one event is arbitrary. Waiting for
the first tick means the data exists.

The test is opt-in: `runSelfTest` defaults to `false`, and the composition root schedules it only when the master
switch and `runSelfTest` are both on (`CommonProxy.java:88`).

## Output

`check(what, ok)` logs `[SelfTest] PASS  ...` or `[SelfTest] FAIL  ...` and counts it (`SelfTest.java:143-152`). The
run ends with:

```
[SelfTest] result: N passed, M failed
```

A missing mod is a `SKIP` line with the reason, never a `FAIL`. A check that cannot set itself up is a `FAIL` with
the reason, never a silent pass.

## What it checks

The checks are grouped by feature area and call one after another in `runChecks` (`SelfTest.java:131-147`):

| Check | What it does |
|---|---|
| Dolly gate | Runs JABBA's own final loop against the live `classExtensions` list, and requires a natural hive and a furnace to stay refused (`SelfTest.java:157-230`). |
| Mattock | Reads the live shovel-material array and checks the shovel material is present once. |
| SuperTankFix | Calls `SuperTankDecision.shouldClear` with plain booleans: the rule is a pure function, a Patchee client clears and a local client clears, while a stock client, a player carrying nothing and a player with no effects are left alone. The armed flag is reported by the feature's own arm line. |
| VeinConfig | Checks the written VeinMiner files, then re-reads them. |
| Aurora break | Runs the break-speed decision on a synthetic player and block. |
| Arctic fur break | The same for the fur set. |
| TeamChunkCap | Builds a one-member team and a multi-member team, compares the raw sums with the patched answers, and sweeps real claims until the server refuses (`SelfTest.java:533-712`). |
| FinalCastleBreak | Checks the registered block set and the break floor. |
| Enriched fertilizer | Checks the recipe is registered exactly once. |
| Fish Trap | Checks the move hotkeys are registered. |
| GlassBreak | Checks the armour threshold from both sides of the boundary. |

A check may run `SKIP` on the dev runtime because a mod is absent (Blood Arsenal, Gendustry, MagicBees). That is the
pin, not a bug.

## What a PASS means

A PASS means the patched decision produced the expected result on a real object, with a control that stayed
unchanged. It does **not** mean:

- that a transformer matched; the transformer's site-count line is the proof of that;
- that a client will see anything; the test runs on a dedicated server and client features log their skip;
- that a stock client can join; that is a manual check ([SIDES.md](SIDES.md)).

Three layers together prove a transformer-backed feature:

1. the transformer's `patched ... <n> site(s)` line, or an ERROR that the shape was not recognised;
2. the feature's `armed` line, gated on the transformer's `patchApplied` flag where a class presence would lie
   (`TeamChunkCapFeature.java:143-163`);
3. the self-test, on real objects.

## Running it

```sh
# In run/server/config/patchee.cfg
#   B:runSelfTest=true
./gradlew runServer
```

On a real server: set the key, restart, read `logs/latest.log`, then set it back to `false`. Run it once after an
install or a pack update. Leaving it on costs one tick per boot and adds log lines.

CI sets it automatically and fails on a missing result line or on any `[SelfTest] FAIL`.

## Adding a check

1. Write a `private void checkYourFeature()` in `SelfTest`, following an existing one.
2. Exercise the real decision, not the class name.
3. Include a control that must stay unchanged.
4. Use `check("what was observed", condition)`; do not log PASS yourself.
5. Add the call to `runChecks`.
6. If the check needs a mod that may be absent, log a `SKIP` with the reason and return.

## See also

- [../ops/CI.md](../ops/CI.md) — the smoke job that runs it.
- [PIPELINE.md](PIPELINE.md) — where the feature's arm step runs.
- [../dev/TESTING.md](../dev/TESTING.md) — the other layers.
