package com.jointspaceforce.patchee.features;

import java.lang.reflect.Field;
import java.util.Map;

import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Turns GregTech's client pollution visuals off and keeps them off.
 *
 * <p>
 * Reflection only: every GregTech name is a string, so this class loads on a
 * server and in unit tests without GregTech present. It writes three places:
 * the two render booleans on {@code gregtech.common.config.Client.render}, the
 * two mirrors on {@code GTMod.proxy} that the renderer actually reads every
 * frame, and the five {@code gregtech.common.pollution.Pollution} block-tint
 * tables. A tint table is replaced with a fresh, empty {@code BlockMatcher} when
 * it holds block IDs, because GregTech's mixins return the original block colour
 * when the matcher is empty.
 *
 * <p>
 * GregTech rebuilds the tint tables on world load, which is why this runs every
 * client tick instead of once at boot. A missing class or field is swallowed
 * with a DEBUG line at most; the tick handler itself never logs per tick on a
 * normal pack.
 *
 * <p>
 * The class is public because 1.7.10's event bus builds its handler in another
 * package, which cannot reach a package-private listener class (the same reason
 * {@code SelfTest.FirstTick} is public).
 */
public final class PollutionVisualsGuard {

    static final String CLIENT_CONFIG_CLASS = "gregtech.common.config.Client";
    static final String RENDER_FIELD = "render";
    static final String GT_MOD_CLASS = "gregtech.GTMod";
    static final String PROXY_FIELD = "proxy";
    static final String POLLUTION_CLASS = "gregtech.common.pollution.Pollution";
    static final String MATCHER_CLASS = "gregtech.common.pollution.BlockMatcher";
    static final String BLOCK_IDS_FIELD = "blockIDs";

    private final Logger log;

    private PollutionVisualsGuard(Logger log) {
        this.log = log;
    }

    /**
     * Arms the guard for this client.
     *
     * <p>
     * The tick event is posted on FML's own bus in 1.7.10, while Forge's
     * {@code EVENT_BUS} is a different bus; the handler is registered on both so
     * the guard works whichever bus a caller expects and no tick is missed.
     * Enforcing twice in one tick is harmless: every write is idempotent and
     * nothing is logged.
     */
    static void install(Logger log) {
        PollutionVisualsGuard guard = new PollutionVisualsGuard(log);
        MinecraftForge.EVENT_BUS.register(guard);
        FMLCommonHandler.instance()
            .bus()
            .register(guard);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        try {
            enforce(log);
        } catch (Throwable ignored) {
            // A tick handler must never take the client down, and nothing is logged per tick.
        }
    }

    /**
     * What the last {@link #enforce(Logger)} pass really achieved.
     *
     * <p>
     * Every field is read back after it was written, so this says what the game
     * objects hold now, not what this class tried to do.
     */
    public static final class State {

        /** {@code GTMod.proxy.mRenderPollutionFog} exists and reads {@code false}. */
        public final boolean fogOff;
        /** {@code GTMod.proxy.mRenderDirtParticles} exists and reads {@code false}. */
        public final boolean dirtOff;
        /** How many of the five {@code Pollution} tint tables are empty now. */
        public final int tablesEmpty;
        /** How many tint tables there are to empty. */
        public final int tablesTotal;
        /** True when the tint pass threw before it could finish. */
        public final boolean tintPassFailed;

        State(boolean fogOff, boolean dirtOff, int tablesEmpty, int tablesTotal, boolean tintPassFailed) {
            this.fogOff = fogOff;
            this.dirtOff = dirtOff;
            this.tablesEmpty = tablesEmpty;
            this.tablesTotal = tablesTotal;
            this.tintPassFailed = tintPassFailed;
        }

        /** True only when both flags really read {@code false} and every tint table is empty. */
        public boolean armed() {
            return PollutionVisuals.armed(fogOff, dirtOff, tablesEmpty, tablesTotal, tintPassFailed);
        }
    }

    /**
     * Forces every switch off and every tint table empty, once.
     *
     * @return what the pass really achieved, read back from the game objects
     */
    static State enforce(Logger log) {
        forceRenderFlags(log);
        boolean[] proxy = forceProxyFlags(log);
        return clearTintTables(log, proxy[0], proxy[1]);
    }

    /** The two booleans GregTech copies onto its proxy at startup. */
    private static void forceRenderFlags(Logger log) {
        Object render = null;
        try {
            Class<?> clientConfig = Class.forName(CLIENT_CONFIG_CLASS);
            Field renderField = clientConfig.getDeclaredField(RENDER_FIELD);
            renderField.setAccessible(true);
            render = renderField.get(null);
        } catch (ReflectiveOperationException | LinkageError absent) {
            log.debug("[PollutionVisuals] no GregTech client render settings ({})", absent.toString());
        }
        if (render == null) {
            return;
        }
        for (String name : PollutionVisuals.RENDER_FIELDS) {
            try {
                Field flag = render.getClass()
                    .getDeclaredField(name);
                flag.setAccessible(true);
                flag.setBoolean(render, false);
            } catch (ReflectiveOperationException | LinkageError absent) {
                log.debug("[PollutionVisuals] {} is not settable on the render settings ({})", name, absent.toString());
            }
        }
    }

    /**
     * The same two flags on {@code GTMod.proxy}, which the frame renderer reads.
     *
     * @return {@code {fogOff, dirtOff}}: each true only when the field exists and
     *         reads {@code false} after the write. A missing field or a write that
     *         threw is {@code false}, never assumed.
     */
    private static boolean[] forceProxyFlags(Logger log) {
        boolean fogOff = false;
        boolean dirtOff = false;
        Object proxy = null;
        try {
            Class<?> gtMod = Class.forName(GT_MOD_CLASS);
            Field proxyField = gtMod.getDeclaredField(PROXY_FIELD);
            proxyField.setAccessible(true);
            proxy = proxyField.get(null);
        } catch (ReflectiveOperationException | LinkageError absent) {
            log.debug("[PollutionVisuals] no GregTech proxy ({})", absent.toString());
        }
        if (proxy == null) {
            return new boolean[] { false, false };
        }
        for (int i = 0; i < PollutionVisuals.PROXY_FIELDS.size(); i++) {
            String name = PollutionVisuals.PROXY_FIELDS.get(i);
            boolean off;
            try {
                Field flag = findField(proxy.getClass(), name);
                flag.setAccessible(true);
                flag.setBoolean(proxy, false);
                off = !flag.getBoolean(proxy);
            } catch (ReflectiveOperationException | LinkageError absent) {
                log.debug("[PollutionVisuals] {} is not settable on GregTech's proxy ({})", name, absent.toString());
                off = false;
            }
            if (i == 0) {
                fogOff = off;
            } else {
                dirtOff = off;
            }
        }
        return new boolean[] { fogOff, dirtOff };
    }

    /**
     * Replaces a non-empty block-tint table with a fresh, empty {@code BlockMatcher}
     * and counts the tables that are empty afterwards.
     *
     * <p>
     * Empty is read back from {@code blockIDs}, so a table that was already empty
     * counts too: this says how many of the five tables can no longer tint a block.
     */
    private static State clearTintTables(Logger log, boolean fogOff, boolean dirtOff) {
        int total = PollutionVisuals.MATCHER_FIELDS.size();
        int empty = 0;
        boolean failed = false;
        try {
            Class<?> pollution = Class.forName(POLLUTION_CLASS);
            Class<?> matcherClass = Class.forName(MATCHER_CLASS);
            for (String name : PollutionVisuals.MATCHER_FIELDS) {
                Field table = pollution.getDeclaredField(name);
                table.setAccessible(true);
                Object current = table.get(null);
                if (current == null) {
                    continue;
                }
                if (!isEmpty(current)) {
                    table.set(
                        null,
                        matcherClass.getDeclaredConstructor()
                            .newInstance());
                    current = table.get(null);
                }
                if (current != null && isEmpty(current)) {
                    empty++;
                }
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException absent) {
            log.debug("[PollutionVisuals] the block-tint pass threw ({})", absent.toString());
            failed = true;
        }
        return new State(fogOff, dirtOff, empty, total, failed);
    }

    /** True when the matcher's {@code blockIDs} map really holds no block ID. */
    private static boolean isEmpty(Object matcher) throws ReflectiveOperationException {
        Field ids = findField(matcher.getClass(), BLOCK_IDS_FIELD);
        ids.setAccessible(true);
        Object raw = ids.get(matcher);
        return raw instanceof Map && ((Map<?, ?>) raw).isEmpty();
    }

    /** Where the field really lives: GregTech declares the proxy mirrors on a superclass. */
    private static Field findField(Class<?> start, String name) throws NoSuchFieldException {
        for (Class<?> current = start; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException absent) {
                // walk up: inherited fields are not getDeclaredField() on the subclass
            }
        }
        throw new NoSuchFieldException(name);
    }
}
