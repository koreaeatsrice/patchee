package com.jointspaceforce.patchee.features;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
 * The team limit rule itself: the two methods ServerUtilities' patched bytecode
 * calls on its way out of {@code getMaxClaimChunks()} and
 * {@code getMaxChunkloaderChunks()}.
 *
 * <p>
 * It runs inside the class loader, before Forge's config exists, so it does not
 * touch Forge: the switches come from {@link TeamChunkCap}'s published holder,
 * the PATCH itself is applied by
 * {@link com.jointspaceforce.patchee.asm.TeamChunkCapTransformer}, and the
 * members' per-rank limits are read by reflection from the live
 * {@code ForgeTeam}/{@code ForgePlayer} objects ServerUtilities hands over
 * (Patchee keeps no dependency on ServerUtilities).
 *
 * <p>
 * The rule: the number ServerUtilities was about to return (the per-member sum)
 * is replaced by the best member's own per-rank limit when that is lower. A
 * failure to read the members never turns into a wrong cap: the rule is skipped,
 * one console warning is written per JVM run, and ServerUtilities' own number
 * stands.
 *
 * <p>
 * Logging is a STATE CHANGE, not a per-call report. ServerUtilities caches these
 * numbers and only recomputes them for a claim attempt, a chunkloader change or
 * a GUI refresh, but a busy server can still recompute them repeatedly, so a
 * line is written at most once every {@link #LOG_INTERVAL_MS} and only when the
 * rule really changed the number.
 */
public final class TeamChunkCapHooks {

    /** One sampled line at most this often, however many teams the rule touches. */
    static final long LOG_INTERVAL_MS = 5000L;

    private static final AtomicLong LIMITED = new AtomicLong();
    private static final AtomicLong LAST_LOG_AT = new AtomicLong();

    /** Samples land here, beside the server's own logs (working-directory relative). */
    private static final Path LOG_FILE = Paths.get("logs", "patchee-claims.log");
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /** One write at a time; the throttle already caps this at one every 5 s. */
    private static final Object WRITE_LOCK = new Object();

    /** One warning at a time for an unreadable team, too. */
    private static final Object READ_LOCK = new Object();

    /** Set once so a broken log file costs one warning per JVM run, not one per sample. */
    private static volatile boolean writeFailureReported;

    /** Set once so an unreadable team costs one warning per JVM run, not one per call. */
    private static volatile boolean readFailureReported;

    private static volatile Logger logger;

    // Each handle is resolved once per class it is read from. The class is part
    // of the cache key: a JVM that loads the same-named class twice (the unit
    // tests do) must never be answered a handle that belongs to the other class.
    private static volatile Field teamField;
    private static volatile Class<?> teamFieldClass;
    private static volatile Method membersMethod;
    private static volatile Class<?> membersMethodClass;
    private static volatile Method rankConfigMethod;
    private static volatile Class<?> rankConfigMethodClass;

    private TeamChunkCapHooks() {}

    /**
     * The injected entry point for {@code getMaxClaimChunks()}.
     *
     * @param data ServerUtilities' team data (the team and its members come from it)
     * @param raw  the number the method was about to return
     * @return the best member's own claim limit when that is lower, else {@code raw}
     */
    public static int clampClaims(Object data, int raw) {
        return limit(data, raw, TeamChunkCap.CLAIMS_NODE, "claim");
    }

    /**
     * The injected entry point for {@code getMaxChunkloaderChunks()}.
     *
     * @param data ServerUtilities' team data (the team and its members come from it)
     * @param raw  the number the method was about to return
     * @return the best member's own loaded-chunk limit when that is lower, else {@code raw}
     */
    public static int clampLoads(Object data, int raw) {
        return limit(data, raw, TeamChunkCap.LOADS_NODE, "loaded-chunk");
    }

    private static int limit(Object data, int raw, String node, String kind) {
        if (!TeamChunkCap.active() || raw <= 0) {
            return raw;
        }
        Integer bestMember = bestMemberLimit(data, node);
        if (bestMember == null || !TeamChunkCap.limitFires(raw, bestMember)) {
            return raw;
        }
        try {
            logSampled(kind, teamName(data), raw, bestMember, LIMITED.incrementAndGet());
        } catch (Throwable unloggable) {
            // logging must never be the reason a limit lookup misbehaves
        }
        return bestMember;
    }

    /**
     * The highest per-rank limit among the team's members for one permission
     * node, or {@code null} when the members cannot be read this call (a
     * ServerUtilities change, a renamed class). A {@code null} answer means
     * "leave ServerUtilities' own number alone".
     */
    private static Integer bestMemberLimit(Object data, String node) {
        try {
            Object team = teamOf(data);
            if (team == null) {
                return unreadable(null);
            }
            Method members = membersMethodOf(team.getClass());
            if (members == null) {
                return unreadable(null);
            }
            Object memberList = members.invoke(team);
            if (!(memberList instanceof Iterable)) {
                return unreadable(null);
            }
            int best = 0;
            boolean seen = false;
            for (Object member : (Iterable<?>) memberList) {
                if (member == null) {
                    continue;
                }
                Method rankConfig = rankConfigMethodOf(member.getClass());
                if (rankConfig == null) {
                    return unreadable(null);
                }
                Object value = rankConfig.invoke(member, node);
                if (value == null) {
                    continue;
                }
                Method getInt = findPublicMethod(value.getClass(), "getInt");
                if (getInt == null) {
                    return unreadable(null);
                }
                best = Math.max(best, (Integer) getInt.invoke(value));
                seen = true;
            }
            return seen ? Integer.valueOf(best) : unreadable(null);
        } catch (Throwable surprise) {
            return unreadable(surprise);
        }
    }

    /** One warning per JVM run for an unreadable team; the caller gets {@code null}. */
    private static Integer unreadable(Throwable cause) {
        synchronized (READ_LOCK) {
            if (readFailureReported) {
                return null;
            }
            readFailureReported = true;
        }
        Logger log = logger();
        if (log != null) {
            log.warn(
                "[TeamChunkCap] cannot read a team's members' per-rank limits — ServerUtilities' own numbers stand (first failure only)",
                cause);
        }
        return null;
    }

    /** One line per {@link #LOG_INTERVAL_MS}, appended to {@link #LOG_FILE}. */
    private static void logSampled(String kind, String team, int raw, int bestMember, long total) {
        long now = System.currentTimeMillis();
        long last = LAST_LOG_AT.get();
        if (now - last < LOG_INTERVAL_MS || !LAST_LOG_AT.compareAndSet(last, now)) {
            return;
        }
        String line = LocalDateTime.now()
            .format(TIMESTAMP) + " "
            + String.format(
                "clamped team %s's %s limit - sum %d -> %d (best member %d; %d clamp(s) so far)",
                team,
                kind,
                raw,
                bestMember,
                bestMember,
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
            warnWriteOnce(unwritable);
        }
    }

    /** The one and only word a broken log file gets for the whole JVM run. */
    private static void warnWriteOnce(Throwable cause) {
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
     * is only ever stringified; the team and its members are reached through
     * fields and public accessors of ServerUtilities' own data classes, each
     * resolved once and each failure answered with ServerUtilities' own number.
     */
    private static String teamName(Object data) {
        Object team = teamOf(data);
        return team == null ? "unknown" : String.valueOf(team);
    }

    private static Object teamOf(Object data) {
        if (data == null) {
            return null;
        }
        Class<?> dataClass = data.getClass();
        Field field = teamField;
        if (teamFieldClass != dataClass) {
            field = resolveTeamField(dataClass);
            teamField = field;
            teamFieldClass = dataClass;
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

    /** {@code ForgeTeam.getMembers()}, resolved once per team class. */
    private static Method membersMethodOf(Class<?> teamClass) {
        Method method = membersMethod;
        if (membersMethodClass != teamClass) {
            method = findPublicMethod(teamClass, "getMembers");
            membersMethod = method;
            membersMethodClass = teamClass;
        }
        return method;
    }

    /** {@code ForgePlayer.getRankConfig(String)}, resolved once per member class. */
    private static Method rankConfigMethodOf(Class<?> memberClass) {
        Method method = rankConfigMethod;
        if (rankConfigMethodClass != memberClass) {
            method = findPublicMethod(memberClass, "getRankConfig", String.class);
            rankConfigMethod = method;
            rankConfigMethodClass = memberClass;
        }
        return method;
    }

    /** A public method, or {@code null} when it is not there (or reflection refuses). */
    private static Method findPublicMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return type.getMethod(name, parameterTypes);
        } catch (Throwable notThere) {
            return null;
        }
    }

    /** How many clamps have fired since boot (the self-test's evidence). */
    public static long clampedCount() {
        return LIMITED.get();
    }
}
