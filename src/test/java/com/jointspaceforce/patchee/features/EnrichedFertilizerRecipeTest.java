package com.jointspaceforce.patchee.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The pure, case-insensitive fluid-name comparison {@link EnrichedFertilizerRecipe} uses to fall
 * back from an exact registry lookup.
 *
 * <p>
 * The running server showed that {@code FluidRegistry.getFluid("cropsnh.enrichedFertilizer")}
 * returns {@code null} even though the fluid is registered — the registry stores names with
 * different case than our constants. {@code nameMatches} is the exact fallback, so it is pinned
 * here without needing a live {@code FluidRegistry}. {@code FluidRegistry} is not on the unit-test
 * classpath, so only this pure method is tested.
 */
class EnrichedFertilizerRecipeTest {

    /** An exact match is still a match. */
    @Test
    void exactNameMatches() {
        assertTrue(EnrichedFertilizerRecipe.nameMatches("cropsnh.fertilizer", "cropsnh.fertilizer"));
    }

    /** CropsNH registers the camel-cased name but the registry may report it lower-cased. */
    @Test
    void lowerCasedRegisteredNameStillMatchesTheCamelCasedWantedName() {
        assertTrue(EnrichedFertilizerRecipe.nameMatches("cropsnh.enrichedfertilizer", "cropsnh.enrichedFertilizer"));
        assertTrue(EnrichedFertilizerRecipe.nameMatches("cropsnh.enrichedFertilizer", "cropsnh.enrichedFertilizer"));
    }

    /** Upper-cased registered names match too. */
    @Test
    void upperCasedRegisteredNameMatches() {
        assertTrue(EnrichedFertilizerRecipe.nameMatches("CROPSNH.ENRICHEDFERTILIZER", "cropsnh.enrichedFertilizer"));
    }

    /** A different fluid is never a match. */
    @Test
    void differentNameNeverMatches() {
        assertFalse(EnrichedFertilizerRecipe.nameMatches("cropsnh.alcohol", "cropsnh.enrichedFertilizer"));
        assertFalse(EnrichedFertilizerRecipe.nameMatches("enrichedFertilizer", "cropsnh.enrichedFertilizer"));
    }

    /** Null on either side is not a match, and never throws. */
    @Test
    void nullNamesNeverMatch() {
        assertFalse(EnrichedFertilizerRecipe.nameMatches(null, "cropsnh.fertilizer"));
        assertFalse(EnrichedFertilizerRecipe.nameMatches("cropsnh.fertilizer", null));
        assertFalse(EnrichedFertilizerRecipe.nameMatches(null, null));
    }

    /** The armed line's side tag names the running side, so a client log is obvious. */
    @Test
    void sideLabelNamesTheSide() {
        assertEquals("(client)", EnrichedFertilizerRecipe.sideLabel(true));
        assertEquals("(server)", EnrichedFertilizerRecipe.sideLabel(false));
    }

    /**
     * GT's NEI cache is dropped only on a physical client that really added the recipe: a server
     * has no NEI, and a failed add must not trigger anything.
     */
    @Test
    void neiReloadOnlyOnAClientWithAnAddedRecipe() {
        assertTrue(EnrichedFertilizerRecipe.shouldRequestNeiReload(true, true));
        assertFalse(EnrichedFertilizerRecipe.shouldRequestNeiReload(false, true));
        assertFalse(EnrichedFertilizerRecipe.shouldRequestNeiReload(true, false));
        assertFalse(EnrichedFertilizerRecipe.shouldRequestNeiReload(false, false));
    }
}
