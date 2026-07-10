package io.pingui.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MetricsHttpServerTest {
    @Test
    void getMetricsReturnsExpositionOnLocalhost() throws Exception {
        PrometheusExporter exporter = new PrometheusExporter();
        exporter.recordRtt("127.0.0.1", 1, 1.5);
        exporter.recordReachable("127.0.0.1", true);

        int port;
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }

        try (MetricsHttpServer server = MetricsHttpServer.start(exporter, port)) {
            assertEquals(port, server.port());
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .build();
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/metrics"))
                            .GET()
                            .timeout(Duration.ofSeconds(2))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("pingui_rtt_ms"));
            assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("text/plain"));
        }
    }
}
