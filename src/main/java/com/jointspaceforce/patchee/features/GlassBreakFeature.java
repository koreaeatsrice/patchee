package com.jointspaceforce.patchee.features;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.BlockGlass;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BlockEvent;

import com.jointspaceforce.patchee.Patchee;
import com.jointspaceforce.patchee.core.Feature;
import com.jointspaceforce.patchee.core.Outcome;
import com.jointspaceforce.patchee.core.Step;
import com.jointspaceforce.patchee.core.Toggles;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;

/**
 * Stops the "mining glass blinds you" debuffs.
 *
 * <p>
 * Blood Arsenal 1.5.12's {@code BloodArsenalEventHooks.harvestEvent} treats
 * every {@code BlockGlass} as something that cuts the player: on
 * {@code random.nextInt(3) == 2} it applies its {@code bleeding} potion for 8
 * seconds (decompiled line {@code 171}), and its living-tick handler adds
 * {@code Potion.blindness} every tick while bleeding is active — with no armour
 * check at all. Chisel's Transparent Technical Block is a metal mesh, not glass,
 * but it is {@code new BlockCarvableGlass()} (Chisel {@code Features.java:3823}),
 * so it cuts too.
 *
 * <p>
 * The owner's rule: that technical block is never glass and must not cut;
 * real glass (vanilla, Chisel glass, stained glass, EFR tinted glass) still cuts
 * a player who wears no armour, and being cut is what Blood Arsenal is for; but
 * armour nullifies it — one filled armour slot, by default, and nothing cuts.
 * The threshold is {@code glassBreak.minArmourPieces} in the config (0 = never
 * protected, 4 = a full set).
 *
 * <p>
 * How: one listener on the Forge bus at {@code EventPriority.LOWEST} that, in the
 * same {@code HarvestDropsEvent} dispatch, removes the bleeding potion and
 * {@code Potion.blindness}. Blood Arsenal's handler is at the default
 * {@code NORMAL} priority, so ours runs after it and before any tick, which means
 * the effect never gets to deal damage or blind. No bytecode is rewritten, and
 * Blood Arsenal is never a compile dependency.
 *
 * <p>
 * Server-side only: the potions are applied on the server, and clearing them
 * there is what reaches the client.
 */
public final class GlassBreakFeature implements Feature {

    public static final String ID = GlassBreak.ID;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String configKey() {
        return GlassBreak.ENABLE_KEY;
    }

    @Override
    public boolean defaultEnabled() {
        return GlassBreak.DEFAULT_ENABLED;
    }

    @Override
    public String description() {
        return "Feature: no bleeding and no blindness from mining glass. Chisel's Transparent Technical Block is a "
            + "metal mesh, not glass, so it never cuts. Real glass (vanilla, Chisel, stained, EFR tinted) still cuts "
            + "a player with no armour on, but armour nullifies it - glassBreak.minArmourPieces says how many "
            + "filled armour slots count as protected (default 1, 0 = never). Server-side only.";
    }

    @Override
    public String disabledMessage() {
        return "[GlassBreak] disabled in config - Blood Arsenal's glass-cut debuffs left alone";
    }

    @Override
    public String failureMessage() {
        return "[GlassBreak] failed - Blood Arsenal's glass-cut debuffs left alone";
    }

    @Override
    public String targetClass() {
        return GlassBreak.CULPRIT_CLASS;
    }

    @Override
    public String missingTargetMessage() {
        return "[GlassBreak] Blood Arsenal is not installed - skipped";
    }

    @Override
    public List<Step> steps() {
        return Arrays.asList(arm());
    }

    /**
     * The one step: read Blood Arsenal's bleeding potion, publish the switches
     * and put the listener on the Forge bus.
     *
     * <p>
     * A client has nothing to do — the debuffs are applied by the server's event
     * handler — so it says so and skips. On the server the potion is read by
     * reflection (never linked) from the first owner class in
     * {@link GlassBreak#POTION_OWNER_CLASSES} that exists, and a missing owner
     * class, a field that is no longer a {@code Potion}, or a reflection error is
     * a hard failure: without a potion id there is nothing to remove, and a boot
     * line claiming otherwise would be wrong.
     */
    private Step arm() {
        return ctx -> {
            if (FMLCommonHandler.instance()
                .getSide() != Side.SERVER) {
                ctx.log()
                    .info(
                        "[GlassBreak] client - Blood Arsenal's glass-cut debuffs are applied on the server; "
                            + "nothing to do here");
                return Outcome.SKIPPED;
            }

            Class<?> owner = null;
            for (String candidate : GlassBreak.POTION_OWNER_CLASSES) {
                owner = ctx.reflect()
                    .find(candidate);
                if (owner != null) {
                    break;
                }
            }
            if (owner == null) {
                ctx.log()
                    .error(
                        "[GlassBreak] {} is missing - not arming",
                        String.join(" or ", GlassBreak.POTION_OWNER_CLASSES));
                return Outcome.FAILED;
            }

            try {
                Field field = ctx.reflect()
                    .staticField(owner, GlassBreak.BLEEDING_FIELD);
                Object value = ctx.reflect()
                    .staticValue(field);
                if (!(value instanceof Potion)) {
                    ctx.log()
                        .error(
                            "[GlassBreak] {}#{} is not a Potion - not arming",
                            owner.getName(),
                            GlassBreak.BLEEDING_FIELD);
                    return Outcome.FAILED;
                }
                int potionId = ((Potion) value).id;
                Toggles toggles = ctx.toggles();
                GlassBreak.configure(
                    toggles.masterEnabled(),
                    toggles.featureEnabled(ID),
                    toggles.glassBreakMinArmourPieces(),
                    potionId);
                MinecraftForge.EVENT_BUS.register(new Handler());
                if (GlassBreak.minArmourPieces() == 0) {
                    ctx.log()
                        .info(
                            "[GlassBreak] armed - armour never protects (glassBreak.minArmourPieces=0); Chisel's "
                                + "Transparent Technical Block never cuts; bleeding potion id {}",
                            potionId);
                } else {
                    ctx.log()
                        .info(
                            "[GlassBreak] armed - a player wearing {} or more armour piece(s) is not cut by glass, "
                                + "and Chisel's Transparent Technical Block never cuts; bleeding potion id {}",
                            GlassBreak.minArmourPieces(),
                            potionId);
                }
                return Outcome.APPLIED;
            } catch (NoSuchFieldException | IllegalAccessException unreadable) {
                ctx.log()
                    .error(
                        "[GlassBreak] {}#{} could not be read - not arming",
                        owner.getName(),
                        GlassBreak.BLEEDING_FIELD,
                        unreadable);
                return Outcome.FAILED;
            }
        };
    }

    /**
     * The Forge-bus listener; server side only, and the last word on the event.
     *
     * <p>
     * Blood Arsenal registers its handler on {@code MinecraftForge.EVENT_BUS} and
     * {@code HarvestDropsEvent} is a Forge event, so it fires there at the default
     * {@code NORMAL} priority. FML dispatches one event through the bus in
     * priority order, so this listener at {@code LOWEST} runs <i>after</i> Blood
     * Arsenal applied its bleeding potion, in the same dispatch and long before
     * any tick: the effect is removed before it can deal damage and before the
     * living-tick handler could add blindness. That ordering is the whole reason
     * no bytecode patch of Blood Arsenal is needed.
     */
    public static final class Handler {

        /** Log the first handler failure only — a broken environment must not spam. */
        private volatile boolean warned = false;

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onHarvestDrops(BlockEvent.HarvestDropsEvent event) {
            try {
                if (event.world == null || event.world.isRemote) return;
                if (!GlassBreak.active()) return;
                Block block = event.block;
                if (block == null) return;
                if (!(block instanceof BlockGlass)) return;
                EntityPlayer player = event.harvester;
                if (player == null) return;
                if (!GlassBreak.suppresses(block, player)) return;
                player.removePotionEffect(GlassBreak.bleedingId());
                player.removePotionEffect(Potion.blindness.id);
            } catch (Exception | LinkageError failure) {
                // Never break a harvest over this; the fix simply does nothing.
                if (!warned) {
                    warned = true;
                    Patchee.LOG.warn("[GlassBreak] harvest handler failed once - the fix will keep trying", failure);
                }
            }
        }
    }
}
