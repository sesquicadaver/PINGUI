package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.config.ConfigError;
import io.pingui.config.HostEntry;
import io.pingui.config.PingExpertEntry;
import io.pingui.model.Models;
import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.RouteSnapshot;
import io.pingui.persistence.SessionDatabase;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionStoreTest {
    @Test
    void updateRouteKeepsPreviousOnChange() {
        SessionStore store = new SessionStore(List.of("h"));
        RouteSnapshot first = new RouteSnapshot("h", "2.2.2.2", List.of(new HopNode(1, "1.1.1.1", 10.0, false)));
        RouteSnapshot second = new RouteSnapshot("h", "2.2.2.2", List.of(new HopNode(1, "9.9.9.9", 10.0, false)));
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
                new RouteSnapshot("h", "2.2.2.2", List.of(new HopNode(1, "1.1.1.1", 10.0, false), Models.timeout(2))));
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
                    "h", new RouteSnapshot("h", "1.1.1.1", List.of(new HopNode(1, "1.1.1.1", (double) i, false))));
        }
        assertEquals(
                SessionStore.MAX_PING_SAMPLES,
                store.get("h").getPingHistory().get("1.1.1.1").size());
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
                "h", new RouteSnapshot("h", "1.1.1.1", List.of(new HopNode(1, "1.1.1.1", 10.0, false))));
        store.appendPingSamples(
                "h", new RouteSnapshot("h", "1.1.1.1", List.of(new HopNode(1, "1.1.1.1", 20.0, false))));
        assertEquals(15.0, store.avgPing("h", "1.1.1.1"));
    }

    @Test
    void canAddHostRespectsLimit() {
        List<String> hosts = java.util.stream.IntStream.range(0, 10)
                .mapToObj(i -> "10.0.0." + i)
                .toList();
        SessionStore store = new SessionStore(hosts);
        assertFalse(store.canAddHost());
    }

    @Test
    void fromEntriesPreservesPingExpert() {
        HostEntry entry = new HostEntry("8.8.8.8", true, false, new PingExpertEntry(true, List.of("-4", "-s", "64")));
        SessionStore store = SessionStore.fromEntries(List.of(entry));
        assertTrue(store.getPingExpert("8.8.8.8").applyToChain());
        assertEquals(List.of("-4", "-s", "64"), store.getPingExpert("8.8.8.8").args());
        assertEquals(entry, store.toHostEntries().get(0));
    }

    @Test
    void targetStatsWhenEnabledWithProbes() {
        SessionStore store = new SessionStore(List.of("h"));
        store.setEnabled("h", true);
        RouteSnapshot snapshot = new RouteSnapshot(
                "h",
                "8.8.8.8",
                List.of(new HopNode(1, "10.0.0.1", 5.0, false), new HopNode(2, "8.8.8.8", 10.0, false)));
        store.updateRoute("h", snapshot);
        store.appendPingSamples("h", snapshot);
        var stats = store.targetStats("h");
        assertEquals(0.0, stats.lossPct());
        assertEquals(10.0, stats.avgMs());
    }

    @Test
    void targetStatsNullWhenDisabledOrEmpty() {
        SessionStore store = new SessionStore(List.of("h"));
        store.setEnabled("h", false);
        assertNull(store.targetStats("h"));
        store.setEnabled("h", true);
        assertNull(store.targetStats("h"));
    }

    @Test
    void setPingOnlyClearsRoutes() {
        SessionStore store = new SessionStore(List.of("h"));
        store.updateRoute("h", new RouteSnapshot("h", "1.1.1.1", List.of(new HopNode(1, "1.1.1.1", 5.0, false))));
        store.setPingOnly("h", true);
        assertTrue(store.get("h").isPingOnly());
        assertTrue(store.get("h").getCurrentRoute().isEmpty());
    }

    @Test
    void loadHostEntriesPreservesTags() {
        HostEntry tagged =
                new HostEntry("8.8.8.8", true, false, PingExpertEntry.empty(), null, null, List.of("dc", "vpn"));
        SessionStore store = SessionStore.fromEntries(List.of(tagged));
        assertEquals(List.of("dc", "vpn"), store.toHostEntries().get(0).tags());
    }

    @Test
    void setTagsUpdatesSessionAndToHostEntries() {
        SessionStore store =
                SessionStore.fromEntries(List.of(new HostEntry("8.8.8.8", true, false, PingExpertEntry.empty())));
        assertEquals(List.of(), store.getTags("8.8.8.8"));
        store.setTags("8.8.8.8", List.of("DC", "vpn", "dc"));
        assertEquals(List.of("dc", "vpn"), store.getTags("8.8.8.8"));
        assertEquals(List.of("dc", "vpn"), store.toHostEntries().get(0).tags());
    }

    @Test
    void setTagsRejectsInvalid() {
        SessionStore store = new SessionStore(List.of("h"));
        assertThrows(ConfigError.class, () -> store.setTags("h", List.of("Bad Tag!")));
    }

    @Test
    void loadHostEntriesRoundTrip() {
        List<HostEntry> entries = List.of(
                new HostEntry("8.8.8.8", true, false, PingExpertEntry.empty()),
                new HostEntry("1.1.1.1", false, true, PingExpertEntry.empty()));
        SessionStore store = SessionStore.fromEntries(entries);
        assertEquals(2, store.hosts().size());
        assertTrue(store.containsHost("8.8.8.8"));
        assertTrue(store.isPingOnly("1.1.1.1"));
        store.loadHostEntries(List.of(new HostEntry("9.9.9.9", true, false, PingExpertEntry.empty())));
        assertEquals(List.of("9.9.9.9"), store.hosts());
    }

    @Test
    void addHostDuplicateThrows() {
        SessionStore store = new SessionStore(List.of("a"));
        assertThrows(ConfigError.class, () -> store.addHost("a", true));
    }

    @Test
    void getUnknownHostThrows() {
        SessionStore store = new SessionStore(List.of());
        assertThrows(ConfigError.class, () -> store.get("missing"));
    }

    @Test
    void reconnectUsesLiveEnabledStateNotYamlDefault(@TempDir Path tempDir) {
        HostEntry yamlDefault = new HostEntry("8.8.8.8", false, false, PingExpertEntry.empty());
        SessionStore ram = SessionStore.fromEntries(List.of(yamlDefault));
        ram.setEnabled("8.8.8.8", true);

        Path dbPath = tempDir.resolve("live.db");
        try (SessionDatabase database = new SessionDatabase(dbPath)) {
            SessionStore persisted = SessionStore.fromEntries(ram.toHostEntries(), database);
            assertTrue(persisted.get("8.8.8.8").isEnabled());

            RouteSnapshot snapshot =
                    new RouteSnapshot("8.8.8.8", "8.8.8.8", List.of(new HopNode(1, "10.0.0.1", 5.0, false)));
            persisted.updateRoute("8.8.8.8", snapshot);

            var loaded = database.load("8.8.8.8");
            assertNotNull(loaded);
            assertTrue(loaded.isEnabled());
            assertEquals(1, loaded.getCurrentRoute().size());
            assertEquals("10.0.0.1", loaded.getCurrentRoute().get(0).ip());
        }
    }

    @Test
    void forwardsRouteAndPingSamplesToTimeSeriesBackend() {
        io.pingui.persistence.timeseries.MemoryTimeSeriesBackend backend =
                new io.pingui.persistence.timeseries.MemoryTimeSeriesBackend();
        SessionStore store = new SessionStore(List.of("8.8.8.8"));
        store.setTimeSeriesBackend(backend);
        RouteSnapshot first = new RouteSnapshot(
                "8.8.8.8",
                "8.8.8.8",
                List.of(new HopNode(1, "10.0.0.1", 4.0, false), new HopNode(2, "8.8.8.8", 8.0, false)));
        RouteSnapshot second = new RouteSnapshot(
                "8.8.8.8",
                "8.8.8.8",
                List.of(new HopNode(1, "9.9.9.9", 4.0, false), new HopNode(2, "8.8.8.8", 8.0, false)));
        store.updateRoute("8.8.8.8", first);
        store.appendPingSamples("8.8.8.8", first);
        store.updateRoute("8.8.8.8", second);
        assertEquals(2, backend.routeEvents().size());
        assertFalse(backend.routeEvents().get(0).routeChanged());
        assertTrue(backend.routeEvents().get(1).routeChanged());
        assertEquals(2, backend.pingSamples().size());
        assertEquals("10.0.0.1", backend.pingSamples().get(0).hopIp());
        store.close();
    }
}
