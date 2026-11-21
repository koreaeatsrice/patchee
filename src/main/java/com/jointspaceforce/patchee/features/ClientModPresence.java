package com.jointspaceforce.patchee.features;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Which live connections have a client that runs Patchee.
 *
 * <p>
 * FML 1.7.10 sends the client's mod list once during the handshake and then
 * throws it away; nothing keeps it for the rest of the session. So the answer is
 * recorded the moment the handshake is read (see {@link SuperTankHandshake}) and
 * remembered here, keyed by the Netty {@code NetworkManager} of that connection.
 * A weak key means the entry dies with the connection: nothing has to be cleaned
 * up when a player leaves.
 *
 * <p>
 * A connection that was never seen, or whose entry is gone, answers
 * {@code false}. That is the safe answer: it means "a stock client", and a stock
 * client is never touched.
 */
public final class ClientModPresence {

    private static final Map<Object, Boolean> CONNECTIONS = Collections
        .synchronizedMap(new WeakHashMap<Object, Boolean>());

    private ClientModPresence() {}

    /**
     * Remember whether one connection's client runs Patchee.
     *
     * @param connection the connection's Netty {@code NetworkManager} (ignored when null)
     * @param hasPatchee true when the client's handshake listed Patchee
     */
    public static void record(Object connection, boolean hasPatchee) {
        if (connection == null) {
            return;
        }
        CONNECTIONS.put(connection, Boolean.valueOf(hasPatchee));
    }

    /**
     * Whether one connection's client runs Patchee.
     *
     * @param connection the connection's Netty {@code NetworkManager}
     * @return true only when the handshake said so; false for null or unknown
     */
    public static boolean hasPatchee(Object connection) {
        if (connection == null) {
            return false;
        }
        Boolean known = CONNECTIONS.get(connection);
        return known != null && known.booleanValue();
    }

    /**
     * Drop one connection's entry. Not required — a weak key dies with the
     * connection — but harmless to call when a disconnect is observed.
     *
     * @param connection the connection's Netty {@code NetworkManager} (ignored when null)
     */
    public static void forget(Object connection) {
        if (connection == null) {
            return;
        }
        CONNECTIONS.remove(connection);
    }

    /** Empties the store; for tests only. */
    static void clearForTest() {
        CONNECTIONS.clear();
    }
}
