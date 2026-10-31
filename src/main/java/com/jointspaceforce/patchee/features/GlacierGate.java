package com.jointspaceforce.patchee.features;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.stats.Achievement;
import net.minecraft.stats.StatFileWriter;
import net.minecraft.stats.StatisticsFile;

import com.jointspaceforce.patchee.Patchee;

/**
 * The Twlight Forest Glacier progression gate, read per player on both sides and
 * made observable.
 *
 * <p>
 * The gate is the Snow Queen achievement
 * ({@code twilightforest.TFAchievementPage.twilightProgressGlacier}). It is read
 * from the side-appropriate stats object, exactly the pack's own idiom
 * ({@code TFBiomeBase.doesPlayerHaveRequiredAchievement}):
 * {@code EntityPlayerMP.func_147099_x()} on the server and
 * {@code EntityClientPlayerMP.getStatFileWriter()} on the client. A non-MP player
 * on the logical server (a Forge {@code FakePlayer}, say) is answered
 * {@code false} before the client branch, because touching the
 * {@code @SideOnly(Side.CLIENT)} class there would throw on a dedicated server.
 *
 * <p>
 * If the client's own writer is stale or null, the same achievement is re-read
 * from the local player's writer ({@code Minecraft.getMinecraft().thePlayer}) so
 * a real client is never denied by a bad primary read. When nothing can be read
 * at all, one WARN line is written for the whole run — the gate never silently
 * returns false without saying so.
 */
final class GlacierGate {

    private GlacierGate() {}

    private static volatile boolean warned = false;

    /** True when {@code player} has really earned {@code achievement}. */
    static boolean earned(EntityPlayer player, Achievement achievement) {
        if (player == null) {
            return false;
        }
        if (achievement == null) {
            warnOnce("the Glacier achievement could not be resolved at boot");
            return false;
        }
        if (player instanceof EntityPlayerMP) {
            StatisticsFile stats = ((EntityPlayerMP) player).func_147099_x();
            if (stats != null) {
                return stats.hasAchievementUnlocked(achievement);
            }
            warnOnce("the server could not read " + player.getCommandSenderName() + "'s stats file");
            return false;
        }
        if (player.worldObj != null && !player.worldObj.isRemote) {
            // A non-MP player on the logical server: nothing to read, and the
            // client class must stay untouched.
            return false;
        }
        if (player instanceof EntityClientPlayerMP) {
            StatFileWriter stats = ((EntityClientPlayerMP) player).getStatFileWriter();
            if (stats != null) {
                return stats.hasAchievementUnlocked(achievement);
            }
        }
        StatFileWriter local = localStatWriter();
        if (local != null) {
            return local.hasAchievementUnlocked(achievement);
        }
        warnOnce("the client could not read the local player's stat file");
        return false;
    }

    /**
     * The local player's own stat writer, used as the client fallback when the
     * passed player's writer is stale/null. Reached only on the client; on a
     * dedicated server the client classes are simply absent, so this returns null.
     */
    private static StatFileWriter localStatWriter() {
        try {
            EntityPlayer local = Minecraft.getMinecraft().thePlayer;
            if (local instanceof EntityClientPlayerMP) {
                return ((EntityClientPlayerMP) local).getStatFileWriter();
            }
        } catch (Throwable unavailable) {
            // No client (dedicated server) — there is no fallback to take.
        }
        return null;
    }

    /** One warning per run: an unreadable gate must never be silent. */
    private static void warnOnce(String why) {
        synchronized (GlacierGate.class) {
            if (warned) {
                return;
            }
            warned = true;
        }
        Patchee.LOG.warn("[GlacierGate] {} — the break-speed floor falls back to stock speed", why);
    }

    /** Test seam: let the warn-once fire again in another test. */
    static void resetWarnForTest() {
        warned = false;
    }
}
