package com.jointspaceforce.patchee.features;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;

/**
 * Matches a block being broken against the blocks a feature resolved at arm
 * time <b>by class</b>, not by instance identity.
 *
 * <p>
 * The three Twilight Forest break-speed features used to compare
 * {@code event.block} against the exact {@link Block} instances they read out of
 * {@code TFBlocks} with reflection. That is fragile: a metadata variant, a
 * GT:NH re-registration or a slab's double variant can be a different instance
 * of the same class, and the identity test would then silently reject every
 * break — the floor logs as armed but never fires. Matching on the runtime class
 * name (and its superclasses, so a subclass still counts) survives all of that.
 *
 * <p>
 * The decision is kept pure and string-based so it is unit-tested with no game.
 */
public final class BlockClassMatcher {

    private BlockClassMatcher() {}

    /** The runtime class names of the given blocks, resolved once at arm time. */
    public static Set<String> classNames(List<Block> blocks) {
        Set<String> names = new LinkedHashSet<String>();
        if (blocks != null) {
            for (Block block : blocks) {
                if (block != null) {
                    names.add(
                        block.getClass()
                            .getName());
                }
            }
        }
        return names;
    }

    /**
     * True when {@code type} — or any of its superclasses — is one of the
     * resolved target class names. Object is never a target, so the walk stops
     * before it.
     */
    public static boolean matchesClass(Set<String> targets, Class<?> type) {
        if (targets == null || targets.isEmpty() || type == null) {
            return false;
        }
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            if (targets.contains(current.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * The pure decision, no game types: true when the block's own class name is a
     * resolved target, or one of the block's superclass names is. A subclass of a
     * resolved block (and a different instance of the same class) therefore
     * matches, while an unrelated block class does not.
     */
    public static boolean matchesClass(Set<String> targets, String blockClass, Iterable<String> blockSuperclasses) {
        if (targets == null || targets.isEmpty() || blockClass == null) {
            return false;
        }
        if (targets.contains(blockClass)) {
            return true;
        }
        if (blockSuperclasses != null) {
            for (String name : blockSuperclasses) {
                if (name != null && targets.contains(name)) {
                    return true;
                }
            }
        }
        return false;
    }
}
