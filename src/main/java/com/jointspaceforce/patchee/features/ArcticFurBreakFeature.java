package com.jointspaceforce.patchee.features;

import java.util.Collections;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemShears;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;

import com.jointspaceforce.patchee.Patchee;
import com.jointspaceforce.patchee.core.Feature;
import com.jointspaceforce.patchee.core.Outcome;
import com.jointspaceforce.patchee.core.Step;
import com.jointspaceforce.patchee.core.Steps;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Backports the modern Twilight Forest "earned" break speed for the arctic fur
 * block while shears are held.
 *
 * <p>
 * Modern Twilight Forest ({@code 1.20.1}, {@code ArcticFurBlock}) returns a flat
 * {@code 0.2F} destroy progress per tick whenever the held item is shears
 * ({@code ShearsItem}) — {@code ItemShears} is checked by type because shears do
 * not advertise {@code IShearable}. The 1.7.10 pack has no such rule. This
 * feature adds it as a <b>floor</b>, never a slowdown, sharing the arithmetic of
 * {@link AuroraBreakFeature#raiseBreakFloor}.
 *
 * <p>
 * The legacy block is {@code twilightforest.block.TFBlocks.arcticFurStorage}, a
 * {@code BlockTFCompressed} of hardness {@code 0.8F}. Note (correcting the
 * spec's premise): the storage types are <b>separate block instances</b>, not
 * metadata variants — {@code TFBlocks} holds one {@code BlockTFCompressed} per
 * type, each built with its own {@code BlockType} field, and
 * {@code BlockTFCompressed} overrides no {@code getSubBlocks}/{@code
 * damageDropped}/{@code getDamageValue}. The metadata of a placed arctic fur
 * block is therefore always {@code 0} and is not the discriminator; the block
 * <b>identity</b> is. The feature matches on identity, so no metadata check is
 * used or needed.
 *
 * <p>
 * The floor is applied on <b>both sides</b>, because in 1.7.10 the client is the
 * clock: {@code PlayerControllerMP} accumulates the break progress and, when it
 * reaches {@code 1.0F}, sends {@code C07PacketPlayerDigging(2, ...)} ("I finished
 * digging"). The server only agrees — {@code NetHandlerPlayServer.processPlayerDigging}
 * hands that packet to {@code ItemInWorldManager.uncheckedTryHarvestBlock}, which
 * breaks only when the elapsed progress is at least {@code 0.7F}. So the speed-up is
 * seen by players who have Patchee <b>on their client</b>, the same deal as Patchee's
 * NEI F7 light overlay, while the server half keeps the two sides in step.
 */
public final class ArcticFurBreakFeature implements Feature {

    public static final String ID = "ArcticFurBreak";

    private static final String TARGET_CLASS = "twilightforest.block.TFBlocks";
    private static final String FIELD_NAME = "arcticFurStorage";

    /** Modern Twilight Forest's flat break rate for arctic fur with shears (0.2/tick -> 5 ticks). */
    static final float FLOOR = 0.2F;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String configKey() {
        return "enableArcticFurBreak";
    }

    @Override
    public boolean defaultEnabled() {
        return true;
    }

    @Override
    public String description() {
        return "Feature: the Twilight Forest arctic fur block breaks at the modern speed (5 ticks) while shears "
            + "are held. false = plain 1.7.10 speed.";
    }

    @Override
    public String disabledMessage() {
        return "[ArcticFurBreak] disabled in config — the arctic fur block is left as it is";
    }

    @Override
    public String failureMessage() {
        return "[ArcticFurBreak] failed — the arctic fur block is left as it is";
    }

    @Override
    public String targetClass() {
        return TARGET_CLASS;
    }

    @Override
    public String missingTargetMessage() {
        return "[ArcticFurBreak] Twilight Forest is not installed — arctic fur break speed skipped";
    }

    @Override
    public List<Step> steps() {
        Block[] fur = new Block[1];
        return Collections.singletonList(Steps.sequence(resolveArcticFur(fur), armArcticFur(fur)));
    }

    /**
     * Step one: read {@code TFBlocks.arcticFurStorage}. A missing field is
     * reported loudly and the feature fails (there is nothing to arm).
     */
    private Step resolveArcticFur(Block[] fur) {
        return ctx -> {
            Class<?> tfBlocks = ctx.reflect()
                .find(TARGET_CLASS);
            Block block = AuroraBreakFeature.readStaticBlock(ctx, tfBlocks, FIELD_NAME);
            if (block == null) {
                ctx.log()
                    .error("[ArcticFurBreak] TFBlocks.{} is missing — arctic fur is not covered", FIELD_NAME);
                return Outcome.FAILED;
            }
            fur[0] = block;
            ctx.log()
                .info("[ArcticFurBreak] resolved the Twilight Forest arctic fur block");
            return Outcome.APPLIED;
        };
    }

    /** Step two: put the break-speed floor on the Forge bus and report it. */
    private Step armArcticFur(Block[] fur) {
        return ctx -> {
            MinecraftForge.EVENT_BUS.register(new Handler(fur[0]));
            ctx.log()
                .info("[ArcticFurBreak] armed — arctic fur breaks at the modern speed while shears are held");
            return Outcome.APPLIED;
        };
    }

    /** The Forge-bus listener; the floor is applied on both sides (the client is the clock). */
    public static final class Handler {

        private final Block fur;

        /** Log the first handler failure only — a broken environment must not spam. */
        private volatile boolean warned = false;

        Handler(Block fur) {
            this.fur = fur;
        }

        @SubscribeEvent
        public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
            try {
                EntityPlayer player = event.entityPlayer;
                if (player == null || player.worldObj == null) return;
                if (event.y < 0) return; // unknown location: hardness cannot be read
                if (fur == null || event.block != fur) return; // fast path
                ItemStack held = player.inventory.getCurrentItem();
                if (held == null || !(held.getItem() instanceof ItemShears)) return;
                AuroraBreakFeature.raiseBreakFloor(event, FLOOR);
            } catch (Exception | LinkageError failure) {
                // Never break a block-break over this; the floor simply does nothing.
                if (!warned) {
                    warned = true;
                    Patchee.LOG
                        .warn("[ArcticFurBreak] break-speed handler failed once — the fix will keep trying", failure);
                }
            }
        }
    }
}
