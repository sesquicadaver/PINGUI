package io.pingui.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.pingui.model.Models.HopNode;
import io.pingui.monitor.SessionStore;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Localhost-only read-only HTTP API for runbook access (P15-040).
 *
 * <p>Endpoints: {@code GET /hosts}, {@code GET /routes/{host}}, {@code GET /openapi.json}. Auth is out
 * of scope for v1.
 */
public final class ReadOnlyApiServer implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(ReadOnlyApiServer.class);

    private final SessionStore store;
    private final HttpServer server;
    private final ExecutorService executor;
    private final int port;

    private ReadOnlyApiServer(SessionStore store, HttpServer server, ExecutorService executor, int port) {
        this.store = store;
        this.server = server;
        this.executor = executor;
        this.port = port;
    }

    /**
     * Binds {@code 127.0.0.1:port} and serves the read-only API.
     *
     * @throws IllegalArgumentException if port is out of range
     * @throws IOException if bind fails
     */
    public static ReadOnlyApiServer start(SessionStore store, int port) throws IOException {
        Objects.requireNonNull(store, "store");
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("api port must be 1..65535, got " + port);
        }
        InetSocketAddress address = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port);
        HttpServer httpServer = HttpServer.create(address, 0);
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "pingui-api-http");
            thread.setDaemon(true);
            return thread;
        });
        ReadOnlyApiServer api = new ReadOnlyApiServer(store, httpServer, executor, port);
        httpServer.createContext("/hosts", api::handleHosts);
        httpServer.createContext("/routes", api::handleRoutes);
        httpServer.createContext("/openapi.json", api::handleOpenApi);
        httpServer.setExecutor(executor);
        httpServer.start();
        LOG.info("Read-only API listening on http://127.0.0.1:{}/ (hosts, routes, openapi.json)", port);
        return api;
    }

    public int port() {
        return port;
    }

    private void handleHosts(HttpExchange exchange) throws IOException {
        if (!isExactPath(exchange, "/hosts")) {
            sendJson(exchange, 404, "{\"error\":\"not_found\"}");
            return;
        }
        if (!requireGet(exchange)) {
            return;
        }
        sendJson(exchange, 200, ReadOnlyApiJson.hostsDocument(store));
    }

    private void handleRoutes(HttpExchange exchange) throws IOException {
        if (!requireGet(exchange)) {
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if (path == null || !path.startsWith("/routes/")) {
            sendJson(exchange, 404, "{\"error\":\"not_found\"}");
            return;
        }
        String encoded = path.substring("/routes/".length());
        if (encoded.isEmpty() || encoded.contains("/")) {
            sendJson(exchange, 404, "{\"error\":\"not_found\"}");
            return;
        }
        String host = URLDecoder.decode(encoded, StandardCharsets.UTF_8);
        if (!store.containsHost(host)) {
            sendJson(exchange, 404, "{\"error\":\"unknown_host\",\"host\":" + JsonStrings.quote(host) + "}");
            return;
        }
        List<HopNode> hops = store.get(host).getCurrentRoute();
        sendJson(exchange, 200, ReadOnlyApiJson.routeDocument(host, hops));
    }

    private void handleOpenApi(HttpExchange exchange) throws IOException {
        if (!isExactPath(exchange, "/openapi.json")) {
            sendJson(exchange, 404, "{\"error\":\"not_found\"}");
            return;
        }
        if (!requireGet(exchange)) {
            return;
        }
        sendJson(exchange, 200, ReadOnlyApiJson.openApiDocument());
    }

    private static boolean isExactPath(HttpExchange exchange, String expected) {
        String path = exchange.getRequestURI().getPath();
        return expected.equals(path);
    }

    private static boolean requireGet(HttpExchange exchange) throws IOException {
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            return true;
        }
        exchange.getResponseHeaders().add("Allow", "GET");
        exchange.sendResponseHeaders(405, -1);
        exchange.close();
        return false;
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
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
        LOG.info("Read-only API server stopped (port={})", port);
    }
}
