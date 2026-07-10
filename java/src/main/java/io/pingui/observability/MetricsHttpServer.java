package io.pingui.observability;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Localhost-only HTTP server exposing {@link PrometheusExporter#scrape()} at {@code GET /metrics}
 * (P15-010).
 */
public final class MetricsHttpServer implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(MetricsHttpServer.class);

    private final PrometheusExporter exporter;
    private final HttpServer server;
    private final ExecutorService executor;
    private final int port;

    private MetricsHttpServer(PrometheusExporter exporter, HttpServer server, ExecutorService executor, int port) {
        this.exporter = exporter;
        this.server = server;
        this.executor = executor;
        this.port = port;
    }

    /**
     * Binds {@code 127.0.0.1:port} and serves {@code /metrics}.
     *
     * @throws IllegalArgumentException if port is out of range
     * @throws IOException if bind fails
     */
    public static MetricsHttpServer start(PrometheusExporter exporter, int port) throws IOException {
        Objects.requireNonNull(exporter, "exporter");
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("metrics port must be 1..65535, got " + port);
        }
        InetSocketAddress address = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port);
        HttpServer httpServer = HttpServer.create(address, 0);
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "pingui-metrics-http");
            thread.setDaemon(true);
            return thread;
        });
        MetricsHttpServer metricsServer = new MetricsHttpServer(exporter, httpServer, executor, port);
        httpServer.createContext("/metrics", metricsServer::handleMetrics);
        httpServer.setExecutor(executor);
        httpServer.start();
        LOG.info("Prometheus metrics listening on http://127.0.0.1:{}/metrics", port);
        return metricsServer;
    }

    public int port() {
        return port;
    }

    public PrometheusExporter exporter() {
        return exporter;
    }

    private void handleMetrics(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }
        byte[] body = exporter.scrape().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        LOG.info("Prometheus metrics server stopped (port={})", port);
    }
}
