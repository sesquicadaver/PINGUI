package io.pingui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.config.ProfilesConfig;
import io.pingui.config.TracingProfile;
import io.pingui.probe.ProbeMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PinguiApplicationTest {

    @Test
    void parseOptions_emptyParams_noProfileOverrides() {
        AppOptions options = PinguiApplication.parseOptions(Map.of());
        assertTrue(options.profileOverrides().isEmpty());
    }

    @Test
    void parseOptions_intervalOverrideOnly() {
        AppOptions options = PinguiApplication.parseOptions(Map.of("interval", "2.5"));
        assertEquals(2.5, options.profileOverrides().intervalSeconds().orElseThrow());
        assertTrue(options.profileOverrides().maxHops().isEmpty());
    }

    @Test
    void profileOverrides_mergePreservesYamlFields() {
        TracingProfile yaml = new TracingProfile(
                30.0, 15, 1.0, ProbeMode.PROCESS, java.util.List.of(), io.pingui.config.AlertConfig.disabled());
        TracingProfile merged = CliProfileOverrides.none().applyTo(yaml);
        assertEquals(30.0, merged.intervalSeconds());
        assertEquals(15, merged.maxHops());

        CliProfileOverrides partial = new CliProfileOverrides(
                java.util.OptionalDouble.of(2.0),
                java.util.OptionalInt.empty(),
                java.util.OptionalDouble.empty(),
                java.util.Optional.empty());
        TracingProfile after = partial.applyTo(yaml);
        assertEquals(2.0, after.intervalSeconds());
        assertEquals(15, after.maxHops());
        assertEquals(ProbeMode.PROCESS, after.probeMode());
    }

    /** ROADMAP M-014: start without {@code --interval} must keep YAML {@code interval: 30}. */
    @Test
    void m014_yamlInterval30_noCliOverride_preservesInterval(@TempDir Path tempDir) throws Exception {
        Path config = tempDir.resolve("hosts.yaml");
        Files.writeString(
                config,
                """
                active_profile: default
                profiles:
                  default:
                    interval: 30.0
                    max_hops: 20
                    timeout: 1.0
                    probe: process
                    hosts: []
                """);

        TracingProfile yaml = ProfilesConfig.load(config).active();
        assertEquals(30.0, yaml.intervalSeconds());

        AppOptions options = PinguiApplication.parseOptions(Map.of("config", config.toString()));
        assertTrue(options.profileOverrides().isEmpty());

        TracingProfile effective = options.profileOverrides().isEmpty()
                ? yaml
                : options.profileOverrides().applyTo(yaml);
        assertEquals(30.0, effective.intervalSeconds());
        assertEquals(20, effective.maxHops());
    }

    @Test
    void parseOptions_alertWebhookOverride() {
        AppOptions options = PinguiApplication.parseOptions(Map.of("alert-webhook", "https://hooks.example.com/x"));
        assertEquals(
                "https://hooks.example.com/x",
                options.alertOverrides().webhookUrl().orElseThrow());
    }

    @Test
    void parseOptions_desktopAlertsFlag() {
        AppOptions options = PinguiApplication.parseOptions(Map.of("desktop-alerts", "true"));
        assertTrue(options.alertOverrides().desktopAlerts().orElseThrow());
    }

    @Test
    void parseOptions_sessionDbPath() {
        AppOptions options = PinguiApplication.parseOptions(Map.of("session-db", "data/ping.db"));
        assertEquals(
                java.nio.file.Path.of("data/ping.db"), options.sessionDbPath().orElseThrow());
    }

    @Test
    void alertOverrides_mergePreservesYamlFields() {
        io.pingui.config.AlertConfig yaml = new io.pingui.config.AlertConfig(true, "https://yaml.example/hook", 10);
        CliAlertOverrides partial = new CliAlertOverrides(
                java.util.Optional.of("https://cli.example/hook"),
                java.util.Optional.empty(),
                java.util.OptionalInt.of(3));
        io.pingui.config.AlertConfig merged = partial.applyTo(yaml);
        assertEquals("https://cli.example/hook", merged.normalizedWebhook());
        assertTrue(merged.desktopAlerts());
        assertEquals(3, merged.maxAlertsPerHour());
    }
}
