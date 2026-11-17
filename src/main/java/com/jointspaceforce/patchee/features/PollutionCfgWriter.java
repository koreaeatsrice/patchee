package com.jointspaceforce.patchee.features;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Writes one rewritten Forge config file, fail-soft.
 *
 * <p>
 * The order is: read the file, apply the key edits, and if anything changed copy
 * the original bytes to a timestamped {@code .bak} beside it, write the new text
 * to a {@code .tmp} beside it, then move the temp over the original (atomically
 * where the file system allows it). A second boot sees the file already correct
 * and does nothing - no write, no backup.
 *
 * <p>
 * Nothing here throws: a missing file, a read-only file or a read-only directory
 * comes back as a {@link Status} instead, so the boot log stays honest and the
 * in-memory half of the feature still runs.
 */
public final class PollutionCfgWriter {

    /** What one {@link #rewrite(File, List, String)} call did. */
    public enum Status {
        WRITTEN,
        ALREADY_CORRECT,
        MISSING_FILE,
        FAILED
    }

    /** The outcome, in a form the log line can read. */
    public static final class Result {

        public final Status status;
        public final List<String> changedKeys;
        public final List<String> missingKeys;

        /** The backup file's path, empty when no backup was made. */
        public final String backupPath;
        public final String message;

        private Result(Status status, List<String> changedKeys, List<String> missingKeys, String backupPath,
            String message) {
            this.status = status;
            this.changedKeys = Collections.unmodifiableList(new ArrayList<String>(changedKeys));
            this.missingKeys = Collections.unmodifiableList(new ArrayList<String>(missingKeys));
            this.backupPath = backupPath;
            this.message = message;
        }
    }

    private PollutionCfgWriter() {}

    /**
     * Rewrites {@code cfgFile} with {@code keys}. Never throws: a missing file is
     * left alone (nothing is created), an already-correct file is not touched,
     * and any I/O or runtime problem is reported as
     * {@link Status#FAILED}.
     */
    public static Result rewrite(File cfgFile, List<PollutionCfg.Key> keys, String timestamp) {
        List<String> changedKeys = new ArrayList<String>();
        try {
            if (cfgFile == null || !cfgFile.exists()) {
                return new Result(Status.MISSING_FILE, changedKeys, new ArrayList<String>(), "", "file does not exist");
            }

            String original = new String(Files.readAllBytes(cfgFile.toPath()), StandardCharsets.UTF_8);
            PollutionCfg.Edit edit = PollutionCfg.apply(original, keys);
            // Nothing to change: every value found is already right, even if some keys are missing altogether.
            // A missing key is GregTech's business, not Patchee's, so no write and no .bak.
            if (edit.changed == 0) {
                return new Result(
                    Status.ALREADY_CORRECT,
                    new ArrayList<String>(),
                    edit.missing,
                    "",
                    "no value needed changing");
            }
            changedKeys.addAll(changedKeys(edit.found, keys));

            File backup = new File(cfgFile.getParentFile(), cfgFile.getName() + "." + timestamp + ".bak");
            Files.copy(cfgFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);

            File temp = new File(cfgFile.getParentFile(), cfgFile.getName() + ".tmp");
            Files.write(temp.toPath(), edit.text.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(
                    temp.toPath(),
                    cfgFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException noAtomicMove) {
                Files.move(temp.toPath(), cfgFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return new Result(
                Status.WRITTEN,
                changedKeys,
                edit.missing,
                backup.getPath(),
                "wrote " + changedKeys.size() + " key(s)");
        } catch (IOException | RuntimeException failure) {
            return new Result(Status.FAILED, changedKeys, new ArrayList<String>(), "", failure.toString());
        }
    }

    /** The keys whose value in the file differs from the wanted one. */
    private static List<String> changedKeys(Map<String, String> found, List<PollutionCfg.Key> keys) {
        List<String> names = new ArrayList<String>();
        if (keys == null) {
            return names;
        }
        for (PollutionCfg.Key key : keys) {
            String old = found.get(key.name);
            if (old != null && !old.equals(key.value)) {
                names.add(key.name);
            }
        }
        return names;
    }
}
