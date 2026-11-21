package com.jointspaceforce.patchee.features;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The per-connection memory behind the Super Tank fix: who has told this server
 * that their client runs Patchee, and nothing remembered for anyone else.
 *
 * <p>
 * A connection is any object the handshake path hands in, so a plain
 * {@code new Object()} stands in for it here.
 */
class ClientModPresenceTest {

    @BeforeEach
    void clearPresence() {
        ClientModPresence.clearForTest();
    }

    @Test
    void aNullConnectionRecordsNothing() {
        ClientModPresence.record(null, true);
        assertFalse(ClientModPresence.hasPatchee(null), "a null connection is never a known client");
    }

    @Test
    void anUnknownConnectionIsNotPresent() {
        assertFalse(ClientModPresence.hasPatchee(new Object()), "a connection that never spoke is unknown");
    }

    @Test
    void aRecordedPatcheeClientAnswersTrue() {
        Object connection = new Object();
        ClientModPresence.record(connection, true);
        assertTrue(ClientModPresence.hasPatchee(connection), "the connection said it runs Patchee");
    }

    @Test
    void aRecordedStockClientAnswersFalse() {
        Object connection = new Object();
        ClientModPresence.record(connection, false);
        assertFalse(ClientModPresence.hasPatchee(connection), "the connection said it does not run Patchee");
    }

    @Test
    void recordingTheSameConnectionAgainOverwrites() {
        Object connection = new Object();
        ClientModPresence.record(connection, true);
        ClientModPresence.record(connection, false);
        assertFalse(ClientModPresence.hasPatchee(connection), "the later answer wins");

        ClientModPresence.record(connection, true);
        assertTrue(ClientModPresence.hasPatchee(connection), "the later answer wins in both directions");
    }

    @Test
    void forgettingAConnectionMakesItUnknownAgain() {
        Object connection = new Object();
        ClientModPresence.record(connection, true);
        ClientModPresence.forget(connection);
        assertFalse(ClientModPresence.hasPatchee(connection), "a forgotten connection is unknown");
    }

    @Test
    void oneConnectionDoesNotSpeakForAnother() {
        Object patchee = new Object();
        Object stock = new Object();
        ClientModPresence.record(patchee, true);
        assertFalse(ClientModPresence.hasPatchee(stock), "presence is per connection, never global");
    }
}
