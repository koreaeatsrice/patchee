package com.jointspaceforce.patchee.features;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.stats.Achievement;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;

import com.jointspaceforce.patchee.Patchee;
import com.jointspaceforce.patchee.core.Feature;
import com.jointspaceforce.patchee.core.Outcome;
import com.jointspaceforce.patchee.core.PatchContext;
import com.jointspaceforce.patchee.core.Step;
import com.jointspaceforce.patchee.core.Steps;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Backports the modern Twilight Forest "earned" break speed for the aurora
 * blocks (aurora block, pillar, slab and double slab).
 *
 * <p>
 * Modern Twilight Forest ({@code 1.20.1}, {@code AuroraBrickBlock}) returns a
 * flat destroy progress per tick once the player has the Glacier progression
 * ({@code progress_glacier}). The 1.7.10 pack has no such rule. This feature adds
 * it as a <b>floor</b> — never a slowdown: the vanilla speed is kept and only
 * raised when it is slower than the modern one, exactly the owner's rule ("keep
 * today's speed, add the fast version on top"). The floor is {@code 0.1} progress
 * per tick (10 ticks), the same floor {@link FinalCastleBreakFeature} uses, so
 * the aurora blocks break at the castle's "deepslate speed". It is gated
 * <b>per player</b>: only a player who has earned the Glacier progression sees
 * it, evaluated on every break.
 *
 * <p>
 * The arm step also marks each resolved block pickaxe-harvestable with
 * {@code Block.setHarvestLevel("pickaxe", 0)}. The aurora blocks are
 * {@code Material.packedIce} and set no harvest tool of their own, so the
 * Tinkers' Construct hammer's 3x3 pass skipped them: that pass tests
 * {@code HarvestTool.isEffective(block, meta)}, which is true when
 * {@code "pickaxe".equals(block.getHarvestTool(meta))} <i>or</i> when the
 * material is one of the hammer's own list (rock, iron, ice, glass, piston,
 * anvil). The castle blocks escape the skip only because their material
 * ({@code Material.rock}) is on that list — not because of any tool. Marking the
 * aurora blocks with the pickaxe tool makes the hammer's test true, so its 3x3
 * area-mine works on them. Level {@code 0} accepts any pickaxe and changes the
 * drop rule nowhere: for every held item that is not a pickaxe the harvest check
 * falls back to the very same {@code player.canHarvestBlock(block)} the unset
 * tool took.
 *
 * <p>
 * The 1.7.10 equivalent of the Glacier progression is the achievement
 * {@code twilightforest.TFAchievementPage.twilightProgressGlacier} (id
 * {@code TwilightForest24}, the Snow Queen trophy) — the same achievement the
 * pack already uses to unprotect the Aurora Palace. The four legacy blocks live
 * in {@code twilightforest.block.TFBlocks} as {@code auroraBlock},
 * {@code auroraPillar}, {@code auroraSlab} and {@code auroraDoubleSlab}, all
 * hardness {@code 2.0F} with no break-speed override; they are read by
 * reflection so Patchee keeps no hard dependency on Twilight Forest. The break is
 * matched by the block's <b>class</b> (with the resolved instances kept as an OR
 * fallback), so a metadata variant, a GT:NH re-registration or a slab's double
 * variant still hits the floor.
 *
 * <p>
 * The floor is applied on <b>both sides</b>, because in 1.7.10 the client is the
 * clock: {@code PlayerControllerMP} accumulates the break progress and, when it
 * reaches {@code 1.0F}, sends {@code C07PacketPlayerDigging(2, ...)} ("I finished
 * digging"). The server only agrees — {@code NetHandlerPlayServer.processPlayerDigging}
 * hands that packet to {@code ItemInWorldManager.uncheckedTryHarvestBlock}, which
 * breaks only when the elapsed progress is at least {@code 0.7F}. So a floor applied
 * only on the server changes nothing a real player feels. It is applied by listening
 * for {@code PlayerEvent.BreakSpeed} on the Forge bus (not the FML bus, which only
 * carries tick events) and rewriting {@code newSpeed}. The {@link #raiseBreakFloor}
 * helper is shared with {@link ArcticFurBreakFeature}.
 *
 * <p>
 * The speed-up is therefore seen by players who have Patchee <b>on their client</b>,
 * the same deal as Patchee's NEI F7 light overlay; the server half is what keeps the
 * two sides in step (it stops the server's own {@code 0.7F} validation from rejecting
 * a fast client). The Glacier gate is read per player through {@link GlacierGate}.
 */
public final class AuroraBreakFeature implements Feature {

    public static final String ID = "AuroraBreak";

    private static final String TARGET_CLASS = "twilightforest.block.TFBlocks";
    private static final String ACHIEVEMENT_CLASS = "twilightforest.TFAchievementPage";
    private static final String ACHIEVEMENT_FIELD = "twilightProgressGlacier";

    /** The four legacy aurora blocks, by their {@code TFBlocks} field names. */
    public static final String[] AURORA_FIELDS = { "auroraBlock", "auroraPillar", "auroraSlab", "auroraDoubleSlab" };

    /** The aurora break rate, matching the castle: 0.1/tick -> 10 ticks. */
    static final float FLOOR = 0.1F;

    /** The harvest tool the Tinkers' Construct hammer's 3x3 pass tests for. */
    static final String PICKAXE = "pickaxe";

    /** Any pickaxe qualifies; the level only has to exist so the tool is reported. */
    static final int PICKAXE_LEVEL = 0;

    /**
     * {@code ForgeHooks.blockStrength} divides the speed by the hardness and by
     * 30 when the player can harvest the block, otherwise by 100. Reusing those
     * divisors turns a speed back into a progress rate.
     */
    static final float HARVESTED_DIVISOR = 30.0F;
    static final float UNHARVESTED_DIVISOR = 100.0F;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String configKey() {
        return "enableAuroraBreak";
    }

    @Override
    public boolean defaultEnabled() {
        return true;
    }

    @Override
    public String description() {
        return "Feature: Twilight Forest aurora blocks (block, pillar, slab and double slab) break in at most "
            + "10 ticks (a floor of 0.1 per tick, the same as the Final Castle) for a player who has earned the "
            + "Glacier progress (the Snow Queen), and are marked pickaxe-harvestable so the Tinkers' Construct "
            + "hammer's 3x3 area-mine works on them; a player without the progress breaks them at the plain "
            + "1.7.10 speed. false = the plain speed for everyone.";
    }

    @Override
    public String disabledMessage() {
        return "[AuroraBreak] disabled in config — aurora blocks left as they are";
    }

    @Override
    public String failureMessage() {
        return "[AuroraBreak] failed — aurora blocks left as they are";
    }

    @Override
    public String targetClass() {
        return TARGET_CLASS;
    }

    @Override
    public String missingTargetMessage() {
        return "[AuroraBreak] Twilight Forest is not installed — aurora break speed skipped";
    }

    @Override
    public List<Step> steps() {
        List<Block> aurora = new ArrayList<Block>();
        Achievement[] glacier = new Achievement[1];
        return Collections.singletonList(Steps.sequence(resolveAurora(aurora, glacier), armAurora(aurora, glacier)));
    }

    /**
     * Step one: read the four aurora blocks and the Glacier achievement. A
     * missing field is reported loudly and the block is left out; the achievement
     * is the gate, so when it cannot be read the rule cannot be applied honestly
     * and the whole feature fails. The resolved block <b>class names</b> are
     * logged so a mismatch with a placed block is visible in the log.
     */
    private Step resolveAurora(List<Block> aurora, Achievement[] glacier) {
        return ctx -> {
            Class<?> tfBlocks = ctx.reflect()
                .find(TARGET_CLASS);
            int found = 0;
            for (String name : AURORA_FIELDS) {
                Block block = readStaticBlock(ctx, tfBlocks, name);
                if (block != null) {
                    aurora.add(block);
                    found++;
                } else {
                    ctx.log()
                        .error("[AuroraBreak] TFBlocks.{} is missing — that block is not covered", name);
                }
            }

            if (found == 0) {
                ctx.log()
                    .error("[AuroraBreak] no aurora blocks resolved — not arming");
                return Outcome.FAILED;
            }
            ctx.log()
                .info("[AuroraBreak] resolved {}/4 aurora blocks, matched by class: {}", found, classNames(aurora));

            Class<?> pageClass = ctx.reflect()
                .find(ACHIEVEMENT_CLASS);
            if (pageClass == null) {
                ctx.log()
                    .error(
                        "[AuroraBreak] {} is missing — the Glacier gate cannot be read, not arming",
                        ACHIEVEMENT_CLASS);
                return Outcome.FAILED;
            }
            try {
                Field field = ctx.reflect()
                    .staticField(pageClass, ACHIEVEMENT_FIELD);
                Object value = ctx.reflect()
                    .staticValue(field);
                if (!(value instanceof Achievement)) {
                    ctx.log()
                        .error(
                            "[AuroraBreak] {}#{} is not an achievement — the Glacier gate cannot be read, not arming",
                            ACHIEVEMENT_CLASS,
                            ACHIEVEMENT_FIELD);
                    return Outcome.FAILED;
                }
                glacier[0] = (Achievement) value;
            } catch (NoSuchFieldException | IllegalAccessException missingGate) {
                ctx.log()
                    .error(
                        "[AuroraBreak] {}#{} is missing — the Glacier gate cannot be read, not arming",
                        ACHIEVEMENT_CLASS,
                        ACHIEVEMENT_FIELD,
                        missingGate);
                return Outcome.FAILED;
            }
            return Outcome.APPLIED;
        };
    }

    /**
     * Step two: mark each block pickaxe-harvestable (so the Tinkers' Construct
     * hammer's 3x3 pass includes it), put the break-speed floor on the Forge bus
     * and report both.
     */
    private Step armAurora(List<Block> aurora, Achievement[] glacier) {
        return ctx -> {
            int pickaxed = 0;
            for (Block block : aurora) {
                if (markPickaxeHarvestable(block)) {
                    pickaxed++;
                } else {
                    ctx.log()
                        .warn(
                            "[AuroraBreak] could not mark {} pickaxe-harvestable — the Tinkers' hammer will not "
                                + "area-mine it",
                            block.getClass()
                                .getName());
                }
            }
            MinecraftForge.EVENT_BUS.register(new Handler(aurora, classNames(aurora), glacier[0]));
            ctx.log()
                .info(
                    "[AuroraBreak] armed — {}/{} aurora blocks marked pickaxe-harvestable (TiC hammer 3x3 works) and "
                        + "they break in at most 10 ticks (floor 0.1/tick, same as the castle) once the Glacier "
                        + "progress is earned",
                    pickaxed,
                    aurora.size());
            return Outcome.APPLIED;
        };
    }

    /**
     * Marks one block pickaxe-harvestable so the Tinkers' Construct hammer's 3x3
     * pass includes it. {@code Block.setHarvestLevel} is a Forge addition to
     * {@link Block} that writes the harvest-tool/level arrays; it is safe to call
     * at arm time and, with level {@code 0}, changes the drop rule for no held
     * item that is not a pickaxe (the harvest check falls back to the same
     * {@code player.canHarvestBlock(block)} an unset tool takes). A failure — an
     * older {@code Block} without the method — is reported, never thrown.
     */
    static boolean markPickaxeHarvestable(Block block) {
        if (block == null) {
            return false;
        }
        try {
            block.setHarvestLevel(PICKAXE, PICKAXE_LEVEL);
            return true;
        } catch (Throwable unsupported) {
            return false;
        }
    }

    /** The class names of the resolved blocks, for the class-name matcher. */
    static Set<String> classNames(List<Block> blocks) {
        return BlockClassMatcher.classNames(blocks);
    }

    /** A declared static {@code Block} field of {@code owner}, or {@code null} when it is not one. */
    static Block readStaticBlock(PatchContext ctx, Class<?> owner, String name) {
        if (owner == null) {
            return null;
        }
        try {
            Object value = ctx.reflect()
                .staticValue(
                    ctx.reflect()
                        .staticField(owner, name));
            return value instanceof Block ? (Block) value : null;
        } catch (NoSuchFieldException | IllegalAccessException absent) {
            return null;
        }
    }

    /**
     * The floor arithmetic both Patchee break-speed features rely on: the break
     * speed whose vanilla result is exactly {@code floor} progress per tick. Kept
     * pure (no game types) so it is unit-tested on its own.
     */
    static float floorSpeed(float hardness, boolean canHarvest, float floor) {
        float divisor = canHarvest ? HARVESTED_DIVISOR : UNHARVESTED_DIVISOR;
        return floor * hardness * divisor;
    }

    /**
     * Raises {@code event.newSpeed} so the block cannot break slower than
     * {@code floor} progress per tick. It only ever raises the speed — the
     * vanilla value is kept when it is already fast enough.
     */
    static void raiseBreakFloor(PlayerEvent.BreakSpeed event, float floor) {
        float hardness = event.block.getBlockHardness(event.entityPlayer.worldObj, event.x, event.y, event.z);
        if (hardness <= 0.0F) {
            return; // unbreakable, or no block there
        }
        boolean canHarvest = ForgeHooks.canHarvestBlock(event.block, event.entityPlayer, event.metadata);
        float needed = floorSpeed(hardness, canHarvest, floor);
        if (event.newSpeed < needed) {
            event.newSpeed = needed;
        }
    }

    /** The Forge-bus listener; the floor is applied on both sides (the client is the clock). */
    public static final class Handler {

        private final List<Block> aurora;
        private final Set<String> classes;
        private final Achievement glacier;
        private final FloorNotices notices = new FloorNotices(ID);

        /** Log the first handler failure only — a broken environment must not spam. */
        private volatile boolean warned = false;

        Handler(List<Block> aurora, Set<String> classes, Achievement glacier) {
            this.aurora = aurora;
            this.classes = classes;
            this.glacier = glacier;
        }

        @SubscribeEvent
        public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
            try {
                EntityPlayer player = event.entityPlayer;
                if (player == null || player.worldObj == null) return;
                if (event.y < 0) return; // unknown location: hardness cannot be read
                Block block = event.block;
                if (block == null) return;
                // Class match first (survives variants / re-registrations); the
                // resolved instances stay as an OR fallback so nothing regresses.
                if (!BlockClassMatcher.matchesClass(classes, block.getClass()) && !aurora.contains(block)) return;
                if (!GlacierGate.earned(player, glacier)) {
                    notices.deniedOnce(player.getCommandSenderName(), "Glacier not earned");
                    return;
                }
                float before = event.newSpeed;
                raiseBreakFloor(event, FLOOR);
                if (event.newSpeed > before) {
                    notices.appliedOnce(
                        player.getCommandSenderName(),
                        block.getClass()
                            .getName(),
                        before,
                        event.newSpeed,
                        event.x,
                        event.y,
                        event.z);
                }
            } catch (Exception | LinkageError failure) {
                // Never break a block-break over this; the floor simply does nothing.
                if (!warned) {
                    warned = true;
                    Patchee.LOG
                        .warn("[AuroraBreak] break-speed handler failed once — the fix will keep trying", failure);
                }
            }
        }
    }
}
