package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.CliAlertOverrides;
import io.pingui.config.AlertConfig;
import io.pingui.ui.AlertsSettingsDialog.FormInput;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class AlertsSettingsDialogTest {
    @Test
    void buildConfigSetsDesktopWebhookAndRate() {
        AlertConfig next = AlertsSettingsDialog.buildConfig(
                AlertConfig.disabled(),
                new FormInput(true, "https://user:secret@hooks.example/path?token=x", "5"),
                CliAlertOverrides.none());
        assertTrue(next.desktopAlerts());
        assertEquals("https://user:secret@hooks.example/path?token=x", next.webhookUrl());
        assertEquals(5, next.maxAlertsPerHour());
        assertTrue(next.isEnabled());
        String redacted = next.toRedactedString();
        assertTrue(redacted.contains("desktop=true"));
        assertTrue(redacted.contains("hooks.example"));
        assertFalse(redacted.contains("secret"));
        assertFalse(redacted.contains("token=x"));
        assertTrue(redacted.contains("rate_limit=5"));
    }

    @Test
    void buildConfigClearsWebhookWhenBlankAndRespectsCliLocks() {
        AlertConfig baseline = new AlertConfig(false, "https://yaml.example/hook", 10);
        CliAlertOverrides locks =
                new CliAlertOverrides(Optional.of("https://cli.example/hook"), Optional.of(true), OptionalInt.of(3));
        AlertConfig next = AlertsSettingsDialog.buildConfig(baseline, new FormInput(false, "", "99"), locks);
        assertTrue(next.desktopAlerts());
        assertEquals("https://cli.example/hook", next.webhookUrl());
        assertEquals(3, next.maxAlertsPerHour());
    }

    @Test
    void buildConfigBlankWebhookDisablesChannel() {
        AlertConfig next = AlertsSettingsDialog.buildConfig(
                new AlertConfig(true, "https://old.example", 10),
                new FormInput(false, "  ", "10"),
                CliAlertOverrides.none());
        assertFalse(next.desktopAlerts());
        assertNull(next.normalizedWebhook());
        assertFalse(next.isEnabled());
    }

    @Test
    void buildConfigRejectsInvalidRateLimit() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> AlertsSettingsDialog.buildConfig(
                        AlertConfig.disabled(), new FormInput(false, "", "0"), CliAlertOverrides.none()));
        assertTrue(ex.getMessage().contains("rate_limit"));
    }
}
