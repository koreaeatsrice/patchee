package com.jointspaceforce.patchee.features;

import java.util.Collections;
import java.util.List;

import com.jointspaceforce.patchee.core.Feature;
import com.jointspaceforce.patchee.core.Outcome;
import com.jointspaceforce.patchee.core.Step;

/**
 * Re-adds the old (GT:NH 2.9.0-beta-3) enriched fertilizer recipe next to the
 * pack's newer one.
 *
 * <p>
 * The work itself lives in {@link EnrichedFertilizerRecipe}, the only Patchee
 * class that touches GregTech. Patchee's house rule is that it must start on a
 * server with no GregTech at all, so this feature never holds a GregTech type in
 * a field, a parameter or a return value: the step is a lambda that calls the
 * holder and the holder class is loaded only when the step actually runs.
 * GregTech's absence is handled twice over — by the factory's missing-target gate
 * ({@link #targetClass()}) and again inside the holder — so a server without GT
 * simply logs the skip and carries on.
 *
 * <p>
 * The feature is config-gated ({@code enableEnrichedFertilizerRecipe}): switched
 * on (the default) the pack's own recipe and the old one both work; switched off
 * only the pack's newer recipe is left.
 */
public final class EnrichedFertilizerRecipeFeature implements Feature {

    public static final String ID = "EnrichedFertilizerRecipe";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String configKey() {
        return "enableEnrichedFertilizerRecipe";
    }

    @Override
    public boolean defaultEnabled() {
        return true;
    }

    @Override
    public String description() {
        return "Feature: keeps the old enriched fertilizer recipe as an extra choice - 4 fertilizer + 1000 L "
            + "fertilizer liquid -> 1000 L enriched fertilizer in a Chemical Reactor or Large Chemical Reactor "
            + "(LV, 1 second). The pack's newer Mixer recipe stays. true = both recipes work. false = only the "
            + "pack's recipe.";
    }

    @Override
    public String disabledMessage() {
        return "[EnrichedFertilizerRecipe] disabled in config - only the pack's own recipe";
    }

    @Override
    public String failureMessage() {
        return "[EnrichedFertilizerRecipe] failed - old recipe NOT added";
    }

    @Override
    public String targetClass() {
        return "gregtech.api.util.GTRecipeConstants";
    }

    @Override
    public String missingTargetMessage() {
        return "[EnrichedFertilizerRecipe] GregTech is not installed - old recipe skipped";
    }

    @Override
    public List<Step> steps() {
        return Collections.singletonList(ctx -> {
            try {
                return EnrichedFertilizerRecipe.apply(ctx);
            } catch (Exception | LinkageError failure) {
                ctx.log()
                    .error("{}", failureMessage(), failure);
                return Outcome.FAILED;
            }
        });
    }
}
