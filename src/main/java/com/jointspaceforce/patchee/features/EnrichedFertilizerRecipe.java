package com.jointspaceforce.patchee.features;

import java.util.Collection;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.jointspaceforce.patchee.core.Outcome;
import com.jointspaceforce.patchee.core.PatchContext;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
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
     * Looks a fluid up by its registry name, tolerating case differences.
     *
     * <p>
     * {@link FluidRegistry#getFluid(String)} is an exact-name map lookup, and
     * registration can store a name with different case than the constant we
     * look for (CropsNH registers {@code cropsnh.enrichedFertilizer}, while the
     * registry's own stored name may be lower-cased). When the exact lookup
     * misses we scan every registered fluid and take the first whose
     * {@link Fluid#getName() name} matches case-insensitively; when that also
     * misses we return {@code null} so the caller reports the failure.
     */
    private static Fluid findFluid(String wanted) {
        Fluid exact = FluidRegistry.getFluid(wanted);
        if (exact != null) {
            return exact;
        }
        for (Fluid fluid : FluidRegistry.getRegisteredFluids()
            .values()) {
            if (fluid != null && nameMatches(fluid.getName(), wanted)) {
                return fluid;
            }
        }
        return null;
    }

    /**
     * True when a registered fluid name equals the wanted name, ignoring case.
     * Pure and free of any registry access, so it is unit-testable on its own.
     */
    static boolean nameMatches(String registered, String wanted) {
        return registered != null && wanted != null && registered.equalsIgnoreCase(wanted);
    }

    /**
     * One line naming every registered fluid whose name contains {@code "fert"}
     * (name plus class), so a future lookup failure shows what the registry
     * actually holds. Never throws and never returns {@code null}.
     */
    private static String describeFertFluids() {
        try {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Fluid fluid : FluidRegistry.getRegisteredFluids()
                .values()) {
                if (fluid == null || fluid.getName() == null
                    || !fluid.getName()
                        .toLowerCase(java.util.Locale.ROOT)
                        .contains("fert")) {
                    continue;
                }
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append(fluid.getName())
                    .append('=')
                    .append(
                        fluid.getClass()
                            .getName());
            }
            if (first) {
                sb.append("none");
            }
            return sb.append(']')
                .toString();
        } catch (Exception | LinkageError failure) {
            return "[<could not list registered fert fluids: " + failure + ">]";
        }
    }

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
        Fluid fert = findFluid(FLUID_NAME);
        Fluid enriched = findFluid(ENRICHED_FLUID_NAME);
        if (item == null || fert == null || enriched == null) {
            ctx.log()
                .error(
                    "[EnrichedFertilizerRecipe] cannot add the old recipe: item={} fertilizer={} enriched={} registered-fert-fluids={}",
                    item,
                    fert,
                    enriched,
                    describeFertFluids());
            return Outcome.FAILED;
        }
        Collection<GTRecipe> added = GTValues.RA.stdBuilder()
            .itemInputs(new ItemStack(item, ITEMS_PER_CRAFT))
            .fluidInputs(new FluidStack(fert, FLUID_MB))
            .fluidOutputs(new FluidStack(enriched, FLUID_MB))
            .duration(DURATION_TICKS)
            .eut(TierEU.RECIPE_LV)
            .addTo(GTRecipeConstants.UniversalChemical);
        boolean addedEntries = added != null && !added.isEmpty();
        if (!addedEntries) {
            ctx.log()
                .error("[EnrichedFertilizerRecipe] the old recipe produced no entries - old recipe NOT added");
            return Outcome.FAILED;
        }
        boolean clientSide = FMLCommonHandler.instance()
            .getSide() == Side.CLIENT;
        ctx.log()
            .info(
                "[EnrichedFertilizerRecipe] armed - old recipe added: 4 fertilizer + 1000 mB fertilizer -> 1000 mB enriched fertilizer (Chemical Reactor and LCR, LV, 20 ticks) "
                    + sideLabel(clientSide));
        // On a physical client the recipe is in the maps but NEI may already have
        // built and cached its Chemical Reactor / LCR pages, so the new recipe
        // never shows until GT's NEI cache is dropped. Patchee adds the recipe at
        // postInit, after NEI's first page build, which is exactly that case.
        if (shouldRequestNeiReload(clientSide, addedEntries)) {
            requestNeiCacheReload(ctx);
        }
        return Outcome.APPLIED;
    }

    /**
     * The side tag the armed line carries, so a client log makes it obvious the
     * feature ran there. Pure and free of any game type, so it is unit-testable.
     */
    static String sideLabel(boolean clientSide) {
        return clientSide ? "(client)" : "(server)";
    }

    /**
     * True when GT's NEI recipe cache must be dropped: only when the recipe was
     * really added and only on the physical client, where NEI builds and caches
     * the Chemical Reactor pages. On a server NEI is absent, so nothing is asked.
     * Pure and free of any game type, so it is unit-testable.
     */
    static boolean shouldRequestNeiReload(boolean clientSide, boolean addedEntries) {
        return clientSide && addedEntries;
    }

    /**
     * Asks GregTech to drop its NEI recipe cache so the pages it already built
     * are rebuilt and pick the old recipe up.
     *
     * <p>
     * Done reflectively on purpose, to keep Patchee's no-compile-linkage
     * discipline: no GregTech type appears in a field, a parameter or a return
     * value here. {@code gregtech.GTMod.proxy} is the running side's proxy (a
     * {@code GTClient} on a client), and {@code reloadNEICache()} is the counter
     * bump GT's handlers watch. Never throws: any failure logs one INFO line and
     * the recipe step still succeeds.
     */
    private static void requestNeiCacheReload(PatchContext ctx) {
        try {
            Class<?> gtMod = ctx.reflect()
                .find("gregtech.GTMod");
            if (gtMod == null) {
                ctx.log()
                    .info(
                        "[EnrichedFertilizerRecipe] could not ask GregTech to reload its NEI recipe cache: gregtech.GTMod is absent");
                return;
            }
            Object proxy = ctx.reflect()
                .staticValue(
                    ctx.reflect()
                        .staticField(gtMod, "proxy"));
            if (proxy == null) {
                ctx.log()
                    .info(
                        "[EnrichedFertilizerRecipe] could not ask GregTech to reload its NEI recipe cache: gregtech.GTMod.proxy is not set yet");
                return;
            }
            proxy.getClass()
                .getMethod("reloadNEICache")
                .invoke(proxy);
            ctx.log()
                .info("[EnrichedFertilizerRecipe] NEI cache reload requested so the page picks the recipe up");
        } catch (Throwable failure) {
            ctx.log()
                .info(
                    "[EnrichedFertilizerRecipe] could not ask GregTech to reload its NEI recipe cache: {}",
                    failure.toString());
        }
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
            Fluid fert = findFluid(FLUID_NAME);
            Fluid enriched = findFluid(ENRICHED_FLUID_NAME);
            if (item == null || fert == null || enriched == null) {
                return "FAIL the old enriched fertilizer recipe cannot be checked: item=" + item
                    + " fertilizer="
                    + fert
                    + " enriched="
                    + enriched
                    + " registered-fert-fluids="
                    + describeFertFluids();
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
