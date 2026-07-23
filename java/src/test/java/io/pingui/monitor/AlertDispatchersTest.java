package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.config.AlertConfig;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AlertDispatchersTest {
    @Test
    void disabledConfigReturnsNoop() {
        AlertDispatcher dispatcher = AlertDispatchers.build(AlertConfig.disabled());
        assertInstanceOf(NoOpAlertDispatcher.class, dispatcher);
    }

    @Test
    void enabledWebhookWrapsRateLimiter() {
        AlertConfig config = new AlertConfig(false, "https://example.com/hook", 5);
        AlertDispatcher dispatcher = AlertDispatchers.build(config);
        assertInstanceOf(RateLimitedAlertDispatcher.class, dispatcher);
    }

    @Test
    void compositeWhenMultipleChannels() {
        AlertConfig config = new AlertConfig(true, "https://example.com/hook", 5);
        AlertDispatcher dispatcher = AlertDispatchers.build(config);
        assertInstanceOf(RateLimitedAlertDispatcher.class, dispatcher);
    }

    @Test
    void desktopChannelUsesInjectedSink() {
        List<String> titles = new ArrayList<>();
        AlertConfig config = new AlertConfig(true, null, 10);
        AlertDispatcher dispatcher = AlertDispatchers.build(config, (title, body) -> titles.add(title));
        RouteChangeEvent event = RouteChangeEvent.fromRouteChange(
                "8.8.8.8", List.of("10.0.0.1"), List.of("10.0.0.2"), "default", Instant.now());
        dispatcher.dispatch(event);
        assertEquals(List.of("PINGUI route change"), titles);
    }

    @Test
    void rateLimiterDropsBurst() {
        AlertConfig config = new AlertConfig(false, "https://example.com/hook", 1);
        RecordingAlertDispatcher inner = new RecordingAlertDispatcher();
        RateLimitedAlertDispatcher limited = new RateLimitedAlertDispatcher(inner, new AlertRateLimiter(1));
        RouteChangeEvent event = RouteChangeEvent.fromRouteChange(
                "8.8.8.8", List.of("10.0.0.1"), List.of("10.0.0.2"), "default", Instant.now());
        limited.dispatch(event);
        limited.dispatch(event);
        assertEquals(1, inner.events().size());
        assertTrue(inner.events().get(0).host().equals("8.8.8.8"));
    }
}
