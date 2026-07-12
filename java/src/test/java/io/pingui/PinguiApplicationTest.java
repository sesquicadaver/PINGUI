package io.pingui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
                30.0,
                15,
                1.0,
                ProbeMode.PROCESS,
                java.util.List.of(),
                io.pingui.config.AlertConfig.disabled(),
                io.pingui.config.PersistenceConfig.defaults());
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
    void parseOptions_exportReportPath() {
        AppOptions options = PinguiApplication.parseOptions(Map.of(
                "session-db", "data/ping.db",
                "export-report", "reports/out.csv"));
        assertEquals(Path.of("reports/out.csv"), options.exportReportPath().orElseThrow());
        assertEquals(Path.of("data/ping.db"), options.sessionDbPath().orElseThrow());
        assertEquals(CliRunMode.EXPORT, options.runMode());
    }

    @Test
    void parseOptions_daemonAndPidFile() {
        AppOptions options = PinguiApplication.parseOptions(Map.of(
                "daemon", "true",
                "pid-file", "/run/pingui/pingui-java.pid"));
        assertEquals(CliRunMode.DAEMON, options.runMode());
        assertEquals(Path.of("/run/pingui/pingui-java.pid"), options.pidFilePath());
        assertTrue(options.metricsPort().isEmpty());
    }

    @Test
    void parseOptions_metricsPort() {
        AppOptions options = PinguiApplication.parseOptions(Map.of("metrics-port", "9090"));
        assertEquals(9090, options.metricsPort().orElseThrow());
    }

    @Test
    void parseOptions_metricsPortRejectsOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> PinguiApplication.parseOptions(Map.of("metrics-port", "0")));
        assertThrows(
                IllegalArgumentException.class, () -> PinguiApplication.parseOptions(Map.of("metrics-port", "70000")));
        assertThrows(IllegalArgumentException.class, () -> PinguiApplication.parseOptions(Map.of("metrics-port", "")));
    }

    @Test
    void parseOptions_stopAndStatus() {
        assertEquals(
                CliRunMode.STOP,
                PinguiApplication.parseOptions(Map.of("stop", "true")).runMode());
        assertEquals(
                CliRunMode.STATUS,
                PinguiApplication.parseOptions(Map.of("status", "true")).runMode());
    }

    @Test
    void parseOptions_noPersistRouteChange() {
        AppOptions options = PinguiApplication.parseOptions(Map.of("no-persist-route-change", "true"));
        assertEquals(false, options.persistenceOverrides().routeChange().orElseThrow());
        assertTrue(options.persistenceOverrides().probeError().isEmpty());
    }

    @Test
    void parseOptions_asnFlags() {
        AppOptions disabled = PinguiApplication.parseOptions(Map.of("no-asn", "true"));
        assertFalse(disabled.asnEnabled());

        AppOptions custom = PinguiApplication.parseOptions(Map.of(
                "asn-hints", "config/custom-asn.yaml",
                "asn-timeout-ms", "1500"));
        assertTrue(custom.asnEnabled());
        assertEquals(Path.of("config/custom-asn.yaml"), custom.asnHintsPath());
        assertEquals(1500, custom.asnTimeoutMs());
    }

    @Test
    void parseOptions_asnTimeoutMustBePositive() {
        assertThrows(
                IllegalArgumentException.class, () -> PinguiApplication.parseOptions(Map.of("asn-timeout-ms", "0")));
    }

    @Test
    void persistenceOverrides_mergePreservesYamlFields() {
        io.pingui.config.PersistenceConfig yaml = io.pingui.config.PersistenceConfig.eventsOnly(
                new io.pingui.config.PersistenceEventsConfig(false, true));
        CliPersistenceOverrides partial =
                new CliPersistenceOverrides(java.util.Optional.of(true), java.util.Optional.empty());
        io.pingui.config.PersistenceConfig merged = partial.applyTo(yaml);
        assertTrue(merged.routeChange());
        assertTrue(merged.probeError());
    }
}
