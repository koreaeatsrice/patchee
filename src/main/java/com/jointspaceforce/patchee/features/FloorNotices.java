package com.jointspaceforce.patchee.features;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.jointspaceforce.patchee.Patchee;

/**
 * The per-player, once-each-way proof lines for a break-speed floor.
 *
 * <p>
 * Each player gets at most one "denied" line (the gate was not met — Glacier not
 * earned, or no shears) and at most one "floor applied" line (the floor really
 * raised a covered block's speed for that player), never one per block or per
 * tick. That is the runtime proof the feature is live: a silent feature and a
 * working one look different in the log.
 */
final class FloorNotices {

    private final String feature;
    private final Set<String> denied = Collections.synchronizedSet(new HashSet<String>());
    private final Set<String> applied = Collections.synchronizedSet(new HashSet<String>());

    FloorNotices(String feature) {
        this.feature = feature;
    }

    /** One line per player the first time the floor is withheld from them. */
    void deniedOnce(String player, String reason) {
        if (player == null) {
            return;
        }
        if (denied.add(player)) {
            Patchee.LOG.info("[{}] {} denied - {}", feature, player, reason);
        }
    }

    /** One line per player the first time the floor really raises a covered block's speed. */
    void appliedOnce(String player, String blockClass, float before, float after, int x, int y, int z) {
        if (player == null) {
            return;
        }
        if (applied.add(player)) {
            Patchee.LOG.info(
                "[{}] {} floor applied on {} (speed {} -> {}) at {},{},{}",
                feature,
                player,
                blockClass,
                before,
                after,
                x,
                y,
                z);
        }
    }
}
