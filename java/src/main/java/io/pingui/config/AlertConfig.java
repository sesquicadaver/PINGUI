package io.pingui.config;

/** Per-profile alert channels + quality rules (P10-021 / P21-003 / ADR_ALERTS + ADR_ALERT_RULES). */
public record AlertConfig(
        boolean desktopAlerts,
        String webhookUrl,
        int maxAlertsPerHour,
        boolean notifyResolved,
        EndpointDownRuleConfig endpointDown) {
    public AlertConfig {
        if (maxAlertsPerHour < 1) {
            throw new IllegalArgumentException("maxAlertsPerHour must be >= 1");
        }
        endpointDown = endpointDown != null ? endpointDown : EndpointDownRuleConfig.disabled();
    }

    /** Channel-only constructor (rules default off). */
    public AlertConfig(boolean desktopAlerts, String webhookUrl, int maxAlertsPerHour) {
        this(desktopAlerts, webhookUrl, maxAlertsPerHour, false, EndpointDownRuleConfig.disabled());
    }

    public static AlertConfig disabled() {
        return new AlertConfig(false, null, 10, false, EndpointDownRuleConfig.disabled());
    }

    public boolean isEnabled() {
        return desktopAlerts || normalizedWebhook() != null;
    }

    /** True when YAML should emit an {@code alerts:} block beyond empty defaults. */
    public boolean hasYamlContent() {
        return isEnabled() || maxAlertsPerHour != 10 || notifyResolved || !endpointDown.isDefaultDisabled();
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
                + ", notify_resolved="
                + notifyResolved
                + ", endpoint_down="
                + (endpointDown.enabled() ? "on" : "off")
                + "}";
    }
}
