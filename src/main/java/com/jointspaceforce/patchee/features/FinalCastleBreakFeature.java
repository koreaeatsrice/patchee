package com.jointspaceforce.patchee.features;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.stats.Achievement;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;

import com.jointspaceforce.patchee.Patchee;
import com.jointspaceforce.patchee.core.Feature;
import com.jointspaceforce.patchee.core.Outcome;
import com.jointspaceforce.patchee.core.PatchContext;
import com.jointspaceforce.patchee.core.Step;
import com.jointspaceforce.patchee.core.Toggles;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;

/**
 * Lets a player who has earned the Glacier progression take the Final Castle
 * apart: the castle bricks stop draining the pickaxe, the thorn wall can be
 * broken for good, and all three castle/thorn blocks break at the modern speed.
 *
 * <p>
 * {@code BlockTFCastleBlock} deals the pickaxe <b>16x</b> durability damage
 * (a {@code for (i &lt; 16)} loop around {@code Item.onBlockDestroyed} — hardness
 * 100, so mining a castle is a pickaxe shredder), and {@code BlockTFThorns}
 * cannot be removed by a survival player at all: it grows straight back and
 * bursts more thorns. The owner's rule is the same progression idea
 * {@link AuroraBreakFeature} uses for the aurora blocks: a player who holds the
 * Glacier achievement (the Snow Queen trophy,
 * {@code twilightforest.TFAchievementPage.twilightProgressGlacier}, id
 * {@code TwilightForest24}) has already beaten the castle and should be able to
 * take it down by hand.
 *
 * <p>
 * There are two halves. The <b>break speed</b> half is the same flat
 * {@code 0.1}-per-tick floor {@link AuroraBreakFeature} uses, applied to the
 * castle bricks ({@code castleBlock}), the castle magic blocks
 * ({@code castleMagic}), the thorn wall ({@code thorns}) and Weathered Deadrock
 * ({@code deadrock}) on <b>both sides</b>
 * (in 1.7.10 the client is the clock), by a {@code PlayerEvent.BreakSpeed}
 * listener on the Forge bus — it only ever raises the speed, never slows a block
 * down. The <b>behaviour</b> half is changed by
 * {@link com.jointspaceforce.patchee.asm.FinalCastleBreakTransformer} while the
 * Twilight Forest classes load, so this feature's resolve and arm steps own the
 * config switch, the achievement the gate reads, the boot line, and the
 * break-speed listener.
 *
 * <p>
 * The class-load behaviour change takes effect on the server (both patched
 * methods are the server's own block behaviour; the transformer itself has no
 * side guard — only this feature's ARMED line is side-gated), and the break-speed
 * floor is registered on both sides, exactly like {@link AuroraBreakFeature}.
 */
public final class FinalCastleBreakFeature implements Feature {

    public static final String ID = FinalCastleBreak.ID;

    private static final String TF_BLOCKS = "twilightforest.block.TFBlocks";
    private static final String ACHIEVEMENT_CLASS = "twilightforest.TFAchievementPage";
    private static final String ACHIEVEMENT_FIELD = "twilightProgressGlacier";

    /**
     * The Twilight Forest blocks this feature's break-speed floor covers, by
     * their {@code TFBlocks} field names: the castle bricks, the castle magic
     * blocks, the thorn wall and Weathered Deadrock ({@code deadrock}). Each is
     * matched by its <b>class</b>, so a metadata variant or a different instance
     * of the same class still hits the floor — Weathered Deadrock is one block
     * ({@code twilightforest.block.BlockTFDeadrock}) whose meta 0 is "Weathered
     * Deadrock", meta 1 "Cracked Deadrock" and meta 2 "Deadrock", and the class
     * match covers all three.
     */
    public static final String[] BLOCK_FIELDS = { "castleBlock", "castleMagic", "thorns", "deadrock" };

    /** Modern Twilight Forest's flat break rate for the castle blocks (0.1/tick -> 10 ticks). */
    static final float FLOOR = 0.1F;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String configKey() {
        return FinalCastleBreak.ENABLE_KEY;
    }

    @Override
    public boolean defaultEnabled() {
        return FinalCastleBreak.DEFAULT_ENABLED;
    }

    @Override
    public String description() {
        return "Feature: once a player has earned the Twilight Forest Glacier progress (the Snow Queen), the Final "
            + "Castle stops fighting back — the castle bricks, the castle magic blocks, the thorn wall and "
            + "Weathered Deadrock break in at most 10 ticks (a floor of 0.1 per tick, per player), the bricks deal "
            + "the pickaxe no extra durability damage (stock 1.7.10 drains it 16x), and the thorn wall can be "
            + "broken for good instead of growing back and multiplying. Players without the achievement keep the "
            + "stock behaviour. The behaviour change takes effect on the server; the break speed is seen on a "
            + "client that has Patchee.";
    }

    @Override
    public String disabledMessage() {
        return "[FinalCastleBreak] disabled in config — Twilight Forest's castle left as it is";
    }

    @Override
    public String failureMessage() {
        return "[FinalCastleBreak] failed — Twilight Forest's castle left as it is";
    }

    @Override
    public String targetClass() {
        return TF_BLOCKS;
    }

    @Override
    public String missingTargetMessage() {
        return "[FinalCastleBreak] Twilight Forest is not installed — castle and thorn behaviour skipped";
    }

    @Override
    public List<Step> steps() {
        List<Block> castle = new ArrayList<Block>();
        return Arrays.asList(resolveTargets(castle), arm(castle));
    }

    /** Which boot line the arm step writes; split out so it is unit-testable. */
    enum ArmReport {

        /** Both targets' call sites were really patched this boot: the armed line goes out. */
        ARMED,

        /** At least one target was present but its shape was not patched: the ERROR line goes out. */
        NOT_ARMED
    }

    /**
     * The armed-line policy, kept pure so a unit test can pin it without a game:
     * the armed line is written only after
     * {@link com.jointspaceforce.patchee.asm.FinalCastleBreakTransformer} really
     * patched <i>both</i> targets' call sites. On a Twilight Forest build whose
     * shape the patch does not recognise the target classes are still
     * <i>present</i> (so the missing-target decorator does not fire), but nothing
     * was changed — the boot line must say so at ERROR instead of claiming the
     * feature is armed. One patched half is not enough: a boot where the castle
     * bricks patched but the thorn wall was refused must NOT say "armed".
     */
    static ArmReport armReport() {
        return FinalCastleBreak.patchApplied() ? ArmReport.ARMED : ArmReport.NOT_ARMED;
    }

    /**
     * One line naming the half the transformer did not patch, for the NOT-armed
     * ERROR. Kept pure and package-private so a unit test can pin it: the boot
     * line must name <i>which</i> half is missing, not just that one is.
     */
    static String missingHalf() {
        boolean castle = FinalCastleBreak.patchedCastle();
        boolean thorns = FinalCastleBreak.patchedThorns();
        if (castle && thorns) {
            return "both targets are patched";
        }
        if (!castle && !thorns) {
            return "neither " + FinalCastleBreakPatch.CASTLE_CLASS
                + " nor "
                + FinalCastleBreakPatch.THORNS_CLASS
                + " was patched";
        }
        if (!castle) {
            return "the castle bricks (" + FinalCastleBreakPatch.CASTLE_CLASS + ") were not patched";
        }
        return "the thorn wall (" + FinalCastleBreakPatch.THORNS_CLASS + ") was not patched";
    }

    /**
     * Step one: read the three castle/thorn blocks and the Glacier achievement the
     * hooks and the break-speed floor gate on, and publish the achievement into
     * the holder. A gate that cannot be read must not grant the bypass, so a
     * missing class or field fails the feature and the blocks keep stock
     * behaviour. The three blocks are collected for the break-speed handler; a
     * missing one is reported and left uncovered.
     */
    private Step resolveTargets(List<Block> castle) {
        return ctx -> {
            Class<?> tfBlocks = ctx.reflect()
                .find(TF_BLOCKS);
            int found = 0;
            for (String name : BLOCK_FIELDS) {
                Block block = readStaticBlock(ctx, tfBlocks, name);
                if (block != null) {
                    castle.add(block);
                    found++;
                } else {
                    ctx.log()
                        .warn("[FinalCastleBreak] TFBlocks.{} is missing — that block is not covered", name);
                }
            }
            ctx.log()
                .info(
                    "[FinalCastleBreak] resolved {}/4 Twilight Forest fast-break blocks (castle bricks, castle magic, "
                        + "thorn wall, Weathered Deadrock), matched by class: {}",
                    found,
                    BlockClassMatcher.classNames(castle));

            Class<?> pageClass = ctx.reflect()
                .find(ACHIEVEMENT_CLASS);
            if (pageClass == null) {
                ctx.log()
                    .error(
                        "[FinalCastleBreak] {} is missing — the Glacier gate cannot be read, not arming",
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
                            "[FinalCastleBreak] {}#{} is not an achievement — the Glacier gate cannot be read, not arming",
                            ACHIEVEMENT_CLASS,
                            ACHIEVEMENT_FIELD);
                    return Outcome.FAILED;
                }
                FinalCastleBreak.publishGlacier((Achievement) value);
            } catch (NoSuchFieldException | IllegalAccessException missingGate) {
                ctx.log()
                    .error(
                        "[FinalCastleBreak] {}#{} is missing — the Glacier gate cannot be read, not arming",
                        ACHIEVEMENT_CLASS,
                        ACHIEVEMENT_FIELD,
                        missingGate);
                return Outcome.FAILED;
            }
            return Outcome.APPLIED;
        };
    }

    /**
     * Step two: put the break-speed floor on the Forge bus (both sides — the client
     * is the clock, exactly like {@link AuroraBreakFeature}), publish the
     * authoritative switches, and report whether the call sites were really
     * patched.
     *
     * <p>
     * The armed line is gated on {@link FinalCastleBreak#patchApplied()} (both
     * targets really patched) <i>and</i> on the logical server: a target class
     * that was merely <i>present</i> is not proof, because the transformer hands
     * an unrecognised shape back untested and logs its own ERROR. In that case
     * this step logs a plain ERROR naming the missing half, and writes no armed
     * line.
     */
    private Step arm(List<Block> castle) {
        return ctx -> {
            Toggles toggles = ctx.toggles();
            FinalCastleBreak.configure(toggles.masterEnabled(), toggles.featureEnabled(ID));
            MinecraftForge.EVENT_BUS.register(new Handler(castle, BlockClassMatcher.classNames(castle)));
            if (FMLCommonHandler.instance()
                .getSide() != Side.SERVER) {
                ctx.log()
                    .info(
                        "[FinalCastleBreak] client half ready — castle bricks, castle magic blocks and the thorn "
                            + "wall break at the modern speed once Glacier is earned; the class-load behaviour patch "
                            + "is the server's half");
                return Outcome.APPLIED;
            }
            if (armReport() != ArmReport.ARMED) {
                ctx.log()
                    .error(
                        "[FinalCastleBreak] NOT armed — the class-load patch is incomplete: {}. The unpatched half "
                            + "keeps its stock behaviour. See the [FinalCastleBreak] shape ERROR above for the "
                            + "observed bytes.",
                        missingHalf());
                return Outcome.FAILED;
            }
            ctx.log()
                .info(
                    "[FinalCastleBreak] armed — once a player earns the Glacier progress, castle bricks deal no extra "
                        + "pickaxe damage and the thorn wall can be broken for good (switches from config/patchee.cfg)");
            return Outcome.APPLIED;
        };
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
     * The Forge-bus listener; the floor is applied on both sides (the client is the
     * clock), to the castle bricks, the castle magic blocks and the thorn wall,
     * and only for a player who has earned the Glacier progress — the gate is read
     * per player on every break, so an unearned player keeps the stock speed. The
     * block is matched by <b>class</b> (with the resolved instances as an OR
     * fallback), so a metadata variant, a GT:NH re-registration or a slab's double
     * variant still hits the floor. The floor arithmetic and the raise are the ones
     * shared with {@link AuroraBreakFeature}.
     */
    public static final class Handler {

        private final List<Block> castle;
        private final Set<String> classes;
        private final FloorNotices notices = new FloorNotices(ID);

        /** Log the first handler failure only — a broken environment must not spam. */
        private volatile boolean warned = false;

        Handler(List<Block> castle, Set<String> classes) {
            this.castle = castle;
            this.classes = classes;
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
                if (!BlockClassMatcher.matchesClass(classes, block.getClass()) && !castle.contains(block)) return;
                if (!FinalCastleBreak.earnedGlacier(player)) {
                    notices.deniedOnce(player.getCommandSenderName(), "Glacier not earned");
                    return;
                }
                float before = event.newSpeed;
                AuroraBreakFeature.raiseBreakFloor(event, FLOOR);
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
                        .warn("[FinalCastleBreak] break-speed handler failed once — the fix will keep trying", failure);
                }
            }
        }
    }
}
