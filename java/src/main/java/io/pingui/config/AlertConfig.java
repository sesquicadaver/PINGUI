package io.pingui.config;

/** Per-profile alert channel settings (P10-021 / ADR_ALERTS). */
public record AlertConfig(boolean desktopAlerts, String webhookUrl, int maxAlertsPerHour) {
    public AlertConfig {
        if (maxAlertsPerHour < 1) {
            throw new IllegalArgumentException("maxAlertsPerHour must be >= 1");
        }
    }

    public static AlertConfig disabled() {
        return new AlertConfig(false, null, 10);
    }

    public boolean isEnabled() {
        return desktopAlerts || normalizedWebhook() != null;
    }

    public String normalizedWebhook() {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return null;
        }
        return webhookUrl.strip();
    }

    /** Debug-safe summary: webhook without userinfo/query (P20-011 / P16-042 redact path). */
    public String toRedactedString() {
        String webhook = normalizedWebhook();
        String redacted = webhook == null ? "(off)" : TelemetryConfig.redactUrl(webhook);
        return "AlertConfig{desktop="
                + desktopAlerts
                + ", webhook="
                + redacted
                + ", rate_limit="
                + maxAlertsPerHour
                + "}";
    }
}
