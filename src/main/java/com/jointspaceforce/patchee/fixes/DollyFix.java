package com.jointspaceforce.patchee.fixes;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.tileentity.TileEntity;

import com.jointspaceforce.patchee.Config;
import com.jointspaceforce.patchee.Patchee;

/**
 * Makes JABBA's Dolly accept the single-block crafted bee housings.
 *
 * <p>
 * Jabba's pickup gate ({@code mcp.mobius.betterbarrels.common.items.dolly.ItemBarrelMover})
 * allows barrels, vanilla chests, and any TileEntity matching the hardcoded
 * static list {@code classExtensions}. That list is built exactly once from
 * {@code classExtensionsNames} in the class initializer (Jabba's preInit) and
 * is never rebuilt, and the gate re-reads it on every use — so appending to it
 * here, at postInit, makes the new blocks movable for the rest of the session.
 *
 * <p>
 * The mod ships a "MovableRegistrar"/"IDollyHandler" API package, but
 * nothing in Jabba ever calls it (verified repo-wide) — it cannot be used.
 * The list append is the only working route, and doing it from a separate mod
 * keeps Jabba's jar untouched.
 *
 * <p>
 * Scope (per the server owner): <b>single-block crafted bee housings only</b>.
 * Natural/world-generation hives and the multi-block Alveary are deliberately
 * NOT included.
 */
public final class DollyFix {

    private static final String MOVER_CLASS = "mcp.mobius.betterbarrels.common.items.dolly.ItemBarrelMover";
    private static final String LIST_FIELD = "classExtensions";

    /**
     * Single-block crafted bee housings, plus Forestry's abstract housing base
     * (its ONLY concrete subclasses are {@code TileApiary} and
     * {@code TileBeehouse} — verified against the shipped Forestry jar — so it
     * cannot sweep in natural hives or multiblock parts). Absent mods are
     * skipped with a log line.
     */
    private static final String[] BEE_HOUSINGS = new String[] {
        // Forestry — Apiary and Bee House (the base class covers both; both are
        // listed explicitly so a future Forestry refactor cannot silently drop one)
        "forestry.apiculture.tiles.TileBeeHousingBase", "forestry.apiculture.tiles.TileApiary",
        "forestry.apiculture.tiles.TileBeehouse",
        // Gendustry — Industrial Apiary
        "net.bdew.gendustry.machines.apiary.TileApiary",
        // MagicBees — Magic Apiary
        "magicbees.tileentity.TileEntityMagicApiary" };

    private DollyFix() {}

    /** The in-scope housing class names, exposed for the self-test. */
    public static String[] beeHousingClassNames() {
        return BEE_HOUSINGS.clone();
    }

    public static void apply() {
        if (!Config.enableDollyFix) {
            Patchee.LOG.info("[DollyFix] disabled in config — Dolly left untouched");
            return;
        }

        List<String> wanted = new ArrayList<String>(Arrays.asList(BEE_HOUSINGS));
        for (String extra : Config.extraDollyClasses) {
            if (extra != null && !extra.trim()
                .isEmpty() && !wanted.contains(extra.trim())) {
                wanted.add(extra.trim());
            }
        }

        Class<?> mover;
        try {
            mover = Class.forName(MOVER_CLASS);
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            // Normal on packs without JABBA — not an error worth a stack trace.
            Patchee.LOG.info("[DollyFix] JABBA is not installed — dolly fix skipped");
            return;
        }

        try {
            Field field = mover.getDeclaredField(LIST_FIELD);
            field.setAccessible(true);

            Object raw = field.get(null);
            if (!(raw instanceof List)) {
                Patchee.LOG.error("[DollyFix] {}#{} is not a List — aborting", MOVER_CLASS, LIST_FIELD);
                return;
            }

            @SuppressWarnings("unchecked")
            List<Class<?>> movable = (List<Class<?>>) raw;

            List<String> absent = new ArrayList<String>();
            List<String> rejected = new ArrayList<String>();
            int added = 0;

            for (String name : wanted) {
                Class<?> clazz;
                try {
                    clazz = Class.forName(name);
                } catch (ClassNotFoundException | LinkageError e) {
                    // The owning mod is absent or half-loaded — expected, keep going.
                    absent.add(name);
                    continue;
                }
                if (!TileEntity.class.isAssignableFrom(clazz)) {
                    // Mirror Jabba's own sanity check: only TileEntities can
                    // ever reach the gate, so anything else is a typo.
                    rejected.add(name);
                    continue;
                }
                if (!movable.contains(clazz)) {
                    movable.add(clazz);
                    added++;
                }
            }

            Patchee.LOG.info(
                "[DollyFix] Dolly now accepts {} bee-housing class(es){}",
                added,
                absent.isEmpty() ? "" : " (mods not installed, skipped: " + absent + ")");
            if (!rejected.isEmpty()) {
                Patchee.LOG.warn("[DollyFix] ignored entries that are not TileEntities: {}", rejected);
            }
        } catch (Exception | LinkageError t) {
            // Fail soft: a changed Jabba must never break the server.
            Patchee.LOG.error("[DollyFix] failed — JABBA Dolly left unchanged", t);
        }
    }
}
