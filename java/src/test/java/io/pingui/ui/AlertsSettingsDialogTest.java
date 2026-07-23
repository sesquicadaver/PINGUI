package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.CliAlertOverrides;
import io.pingui.config.AlertConfig;
import io.pingui.config.EndpointDownRuleConfig;
import io.pingui.ui.AlertsSettingsDialog.FormInput;
import java.util.Optional;
import java.util.OptionalInt;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.Test;

class AlertsSettingsDialogTest {
    private static FormInput form(
            boolean desktop,
            String webhook,
            String rate,
            boolean notifyResolved,
            boolean endpointDown,
            String fail,
            String clear,
            String cooldown) {
        return new FormInput(
                desktop,
                webhook,
                rate,
                notifyResolved,
                endpointDown,
                fail,
                clear,
                cooldown,
                false,
                "2.0",
                "3",
                "2",
                "15");
    }

    @Test
    void formLabelAndColumnsKeepLabelColumnFromShrinking() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            Label label = AlertsSettingsDialog.formLabel("rate_limit / год:");
            assertEquals(Region.USE_PREF_SIZE, label.getMinWidth(), 0.0);
            GridPane grid = new GridPane();
            AlertsSettingsDialog.applyLabelFieldColumns(grid);
            assertEquals(2, grid.getColumnConstraints().size());
            assertEquals(Priority.NEVER, grid.getColumnConstraints().get(0).getHgrow());
            assertEquals(Priority.ALWAYS, grid.getColumnConstraints().get(1).getHgrow());
            assertEquals(
                    Region.USE_PREF_SIZE, grid.getColumnConstraints().get(0).getMinWidth(), 0.0);
        });
    }

    @Test
    void buildConfigSetsDesktopWebhookRateAndEndpointDown() {
        AlertConfig next = AlertsSettingsDialog.buildConfig(
                AlertConfig.disabled(),
                form(true, "https://user:secret@hooks.example/path?token=x", "5", true, true, "3", "2", "15"),
                CliAlertOverrides.none());
        assertTrue(next.desktopAlerts());
        assertEquals("https://user:secret@hooks.example/path?token=x", next.webhookUrl());
        assertEquals(5, next.maxAlertsPerHour());
        assertTrue(next.notifyResolved());
        assertTrue(next.endpointDown().enabled());
        assertEquals(3, next.endpointDown().failAfter());
        assertTrue(next.isEnabled());
        String redacted = next.toRedactedString();
        assertTrue(redacted.contains("desktop=true"));
        assertTrue(redacted.contains("hooks.example"));
        assertFalse(redacted.contains("secret"));
        assertTrue(redacted.contains("endpoint_down=on"));
        assertTrue(redacted.contains("notify_resolved=true"));
    }

    @Test
    void buildConfigEnablesLatencyHighCriticalDefaults() {
        AlertConfig next = AlertsSettingsDialog.buildConfig(
                AlertConfig.disabled(),
                new FormInput(false, "", "10", false, false, "3", "2", "15", true, "2.0", "3", "2", "15"),
                CliAlertOverrides.none());
        assertTrue(next.latencyHigh().enabled());
        assertEquals(2.0, next.latencyHigh().multiplier(), 0.0);
        assertEquals(3, next.latencyHigh().failAfter());
        assertTrue(next.toRedactedString().contains("latency_high=on"));
    }

    @Test
    void buildConfigClearsWebhookWhenBlankAndRespectsCliLocks() {
        AlertConfig baseline =
                new AlertConfig(false, "https://yaml.example/hook", 10, false, EndpointDownRuleConfig.balanced(true));
        CliAlertOverrides locks =
                new CliAlertOverrides(Optional.of("https://cli.example/hook"), Optional.of(true), OptionalInt.of(3));
        AlertConfig next =
                AlertsSettingsDialog.buildConfig(baseline, form(false, "", "99", true, false, "5", "3", "30"), locks);
        assertTrue(next.desktopAlerts());
        assertEquals("https://cli.example/hook", next.webhookUrl());
        assertEquals(3, next.maxAlertsPerHour());
        assertTrue(next.notifyResolved());
        assertFalse(next.endpointDown().enabled());
        assertEquals(5, next.endpointDown().failAfter());
    }

    @Test
    void buildConfigBlankWebhookDisablesChannel() {
        AlertConfig next = AlertsSettingsDialog.buildConfig(
                new AlertConfig(true, "https://old.example", 10),
                form(false, "  ", "10", false, false, "3", "2", "15"),
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
                        AlertConfig.disabled(),
                        form(false, "", "0", false, false, "3", "2", "15"),
                        CliAlertOverrides.none()));
        assertTrue(ex.getMessage().contains("rate_limit"));
    }

    @Test
    void buildConfigRejectsInvalidRuleNumbers() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AlertsSettingsDialog.buildConfig(
                        AlertConfig.disabled(),
                        form(false, "", "10", false, true, "x", "2", "15"),
                        CliAlertOverrides.none()));
        assertThrows(
                IllegalArgumentException.class,
                () -> AlertsSettingsDialog.buildConfig(
                        AlertConfig.disabled(),
                        form(false, "", "10", false, true, "3", "2", "-1"),
                        CliAlertOverrides.none()));
    }

    @Test
    void presetKeyMapsUkrainianLabels() {
        assertEquals("calm", AlertsSettingsDialog.presetKey("Спокійно"));
        assertEquals("sensitive", AlertsSettingsDialog.presetKey("Чутливо"));
        assertEquals("balanced", AlertsSettingsDialog.presetKey("Збалансовано"));
        assertEquals("balanced", AlertsSettingsDialog.presetKey("Власні значення"));
    }
}
