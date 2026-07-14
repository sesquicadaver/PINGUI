package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.CliTelemetryOverrides;
import io.pingui.config.TelemetryConfig;
import io.pingui.telemetry.GelfSink;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TelemetrySettingsDialogTest {

    @Test
    void buildConfigSetsLocalAndSyslogAndPreservesRemoteSinks() {
        TelemetryConfig baseline = new TelemetryConfig(
                true,
                true,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(new TelemetryConfig.GelfSinkConfig("gelf.example", 12201, GelfSink.Transport.TCP)),
                Optional.of(new TelemetryConfig.LokiSinkConfig("http://loki:3100", "lab")),
                Optional.of(new TelemetryConfig.OtlpSinkConfig("http://otel:4318", "pingui")));

        TelemetryConfig next = TelemetrySettingsDialog.buildConfig(
                baseline,
                false,
                "data/telemetry.db",
                "data/jsonl",
                "127.0.0.1:1514",
                true,
                CliTelemetryOverrides.none());

        assertFalse(next.eventsOnly());
        assertTrue(next.logAggregates());
        assertEquals(Path.of("data/telemetry.db"), next.sqlitePath().orElseThrow());
        assertEquals(Path.of("data/jsonl"), next.jsonlDir().orElseThrow());
        assertEquals("127.0.0.1", next.syslog().orElseThrow().host());
        assertEquals(1514, next.syslog().orElseThrow().port());
        assertTrue(next.syslog().orElseThrow().tls());
        assertEquals(baseline.gelf(), next.gelf());
        assertEquals(baseline.loki(), next.loki());
        assertEquals(baseline.otlp(), next.otlp());
    }

    @Test
    void buildConfigClearsSyslogWhenBlankAndRespectsCliLocks() {
        TelemetryConfig baseline = new TelemetryConfig(
                true,
                false,
                Optional.of(Path.of("a.db")),
                Optional.of(Path.of("jsonl")),
                Optional.of(new TelemetryConfig.SyslogSinkConfig("yaml.host", 514, false)),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
        CliTelemetryOverrides locks = new CliTelemetryOverrides(
                Optional.of(new TelemetryConfig.SyslogSinkConfig("cli.host", 1514, false)),
                Optional.of(Path.of("cli-jsonl")),
                Optional.empty());

        TelemetryConfig next =
                TelemetrySettingsDialog.buildConfig(baseline, true, "", "ignored", "ignored:9", true, locks);

        assertTrue(next.sqlitePath().isEmpty());
        assertEquals(Path.of("jsonl"), next.jsonlDir().orElseThrow());
        assertEquals("yaml.host", next.syslog().orElseThrow().host());
        assertEquals(514, next.syslog().orElseThrow().port());
    }

    @Test
    void parseSyslogRejectsInvalidHostPort() {
        assertThrows(IllegalArgumentException.class, () -> TelemetrySettingsDialog.parseSyslog("no-port", false));
    }

    @Test
    void formatSyslogWrapsIpv6() {
        Optional<TelemetryConfig.SyslogSinkConfig> cfg =
                Optional.of(new TelemetryConfig.SyslogSinkConfig("::1", 1514, false));
        assertEquals("[::1]:1514", TelemetrySettingsDialog.formatSyslog(cfg));
    }
}
