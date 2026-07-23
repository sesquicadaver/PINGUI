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

    /** Builds the pipeline; desktop channel uses {@link DesktopAlertSink#noop()} (no popup). */
    public static AlertDispatcher build(AlertConfig config) {
        return build(config, DesktopAlertSink.noop());
    }

    /**
     * Builds the pipeline with an explicit desktop popup sink (GUI supplies JavaFX; tests may record).
     */
    public static AlertDispatcher build(AlertConfig config, DesktopAlertSink desktopSink) {
        if (config == null || !config.isEnabled()) {
            return AlertDispatcher.noop();
        }
        DesktopAlertSink sink = desktopSink != null ? desktopSink : DesktopAlertSink.noop();
        List<AlertDispatcher> channels = new ArrayList<>();
        String webhook = config.normalizedWebhook();
        if (webhook != null) {
            channels.add(new WebhookAlertDispatcher(webhook));
            LOG.info("Webhook alerts enabled ({})", AlertWebhookSupport.redactWebhookUrl(webhook));
        }
        if (config.desktopAlerts()) {
            channels.add(new DesktopAlertDispatcher(sink));
            LOG.info("Desktop alerts enabled (in-app popup)");
        }
        if (channels.isEmpty()) {
            return AlertDispatcher.noop();
        }
        AlertDispatcher inner = channels.size() == 1 ? channels.get(0) : new CompositeAlertDispatcher(channels);
        return new RateLimitedAlertDispatcher(inner, new AlertRateLimiter(config.maxAlertsPerHour()));
    }
}
