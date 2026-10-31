package com.jointspaceforce.patchee.features;

import java.util.Collection;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.jointspaceforce.patchee.core.Outcome;
import com.jointspaceforce.patchee.core.PatchContext;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.TierEU;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeConstants;

/**
 * Keeps the old (GT:NH 2.9.0-beta-3) enriched fertilizer recipe as an extra
 * choice next to the pack's newer one.
 *
 * <p>
 * GT:NH 2.9.0 ships CropsNH 2.0.140, whose item registry name is
 * {@code cropsnh:fertilizer} and whose two fluids are registered as
 * {@code cropsnh.fertilizer} and {@code cropsnh.enrichedFertilizer}. The old
 * recipe — 4 fertilizer items plus 1000 mB fertilizer fluid turning into 1000 mB
 * enriched fertilizer in the Chemical Reactor (and the Large Chemical Reactor) —
 * was dropped from the pack. This class re-adds it, and only adds: the pack's
 * own recipe is never touched.
 *
 * <p>
 * This is the <b>only</b> Patchee class that links against GregTech. Patchee's
 * house rule is that it must start on a server that has no GregTech at all, so
 * the feature that owns this work reaches it only when the step actually runs
 * (see {@link EnrichedFertilizerRecipeFeature}); the holder class is therefore
 * never loaded unless GregTech is present. Every type here is GT5-Unofficial's
 * public API: {@code GTValues.RA.stdBuilder()} builds the recipe and
 * {@code addTo(GTRecipeConstants.UniversalChemical)} registers it in the
 * Chemical Reactor <i>and</i> the Large Chemical Reactor in one call, exactly
 * the way the pack's own recipes are added.
 */
public final class EnrichedFertilizerRecipe {

    /** CropsNH's mod id. */
    public static final String CROP_MODID = "cropsnh";

    /** CropsNH's fertilizer item registry name. */
    private static final String FERTILIZER_ITEM = "fertilizer";

    /** The plain fertilizer fluid's registry name. */
    private static final String FLUID_NAME = "cropsnh.fertilizer";

    /** The enriched fertilizer fluid's registry name. */
    private static final String ENRICHED_FLUID_NAME = "cropsnh.enrichedFertilizer";

    /** How many fertilizer items one craft consumes. */
    private static final int ITEMS_PER_CRAFT = 4;

    /** How much fertilizer fluid one craft consumes, and how much enriched fluid it makes, in mB. */
    private static final int FLUID_MB = 1000;

    /** How long one craft takes, in ticks. */
    private static final int DURATION_TICKS = 20;

    /** The expected recipe tier's EU/t: 30 EU/t, i.e. {@code TierEU.RECIPE_LV}. */
    private static final int EXPECTED_EUT = 30;

    /** The duration the old recipe must have, in ticks. */
    private static final int EXPECTED_DURATION = DURATION_TICKS;

    private EnrichedFertilizerRecipe() {}

    /**
     * Adds the old recipe to the Chemical Reactor and the Large Chemical
     * Reactor. CropsNH being absent is not an error (Patchee starts without it):
     * the old recipe is simply skipped with an INFO line.
     */
    public static Outcome apply(PatchContext ctx) {
        if (!Loader.isModLoaded(CROP_MODID)) {
            ctx.log()
                .info("[EnrichedFertilizerRecipe] CropsNH is not installed - old recipe skipped");
            return Outcome.SKIPPED;
        }
        Item item = GameRegistry.findItem(CROP_MODID, FERTILIZER_ITEM);
        Fluid fert = FluidRegistry.getFluid(FLUID_NAME);
        Fluid enriched = FluidRegistry.getFluid(ENRICHED_FLUID_NAME);
        if (item == null || fert == null || enriched == null) {
            ctx.log()
                .error(
                    "[EnrichedFertilizerRecipe] cannot add the old recipe: item={} fertilizer={} enriched={}",
                    item,
                    fert,
                    enriched);
            return Outcome.FAILED;
        }
        Collection<GTRecipe> added = GTValues.RA.stdBuilder()
            .itemInputs(new ItemStack(item, ITEMS_PER_CRAFT))
            .fluidInputs(new FluidStack(fert, FLUID_MB))
            .fluidOutputs(new FluidStack(enriched, FLUID_MB))
            .duration(DURATION_TICKS)
            .eut(TierEU.RECIPE_LV)
            .addTo(GTRecipeConstants.UniversalChemical);
        if (added == null || added.isEmpty()) {
            ctx.log()
                .error("[EnrichedFertilizerRecipe] the old recipe produced no entries - old recipe NOT added");
            return Outcome.FAILED;
        }
        ctx.log()
            .info(
                "[EnrichedFertilizerRecipe] armed - old recipe added: 4 fertilizer + 1000 mB fertilizer -> 1000 mB enriched fertilizer (Chemical Reactor and LCR, LV, 20 ticks)");
        return Outcome.APPLIED;
    }

    /**
     * The post-boot self-check: read the two chemical recipe maps back and prove
     * the old recipe is really in both. Returns {@code null} when CropsNH is
     * absent (nothing to check); otherwise a one-line string starting with
     * {@code "PASS "} or {@code "FAIL "}. Never throws — a self-check that
     * crashes the server would be worse than the problem it reports.
     */
    public static String selfTest() {
        try {
            if (!Loader.isModLoaded(CROP_MODID)) {
                return null;
            }
            Item item = GameRegistry.findItem(CROP_MODID, FERTILIZER_ITEM);
            Fluid fert = FluidRegistry.getFluid(FLUID_NAME);
            Fluid enriched = FluidRegistry.getFluid(ENRICHED_FLUID_NAME);
            if (item == null || fert == null || enriched == null) {
                return "FAIL the old enriched fertilizer recipe cannot be checked: item=" + item
                    + " fertilizer="
                    + fert
                    + " enriched="
                    + enriched;
            }
            int reactor = countMatches(RecipeMaps.chemicalReactorRecipes.getAllRecipes(), item, fert, enriched);
            int lcr = countMatches(RecipeMaps.multiblockChemicalReactorRecipes.getAllRecipes(), item, fert, enriched);
            if (reactor >= 1 && lcr >= 1) {
                return "PASS the old enriched fertilizer recipe is present in chemicalReactorRecipes (" + reactor
                    + " match(es)) and multiblockChemicalReactorRecipes ("
                    + lcr
                    + " match(es)) at LV 30 EU/t, 20 ticks";
            }
            return "FAIL the old enriched fertilizer recipe was not found: " + reactor
                + " match(es) in chemicalReactorRecipes, "
                + lcr
                + " match(es) in multiblockChemicalReactorRecipes";
        } catch (Exception | LinkageError failure) {
            return "FAIL the old enriched fertilizer recipe check crashed: " + failure;
        }
    }

    /** How many recipes in the map are the old enriched fertilizer recipe. */
    private static int countMatches(Collection<GTRecipe> recipes, Item item, Fluid fert, Fluid enriched) {
        int count = 0;
        for (GTRecipe recipe : recipes) {
            if (isOldRecipe(recipe, item, fert, enriched)) {
                count++;
            }
        }
        return count;
    }

    /**
     * True when a recipe is 1000 mB fertilizer + 4 fertilizer items -> 1000 mB enriched fertilizer at
     * LV 30 EU/t and 20 ticks. The tier and duration are checked too, so a wrong-tier or
     * wrong-duration recipe can never count as a match.
     */
    private static boolean isOldRecipe(GTRecipe recipe, Item item, Fluid fert, Fluid enriched) {
        if (recipe.mEUt != EXPECTED_EUT || recipe.mDuration != EXPECTED_DURATION) {
            return false;
        }
        if (!hasFluid(recipe.mFluidInputs, fert, FLUID_MB)) {
            return false;
        }
        if (!hasFluid(recipe.mFluidOutputs, enriched, FLUID_MB)) {
            return false;
        }
        return itemCount(recipe.mInputs, item) == ITEMS_PER_CRAFT;
    }

    /** True when the fluid array holds exactly {@code mB} of {@code fluid}. */
    private static boolean hasFluid(FluidStack[] fluids, Fluid fluid, int mB) {
        if (fluids == null) {
            return false;
        }
        for (FluidStack stack : fluids) {
            if (stack != null && stack.getFluid() == fluid && stack.amount == mB) {
                return true;
            }
        }
        return false;
    }

    /** How many of {@code item} the item array holds in total. */
    private static int itemCount(ItemStack[] inputs, Item item) {
        if (inputs == null) {
            return 0;
        }
        int total = 0;
        for (ItemStack stack : inputs) {
            if (stack != null && stack.getItem() == item) {
                total += stack.stackSize;
            }
        }
        return total;
    }
}
