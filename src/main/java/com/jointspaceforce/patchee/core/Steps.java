package com.jointspaceforce.patchee.core;

import java.lang.reflect.Field;

/**
 * Composition helpers: the glue that turns small steps into a feature's step
 * list, plus the reusable steps for the reflection work every feature shares
 * (find the target's static field, then act on it).
 */
public final class Steps {

    private Steps() {}

    /**
     * Runs the given steps in order. A {@link Outcome#FAILED} step stops the
     * sequence — the later steps of a feature depend on the earlier ones — while
     * a skip is not a reason to stop, so the feature still reports what it could
     * see. A step that throws is not caught here: the fail-soft decorator
     * already deals with that.
     */
    public static Step sequence(Step... steps) {
        return ctx -> {
            Outcome last = Outcome.SKIPPED;
            for (Step step : steps) {
                last = step.run(ctx);
                if (last == Outcome.FAILED) {
                    break;
                }
            }
            return last;
        };
    }

    /**
     * A step that only logs one line. Used to keep a feature's existing lines in
     * their original order when the work behind them is split up.
     */
    public static Step note(String infoLine) {
        return ctx -> {
            ctx.log()
                .info(infoLine);
            return Outcome.APPLIED;
        };
    }

    /**
     * The shared reflection step: resolve a declared static field of an optional
     * mod class, then let the feature's action do its work on that field.
     *
     * <p>
     * A class that is not installed is a missing target: wire this step behind
     * {@link StepDecorators#requiresTarget}. A class that is installed but no
     * longer has the field throws {@link NoSuchFieldException}, which the
     * fail-soft decorator reports as the feature's "failed - ... left unchanged"
     * line.
     */
    public static Step withStaticField(String ownerClassName, String fieldName, FieldAction action) {
        return ctx -> {
            Class<?> owner = ctx.reflect()
                .find(ownerClassName);
            if (owner == null) {
                return Outcome.SKIPPED;
            }
            Field field = ctx.reflect()
                .staticField(owner, fieldName);
            return action.run(field, ctx);
        };
    }

    /**
     * A target class is present on the classpath. Reads as a sentence at the call
     * site: {@code requiresTarget(id, present(CLASS), "... not installed - skipped", ...)}.
     */
    public static StepDecorators.TargetCheck present(String className) {
        return ctx -> ctx.reflect()
            .find(className) != null;
    }

    /** What a feature does with the static field it asked for. */
    @FunctionalInterface
    public interface FieldAction {

        Outcome run(Field field, PatchContext ctx) throws Exception;
    }

    /** A do-nothing step; keeps single-step features honest about being a step list. */
    public static Step noop() {
        return ctx -> Outcome.SKIPPED;
    }
}
