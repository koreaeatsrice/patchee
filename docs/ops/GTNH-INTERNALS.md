# GT:NH / 1.7.10 internals

This page collects the 1.7.10 and GT:New Horizons facts that trip up a contributor. It is not a general Forge
tutorial. Every item here has bitten this repo or is required to read its transformers.

## 1. The toolchain in one table

| Setting | Value | Where |
|---|---|---|
| Minecraft | 1.7.10 | `gradle.properties` (`minecraftVersion`) |
| Forge | 10.13.4.1614 | `gradle.properties` (`forgeVersion`) |
| MCP channel | `stable` | `gradle.properties` (`channel`) |
| MCP mappings | `12` | `gradle.properties` (`mappingsVersion`) |
| Java syntax | Jabel (`enableModernJavaSyntax = jabel`) | `gradle.properties` |
| Coremod class | `asm.FMLPlugin` | `gradle.properties` (`coreModClass`) |
| Mixins | Off (`usesMixins = false`) | `gradle.properties` |

Patchee does not use Mixins. The class changes are plain `IClassTransformer`s, registered by `FMLPlugin`. If you
add a Mixin, you are changing the build's contract and the review surface; do not do it for a small fix.

## 2. Two sets of names

At runtime, Minecraft's own classes and members have two names depending on the environment:

- **Development / deobfuscated (MCP names).** `./gradlew runServer` and `./gradlew runClient` run a deobfuscated
  environment. You see `World.setBlock`, `EntityPlayer.getCommandSenderName`, and so on. The dev dependency jars
  from the GTNH maven (`...:dev`) carry MCP names.
- **Production / obfuscated (SRG names).** A released jar runs against obfuscated Minecraft. Methods are
  `func_12345_a`, fields are `field_12345_a`. A mod compiled for production calls those SRG names. The released
  jars on the server (ServerUtilities, Twilight Forest, IC2) carry SRG names.

`tools/matcher-check/README.md` states this directly for the castle check: the live bytes use SRG member names,
the 2.7.41 dev dependency uses MCP member names, and both are tested because real deployments differ only in
member names.

What this means for a patch:

- **Do not match on a Minecraft member name.** It differs between the two environments. Match on the instruction
  shape, the opcodes, the descriptors of the mod's own methods, or a constant.
- **A mod's own class and member names are stable.** `serverutils.data.ServerUtilitiesTeamData`,
  `ic2.core.item.armor.ItemArmorQuantumSuit` and `gravisuite.ItemAdvancedJetPack` are the same in both
  environments. That is why the target classes can be named in code.
- **Class names are readable in both environments.** FML remaps the obfuscated class names to names such as
  `net.minecraft.world.World`, which is why a crash report on a production server shows readable class names and
  `func_*` members. Members are the part that differs: SRG in production, MCP in dev. Still, match the mod's own
  class, not a Minecraft member, when a patch has a choice.
- **Reflection on a Minecraft member is environment-dependent.** If you must reflect on one, verify the name in
  the environment you will run in. Prefer reflecting on the patched mod's own members.

`func_*` and `field_*` are SRG names, not "the obfuscated names" in a loose sense. `javap` on a production jar
shows them. `javap` on a dev jar shows MCP names.

## 3. Forge coremod loading order

1. FML discovers coremods before it constructs mods. Patchee's coremod is declared through the
   `FMLCorePlugin` manifest attribute that the GTNH build writes from `coreModClass = asm.FMLPlugin`.
2. Each coremod's `IFMLLoadingPlugin` is constructed and `injectData(Map)` is called during loading, before mod
   construction.
3. The transformers returned by `getASMTransformerClass()` are installed in the class loader. They run when a
   class is loaded, not at a lifecycle event.
4. Mod construction and the lifecycle follow: every mod's `preInit`, then every mod's `postInit`, then server
   start.

Consequences this repo depends on:

- **The order between two coremods is not a contract.** GregTech reads `config/GregTech/Pollution.cfg` in its own
  coremod's static initializer. The comment in `FMLPlugin.java` and `PollutionPreload.java` records that FML runs
  that initializer before Patchee's `injectData`. So Patchee's file rewrite lands **after** GregTech has read the
  file: the new values take effect on the next boot. The in-memory pass at `postInit` is what fixes the current
  boot. Do not "fix" this by moving the write earlier; there is no earlier hook.
- **A transformer cannot read Forge's config.** Forge's `Configuration` does not exist when the class loads.
  `LightOverlaySettings` and `TeamChunkCapSettings` parse `config/patchee.cfg` themselves for exactly this reason.
- **A transformer cannot use `Toggles`.** The snapshot is built in `preInit`. A transformer may use a static
  holder that the feature later republishes, as `TeamChunkCap` does.
- **A mod's own config load can precede yours.** If your feature writes a file another mod reads in its
  `preInit`, you need `early()` and a `before:<modid>` dependency, as VeinConfig does.

Two jars with the same `modid` abort the boot. FML detects the duplicate and stops with a duplicate-mod error
before the game starts. There is no "last one wins". This is why the install docs say to keep exactly one
`patchee-*.jar` in `mods/`.

## 4. Reflection or ASM

| You need to change | Use | Why |
|---|---|---|
| A static field's value (a list, an array, a boolean) | Reflection | The field is reachable at runtime; no bytecode edit. |
| A registry entry, a recipe, a config file | The mod's API, a Forge registry, or a file write | No bytecode involved. |
| React to an event | A `@SubscribeEvent` handler on the right bus | No bytecode involved. |
| A constant inside a method | ASM | A constant is an operand, not a field. |
| A return value inside a method | ASM | There is no hook; the value is computed and returned. |
| A constructor argument inside a method | ASM | Same. |
| A branch that does not exist | ASM | Reflection cannot add control flow. |

Reflection is preferred: it is readable, testable, and fails with a named exception. ASM is the exception, used
only when the thing to change has no runtime representation.

The costs of ASM, and what this repo does about them:

- **Verification.** A wrong stack map frame or a bad local variable index makes the JVM reject the class at load.
  Patchee changes operands where possible, leaves frames alone where it injects, and relies on the JVM verifier
  plus the dev server to prove the patched class loads.
- **Shape drift.** A new mod build changes the bytecode and the matcher stops firing. A matcher that never fires
  is silent. Every transformer reports its site count, and every matcher tool asserts it.
- **Idempotence.** The loader can transform a class more than once in some setups. Every patch detects its own
  output and returns the input unchanged.
- **Breadth.** A transformer sees every class. The name gate must be the first check, and a non-target class must
  come back byte-identical.

## 5. Why the client and the server can disagree

1.7.10 is not a server-authoritative game for everything. The important cases:

- **Breaking blocks.** The client is the clock. `PlayerControllerMP` accumulates break progress and sends
  `C07PacketPlayerDigging(2)` when progress reaches `1.0F`. The server only validates a `0.7F` minimum in
  `ItemInWorldManager.uncheckedTryHarvestBlock`. A floor applied only on the server changes nothing a player
  feels. That is why `AuroraBreak`, `ArcticFurBreak` and `FinalCastleBreak` apply their break-speed floor on
  **both** sides. A client without Patchee keeps the stock speed; the server half is what stops the server
  refusing the faster break.
- **Key state.** IC2 and GraviSuite send their own key-state packets to the server. A patched server and an
  unpatched client disagree in both directions, not just the climb. The same jar on both sides is the fix.
- **Potion effects and movement.** A server can clear an effect; a client without the mod can still render it and
  predict movement differently. That is the documented tank-effect mismatch.
- **Client-only classes.** `@SideOnly(Side.CLIENT)` classes do not exist on a dedicated server. Calling one, or
  resolving a method whose signature mentions one, throws `NoClassDefFoundError`. `SelfTest` records a live case:
  resolving `ItemBarrelMover.isTEMovable` pulled in `IIconRegister` and died on a dedicated server, so the test
  runs the gate's own loop instead. A transformer or a reflection helper that touches a client-only signature must
  be side-guarded.
- **Event buses.** Forge's bus (`MinecraftForge.EVENT_BUS`) carries `PlayerEvent.BreakSpeed` and block events;
  FML's bus carries lifecycle and tick events. Registering on the wrong bus means the handler never runs.
- **The tick versus the event.** `FMLServerStartedEvent` fires while the universe is still loading for some mods.
  ServerUtilities builds its claim data there, and the order of two handlers for one event is arbitrary, so
  `SelfTest` waits for the first server tick (`SelfTest.java:80-120`).

A feature that changes gameplay must say which side it runs on and what a stock client sees.
`docs/internals/SIDES.md` is the contract.

## 6. Inspecting a candidate mod jar

Never guess a class, field or method. Inspect the jar the pack actually ships.

```sh
# List the classes without unpacking.
unzip -l ServerUtilities-2.4.9.jar | grep -i 'TeamData\|ClaimedChunks'

# Show a class's members and descriptors.
javap -p -classpath ServerUtilities-2.4.9.jar serverutils.data.ServerUtilitiesTeamData

# Show the bytecode of the methods you intend to match.
javap -p -c -classpath ServerUtilities-2.4.9.jar serverutils.data.ServerUtilitiesTeamData

# Show generic signatures when a field is a List or a Map.
javap -p -s -classpath ServerUtilities-2.4.9.jar serverutils.data.ServerUtilitiesTeamData

# Find a string constant or a log message in a class file.
unzip -p SomeMod.jar path/to/Class.class | strings | grep -i 'something'
```

For a jar whose classes are in a non-standard layout, extract it:

```sh
mkdir -p /tmp/modjar && cd /tmp/modjar && unzip -q /path/to/Mod.jar
javap -p -c path/to/Class.class
```

`javap -c` prints SRG names when the jar is a production jar and MCP names when it is a dev jar. That is expected,
not a sign that you have the wrong class.

To check a transformer against real bytes, use `tools/matcher-check/`:

- `MatcherCheck.java` — ServerUtilities `ServerUtilitiesTeamData`; expects `RESULT: 8/8 PASS`.
- `CastleCheck.java` — Twilight Forest `BlockTFCastleBlock` and `BlockTFThorns`; expects `RESULT: 10/10 PASS`.
- `ChestplateCheck.java` — IC2 `ItemArmorQuantumSuit`; expects `RESULT: 18/18 PASS`.

The README gives the full `javac` and `java` classpaths and the live-versus-dev caveat. After a patch, `javap -p
-c` on the patched class is a cheap eyeball check; the JVM verifier and a dev server are the real proof.

## 7. Licence position, short form

- Patchee is MIT (`LICENSE`). A contribution is accepted under MIT.
- Twilight Forest (the modern 1.20.1 mod and the old 1.7.10 mod) is LGPL-2.1-or-later for code. The modern mod's
  art is CC BY-NC-SA 4.0 and its sounds are all rights reserved. The GTNH 1.7.10 fork follows the same split.
- IC2 and GraviSuite ship closed source. Patchee never copies their code; it matches the shape of their compiled
  methods and refuses a class it does not recognise.
- GregTech, Forestry, Gendustry, MagicBees and Pam's HarvestCraft are other people's work. Patchee reflects on
  their runtime objects or rewrites their bytecode at load; it does not vendor them.

The rule that follows: **never copy another mod's source, art or sound into this repo, including
`src/test/resources/`.** Re-implement the behaviour, or match the bytecode of a jar the user already owns. If you
are unsure whether a snippet is a copy, do not commit it. A copied LGPL file would put copyleft on a file that is
currently MIT, and a copied texture or sound is barred outright.

Extracted class files in `src/test/resources/finalcastlebreak/` are test inputs for the matcher, not a
redistribution of the mod. Do not add a whole third-party jar to the repo; use an environment-variable gate like
`PATCHEE_GREGTECH_JAR` (`PollutionVisualsRealJarTest`) when the real jar is needed for a test.

## 8. Small 1.7.10 facts that save an hour

- `Loader.isModLoaded("modid")` is the cheap presence check for a mod. `Class.forName` is the check for a class,
  and it can throw `LinkageError` as well as `ClassNotFoundException`; `Reflective.find` catches both.
- `FMLCommonHandler.instance().getSide()` tells you the physical side. `world.isRemote` tells you the logical
  side of a world object. They are not the same, and a dedicated server has only the server side.
- A `PlayerEvent.BreakSpeed` event can arrive with `y < 0`: Forge Multipart reports its wrapped block at
  `(0,-1,0)`. Read the block's own hardness, not the world, in that case (`features/BreakHardness.java`).
- Block wrappers (Forge Multipart, ArchitectureCraft, Carpenter's Blocks, LittleTiles) compute break progress from
  the block they wrap, so the event already carries the Twilight Forest block. Do not add per-wrapper unwrapping;
  `features/WrapperCoverage.java` is the researched table, not a hook.
- `TileEntity` NBT is attacker-controlled in the sense that a crafted world can contain anything. Validate a class
  before appending it to a movable list, as `DollyFeature` does with `TileEntity.class.isAssignableFrom`.
- A config file write must keep the original. Patchee writes a timestamped `.bak` next to GregTech's config and
  changes only its own lines. Do not rewrite a whole file from a template.
- `runServer` runs a dedicated server. A client-only feature will log its skip line there, and CI will only ever
  see that line. That is why `PollutionVisualsTest` exists and why `DEVELOPER.md` says the client decision is
  proven by a unit test, not by the smoke job.
