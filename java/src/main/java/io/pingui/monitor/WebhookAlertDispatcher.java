package io.pingui.monitor;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** POST JSON {@link RouteChangeEvent} to a webhook URL (P10-030 / ADR_ALERTS). */
public final class WebhookAlertDispatcher implements AlertDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(WebhookAlertDispatcher.class);

    private final String url;
    private final HttpClient client;
    private final Duration timeout;

    public WebhookAlertDispatcher(String url) {
        this(url, HttpClient.newHttpClient(), Duration.ofSeconds(5));
    }

    WebhookAlertDispatcher(String url, HttpClient client, Duration timeout) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("webhook URL is required");
        }
        this.url = url.strip();
        this.client = client;
        this.timeout = timeout;
    }

    @Override
    public void dispatch(RouteChangeEvent event) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(event.toJson()))
                .build();
        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 400) {
                LOG.warn(
                        "Webhook alert HTTP {} for {} ({})",
                        response.statusCode(),
                        event.host(),
                        AlertWebhookSupport.redactWebhookUrl(url));
            }
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.warn(
                    "Webhook alert failed for {} ({}): {}",
                    event.host(),
                    AlertWebhookSupport.redactWebhookUrl(url),
                    ex.getMessage());
        }
    }
}
