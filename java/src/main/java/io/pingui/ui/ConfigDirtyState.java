package io.pingui.ui;

import java.util.Objects;

/** Tracks whether the in-memory profile document needs a YAML Save. */
final class ConfigDirtyState {
    private boolean dirty;
    private final Runnable onChanged;

    ConfigDirtyState(Runnable onChanged) {
        this.onChanged = Objects.requireNonNull(onChanged, "onChanged");
    }

    boolean isDirty() {
        return dirty;
    }

    void mark() {
        if (!dirty) {
            dirty = true;
            onChanged.run();
        }
    }

    void clear() {
        if (dirty) {
            dirty = false;
            onChanged.run();
        }
    }
}
