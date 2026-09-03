package io.pingui.persistence;

import java.util.List;
import java.util.Objects;

/** Result of {@code PRAGMA integrity_check} on a session database (P30-006). */
public record IntegrityCheckResult(boolean ok, List<String> messages) {

    public IntegrityCheckResult {
        Objects.requireNonNull(messages, "messages");
        messages = List.copyOf(messages);
    }
}
