package io.pingui.config;

/** Raised when host configuration is invalid. */
public class ConfigError extends RuntimeException {
    public ConfigError(String message) {
        super(message);
    }
}
