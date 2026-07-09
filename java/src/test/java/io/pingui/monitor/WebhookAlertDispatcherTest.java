package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class WebhookAlertDispatcherTest {
    @Test
    void postsRouteChangeJson() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes()));
            byte[] ok = "ok".getBytes();
            exchange.sendResponseHeaders(204, ok.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(ok);
            }
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            String url = "http://127.0.0.1:" + port + "/hook?secret=token";
            WebhookAlertDispatcher dispatcher =
                    new WebhookAlertDispatcher(url, HttpClient.newHttpClient(), java.time.Duration.ofSeconds(5));
            RouteChangeEvent event = RouteChangeEvent.fromRouteChange(
                    "8.8.8.8", List.of("10.0.0.1"), List.of("8.8.8.8"), "lab", Instant.parse("2026-07-09T07:30:00Z"));
            dispatcher.dispatch(event);
            String posted = body.get();
            assertTrue(posted != null && !posted.isBlank());
            RouteChangeEvent parsed = RouteChangeEvent.fromJson(posted);
            assertEquals(event.host(), parsed.host());
            assertEquals(event.oldIps(), parsed.oldIps());
            assertEquals(event.newIps(), parsed.newIps());
            assertEquals(event.profile(), parsed.profile());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void redactWebhookUrlStripsCredentialsAndQuery() {
        String redacted = AlertWebhookSupport.redactWebhookUrl("https://user:pass@hooks.example.com/path?token=abc");
        assertEquals("https://hooks.example.com/path", redacted);
    }
}
