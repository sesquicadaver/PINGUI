package io.pingui.monitor;

import io.pingui.config.AlertConfig;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Factory for the rate-limited alert pipeline (parity with Python {@code build_alert_dispatcher}). */
public final class AlertDispatchers {
    private static final Logger LOG = LoggerFactory.getLogger(AlertDispatchers.class);

    private AlertDispatchers() {}

    public static AlertDispatcher build(AlertConfig config) {
        if (config == null || !config.isEnabled()) {
            return AlertDispatcher.noop();
        }
        List<AlertDispatcher> channels = new ArrayList<>();
        String webhook = config.normalizedWebhook();
        if (webhook != null) {
            channels.add(new WebhookAlertDispatcher(webhook));
            LOG.info("Webhook alerts enabled ({})", AlertWebhookSupport.redactWebhookUrl(webhook));
        }
        if (config.desktopAlerts()) {
            channels.add(new DesktopAlertDispatcher());
            LOG.info("Desktop alerts enabled");
        }
        if (channels.isEmpty()) {
            return AlertDispatcher.noop();
        }
        AlertDispatcher inner = channels.size() == 1 ? channels.get(0) : new CompositeAlertDispatcher(channels);
        return new RateLimitedAlertDispatcher(inner, new AlertRateLimiter(config.maxAlertsPerHour()));
    }
}
