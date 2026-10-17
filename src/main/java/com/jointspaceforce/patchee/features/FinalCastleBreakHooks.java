package com.jointspaceforce.patchee.features;

import java.lang.reflect.Method;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The FinalCastleBreak call sites: the three static methods Twilight Forest's
 * patched bytecode calls on its way through the castle blocks and the thorn wall.
 *
 * <p>
 * It runs inside the class loader, before Forge's config exists, so it does not
 * touch Forge: the switches come from {@link FinalCastleBreak}'s published holder
 * and the Glacier gate from {@link FinalCastleBreak#earnedGlacier}. The PATCH
 * itself is applied by
 * {@link com.jointspaceforce.patchee.asm.FinalCastleBreakTransformer}.
 *
 * <p>
 * Each hook is <b>fail-soft</b>: any surprise — the gate unreadable, a renamed
 * Twilight Forest method, a missing logger — falls back to the original stock
 * behaviour and costs a warning line once, never a broken block break. The one
 * pure decision ({@link #bypass(boolean, boolean)}) is separated from the game
 * calls so it can be unit-tested without a game.
 */
public final class FinalCastleBreakHooks {

    private static final Logger LOG = LogManager.getLogger("patchee");

    /** The direction/growth helper Twilight Forest's burst reaches, by name. */
    private static final String BURST_METHOD = "doThornBurst";

    private static volatile Logger logger;
    private static volatile boolean warned;
    private static volatile Method burst;
    private static volatile Class<?> burstOwner;

    private FinalCastleBreakHooks() {}

    /**
     * The one decision every hook makes: bypass stock behaviour only when the
     * feature is on AND the player has earned the Glacier progress. Pure — no game
     * types — so the contract is pinned by
     * {@code FinalCastleBreakPatchTest.thePureGateDecisionNeedsBothTheSwitchAndTheEarn}.
     */
    public static boolean bypass(boolean active, boolean earnedGlacier) {
        return active && earnedGlacier;
    }

    /**
     * The injected entry point for {@code BlockTFCastleBlock.harvestBlock}'s
     * pickaxe-damage call ({@code Item.onBlockDestroyed}). When the player has
     * earned the Glacier progress the extra damage is skipped entirely
     * ({@code false}); otherwise the call is passed straight through to
     * {@code Item.onBlockDestroyed}, so every other player keeps the stock 16x
     * durability drain.
     */
    public static boolean onBlockDestroyed(Item item, ItemStack stack, World world, Block block, int x, int y, int z,
        EntityLivingBase player) {
        try {
            if (bypass(FinalCastleBreak.active(), earnedGlacier(player))) {
                return false;
            }
        } catch (Throwable unexpected) {
            warnOnce(unexpected);
        }
        return item.onBlockDestroyed(stack, world, block, x, y, z, player);
    }

    /**
     * The injected entry point for {@code BlockTFThorns.removedByPlayer}'s "grow
     * back" call. When the player has earned the Glacier progress the thorn block
     * is removed for good; otherwise the block is grown straight back exactly as
     * Twilight Forest does ({@code meta = (meta &amp; 12) | 1}, flags {@code 2}).
     */
    public static boolean setThornBlock(World world, int x, int y, int z, Block block, int meta, int flags,
        EntityPlayer player) {
        try {
            if (bypass(FinalCastleBreak.active(), FinalCastleBreak.earnedGlacier(player))) {
                return world.setBlockToAir(x, y, z);
            }
        } catch (Throwable unexpected) {
            warnOnce(unexpected);
        }
        return world.setBlock(x, y, z, block, meta, flags);
    }

    /**
     * The injected entry point for {@code BlockTFThorns.removedByPlayer}'s
     * {@code doThornBurst} call. When the player has earned the Glacier progress
     * the burst is suppressed; otherwise Twilight Forest's own burst is invoked,
     * so every other player sees the stock thorn multiplication.
     *
     * <p>
     * {@code doThornBurst} is private in Twilight Forest and Patchee never links
     * against the mod, so the original burst is reached by reflection on the
     * block's own class. That keeps this class free of Twilight Forest (and,
     * importantly, of Forge) types, which is what lets it load before the mods do.
     */
    public static void thornBurst(Block block, World world, int x, int y, int z, int meta, EntityPlayer player) {
        try {
            if (bypass(FinalCastleBreak.active(), FinalCastleBreak.earnedGlacier(player))) {
                return;
            }
        } catch (Throwable unexpected) {
            warnOnce(unexpected);
        }
        originalBurst(block, world, x, y, z, meta);
    }

    /** Twilight Forest's own thorn burst, reached by reflection; a failure costs a warning once. */
    private static void originalBurst(Block block, World world, int x, int y, int z, int meta) {
        if (block == null) {
            return;
        }
        try {
            Method method = burstMethod(block.getClass());
            if (method != null) {
                method.invoke(block, world, x, y, z, meta);
            }
        } catch (Throwable failed) {
            warnOnce(failed);
        }
    }

    /** Resolve (once) the block class' private {@code doThornBurst(World,int,int,int,int)}. */
    private static Method burstMethod(Class<?> type) {
        if (type == burstOwner) {
            return burst;
        }
        Method method = null;
        try {
            method = type.getDeclaredMethod(BURST_METHOD, World.class, int.class, int.class, int.class, int.class);
            method.setAccessible(true);
        } catch (Throwable absent) {
            method = null;
        }
        burst = method;
        burstOwner = type;
        return method;
    }

    /** The Glacier gate, guarded so a broken stats read falls back to stock behaviour. */
    private static boolean earnedGlacier(EntityLivingBase player) {
        if (player instanceof EntityPlayer) {
            return FinalCastleBreak.earnedGlacier((EntityPlayer) player);
        }
        return false;
    }

    /** One warning for the whole JVM run, never one per block break. */
    private static void warnOnce(Throwable cause) {
        synchronized (FinalCastleBreakHooks.class) {
            if (warned) {
                return;
            }
            warned = true;
        }
        Logger log = logger();
        if (log != null) {
            log.warn("[FinalCastleBreak] a hook failed once — falling back to stock Twilight Forest behaviour", cause);
        }
    }

    /** The mod logger, or {@code null} when log4j is not there (the game always has it). */
    private static Logger logger() {
        Logger local = logger;
        if (local == null) {
            try {
                local = LOG;
                logger = local;
            } catch (Throwable missing) {
                return null;
            }
        }
        return local;
    }
}
