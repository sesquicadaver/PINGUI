package io.pingui.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.telemetry.GelfSink;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TelemetryConfigRedactionTest {
    @Test
    void redactUrlStripsCredentialsAndQuery() {
        assertEquals(
                "https://hooks.example.com/path",
                TelemetryConfig.redactUrl("https://user:pass@hooks.example.com/path?token=abc"));
        assertEquals(
                "http://127.0.0.1:3100/loki/api/v1/push",
                TelemetryConfig.redactUrl("http://user:secret@127.0.0.1:3100/loki/api/v1/push?api_key=xyz"));
        assertEquals("", TelemetryConfig.redactUrl("  "));
        assertEquals("<invalid-url>", TelemetryConfig.redactUrl("://bad"));
    }

    @Test
    void redactSecretNeverEchoesFullValue() {
        assertEquals("", TelemetryConfig.redactSecret(null));
        assertEquals("****", TelemetryConfig.redactSecret("ab"));
        String masked = TelemetryConfig.redactSecret("super-secret-token");
        assertTrue(masked.startsWith("su"));
        assertTrue(masked.endsWith("****"));
        assertFalse(masked.contains("secret-token"));
    }

    @Test
    void toRedactedStringMasksLokiUrl() {
        TelemetryConfig cfg = new TelemetryConfig(
                true,
                false,
                Optional.of(Path.of("data/t.db")),
                Optional.empty(),
                Optional.of(new TelemetryConfig.SyslogSinkConfig("syslog.example", 514, true)),
                Optional.of(new TelemetryConfig.GelfSinkConfig("gelf.example", 12201, GelfSink.Transport.UDP)),
                Optional.of(new TelemetryConfig.LokiSinkConfig(
                        "https://token:secret@loki.example:3100/loki/api/v1/push?orgId=1", "noc")),
                Optional.of(new TelemetryConfig.OtlpSinkConfig(
                        "https://token:secret@otel.example:4318?api_key=1", "pingui")));
        String debug = cfg.toRedactedString();
        assertTrue(debug.contains("syslog=syslog.example:514(tls)"));
        assertTrue(debug.contains("gelf=gelf.example:12201/udp"));
        assertTrue(debug.contains("loki=https://loki.example:3100/loki/api/v1/push"));
        assertTrue(debug.contains("site=noc"));
        assertTrue(debug.contains("otlp=https://otel.example:4318"));
        assertFalse(debug.contains("token:secret"));
        assertFalse(debug.contains("orgId=1"));
        assertFalse(debug.contains("api_key"));
    }
}
