package io.pingui.config;

import java.util.Locale;

/**
 * Profile-level {@code endpoint_down} rule parameters (P21-002/003 / ADR_ALERT_RULES).
 */
public record EndpointDownRuleConfig(boolean enabled, int failAfter, int clearAfter, int cooldownMinutes) {
    public EndpointDownRuleConfig {
        if (failAfter < 1) {
            throw new IllegalArgumentException("failAfter must be >= 1");
        }
        if (clearAfter < 1) {
            throw new IllegalArgumentException("clearAfter must be >= 1");
        }
        if (cooldownMinutes < 0) {
            throw new IllegalArgumentException("cooldownMinutes must be >= 0");
        }
    }

    /** Disabled balanced defaults (ADR). */
    public static EndpointDownRuleConfig disabled() {
        return balanced(false);
    }

    public static EndpointDownRuleConfig balanced(boolean enabled) {
        return new EndpointDownRuleConfig(enabled, 3, 2, 15);
    }

    public static EndpointDownRuleConfig calm(boolean enabled) {
        return new EndpointDownRuleConfig(enabled, 5, 3, 30);
    }

    public static EndpointDownRuleConfig sensitive(boolean enabled) {
        return new EndpointDownRuleConfig(enabled, 2, 1, 5);
    }

    /** True when values match disabled balanced defaults (omit from YAML). */
    public boolean isDefaultDisabled() {
        return equals(disabled());
    }

    /**
     * Maps preset aliases: {@code calm}/{@code спокійно}, {@code balanced}/{@code збалансовано},
     * {@code sensitive}/{@code чутливо}.
     */
    public static EndpointDownRuleConfig fromPreset(String preset, boolean enabled) {
        if (preset == null || preset.isBlank()) {
            return balanced(enabled);
        }
        String key = preset.strip().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "calm", "спокійно" -> calm(enabled);
            case "sensitive", "чутливо" -> sensitive(enabled);
            case "balanced", "збалансовано" -> balanced(enabled);
            default -> throw new IllegalArgumentException("Unknown endpoint_down preset: " + preset);
        };
    }

    /** Matching preset name for GUI, or empty if custom numbers. */
    public String matchingPreset() {
        if (equals(calm(enabled))) {
            return "calm";
        }
        if (equals(balanced(enabled))) {
            return "balanced";
        }
        if (equals(sensitive(enabled))) {
            return "sensitive";
        }
        return "";
    }
}
