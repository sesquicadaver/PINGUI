package io.pingui.persistence;

/** Unchecked failure from SQLite session persistence (P11-010). */
public final class PersistenceException extends RuntimeException {
    public PersistenceException(String message) {
        super(message);
    }

    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
