# Client and server sides

For an implementer deciding which side a feature runs on. It states the hard rule, gives the three shapes, and
lists the mechanics that keep Patchee server-only. The user-facing version is
[../guide/CLIENT.md](../guide/CLIENT.md); the proxy code is in `CommonProxy`/`ClientProxy`.

## The rule

Patchee is installed on the server. A player joins with a stock client and nothing installed. **A PR that makes the
jar required on the client is rejected by policy.** This is a product constraint, not a preference: the server owner
cannot ask every player to install a mod.

## The three shapes

| Shape | Contract | Examples |
|---|---|---|
| **SERVER-ONLY** (default) | The server changes behaviour; a stock client needs nothing. | Dolly, GlassBreak, VeinConfig, TeamChunkCap, Fish Trap hotkeys. |
| **BOTH-SIDES-OPTIONAL** | The server half is complete. A client copy only improves what that player sees or feels; a stock client keeps stock local behaviour. | Twilight Forest break-speed floors, the mattock's shovel speed (drops are server-side), the Super Tank effect clear, the fertilizer recipe's NEI refresh, IC2 and GraviSuite flight edits, pollution visuals. |
| **CLIENT-ONLY** | Cosmetic and inert without Patchee. The server is not involved. | NEI's F7 light overlay. |

Choose the first shape that works. "Both sides" is not a way to make a server feature work; it is a way to make a
server feature feel better for players who also installed the jar.

## Mechanics

### 1. No packet, no channel

Patchee registers nothing with `NetworkRegistry` and defines no `IMessage` or `IPacket`. Maintained check:

```sh
grep -rn "SimpleNetworkWrapper\|NetworkRegistry\|IMessage\|IPacket" src/main/java
```

It must print nothing. Today the only match for the word `network` is a comment naming IC2's own packet
(`features/ChestplateHoverLiftFeature.java:35`).

If a feature seems to need a packet, the design is wrong. Either the server can do it alone, or it is a client-only
cosmetic and needs no server data.

### 2. Gate the step

A server-only step:

```java
if (FMLCommonHandler.instance().getSide() != Side.SERVER) {
    return Outcome.SKIPPED;   // plus a log line
}
```

Real cases: `GlassBreakFeature.java:126`, `TeamChunkCapFeature.java:145`. A client-only step checks
`!= Side.CLIENT`: `LightOverlayFeature.java:99`, `PollutionConfigFeature.java:245`,
`PollutionVisualsFeature.java:164`. `PollutionVisualsFeature.java:115` decides the side before it looks for
GregTech, so a dedicated server logs one line and does nothing.

### 3. Guard the event

An event handler checks before it touches server state:

```java
if (event.world == null || event.world.isRemote) return;
```

`GlassBreakFeature.java:219`. `GlacierGate.java:58` requires
`player.worldObj != null && !player.worldObj.isRemote` before reading progression. A handler that runs on the client
must not mutate server-authoritative state.

### 4. One composition root

`CommonProxy` builds one feature set on both sides and branches at three points:

- the early pipeline runs only when `getSide() == Side.SERVER && toggles.masterEnabled()`
  (`CommonProxy.java:67`);
- VeinConfig's live verification runs under the same condition (`CommonProxy.java:84`);
- the self-test is scheduled when the master switch and `runSelfTest` are on (`CommonProxy.java:88`); each check
  then guards itself.

`ClientProxy` is eight lines and overrides nothing. Its comment says a future client override must call `super`.
Because the registry, the config and the banner are identical on both sides, a client and a server read the same
keys.

### 5. Improvements are optional by construction

The pollution visuals are the model. The server always sends the real number. Only a Patchee client turns the
render flags and tint tables off. A stock client still draws GregTech's smog, and that is accepted and documented.
The server is complete without the client.

## Why "both sides" is sometimes needed

1.7.10 is not server-authoritative for everything. Two cases:

- **Breaking blocks.** The client accumulates break progress in `PlayerControllerMP` and sends the dig packet at
  `1.0F`; the server only validates a `0.7F` minimum. A floor applied only on the server changes nothing a player
  feels. `AuroraBreak`, `ArcticFurBreak` and `FinalCastleBreak` therefore apply it on both sides. A stock client
  keeps the stock speed; the server half stops the server refusing the faster break.
- **Key state.** IC2 and GraviSuite send their own key-state packets. A patched server and an unpatched client
  disagree in both directions. The same jar on both sides is the fix, and a stock client is still playable.

## How each shape is proven

- **Server side:** the CI smoke job, `Server smoke test (self-test must pass)` in `.github/workflows/ci.yml`. It
  boots `runServer` with `runSelfTest=true`, waits for `[SelfTest] result:`, and fails on a missing arm line or any
  `[SelfTest] FAIL`. It greps the Dolly, Mattock, VeinConfig, Aurora, ArcticFur, TeamChunkCap, FinalCastle and
  GlassBreak-skip lines.
- **Client-only:** the class is client-only and the feature's own side guard. `LightOverlayTransformer` name-gates
  on `codechicken.nei.WorldOverlayRenderer`; a dedicated server never loads it, so the transformer never fires and
  the feature logs its skip. `PollutionVisualsTest` proves the client decision without a game; CI only sees the
  dedicated-server skip line.
- **The stock-client join:** a real client connection to a server running the new jar, with no Patchee on the
  client, and a clean `latest.log` on both sides. There is no automated substitute. In dev, `./gradlew runServer`
  plus `./gradlew runClient` is the closest available, and the client must not be given the jar.

## What breaks client optionality

Any of these is out of scope, even if it "works" on a patched client:

- registering a packet, a channel or a `NetworkRegistry` handler;
- requiring a client mod (a hard `@Mod` dependency, an unguarded client-only class, or an item/block the client
  must have to render);
- changing the block or item registration set, which changes the registry a client syncs against;
- changing world data or metadata a stock client must interpret;
- patching a class that exists only on one side without a side guard or a name gate;
- an event handler that runs on both sides and mutates state only the server owns.

## See also

- [PIPELINE.md](PIPELINE.md) — how a step is run.
- [TRANSFORMERS.md](TRANSFORMERS.md) — the byte layer.
- [../guide/CLIENT.md](../guide/CLIENT.md) — what a player gets from a client copy.
