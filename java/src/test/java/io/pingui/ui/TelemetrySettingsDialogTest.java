package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.CliTelemetryOverrides;
import io.pingui.config.TelemetryConfig;
import io.pingui.telemetry.GelfSink;
import io.pingui.ui.TelemetrySettingsDialog.FormInput;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TelemetrySettingsDialogTest {

    @Test
    void buildConfigSetsAllSinksAndPolicyFlags() {
        TelemetryConfig baseline = TelemetryConfig.defaults();
        FormInput form = new FormInput(
                false,
                true,
                "data/telemetry.db",
                "data/jsonl",
                "127.0.0.1:1514",
                true,
                "127.0.0.1:12201",
                "udp",
                "http://loki:3100",
                "lab",
                "http://otel:4318",
                "pingui-gui");

        TelemetryConfig next = TelemetrySettingsDialog.buildConfig(baseline, form, CliTelemetryOverrides.none());

        assertFalse(next.eventsOnly());
        assertTrue(next.logAggregates());
        assertEquals(Path.of("data/telemetry.db"), next.sqlitePath().orElseThrow());
        assertEquals(Path.of("data/jsonl"), next.jsonlDir().orElseThrow());
        assertEquals("127.0.0.1", next.syslog().orElseThrow().host());
        assertTrue(next.syslog().orElseThrow().tls());
        assertEquals(GelfSink.Transport.UDP, next.gelf().orElseThrow().transport());
        assertEquals("http://loki:3100", next.loki().orElseThrow().url());
        assertEquals("lab", next.loki().orElseThrow().site());
        assertEquals("http://otel:4318", next.otlp().orElseThrow().endpoint());
        assertEquals("pingui-gui", next.otlp().orElseThrow().serviceName());
        assertTrue(next.toRedactedString().contains("otlp="));
        assertFalse(next.toRedactedString().contains("token="));
    }

    @Test
    void buildConfigClearsSinksWhenBlankAndRespectsCliLocks() {
        TelemetryConfig baseline = new TelemetryConfig(
                true,
                false,
                Optional.of(Path.of("a.db")),
                Optional.of(Path.of("jsonl")),
                Optional.of(new TelemetryConfig.SyslogSinkConfig("yaml.host", 514, false)),
                Optional.of(new TelemetryConfig.GelfSinkConfig("gelf", 12201, GelfSink.Transport.TCP)),
                Optional.of(new TelemetryConfig.LokiSinkConfig("http://loki", "lab")),
                Optional.of(new TelemetryConfig.OtlpSinkConfig("http://otel:4318", "cli")));
        CliTelemetryOverrides locks = new CliTelemetryOverrides(
                Optional.of(new TelemetryConfig.SyslogSinkConfig("cli.host", 1514, false)),
                Optional.of(Path.of("cli-jsonl")),
                Optional.of(new TelemetryConfig.OtlpSinkConfig("http://cli-otel:4318", "cli")));

        FormInput form =
                new FormInput(true, false, "", "ignored", "ignored:9", true, "", "tcp", "", "", "ignored", "x");
        TelemetryConfig next = TelemetrySettingsDialog.buildConfig(baseline, form, locks);

        assertTrue(next.sqlitePath().isEmpty());
        assertEquals(Path.of("cli-jsonl"), next.jsonlDir().orElseThrow());
        assertEquals("cli.host", next.syslog().orElseThrow().host());
        assertEquals(1514, next.syslog().orElseThrow().port());
        assertTrue(next.gelf().isEmpty());
        assertTrue(next.loki().isEmpty());
        assertEquals(locks.otlp(), next.otlp());
    }

    @Test
    void parseSyslogRejectsInvalidHostPort() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> TelemetrySettingsDialog.parseSyslog("no-port", false));
        assertTrue(ex.getMessage().contains("Syslog"));
    }

    @Test
    void parseLokiRequiresBothOrNeither() {
        assertThrows(IllegalArgumentException.class, () -> TelemetrySettingsDialog.parseLoki("http://loki", ""));
        assertTrue(TelemetrySettingsDialog.parseLoki("", "").isEmpty());
    }

    @Test
    void formatSyslogWrapsIpv6() {
        Optional<TelemetryConfig.SyslogSinkConfig> cfg =
                Optional.of(new TelemetryConfig.SyslogSinkConfig("::1", 1514, false));
        assertEquals("[::1]:1514", TelemetrySettingsDialog.formatSyslog(cfg));
    }
}
