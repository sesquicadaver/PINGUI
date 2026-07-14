package io.pingui.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.HostSessionData;
import io.pingui.monitor.SessionStore;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReadOnlyApiServerTest {

    @Test
    void servesHostsRoutesAndOpenApi() throws Exception {
        SessionStore store = new SessionStore(List.of());
        store.addHost("8.8.8.8", true);
        HostSessionData data = store.get("8.8.8.8");
        data.setCurrentRoute(List.of(new HopNode(1, "10.0.0.1", 5.0, false), new HopNode(2, "8.8.8.8", 12.0, false)));

        int port;
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }

        HttpClient client =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        try (ReadOnlyApiServer server = ReadOnlyApiServer.start(store, port)) {
            assertEquals(port, server.port());

            HttpResponse<String> hosts = get(client, port, "/hosts");
            assertEquals(200, hosts.statusCode());
            assertTrue(hosts.body().contains("\"address\":\"8.8.8.8\""));
            assertTrue(hosts.body().contains("\"enabled\":true"));
            assertTrue(hosts.headers().firstValue("Content-Type").orElse("").contains("application/json"));

            HttpResponse<String> route = get(client, port, "/routes/8.8.8.8");
            assertEquals(200, route.statusCode());
            assertTrue(route.body().contains("\"ip\":\"10.0.0.1\""));
            assertTrue(route.body().contains("\"ping_ms\":12.0"));

            HttpResponse<String> missing = get(client, port, "/routes/missing.example");
            assertEquals(404, missing.statusCode());
            assertTrue(missing.body().contains("unknown_host"));

            HttpResponse<String> openapi = get(client, port, "/openapi.json");
            assertEquals(200, openapi.statusCode());
            assertTrue(openapi.body().contains("\"openapi\": \"3.0.3\"")
                    || openapi.body().contains("\"openapi\":\"3.0.3\""));
            assertTrue(openapi.body().contains("/hosts"));

            HttpResponse<String> post = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/hosts"))
                            .POST(HttpRequest.BodyPublishers.noBody())
                            .timeout(Duration.ofSeconds(2))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(405, post.statusCode());
        }
    }

    @Test
    void decodesUrlEncodedHostPath() throws Exception {
        SessionStore store = new SessionStore(List.of());
        store.addHost("foo.example.com", true);

        int port;
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }

        HttpClient client =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        try (ReadOnlyApiServer server = ReadOnlyApiServer.start(store, port)) {
            HttpResponse<String> route = get(client, port, "/routes/foo%2Eexample%2Ecom");
            assertEquals(200, route.statusCode());
            assertTrue(route.body().contains("\"host\":\"foo.example.com\""));
        }
    }

    private static HttpResponse<String> get(HttpClient client, int port, String path) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .GET()
                        .timeout(Duration.ofSeconds(2))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
