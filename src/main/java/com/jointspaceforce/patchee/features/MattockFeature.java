package com.jointspaceforce.patchee.features;

import java.util.Collections;
import java.util.List;

import net.minecraft.block.material.Material;

import com.jointspaceforce.patchee.core.Feature;
import com.jointspaceforce.patchee.core.Outcome;
import com.jointspaceforce.patchee.core.Step;
import com.jointspaceforce.patchee.core.Steps;

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
 *
 * <p>
 * The work itself is one composed step: read the static material array through
 * the shared reflection helper, add the missing materials once, and write it
 * back only when something was actually missing (so a second call is a no-op).
 */
public final class MattockFeature implements Feature {

    public static final String ID = "MattockFix";

    private static final String MATTOCK_CLASS = "tconstruct.items.tools.Mattock";
    private static final String FIELD_NAME = "shovelMaterials";

    /** The shovel-friendly materials the mattock is missing by default. */
    private static final Material[] EXTRA_MATERIALS = new Material[] { Material.sand, Material.snow,
        Material.craftedSnow };

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String configKey() {
        return "enableMattockFix";
    }

    @Override
    public boolean defaultEnabled() {
        return true;
    }

    @Override
    public String description() {
        return "Feature: the Tinkers' Construct mattock digs sand and snow like a shovel. true = fixed. "
            + "false = vanilla behaviour.";
    }

    @Override
    public String disabledMessage() {
        return "[MattockFix] disabled in config — mattock left untouched";
    }

    @Override
    public String failureMessage() {
        return "[MattockFix] failed — mattock left unchanged";
    }

    @Override
    public String targetClass() {
        return MATTOCK_CLASS;
    }

    @Override
    public String missingTargetMessage() {
        return "[MattockFix] Tinkers' Construct is not installed — mattock fix skipped";
    }

    @Override
    public List<Step> steps() {
        return Collections.singletonList(extendShovelMaterials());
    }

    /** The composed step: extend the static shovel-material array, reporting the delta. */
    private Step extendShovelMaterials() {
        return Steps.withStaticField(MATTOCK_CLASS, FIELD_NAME, (field, ctx) -> {
            Material[] current = (Material[]) ctx.reflect()
                .staticValue(field);
            if (current == null) {
                ctx.log()
                    .error("[MattockFix] {}#{} is null — aborting", MATTOCK_CLASS, FIELD_NAME);
                return Outcome.FAILED;
            }

            Material[] merged = ctx.reflect()
                .extendMaterials(current, EXTRA_MATERIALS);
            int added = merged.length - current.length;

            if (added == 0) {
                ctx.log()
                    .info("[MattockFix] shovel materials already present — nothing to do");
                return Outcome.SKIPPED;
            }

            ctx.reflect()
                .setStatic(field, merged);
            ctx.log()
                .info("[MattockFix] mattock shovel materials {} -> {} (+{})", current.length, merged.length, added);
            return Outcome.APPLIED;
        });
    }
}
