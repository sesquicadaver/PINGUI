package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DesktopAlertDispatcherTest {

    @Test
    void dispatchRouteChangeUsesPopupSink() {
        List<String> titles = new ArrayList<>();
        List<String> bodies = new ArrayList<>();
        DesktopAlertDispatcher dispatcher = new DesktopAlertDispatcher((title, body) -> {
            titles.add(title);
            bodies.add(body);
        });
        RouteChangeEvent event = RouteChangeEvent.fromRouteChange(
                "8.8.8.8", List.of("10.0.0.1"), List.of("10.0.0.2"), "default", Instant.parse("2026-07-23T12:00:00Z"));
        dispatcher.dispatch(event);
        assertEquals(List.of("PINGUI route change"), titles);
        assertEquals(1, bodies.size());
        assertTrue(bodies.get(0).contains("8.8.8.8"));
        assertTrue(bodies.get(0).contains("10.0.0.1"));
        assertTrue(bodies.get(0).contains("10.0.0.2"));
    }

    @Test
    void dispatchQualityUsesPopupSink() {
        List<String> seen = new ArrayList<>();
        DesktopAlertDispatcher dispatcher = new DesktopAlertDispatcher((title, body) -> seen.add(title + "|" + body));
        QualityAlertEvent event = QualityAlertEvent.endpointDownFiring(
                "1.1.1.1", "lab", Instant.parse("2026-07-23T12:00:00Z"), Map.of("fail_streak", 3));
        dispatcher.dispatchQuality(event);
        assertEquals(List.of("PINGUI endpoint_down|1.1.1.1: firing"), seen);
    }

    @Test
    void defaultConstructorIsNoopWithoutSink() {
        new DesktopAlertDispatcher()
                .dispatch(RouteChangeEvent.fromRouteChange(
                        "8.8.8.8", List.of("10.0.0.1"), List.of("10.0.0.2"), "default", Instant.now()));
    }
}
