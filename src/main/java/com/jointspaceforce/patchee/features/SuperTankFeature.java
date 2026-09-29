package com.jointspaceforce.patchee.features;

import java.util.Arrays;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraftforge.fluids.FluidStack;

import com.jointspaceforce.patchee.Patchee;
import com.jointspaceforce.patchee.core.Feature;
import com.jointspaceforce.patchee.core.Outcome;
import com.jointspaceforce.patchee.core.Step;
import com.jointspaceforce.patchee.core.Steps;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Stops the "carry a full GregTech Super Tank / Super Chest" debuffs.
 *
 * <p>
 * GT5-Unofficial's machine ItemBlock ({@code gregtech.common.blocks.ItemMachines},
 * method {@code onUpdate}) gives its carrier Hunger II, Slowness II, Mining
 * Fatigue II and Weakness II while the carried item holds items
 * ({@code mItemCount > 0}) or more than 64 000 mB of fluid ({@code mFluid}).
 * The condition and the four effects are hardcoded: GT5-Unofficial reads no
 * config there, and 1.7.10 has no Forge event for "a potion is about to be
 * applied", so the effect cannot be cancelled at its source by a separate mod
 * without editing GT's bytecode.
 *
 * <p>
 * What this fix does instead: once per player tick (phase END — after inventory
 * items have run their own update), if the player has any of those four effects
 * AND is carrying a qualifying tank/chest item, clear them. The item re-applies
 * them on the next tick and they are cleared again, so the debuff never sticks.
 *
 * <p>
 * It runs on <b>both sides</b> on purpose: the item applies the effects on the
 * client too, so the local HUD/slowness only disappear when the client also
 * runs Patchee. Players without the mod still lose the server-side effects
 * (hunger drain and everything server-authoritative); for the full effect they
 * can install the same jar client-side — it is and stays client-optional.
 *
 * <p>
 * Honest side effect, documented in the README: while carrying a qualifying
 * item, these four effects are cleared regardless of where they came from
 * (the pack's pollution system can also use them). The whole feature is
 * config-gated ({@code enableSuperTankFix}).
 *
 * <p>
 * The work is two composed steps — register the tick handler, then report that
 * the fix is armed — so the existing log line keeps its place in the boot log.
 */
public final class SuperTankFeature implements Feature {

    public static final String ID = "SuperTankFix";

    private static final String ITEM_CLASS = "gregtech.common.blocks.ItemMachines";
    /**
     * GT5-Unofficial registers the Super Tank (LV..IV) at metas 130-134 and the
     * Super Chest at 135-139 (MetaTileEntityIDs). GT itself keys off the item's
     * NBT contents; this meta gate keeps the fix from clearing the effects for
     * any other GT machine item that happens to carry those keys. If a future GT
     * renumbers them, the fix simply stops clearing (a safe, narrow failure) —
     * renumbering this range then is the whole update.
     */
    private static final int SUPER_STORAGE_MIN_META = 130;
    private static final int SUPER_STORAGE_MAX_META = 139;
    private static final int[] DEBUFF_IDS = { Potion.hunger.id, Potion.moveSlowdown.id, Potion.digSlowdown.id,
        Potion.weakness.id };
    private static final long MIN_FLUID_MB = 64000L;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String configKey() {
        return "enableSuperTankFix";
    }

    @Override
    public boolean defaultEnabled() {
        return true;
    }

    @Override
    public String description() {
        return "Feature: no debuffs from carrying a GregTech Super Tank or Super Chest that has contents "
            + "(hunger, slowness, mining fatigue, weakness). true = the debuffs never stick. "
            + "false = vanilla GregTech behaviour. Note: while a filled tank/chest is carried, "
            + "those four effects are cleared whatever their source; and the full effect needs "
            + "the player to have this same mod installed too (it is optional on clients).";
    }

    @Override
    public String disabledMessage() {
        return "[SuperTankFix] disabled in config — GT tank debuffs left alone";
    }

    @Override
    public String failureMessage() {
        return "[SuperTankFix] failed — GT tank debuff handler not armed";
    }

    @Override
    public String targetClass() {
        return null;
    }

    @Override
    public String missingTargetMessage() {
        return null;
    }

    @Override
    public List<Step> steps() {
        return Arrays.<Step>asList(
            Steps.sequence(
                registerTickHandler(),
                Steps.note("[SuperTankFix] armed — carried GT Super Tanks/Chests " + "will not keep their debuffs")));
    }

    /** Step one: put the per-tick cleaner on the FML bus. */
    private Step registerTickHandler() {
        return ctx -> {
            FMLCommonHandler.instance()
                .bus()
                .register(new Handler());
            return Outcome.APPLIED;
        };
    }

    /** Per-tick cleaner; server side is authoritative, client side kills the local copy. */
    public static final class Handler {

        /** Log the first handler failure only — a broken environment must not spam. */
        private volatile boolean warned = false;

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onPlayerTick(TickEvent.PlayerTickEvent event) {
            try {
                if (event.phase != TickEvent.Phase.END) return;
                EntityPlayer player = event.player;
                if (player == null || player.worldObj == null) return;
                boolean hasAny = false;
                for (int id : DEBUFF_IDS) {
                    if (player.isPotionActive(id)) {
                        hasAny = true;
                        break;
                    }
                }
                if (!hasAny) return; // fast path: nothing to clean
                if (!carriesQualifyingTank(player)) return;
                for (int id : DEBUFF_IDS) {
                    player.removePotionEffect(id);
                }
            } catch (Exception | LinkageError failure) {
                // Never break a player tick over this; the fix simply does nothing.
                if (!warned) {
                    warned = true;
                    Patchee.LOG.warn("[SuperTankFix] tick handler failed once — the fix will keep trying", failure);
                }
            }
        }
    }

    /** Mirrors GregTech's own check: a GT Super Tank/Super Chest item with contents. */
    private static boolean carriesQualifyingTank(EntityPlayer player) {
        for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++) {
            ItemStack stack = player.inventory.getStackInSlot(slot);
            if (stack == null || stack.getItem() == null) continue;
            if (!ITEM_CLASS.equals(
                stack.getItem()
                    .getClass()
                    .getName()))
                continue;
            int meta = stack.getItemDamage();
            if (meta < SUPER_STORAGE_MIN_META || meta > SUPER_STORAGE_MAX_META) continue;
            NBTTagCompound tag = stack.getTagCompound();
            if (tag == null) continue;
            if (tag.hasKey("mItemCount") && tag.getInteger("mItemCount") > 0) return true;
            if (tag.hasKey("mFluid")) {
                FluidStack fluid = FluidStack.loadFluidStackFromNBT(tag.getCompoundTag("mFluid"));
                if (fluid != null && fluid.amount > MIN_FLUID_MB) return true;
            }
        }
        return false;
    }
}
