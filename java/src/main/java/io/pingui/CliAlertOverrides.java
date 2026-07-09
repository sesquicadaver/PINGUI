package io.pingui;

import io.pingui.config.AlertConfig;
import java.util.Optional;
import java.util.OptionalInt;

/** CLI overrides for alert settings; empty fields keep YAML values (ADR_ALERTS §6). */
public record CliAlertOverrides(
        Optional<String> webhookUrl, Optional<Boolean> desktopAlerts, OptionalInt rateLimitPerHour) {

    public static CliAlertOverrides none() {
        return new CliAlertOverrides(Optional.empty(), Optional.empty(), OptionalInt.empty());
    }

    public boolean isEmpty() {
        return webhookUrl.isEmpty() && desktopAlerts.isEmpty() && rateLimitPerHour.isEmpty();
    }

    /** Returns alert config with only present CLI fields replaced. */
    public AlertConfig applyTo(AlertConfig yaml) {
        return new AlertConfig(
                desktopAlerts.orElse(yaml.desktopAlerts()),
                webhookUrl.orElse(yaml.webhookUrl()),
                rateLimitPerHour.orElse(yaml.maxAlertsPerHour()));
    }
}
