package com.jointspaceforce.patchee.features;

import java.util.Arrays;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
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
 * The clearing is per side. On the <b>logical client</b> it always runs — that
 * client is running Patchee, so it wipes the local copies on that client (every
 * player entity it ticks, a screen-only cleanup) and the HUD/slowness goes away.
 * On the <b>server</b> it runs only for a client
 * whose FML handshake listed Patchee: the server reads that list at the entry of
 * FML's own handshake step (one tiny bytecode hook, no packet channel and no
 * client-only requirement added), remembers the answer per connection, and a
 * <b>stock client is never touched</b>.
 *
 * <p>
 * So a player without the mod gets exactly GregTech's own behaviour: debuffed
 * while carrying the tank, and the effects fade on GT's own timer — up to ten
 * minutes — after the tank is put down. Nothing about their game changes.
 *
 * <p>
 * Honest side effect, documented in docs/SETTINGS.md: for a Patchee client, while
 * carrying a qualifying item, these four effects are cleared regardless of where
 * they came from (the pack's pollution system can also use them). The whole
 * feature is config-gated ({@code enableSuperTankFix}).
 *
 * <p>
 * The work is two composed steps — register the tick handler (which also marks
 * the feature armed for the handshake transformer), then report that the fix is
 * armed — so the existing log line keeps its place in the boot log.
 */
public final class SuperTankFeature implements Feature {

    public static final String ID = "SuperTankFix";

    private static final String ITEM_CLASS = "gregtech.common.blocks.ItemMachines";

    /** Set once the tick handler is registered: only then is the handshake hook worth patching in. */
    private static volatile boolean armed = false;

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
            + "(hunger, slowness, mining fatigue, weakness). The server clears them only for players whose "
            + "client listed this mod in its login handshake; the mod's own client half clears them locally. "
            + "A stock client is never touched and keeps plain GregTech behaviour (debuffed while carrying, "
            + "then fading on GT's timer, up to ten minutes, after the tank is put down). true = the fix is "
            + "on. false = vanilla GregTech behaviour. Note: for a Patchee client, while a filled tank/chest "
            + "is carried, those four effects are cleared whatever their source.";
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
                Steps.note(
                    "[SuperTankFix] armed — clears the debuffs only for clients that run Patchee; "
                        + "a stock client keeps GregTech's own debuff and timer")));
    }

    /** Step one: put the per-tick cleaner on the FML bus, and say the fix is armed. */
    private Step registerTickHandler() {
        return ctx -> {
            markArmed();
            FMLCommonHandler.instance()
                .bus()
                .register(new Handler());
            return Outcome.APPLIED;
        };
    }

    /**
     * Marks the fix armed: the tick handler is registered, so the FML handshake
     * hook is worth patching in. Called by {@link #registerTickHandler()}.
     */
    public static void markArmed() {
        armed = true;
    }

    /** Whether the tick handler is registered (the handshake transformer reads this). */
    public static boolean isArmed() {
        return armed;
    }

    /**
     * Whether the client behind this player's connection listed Patchee in its FML
     * handshake. A non-server player, an unbound connection or an unknown
     * connection all answer false — the safe answer, a stock client.
     */
    private static boolean clientHasPatchee(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) {
            return false;
        }
        EntityPlayerMP mp = (EntityPlayerMP) player;
        if (mp.playerNetServerHandler == null) {
            return false;
        }
        return ClientModPresence.hasPatchee(mp.playerNetServerHandler.netManager);
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
                boolean logicalClient = player.worldObj.isRemote;
                boolean patcheeClient = logicalClient || clientHasPatchee(player);
                boolean carries = carriesQualifyingTank(player);
                if (!SuperTankDecision.shouldClear(logicalClient, patcheeClient, carries, hasAny)) return;
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
