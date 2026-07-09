package io.pingui.monitor;

import java.net.URI;
import java.net.URISyntaxException;

/** Log-safe webhook URL helpers (P10-031 / ADR_ALERTS). */
final class AlertWebhookSupport {
    private AlertWebhookSupport() {}

    static String redactWebhookUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            URI uri = new URI(url.strip());
            String host = uri.getHost() != null ? uri.getHost() : "";
            int port = uri.getPort();
            if (port > 0) {
                host = host + ":" + port;
            }
            String path = uri.getRawPath() != null ? uri.getRawPath() : "";
            return uri.getScheme() + "://" + host + path;
        } catch (URISyntaxException ex) {
            return "<invalid-url>";
        }
    }
}
