package com.jointspaceforce.patchee.features;

import java.util.Map;

import com.jointspaceforce.patchee.Patchee;

import cpw.mods.fml.common.network.handshake.FMLHandshakeMessage;
import cpw.mods.fml.common.network.handshake.NetworkDispatcher;
import io.netty.channel.ChannelHandlerContext;

/**
 * The FML handshake hook: notes whether the joining client listed Patchee.
 *
 * <p>
 * FML's server-side handshake reads the client's mod list in
 * {@code FMLHandshakeServerState$2.accept} and then discards it — 1.7.10 keeps
 * no list of a client's mods for the rest of the session. Patchee's transformer
 * calls this method at the entry of that {@code accept}, which is the only place
 * the list exists, and records the answer in {@link ClientModPresence} against
 * the connection.
 *
 * <p>
 * This runs inside FML's handshake, on a Netty network thread, before the player
 * has fully joined. Throwing there would break the join, so it never throws: any
 * failure is swallowed and the connection is simply treated as a stock client.
 */
public final class SuperTankHandshake {

    private SuperTankHandshake() {}

    /**
     * Called at the entry of FML's handshake mod-list step.
     *
     * @param ctx     the channel handler context FML handed to {@code accept}
     * @param message the handshake message FML is processing
     */
    public static void observe(ChannelHandlerContext ctx, FMLHandshakeMessage message) {
        try {
            if (ctx == null || !(message instanceof FMLHandshakeMessage.ModList)) {
                return;
            }
            NetworkDispatcher dispatcher = ctx.channel()
                .attr(NetworkDispatcher.FML_DISPATCHER)
                .get();
            if (dispatcher == null || dispatcher.manager == null) {
                return;
            }
            Map<String, String> mods = ((FMLHandshakeMessage.ModList) message).modList();
            boolean has = mods != null && mods.containsKey(Patchee.MODID);
            ClientModPresence.record(dispatcher.manager, has);
        } catch (Exception | LinkageError ignored) {
            // Never break a join: an unreadable handshake means "stock client".
        }
    }
}
