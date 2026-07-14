package io.pingui.monitor;

import io.pingui.config.TelemetryConfig;

/** Log-safe webhook URL helpers (P10-031 / P16-050 / ADR_ALERTS). */
final class AlertWebhookSupport {
    private AlertWebhookSupport() {}

    /** Delegates to {@link TelemetryConfig#redactUrl(String)} (single redact path with P16-042). */
    static String redactWebhookUrl(String url) {
        return TelemetryConfig.redactUrl(url);
    }
}
