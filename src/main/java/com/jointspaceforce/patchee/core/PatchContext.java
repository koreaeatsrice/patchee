package com.jointspaceforce.patchee.core;

import java.io.File;

import org.apache.logging.log4j.Logger;

import com.jointspaceforce.patchee.core.policy.FailurePolicy;
import com.jointspaceforce.patchee.core.policy.MissingTargetPolicy;
import com.jointspaceforce.patchee.core.reflect.Reflective;

/**
 * Everything a {@link Step} is allowed to use, handed over once by the
 * composition root: the mod logger, the config snapshot, the policies and the
 * shared reflection helpers. Nothing in a feature reaches for a static.
 */
public final class PatchContext {

    private final Logger log;
    private final Toggles toggles;
    private final FailurePolicy failurePolicy;
    private final MissingTargetPolicy missingTargetPolicy;
    private final Reflective reflect;
    private final File configDirectory;

    public PatchContext(Logger log, Toggles toggles, FailurePolicy failurePolicy,
        MissingTargetPolicy missingTargetPolicy, Reflective reflect, File configDirectory) {
        this.log = log;
        this.toggles = toggles;
        this.failurePolicy = failurePolicy;
        this.missingTargetPolicy = missingTargetPolicy;
        this.reflect = reflect;
        this.configDirectory = configDirectory;
    }

    public Logger log() {
        return log;
    }

    public Toggles toggles() {
        return toggles;
    }

    public FailurePolicy failurePolicy() {
        return failurePolicy;
    }

    public MissingTargetPolicy missingTargetPolicy() {
        return missingTargetPolicy;
    }

    public Reflective reflect() {
        return reflect;
    }

    /**
     * The game's {@code config/} directory, as handed to Patchee at preInit.
     * A feature that writes another mod's config file builds its path from this
     * instead of reaching for a static.
     */
    public File configDirectory() {
        return configDirectory;
    }
}
