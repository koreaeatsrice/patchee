package com.jointspaceforce.patchee.features;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The pure, string-based class-name matcher the Twilight break-speed features use
 * instead of instance identity. No game is needed: the decision is only about
 * class names.
 *
 * <p>
 * The point is that a placed block whose class is one of the resolved target
 * classes — a different instance of the same class, a re-registration or a
 * metadata variant — matches, and so does a subclass of a resolved block, while
 * an unrelated block class does not.
 */
class BlockClassMatcherTest {

    private static final Set<String> AURORA = Collections.singleton("twilightforest.block.BlockTFAuroraBrick");

    @Test
    void acceptsAnotherInstanceOfTheSameClass() {
        assertTrue(BlockClassMatcher.matchesClass(AURORA, "twilightforest.block.BlockTFAuroraBrick", null));
    }

    @Test
    void acceptsASubclassOfAResolvedBlock() {
        // The placed block is some other class, but its superclass is a resolved one.
        assertTrue(
            BlockClassMatcher.matchesClass(
                AURORA,
                "com.example.MyAuroraVariant",
                Arrays.asList("twilightforest.block.BlockTFAuroraBrick", "net.minecraft.block.Block")));
    }

    @Test
    void rejectsAnUnrelatedBlockClass() {
        assertFalse(
            BlockClassMatcher.matchesClass(
                AURORA,
                "net.minecraft.block.BlockStone",
                Arrays.asList("net.minecraft.block.Block", "java.lang.Object")));
    }

    @Test
    void rejectsWhenNothingWasResolved() {
        assertFalse(BlockClassMatcher.matchesClass(Collections.<String>emptySet(), "any.Block", null));
        assertFalse(BlockClassMatcher.matchesClass(null, "any.Block", null));
        assertFalse(BlockClassMatcher.matchesClass(AURORA, null, null));
    }

    @Test
    void theClassOverloadWalksSuperclassesAndStopsBeforeObject() {
        Set<String> lists = Collections.singleton("java.util.AbstractList");
        assertTrue(BlockClassMatcher.matchesClass(lists, java.util.ArrayList.class));
        assertFalse(BlockClassMatcher.matchesClass(lists, String.class));
        // Object itself is never a target, so a set naming it matches nothing new.
        assertFalse(BlockClassMatcher.matchesClass(Collections.singleton("java.lang.Object"), String.class));
    }
}
