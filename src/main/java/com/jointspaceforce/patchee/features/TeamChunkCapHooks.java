package com.jointspaceforce.patchee.features;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The clamp itself: the two methods ServerUtilities' patched bytecode calls on
 * its way out of {@code getMaxClaimChunks()} and {@code getMaxChunkloaderChunks()}.
 *
 * <p>
 * It runs inside the class loader, before Forge's config exists, so it does not
 * touch Forge: the switches and caps come from {@link TeamChunkCap}'s published
 * holder, and the PATCH itself is applied by
 * {@link com.jointspaceforce.patchee.asm.TeamChunkCapTransformer}.
 *
 * <p>
 * Everything here is fail-soft. Any surprise — the team field renamed, a missing
 * logger, a changed ServerUtilities — costs the sampled log line and nothing
 * else: the clamp still happens, because the clamp is the point.
 *
 * <p>
 * Logging is a STATE CHANGE, not a per-call report. ServerUtilities caches these
 * numbers and only recomputes them for a claim attempt, a chunkloader change or
 * a GUI refresh, but a busy server can still recompute them repeatedly, so a
 * line is written at most once every {@link #LOG_INTERVAL_MS} and only when the
 * clamp really changed the number.
 */
public final class TeamChunkCapHooks {

    /** One sampled line at most this often, however many teams are over the cap. */
    static final long LOG_INTERVAL_MS = 5000L;

    private static final AtomicLong CLAMPED = new AtomicLong();
    private static final AtomicLong LAST_LOG_AT = new AtomicLong();

    /** Samples land here, beside the server's own logs (working-directory relative). */
    private static final Path LOG_FILE = Paths.get("logs", "patchee-claims.log");
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /** One write at a time; the throttle already caps this at one every 5 s. */
    private static final Object WRITE_LOCK = new Object();

    /** Set once so a broken log file costs one warning per JVM run, not one per sample. */
    private static volatile boolean writeFailureReported;

    private static volatile Logger logger;
    private static volatile Field teamField;
    private static volatile boolean teamFieldResolved;

    private TeamChunkCapHooks() {}

    /**
     * The injected entry point for {@code getMaxClaimChunks()}.
     *
     * @param data ServerUtilities' team data (only used to name the team in the log)
     * @param raw  the number the method was about to return
     * @return {@code raw} unchanged unless the team is over Patchee's cap
     */
    public static int clampClaims(Object data, int raw) {
        return clamp(data, raw, TeamChunkCap.maxClaims(), "claim");
    }

    /**
     * The injected entry point for {@code getMaxChunkloaderChunks()}.
     *
     * @param data ServerUtilities' team data (only used to name the team in the log)
     * @param raw  the number the method was about to return
     * @return {@code raw} unchanged unless the team is over Patchee's cap
     */
    public static int clampLoads(Object data, int raw) {
        return clamp(data, raw, TeamChunkCap.maxLoads(), "loaded-chunk");
    }

    private static int clamp(Object data, int raw, int cap, String kind) {
        if (!TeamChunkCap.active() || !TeamChunkCap.clampFires(raw, cap)) {
            return raw;
        }
        try {
            logSampled(kind, teamName(data), raw, cap, CLAMPED.incrementAndGet());
        } catch (Throwable unloggable) {
            // logging must never be the reason a limit lookup misbehaves
        }
        return cap;
    }

    /** One line per {@link #LOG_INTERVAL_MS}, appended to {@link #LOG_FILE}. */
    private static void logSampled(String kind, String team, int raw, int cap, long total) {
        long now = System.currentTimeMillis();
        long last = LAST_LOG_AT.get();
        if (now - last < LOG_INTERVAL_MS || !LAST_LOG_AT.compareAndSet(last, now)) {
            return;
        }
        String line = LocalDateTime.now()
            .format(TIMESTAMP) + " "
            + String.format(
                "clamped team %s's %s limit - sum %d -> %d (cap %d; %d clamp(s) so far)",
                team,
                kind,
                raw,
                cap,
                cap,
                total);
        appendSample(line);
    }

    /**
     * Appends one line to {@link #LOG_FILE}, creating {@code logs/} if it is not
     * there yet. A write that cannot be done is reported at most once per JVM run
     * and never falls back to a console line per event.
     */
    private static void appendSample(String line) {
        try {
            synchronized (WRITE_LOCK) {
                Files.createDirectories(LOG_FILE.getParent());
                Files.write(
                    LOG_FILE,
                    (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
            }
        } catch (Throwable unwritable) {
            warnOnce(unwritable);
        }
    }

    /** The one and only word a broken log file gets for the whole JVM run. */
    private static void warnOnce(Throwable cause) {
        synchronized (WRITE_LOCK) {
            if (writeFailureReported) {
                return;
            }
            writeFailureReported = true;
        }
        Logger log = logger();
        if (log != null) {
            log.warn("[TeamChunkCap] cannot append sampled clamps to {}", LOG_FILE, cause);
        }
    }

    /** The mod logger, or {@code null} when log4j is not there (the game always has it). */
    private static Logger logger() {
        Logger local = logger;
        if (local == null) {
            try {
                local = LogManager.getLogger("patchee");
                logger = local;
            } catch (Throwable missing) {
                return null;
            }
        }
        return local;
    }

    /**
     * The team's own name. ServerUtilities' {@code ForgeTeam} extends
     * {@code FinalIDObject}, whose {@code toString()} is the team id, so the value
     * is only ever stringified — no method of the target is resolved (looking up
     * a method by reflection on a partly client-only class is the documented way
     * to die on a dedicated server; fields are safe).
     */
    private static String teamName(Object data) {
        Object team = teamOf(data);
        return team == null ? "unknown" : String.valueOf(team);
    }

    private static Object teamOf(Object data) {
        if (data == null) {
            return null;
        }
        Field field = teamField;
        if (!teamFieldResolved) {
            field = resolveTeamField(data.getClass());
            teamField = field;
            teamFieldResolved = true;
        }
        if (field == null) {
            return null;
        }
        try {
            return field.get(data);
        } catch (Throwable unreadable) {
            return null;
        }
    }

    /** {@code team} as ServerUtilities declares it, walking up from the data object. */
    static Field resolveTeamField(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField("team");
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException next) {
                // declared higher up, or renamed by a ServerUtilities change
            } catch (RuntimeException refused) {
                return null;
            }
        }
        return null;
    }

    /** How many clamps have fired since boot (the self-test's evidence). */
    public static long clampedCount() {
        return CLAMPED.get();
    }
}
