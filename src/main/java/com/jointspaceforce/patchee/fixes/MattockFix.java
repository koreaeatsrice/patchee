package com.jointspaceforce.patchee.fixes;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.block.material.Material;

import com.jointspaceforce.patchee.Config;
import com.jointspaceforce.patchee.Patchee;

/**
 * Makes the Tinkers' Construct Mattock dig sand, snow layers and snow blocks
 * at full shovel speed.
 *
 * <p>
 * {@code tconstruct.items.tools.Mattock} keeps two static
 * {@code Material[]} arrays — {@code axeMaterials} and {@code shovelMaterials}
 * — returned by {@code getEffectiveMaterials()} /
 * {@code getEffectiveSecondaryMaterials()}. {@code DualHarvestTool}'s
 * {@code getDigSpeed}/{@code isEffective} read those getters on every call
 * (verified in bytecode: {@code getstatic} + {@code areturn}), so replacing
 * the {@code shovelMaterials} array affects already-crafted mattocks
 * immediately.
 *
 * <p>
 * This mirrors the behaviour of the earlier bytecode patch: the blocks are
 * dug at full mattock speed and harvested (snow layer/block drop their
 * snowballs), and the Forge "shovel" tool class is deliberately NOT claimed.
 * The only intended visible change is that the mattock now reports
 * {@code canHarvestBlock}/{@code isEffective} as true for those materials —
 * that is what the fix is for and all that reads it. This fix therefore lives
 * in a separate mod instead of editing TConstruct's jar (which a pack update
 * would silently revert).
 */
public final class MattockFix {

    private static final String MATTOCK_CLASS = "tconstruct.items.tools.Mattock";
    private static final String FIELD_NAME = "shovelMaterials";

    /** The shovel-friendly materials the mattock is missing by default. */
    private static final Material[] EXTRA_MATERIALS = new Material[] { Material.sand, Material.snow,
        Material.craftedSnow };

    private MattockFix() {}

    public static void apply() {
        if (!Config.enableMattockFix) {
            Patchee.LOG.info("[MattockFix] disabled in config — mattock left untouched");
            return;
        }

        Class<?> mattock;
        try {
            mattock = Class.forName(MATTOCK_CLASS);
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            // Normal on packs without TConstruct — not an error worth a stack trace.
            Patchee.LOG.info("[MattockFix] Tinkers' Construct is not installed — mattock fix skipped");
            return;
        }

        try {
            Field field = mattock.getDeclaredField(FIELD_NAME);
            field.setAccessible(true);

            Material[] current = (Material[]) field.get(null);
            if (current == null) {
                Patchee.LOG.error("[MattockFix] {}#{} is null — aborting", MATTOCK_CLASS, FIELD_NAME);
                return;
            }

            List<Material> merged = new ArrayList<Material>(Arrays.asList(current));
            int added = 0;
            for (Material material : EXTRA_MATERIALS) {
                if (material != null && !merged.contains(material)) {
                    merged.add(material);
                    added++;
                }
            }

            if (added == 0) {
                Patchee.LOG.info("[MattockFix] shovel materials already present — nothing to do");
                return;
            }

            field.set(null, merged.toArray(new Material[0]));
            Patchee.LOG
                .info("[MattockFix] mattock shovel materials {} -> {} (+{})", current.length, merged.size(), added);
        } catch (Exception | LinkageError t) {
            // Fail soft: a changed TConstruct must never break the server.
            Patchee.LOG.error("[MattockFix] failed — mattock left unchanged", t);
        }
    }
}
