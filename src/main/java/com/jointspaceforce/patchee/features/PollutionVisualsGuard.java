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
     * Forces every switch off and every tint table empty, once.
     *
     * @return the number of tint tables replaced, or {@code -1} when the tint
     *         pass threw for a reason other than a missing GregTech class or
     *         field
     */
    static int enforce(Logger log) {
        forceRenderFlags(log);
        forceProxyFlags(log);
        return clearTintTables(log);
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

    /** The same two flags on {@code GTMod.proxy}, which the frame renderer reads. */
    private static void forceProxyFlags(Logger log) {
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
            return;
        }
        for (String name : PollutionVisuals.PROXY_FIELDS) {
            try {
                Field flag = findField(proxy.getClass(), name);
                flag.setAccessible(true);
                flag.setBoolean(proxy, false);
            } catch (ReflectiveOperationException | LinkageError absent) {
                log.debug("[PollutionVisuals] {} is not settable on GregTech's proxy ({})", name, absent.toString());
            }
        }
    }

    /** Replaces a non-empty block-tint table with a fresh, empty {@code BlockMatcher}. */
    private static int clearTintTables(Logger log) {
        try {
            Class<?> pollution = Class.forName(POLLUTION_CLASS);
            Class<?> matcherClass = Class.forName(MATCHER_CLASS);
            int replaced = 0;
            for (String name : PollutionVisuals.MATCHER_FIELDS) {
                Field table = pollution.getDeclaredField(name);
                table.setAccessible(true);
                Object current = table.get(null);
                if (current == null) {
                    continue;
                }
                Field ids = findField(current.getClass(), BLOCK_IDS_FIELD);
                ids.setAccessible(true);
                Object raw = ids.get(current);
                if (raw instanceof Map && !((Map<?, ?>) raw).isEmpty()) {
                    table.set(
                        null,
                        matcherClass.getDeclaredConstructor()
                            .newInstance());
                    replaced++;
                }
            }
            return replaced;
        } catch (ClassNotFoundException | NoSuchFieldException absent) {
            log.debug("[PollutionVisuals] no GregTech block-tint tables ({})", absent.toString());
            return 0;
        } catch (Throwable failure) {
            log.debug("[PollutionVisuals] the block-tint pass threw ({})", failure.toString());
            return -1;
        }
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
