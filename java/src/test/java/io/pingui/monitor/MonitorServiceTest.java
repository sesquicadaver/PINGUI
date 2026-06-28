package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.RouteSnapshot;
import io.pingui.probe.RouteProbe;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class MonitorServiceTest {
    @Test
    void emitsSnapshotWhenEnabled() throws Exception {
        RouteSnapshot snapshot =
                new RouteSnapshot(
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
        MonitorService service = new MonitorService(1.0, 20, 0.5, new FakeRouteProbe(
                new RouteSnapshot("a", "1.1.1.1", List.of(new HopNode(1, "1.1.1.1", 1.0, false)))));
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
        RouteSnapshot snapA =
                new RouteSnapshot("8.8.8.8", "8.8.8.8", List.of(new HopNode(1, "10.0.0.1", 5.0, false)));
        RouteSnapshot snapB =
                new RouteSnapshot("1.1.1.1", "1.1.1.1", List.of(new HopNode(1, "10.0.0.2", 6.0, false)));
        CountDownLatch latch = new CountDownLatch(2);
        RouteProbe probe =
                (targetHost, maxHops, timeoutSeconds) ->
                        targetHost.equals("8.8.8.8") ? snapA : snapB;
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
                new RouteSnapshot(
                        "rezka.ag",
                        "1.1.1.1",
                        List.of(new HopNode(1, "10.0.0.1", 5.0, false)));
        CountDownLatch probeStarted = new CountDownLatch(1);
        CountDownLatch releaseProbe = new CountDownLatch(1);
        RouteProbe slowProbe =
                (targetHost, maxHops, timeoutSeconds) -> {
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
}
