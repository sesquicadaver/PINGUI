package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.config.ConfigError;
import io.pingui.config.HostEntry;
import io.pingui.config.PingExpertEntry;
import io.pingui.model.Models;
import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.RouteSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class SessionStoreTest {
    @Test
    void updateRouteKeepsPreviousOnChange() {
        SessionStore store = new SessionStore(List.of("h"));
        RouteSnapshot first =
                new RouteSnapshot("h", "2.2.2.2", List.of(new HopNode(1, "1.1.1.1", 10.0, false)));
        RouteSnapshot second =
                new RouteSnapshot("h", "2.2.2.2", List.of(new HopNode(1, "9.9.9.9", 10.0, false)));
        store.updateRoute("h", first);
        store.updateRoute("h", second);
        assertEquals("1.1.1.1", store.get("h").getPreviousRoute().get(0).ip());
    }

    @Test
    void inactiveRouteFillsLastKnown() {
        SessionStore store = new SessionStore(List.of("h"));
        store.updateRoute(
                "h",
                new RouteSnapshot(
                        "h",
                        "2.2.2.2",
                        List.of(new HopNode(1, "1.1.1.1", 10.0, false), new HopNode(2, "2.2.2.2", 20.0, false))));
        store.updateRoute(
                "h",
                new RouteSnapshot(
                        "h",
                        "2.2.2.2",
                        List.of(new HopNode(1, "1.1.1.1", 10.0, false), Models.timeout(2))));
        store.updateRoute(
                "h",
                new RouteSnapshot(
                        "h",
                        "2.2.2.2",
                        List.of(new HopNode(1, "9.9.9.9", 10.0, false), new HopNode(2, "2.2.2.2", 20.0, false))));
        assertEquals("2.2.2.2", store.inactiveRoute("h").get(1).ip());
    }

    @Test
    void appendPingSamplesTrimsHistory() {
        SessionStore store = new SessionStore(List.of("h"));
        for (int i = 0; i < SessionStore.MAX_PING_SAMPLES + 5; i++) {
            store.appendPingSamples(
                    "h",
                    new RouteSnapshot(
                            "h",
                            "1.1.1.1",
                            List.of(new HopNode(1, "1.1.1.1", (double) i, false))));
        }
        assertEquals(SessionStore.MAX_PING_SAMPLES, store.get("h").getPingHistory().get("1.1.1.1").size());
    }

    @Test
    void renameHost() {
        SessionStore store = new SessionStore(List.of("old"));
        store.setEnabled("old", true);
        String renamed = store.renameHost("old", "new.example");
        assertEquals("new.example", renamed);
        assertTrue(store.get("new.example").isEnabled());
    }

    @Test
    void removeUnknownHostThrows() {
        SessionStore store = new SessionStore(List.of("a"));
        assertThrows(ConfigError.class, () -> store.removeHost("missing"));
    }

    @Test
    void avgPingReturnsNullWhenEmpty() {
        SessionStore store = new SessionStore(List.of("h"));
        assertNull(store.avgPing("h", "1.1.1.1"));
    }

    @Test
    void avgPingComputesMean() {
        SessionStore store = new SessionStore(List.of("h"));
        store.appendPingSamples(
                "h",
                new RouteSnapshot("h", "1.1.1.1", List.of(new HopNode(1, "1.1.1.1", 10.0, false))));
        store.appendPingSamples(
                "h",
                new RouteSnapshot("h", "1.1.1.1", List.of(new HopNode(1, "1.1.1.1", 20.0, false))));
        assertEquals(15.0, store.avgPing("h", "1.1.1.1"));
    }

    @Test
    void canAddHostRespectsLimit() {
        List<String> hosts =
                java.util.stream.IntStream.range(0, 10).mapToObj(i -> "10.0.0." + i).toList();
        SessionStore store = new SessionStore(hosts);
        assertFalse(store.canAddHost());
    }

    @Test
    void fromEntriesPreservesPingExpert() {
        HostEntry entry =
                new HostEntry("8.8.8.8", true, new PingExpertEntry(true, List.of("-4", "-s", "64")));
        SessionStore store = SessionStore.fromEntries(List.of(entry));
        assertTrue(store.getPingExpert("8.8.8.8").applyToChain());
        assertEquals(List.of("-4", "-s", "64"), store.getPingExpert("8.8.8.8").args());
        assertEquals(entry, store.toHostEntries().get(0));
    }
}
