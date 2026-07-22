package io.pingui.monitor;

/**
 * Profile-level {@code endpoint_down} rule parameters (P21-002 / ADR_ALERT_RULES). YAML binding —
 * P21-003.
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

    /**
     * Maps preset aliases: {@code calm}/{@code спокійно}, {@code balanced}/{@code збалансовано},
     * {@code sensitive}/{@code чутливо}.
     */
    public static EndpointDownRuleConfig fromPreset(String preset, boolean enabled) {
        if (preset == null || preset.isBlank()) {
            return balanced(enabled);
        }
        String key = preset.strip().toLowerCase(java.util.Locale.ROOT);
        return switch (key) {
            case "calm", "спокійно" -> calm(enabled);
            case "sensitive", "чутливо" -> sensitive(enabled);
            case "balanced", "збалансовано" -> balanced(enabled);
            default -> throw new IllegalArgumentException("Unknown endpoint_down preset: " + preset);
        };
    }
}
