package com.jointspaceforce.patchee.features;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.tileentity.TileEntity;

import com.jointspaceforce.patchee.core.Feature;
import com.jointspaceforce.patchee.core.Outcome;
import com.jointspaceforce.patchee.core.PatchContext;
import com.jointspaceforce.patchee.core.Step;
import com.jointspaceforce.patchee.core.Steps;

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
 *
 * <p>
 * The work itself is one composed step: find the gate's static list through the
 * shared reflection helper, then append every housing class that is actually
 * there. JABBA being absent is a missing target, so it is skipped with an INFO
 * line instead of failing.
 */
public final class DollyFeature implements Feature {

    public static final String ID = "DollyFix";

    private static final String MOVER_CLASS = "mcp.mobius.betterbarrels.common.items.dolly.ItemBarrelMover";
    private static final String LIST_FIELD = "classExtensions";

    /**
     * Single-block crafted bee housings, plus Forestry's abstract housing base
     * (its ONLY concrete subclasses are {@code TileApiary} and
     * {@code TileBeehouse} — verified against the shipped Forestry jar — so it
     * cannot sweep in natural hives or multiblock parts). Absent mods are
     * skipped with a log line.
     */
    static final String[] HOUSINGS = new String[] {
        // Forestry — Apiary and Bee House (the base class covers both; both are
        // listed explicitly so a future Forestry refactor cannot silently drop one)
        "forestry.apiculture.tiles.TileBeeHousingBase", "forestry.apiculture.tiles.TileApiary",
        "forestry.apiculture.tiles.TileBeehouse",
        // Gendustry — Industrial Apiary
        "net.bdew.gendustry.machines.apiary.TileApiary",
        // MagicBees — Magic Apiary
        "magicbees.tileentity.TileEntityMagicApiary" };

    /** The in-scope housing class names, exposed for the self-test. */
    public static String[] beeHousingClassNames() {
        return HOUSINGS.clone();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String configKey() {
        return "enableDollyFix";
    }

    @Override
    public boolean defaultEnabled() {
        return true;
    }

    @Override
    public String description() {
        return "Feature: move bee houses with the JABBA Dolly (keeps the bees inside). true = the Dolly "
            + "can pick up Forestry's Apiary and Bee House, Gendustry's Industrial Apiary and "
            + "MagicBees' Magic Apiary. false = vanilla behaviour.";
    }

    @Override
    public String disabledMessage() {
        return "[DollyFix] disabled in config — Dolly left untouched";
    }

    @Override
    public String failureMessage() {
        return "[DollyFix] failed — JABBA Dolly left unchanged";
    }

    @Override
    public String targetClass() {
        return MOVER_CLASS;
    }

    @Override
    public String missingTargetMessage() {
        return "[DollyFix] JABBA is not installed — dolly fix skipped";
    }

    @Override
    public List<Step> steps() {
        return Collections.singletonList(appendHousings());
    }

    /** The composed step: the list append, including its one-line summary. */
    private Step appendHousings() {
        return Steps.withStaticField(MOVER_CLASS, LIST_FIELD, (field, ctx) -> {
            List<Class<?>> movable = ctx.reflect()
                .staticClassList(field);
            if (movable == null) {
                ctx.log()
                    .error("[DollyFix] {}#{} is not a List — aborting", MOVER_CLASS, LIST_FIELD);
                return Outcome.FAILED;
            }

            List<String> absent = new ArrayList<String>();
            List<String> rejected = new ArrayList<String>();
            int added = 0;

            for (String name : wanted(ctx)) {
                Class<?> clazz = ctx.reflect()
                    .find(name);
                if (clazz == null) {
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
                if (ctx.reflect()
                    .appendIfAbsent(movable, clazz)) {
                    added++;
                }
            }

            ctx.log()
                .info(
                    "[DollyFix] Dolly now accepts {} bee-housing class(es){}",
                    added,
                    absent.isEmpty() ? "" : " (mods not installed, skipped: " + absent + ")");
            if (!rejected.isEmpty()) {
                ctx.log()
                    .warn("[DollyFix] ignored entries that are not TileEntities: {}", rejected);
            }
            return Outcome.APPLIED;
        });
    }

    /** The in-scope housings, then the config's extra names (trimmed, once each). */
    private static List<String> wanted(PatchContext ctx) {
        List<String> wanted = new ArrayList<String>(Arrays.asList(HOUSINGS));
        for (String extra : ctx.toggles()
            .extraDollyClasses()) {
            if (extra != null && !extra.trim()
                .isEmpty() && !wanted.contains(extra.trim())) {
                wanted.add(extra.trim());
            }
        }
        return wanted;
    }
}
