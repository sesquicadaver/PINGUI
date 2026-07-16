package io.pingui.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.AppInfo;
import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.HostSessionData;
import io.pingui.monitor.HostProbeMode;
import io.pingui.monitor.SessionStore;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for the read-only runbook API (P15-050).
 *
 * <p>Uses a real localhost {@link ReadOnlyApiServer} as the HTTP mock of production binding.
 */
class ReadOnlyApiContractTest {

    @Test
    void hostsDocumentMatchesV1Contract() throws Exception {
        SessionStore store = new SessionStore(List.of());
        store.addHost("8.8.8.8", true);
        store.addHost("1.1.1.1", false);
        store.setProbeMode("1.1.1.1", HostProbeMode.PING_ONLY);

        try (ReadOnlyApiServer server = start(store)) {
            HttpResponse<String> response = get(server.port(), "/hosts");
            assertEquals(200, response.statusCode());
            assertEquals(
                    "application/json; charset=utf-8",
                    response.headers().firstValue("Content-Type").orElseThrow());
            assertEquals(
                    "{\"hosts\":["
                            + "{\"address\":\"8.8.8.8\",\"enabled\":true,\"probe_mode\":\"trace\"},"
                            + "{\"address\":\"1.1.1.1\",\"enabled\":false,\"probe_mode\":\"ping_only\"}"
                            + "]}",
                    response.body());
        }
    }

    @Test
    void routeDocumentMatchesV1ContractIncludingNullPing() throws Exception {
        SessionStore store = new SessionStore(List.of());
        store.addHost("target.example", true);
        HostSessionData data = store.get("target.example");
        data.setCurrentRoute(List.of(new HopNode(1, "10.0.0.1", 4.5, false), new HopNode(2, "*", null, true)));

        try (ReadOnlyApiServer server = start(store)) {
            HttpResponse<String> response = get(server.port(), "/routes/target.example");
            assertEquals(200, response.statusCode());
            assertEquals(
                    "{\"host\":\"target.example\",\"hops\":["
                            + "{\"hop\":1,\"ip\":\"10.0.0.1\",\"ping_ms\":4.5,\"timeout\":false},"
                            + "{\"hop\":2,\"ip\":\"*\",\"ping_ms\":null,\"timeout\":true}"
                            + "]}",
                    response.body());
        }
    }

    @Test
    void unknownHostReturnsContract404() throws Exception {
        SessionStore store = new SessionStore(List.of());
        try (ReadOnlyApiServer server = start(store)) {
            HttpResponse<String> response = get(server.port(), "/routes/missing.example");
            assertEquals(404, response.statusCode());
            assertEquals("{\"error\":\"unknown_host\",\"host\":\"missing.example\"}", response.body());
        }
    }

    @Test
    void openApiStubDocumentsRequiredPaths() throws Exception {
        SessionStore store = new SessionStore(List.of());
        try (ReadOnlyApiServer server = start(store)) {
            HttpResponse<String> response = get(server.port(), "/openapi.json");
            assertEquals(200, response.statusCode());
            String body = response.body();
            assertTrue(body.contains("\"openapi\": \"3.0.3\"") || body.contains("\"openapi\":\"3.0.3\""));
            assertTrue(body.contains("\"/hosts\""));
            assertTrue(body.contains("\"/routes/{host}\""));
            assertTrue(body.contains("\"404\""));
            String apiVersion = AppInfo.version().replace("-SNAPSHOT", "");
            assertTrue(body.contains("\"version\": \"" + apiVersion + "\"")
                    || body.contains("\"version\":\"" + apiVersion + "\""));
        }
    }

    @Test
    void nonGetMethodsAreRejectedWith405() throws Exception {
        SessionStore store = new SessionStore(List.of());
        try (ReadOnlyApiServer server = start(store)) {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .build();
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/hosts"))
                            .method("PUT", HttpRequest.BodyPublishers.noBody())
                            .timeout(Duration.ofSeconds(2))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(405, response.statusCode());
            assertTrue(response.headers().allValues("Allow").stream().anyMatch(v -> v.contains("GET")));
        }
    }

    private static ReadOnlyApiServer start(SessionStore store) throws Exception {
        int port;
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }
        return ReadOnlyApiServer.start(store, port);
    }

    private static HttpResponse<String> get(int port, String path) throws Exception {
        HttpClient client =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        return client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .GET()
                        .timeout(Duration.ofSeconds(2))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
