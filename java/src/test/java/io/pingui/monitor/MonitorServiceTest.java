package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.config.ConfigError;
import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.RouteSnapshot;
import io.pingui.persistence.PersistenceEventType;
import io.pingui.persistence.SessionDatabase;
import io.pingui.probe.RouteProbe;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class MonitorServiceTest {
    @Test
    void emitsSnapshotWhenEnabled() throws Exception {
        RouteSnapshot snapshot = new RouteSnapshot(
                "8.8.8.8",
                "8.8.8.8",
                List.of(new HopNode(1, "10.0.0.1", 5.0, false), new HopNode(2, "8.8.8.8", 10.0, false)));
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> hostRef = new AtomicReference<>();
        MonitorService service = new MonitorService(0.05, 20, 0.5, new FakeRouteProbe(snapshot));
        service.setListener(new MonitorService.Listener() {
            @Override
            public void onDataReceived(String host, RouteSnapshot snap) {
                hostRef.set(host);
                latch.countDown();
            }

            @Override
            public void onRouteChanged(String host, List<String> oldIps, List<String> newIps) {}

            @Override
            public void onProbeError(String host, String message) {}
        });
        service.addHost("8.8.8.8", true);
        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertEquals("8.8.8.8", hostRef.get());
        service.close();
    }

    @Test
    void emitsProbeError() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> messageRef = new AtomicReference<>();
        MonitorService service = new MonitorService(0.05, 20, 0.5, FailingRouteProbe.io("timeout"));
        service.setListener(new MonitorService.Listener() {
            @Override
            public void onDataReceived(String host, RouteSnapshot snap) {}

            @Override
            public void onRouteChanged(String host, List<String> oldIps, List<String> newIps) {}

            @Override
            public void onProbeError(String host, String message) {
                messageRef.set(message);
                latch.countDown();
            }
        });
        service.addHost("8.8.8.8", true);
        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertEquals("timeout", messageRef.get());
        service.close();
    }

    @Test
    void hostManagementGuards() {
        MonitorService service = new MonitorService(
                1.0,
                20,
                0.5,
                new FakeRouteProbe(new RouteSnapshot("a", "1.1.1.1", List.of(new HopNode(1, "1.1.1.1", 1.0, false)))));
        service.addHost("a", false);
        assertEquals(List.of("a"), service.hosts());
        assertEquals(List.of(), service.enabledHosts());
        assertTrue(service.canAddHost());
        service.setHostEnabled("a", true);
        assertEquals(List.of("a"), service.enabledHosts());
        service.renameHost("a", "b");
        assertEquals(List.of("b"), service.hosts());
        service.removeHost("b");
        assertEquals(List.of(), service.hosts());
        service.close();
    }

    @Test
    void emitsSnapshotsForMultipleEnabledHosts() throws Exception {
        RouteSnapshot snapA = new RouteSnapshot("8.8.8.8", "8.8.8.8", List.of(new HopNode(1, "10.0.0.1", 5.0, false)));
        RouteSnapshot snapB = new RouteSnapshot("1.1.1.1", "1.1.1.1", List.of(new HopNode(1, "10.0.0.2", 6.0, false)));
        CountDownLatch latch = new CountDownLatch(2);
        RouteProbe probe = (targetHost, maxHops, timeoutSeconds) -> targetHost.equals("8.8.8.8") ? snapA : snapB;
        MonitorService service = new MonitorService(0.05, 20, 0.5, probe);
        service.setListener(new MonitorService.Listener() {
            @Override
            public void onDataReceived(String host, RouteSnapshot snap) {
                latch.countDown();
            }

            @Override
            public void onRouteChanged(String host, List<String> oldIps, List<String> newIps) {}

            @Override
            public void onProbeError(String host, String message) {}
        });
        service.addHost("8.8.8.8", true);
        service.addHost("1.1.1.1", true);
        assertTrue(latch.await(3, TimeUnit.SECONDS));
        service.close();
    }

    @Test
    void dropsStaleCallbackAfterHostRemoved() throws Exception {
        RouteSnapshot snapshot =
                new RouteSnapshot("rezka.ag", "1.1.1.1", List.of(new HopNode(1, "10.0.0.1", 5.0, false)));
        CountDownLatch probeStarted = new CountDownLatch(1);
        CountDownLatch releaseProbe = new CountDownLatch(1);
        RouteProbe slowProbe = (targetHost, maxHops, timeoutSeconds) -> {
            probeStarted.countDown();
            try {
                if (!releaseProbe.await(3, TimeUnit.SECONDS)) {
                    throw new java.io.IOException("probe wait timed out");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new java.io.IOException("probe interrupted", ex);
            }
            return snapshot;
        };
        AtomicInteger received = new AtomicInteger();
        MonitorService service = new MonitorService(0.05, 20, 0.5, slowProbe);
        service.setListener(new MonitorService.Listener() {
            @Override
            public void onDataReceived(String host, RouteSnapshot snap) {
                received.incrementAndGet();
            }

            @Override
            public void onRouteChanged(String host, List<String> oldIps, List<String> newIps) {}

            @Override
            public void onProbeError(String host, String message) {}
        });
        service.addHost("rezka.ag", true);
        assertTrue(probeStarted.await(3, TimeUnit.SECONDS));
        service.removeHost("rezka.ag");
        releaseProbe.countDown();
        Thread.sleep(300);
        assertEquals(0, received.get());
        service.close();
    }

    @Test
    void emitsRouteChangedWhenIpsDiffer() throws Exception {
        RouteSnapshot first = new RouteSnapshot("8.8.8.8", "8.8.8.8", List.of(new HopNode(1, "10.0.0.1", 5.0, false)));
        RouteSnapshot second =
                new RouteSnapshot("8.8.8.8", "8.8.8.8", List.of(new HopNode(1, "192.168.1.1", 6.0, false)));
        java.util.concurrent.atomic.AtomicInteger probeCalls = new java.util.concurrent.atomic.AtomicInteger();
        RouteProbe probe = (targetHost, maxHops, timeoutSeconds) -> {
            return probeCalls.getAndIncrement() == 0 ? first : second;
        };
        CountDownLatch latch = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<List<String>> oldRef =
                new java.util.concurrent.atomic.AtomicReference<>();
        MonitorService service = new MonitorService(0.05, 20, 0.5, probe);
        service.setListener(new MonitorService.Listener() {
            @Override
            public void onDataReceived(String host, RouteSnapshot snap) {}

            @Override
            public void onRouteChanged(String host, List<String> oldIps, List<String> newIps) {
                if (!oldIps.isEmpty()) {
                    oldRef.set(oldIps);
                    latch.countDown();
                }
            }

            @Override
            public void onProbeError(String host, String message) {}
        });
        service.addHost("8.8.8.8", true);
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(List.of("10.0.0.1"), oldRef.get());
        service.close();
    }

    @Test
    void dispatchesAlertOnRouteChange() throws Exception {
        RouteSnapshot first = new RouteSnapshot("8.8.8.8", "8.8.8.8", List.of(new HopNode(1, "10.0.0.1", 5.0, false)));
        RouteSnapshot second =
                new RouteSnapshot("8.8.8.8", "8.8.8.8", List.of(new HopNode(1, "192.168.1.1", 6.0, false)));
        AtomicInteger probeCalls = new AtomicInteger();
        RouteProbe probe = (targetHost, maxHops, timeoutSeconds) -> probeCalls.getAndIncrement() == 0 ? first : second;
        RecordingAlertDispatcher alerts = new RecordingAlertDispatcher();
        MonitorService service = new MonitorService(0.05, 20, 0.5, probe);
        service.setAlertDispatcher(alerts);
        service.setAlertProfileName("noc");
        service.setListener(new MonitorService.Listener() {
            @Override
            public void onDataReceived(String host, RouteSnapshot snap) {}

            @Override
            public void onRouteChanged(String host, List<String> oldIps, List<String> newIps) {}

            @Override
            public void onProbeError(String host, String message) {}
        });
        service.addHost("8.8.8.8", true);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (alerts.events().isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        assertEquals(1, alerts.events().size());
        RouteChangeEvent event = alerts.events().get(0);
        assertEquals("8.8.8.8", event.host());
        assertEquals(List.of("10.0.0.1"), event.oldIps());
        assertEquals(List.of("192.168.1.1"), event.newIps());
        assertEquals("noc", event.profile());
        service.close();
    }

    @Test
    void pollsHostsOnIndependentSchedules() throws Exception {
        AtomicInteger tracePolls = new AtomicInteger();
        AtomicInteger pingOnlyPolls = new AtomicInteger();
        RouteProbe probe = (targetHost, maxHops, timeoutSeconds) -> {
            if ("8.8.8.8".equals(targetHost)) {
                tracePolls.incrementAndGet();
            } else {
                pingOnlyPolls.incrementAndGet();
            }
            return new RouteSnapshot(targetHost, targetHost, List.of(new HopNode(1, "10.0.0.1", 1.0, false)));
        };
        MonitorService service = new MonitorService(0.05, 20, 0.5, probe);
        service.addHost("8.8.8.8", true, HostProbeMode.TRACE);
        service.addHost("1.1.1.1", true, HostProbeMode.PING_ONLY);
        Thread.sleep(900);
        assertTrue(tracePolls.get() >= 3, "trace host should poll frequently");
        assertTrue(pingOnlyPolls.get() <= 1, "ping_only host polls at most once in ~900ms");
        service.close();
    }

    @Test
    void duplicateHostRejected() {
        MonitorService service = new MonitorService(
                1.0,
                20,
                0.5,
                new FakeRouteProbe(new RouteSnapshot("a", "1.1.1.1", List.of(new HopNode(1, "1.1.1.1", 1.0, false)))));
        service.addHost("a", false);
        assertThrows(ConfigError.class, () -> service.addHost("a", true));
        service.close();
    }

    @Test
    void pingOnlyHostPollsWithoutTraceroute() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        MonitorService service = new MonitorService(
                0.05,
                20,
                0.5,
                new FakeRouteProbe(new RouteSnapshot("x", "x", List.of(new HopNode(1, "1.1.1.1", 1.0, false)))));
        service.setListener(new MonitorService.Listener() {
            @Override
            public void onDataReceived(String host, RouteSnapshot snap) {
                latch.countDown();
            }

            @Override
            public void onRouteChanged(String host, List<String> oldIps, List<String> newIps) {}

            @Override
            public void onProbeError(String host, String message) {
                latch.countDown();
            }
        });
        service.addHost("127.0.0.1", true, true);
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        service.close();
    }

    @Test
    void setHostPingOnlyGuardsUnknownHost() {
        MonitorService service = new MonitorService(
                1.0,
                20,
                0.5,
                new FakeRouteProbe(new RouteSnapshot("a", "1.1.1.1", List.of(new HopNode(1, "1.1.1.1", 1.0, false)))));
        service.addHost("a", true, false);
        service.setHostPingOnly("a", true);
        assertThrows(ConfigError.class, () -> service.setHostPingOnly("missing", true));
        service.close();
    }

    @Test
    void pingOnlyResolverOverridesMapFlag() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        MonitorService service = new MonitorService(
                0.05,
                20,
                0.5,
                new FakeRouteProbe(
                        new RouteSnapshot("8.8.8.8", "8.8.8.8", List.of(new HopNode(1, "10.0.0.1", 5.0, false)))));
        service.setPingOnlyResolver(host -> true);
        service.setListener(new MonitorService.Listener() {
            @Override
            public void onDataReceived(String host, RouteSnapshot snap) {
                latch.countDown();
            }

            @Override
            public void onRouteChanged(String host, List<String> oldIps, List<String> newIps) {}

            @Override
            public void onProbeError(String host, String message) {
                latch.countDown();
            }
        });
        service.addHost("127.0.0.1", true, false);
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        service.close();
    }

    @Test
    void usesMapFlagWhenResolverUnset() throws Exception {
        RouteSnapshot snapshot =
                new RouteSnapshot("8.8.8.8", "8.8.8.8", List.of(new HopNode(1, "10.0.0.1", 5.0, false)));
        CountDownLatch latch = new CountDownLatch(1);
        MonitorService service = new MonitorService(0.05, 20, 0.5, new FakeRouteProbe(snapshot));
        service.setListener(new MonitorService.Listener() {
            @Override
            public void onDataReceived(String host, RouteSnapshot snap) {
                assertEquals("10.0.0.1", snap.nodes().get(0).ip());
                latch.countDown();
            }

            @Override
            public void onRouteChanged(String host, List<String> oldIps, List<String> newIps) {}

            @Override
            public void onProbeError(String host, String message) {}
        });
        service.addHost("8.8.8.8", true, false);
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        service.close();
    }

    @Test
    void persistsBaselineRouteChangeOnFirstPoll() throws Exception {
        RouteSnapshot stable = new RouteSnapshot("8.8.8.8", "8.8.8.8", List.of(new HopNode(1, "10.0.0.1", 5.0, false)));
        Path dbPath = java.nio.file.Files.createTempDirectory("pingui-baseline").resolve("baseline.db");
        try (SessionDatabase database = new SessionDatabase(dbPath)) {
            MonitorService service = new MonitorService(0.05, 20, 0.5, new FakeRouteProbe(stable));
            service.setPersistenceEventWriter(
                    new io.pingui.persistence.PersistenceEventWriter(database, service.persistencePolicy()));
            service.setListener(new MonitorService.Listener() {
                @Override
                public void onDataReceived(String host, RouteSnapshot snap) {}

                @Override
                public void onRouteChanged(String host, List<String> oldIps, List<String> newIps) {}

                @Override
                public void onProbeError(String host, String message) {}
            });
            service.addHost("8.8.8.8", true);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while (database.countEvents(PersistenceEventType.ROUTE_CHANGE) == 0 && System.nanoTime() < deadline) {
                Thread.sleep(50);
            }
            assertEquals(1, database.countEvents(PersistenceEventType.ROUTE_CHANGE));
            Thread.sleep(300);
            assertEquals(1, database.countEvents(PersistenceEventType.ROUTE_CHANGE));
            service.close();
        }
    }

    @Test
    void persistsRouteChangeAndProbeErrorEvents() throws Exception {
        Path dbPath = java.nio.file.Files.createTempDirectory("pingui-events").resolve("events.db");
        try (SessionDatabase database = new SessionDatabase(dbPath)) {
            MonitorService service = new MonitorService(0.05, 20, 0.5, singleRouteChangeProbe());
            service.setPersistenceEventWriter(
                    new io.pingui.persistence.PersistenceEventWriter(database, service.persistencePolicy()));
            service.setListener(new MonitorService.Listener() {
                @Override
                public void onDataReceived(String host, RouteSnapshot snap) {}

                @Override
                public void onRouteChanged(String host, List<String> oldIps, List<String> newIps) {}

                @Override
                public void onProbeError(String host, String message) {}
            });
            service.addHost("8.8.8.8", true);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while (database.countEvents(PersistenceEventType.ROUTE_CHANGE) == 0 && System.nanoTime() < deadline) {
                Thread.sleep(50);
            }
            assertEquals(1, database.countEvents(PersistenceEventType.ROUTE_CHANGE));
            service.close();
        }

        try (SessionDatabase database = new SessionDatabase(dbPath)) {
            MonitorService service = new MonitorService(0.05, 20, 0.5, FailingRouteProbe.io("timeout"));
            service.setPersistenceEventWriter(
                    new io.pingui.persistence.PersistenceEventWriter(database, service.persistencePolicy()));
            service.setListener(new MonitorService.Listener() {
                @Override
                public void onDataReceived(String host, RouteSnapshot snap) {}

                @Override
                public void onRouteChanged(String host, List<String> oldIps, List<String> newIps) {}

                @Override
                public void onProbeError(String host, String message) {}
            });
            service.addHost("1.1.1.1", true);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while (database.countEvents(PersistenceEventType.PROBE_ERROR) == 0 && System.nanoTime() < deadline) {
                Thread.sleep(50);
            }
            assertEquals(1, database.countEvents(PersistenceEventType.PROBE_ERROR));
            service.close();
        }
    }

    @Test
    void appliesPersistencePolicyAfterPollCycle() throws Exception {
        Path dbPath = java.nio.file.Files.createTempDirectory("pingui-policy").resolve("policy.db");
        try (SessionDatabase database = new SessionDatabase(dbPath)) {
            MonitorService service = new MonitorService(0.05, 20, 0.5, alternatingRouteChangeProbe());
            service.setPersistenceEventWriter(
                    new io.pingui.persistence.PersistenceEventWriter(database, service.persistencePolicy()));
            service.setPendingPersistencePolicy(io.pingui.persistence.PersistencePolicy.of(false, true));
            service.setListener(new MonitorService.Listener() {
                @Override
                public void onDataReceived(String host, RouteSnapshot snap) {}

                @Override
                public void onRouteChanged(String host, List<String> oldIps, List<String> newIps) {}

                @Override
                public void onProbeError(String host, String message) {}
            });
            service.addHost("8.8.8.8", true);

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(6);
            while (System.nanoTime() < deadline && database.countEvents(PersistenceEventType.ROUTE_CHANGE) == 0) {
                Thread.sleep(50);
            }
            assertEquals(0, database.countEvents(PersistenceEventType.ROUTE_CHANGE));

            service.setPendingPersistencePolicy(io.pingui.persistence.PersistencePolicy.defaults());
            deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while (database.countEvents(PersistenceEventType.ROUTE_CHANGE) == 0 && System.nanoTime() < deadline) {
                Thread.sleep(50);
            }
            assertEquals(1, database.countEvents(PersistenceEventType.ROUTE_CHANGE));
            service.close();
        }
    }

    private static RouteProbe singleRouteChangeProbe() {
        RouteSnapshot first = new RouteSnapshot("8.8.8.8", "8.8.8.8", List.of(new HopNode(1, "10.0.0.1", 5.0, false)));
        RouteSnapshot second =
                new RouteSnapshot("8.8.8.8", "8.8.8.8", List.of(new HopNode(1, "192.168.1.1", 6.0, false)));
        AtomicInteger probeCalls = new AtomicInteger();
        return (targetHost, maxHops, timeoutSeconds) -> probeCalls.getAndIncrement() == 0 ? first : second;
    }

    private static RouteProbe alternatingRouteChangeProbe() {
        RouteSnapshot first = new RouteSnapshot("8.8.8.8", "8.8.8.8", List.of(new HopNode(1, "10.0.0.1", 5.0, false)));
        RouteSnapshot second =
                new RouteSnapshot("8.8.8.8", "8.8.8.8", List.of(new HopNode(1, "192.168.1.1", 6.0, false)));
        AtomicInteger probeCalls = new AtomicInteger();
        return (targetHost, maxHops, timeoutSeconds) -> probeCalls.getAndIncrement() % 2 == 0 ? first : second;
    }
}
