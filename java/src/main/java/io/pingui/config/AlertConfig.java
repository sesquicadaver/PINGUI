package io.pingui.config;

/** Per-profile alert channels + quality rules + silence (P10-021 / P21-003 / P23 / P29-003). */
public record AlertConfig(
        boolean desktopAlerts,
        String webhookUrl,
        int maxAlertsPerHour,
        boolean notifyResolved,
        EndpointDownRuleConfig endpointDown,
        LatencyHighRuleConfig latencyHigh,
        AlertSilenceConfig silence) {
    public AlertConfig {
        if (maxAlertsPerHour < 1) {
            throw new IllegalArgumentException("maxAlertsPerHour must be >= 1");
        }
        endpointDown = endpointDown != null ? endpointDown : EndpointDownRuleConfig.disabled();
        latencyHigh = latencyHigh != null ? latencyHigh : LatencyHighRuleConfig.disabled();
        silence = silence != null ? silence : AlertSilenceConfig.none();
    }

    /** Channel-only constructor (rules/silence default off). */
    public AlertConfig(boolean desktopAlerts, String webhookUrl, int maxAlertsPerHour) {
        this(
                desktopAlerts,
                webhookUrl,
                maxAlertsPerHour,
                false,
                EndpointDownRuleConfig.disabled(),
                LatencyHighRuleConfig.disabled(),
                AlertSilenceConfig.none());
    }

    /** Channels + endpoint_down (latency/silence defaults off). */
    public AlertConfig(
            boolean desktopAlerts,
            String webhookUrl,
            int maxAlertsPerHour,
            boolean notifyResolved,
            EndpointDownRuleConfig endpointDown) {
        this(
                desktopAlerts,
                webhookUrl,
                maxAlertsPerHour,
                notifyResolved,
                endpointDown,
                LatencyHighRuleConfig.disabled(),
                AlertSilenceConfig.none());
    }

    /** Channels + rules without silence. */
    public AlertConfig(
            boolean desktopAlerts,
            String webhookUrl,
            int maxAlertsPerHour,
            boolean notifyResolved,
            EndpointDownRuleConfig endpointDown,
            LatencyHighRuleConfig latencyHigh) {
        this(
                desktopAlerts,
                webhookUrl,
                maxAlertsPerHour,
                notifyResolved,
                endpointDown,
                latencyHigh,
                AlertSilenceConfig.none());
    }

    public static AlertConfig disabled() {
        return new AlertConfig(
                false,
                null,
                10,
                false,
                EndpointDownRuleConfig.disabled(),
                LatencyHighRuleConfig.disabled(),
                AlertSilenceConfig.none());
    }

    public boolean isEnabled() {
        return desktopAlerts || normalizedWebhook() != null;
    }

    /** True when YAML should emit an {@code alerts:} block beyond empty defaults. */
    public boolean hasYamlContent() {
        return isEnabled()
                || maxAlertsPerHour != 10
                || notifyResolved
                || !endpointDown.isDefaultDisabled()
                || !latencyHigh.isDefaultDisabled()
                || !silence.isEmpty();
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
                + ", latency_high="
                + (latencyHigh.enabled() ? "on" : "off")
                + ", silence="
                + silence.entries().size()
                + "}";
    }
}
