package io.pingui.monitor;

import io.pingui.telemetry.WebhookTelemetrySink;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Alert channel that POSTs JSON {@link RouteChangeEvent} via {@link WebhookTelemetrySink} (P10-030 /
 * P16-050 / ADR_ALERTS).
 *
 * <p>HTTP emit lives in the telemetry sink — no second client next to the bus.
 */
public final class WebhookAlertDispatcher implements AlertDispatcher {
    private final WebhookTelemetrySink sink;

    public WebhookAlertDispatcher(String url) {
        this(new WebhookTelemetrySink(url));
    }

    WebhookAlertDispatcher(String url, HttpClient client, Duration timeout) {
        this(new WebhookTelemetrySink(url, client, timeout));
    }

    WebhookAlertDispatcher(WebhookTelemetrySink sink) {
        this.sink = sink;
    }

    /** Shared telemetry sink (same instance the alert channel uses for HTTP). */
    public WebhookTelemetrySink telemetrySink() {
        return sink;
    }

    @Override
    public void dispatch(RouteChangeEvent event) {
        if (event == null) {
            return;
        }
        sink.postJson(event.toJson(), event.host());
    }
}
