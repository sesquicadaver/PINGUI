package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.RouteSnapshot;
import io.pingui.telemetry.MetricSample;
import io.pingui.telemetry.SinkRegistry;
import io.pingui.telemetry.TelemetryBus;
import io.pingui.telemetry.TelemetryEvent;
import io.pingui.telemetry.TelemetrySink;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class MonitorServiceTelemetryTest {
    @Test
    void offersSamplesAndRouteChangeToBus() throws Exception {
        RouteSnapshot snapshot = new RouteSnapshot(
                "8.8.8.8",
                "8.8.8.8",
                List.of(new HopNode(1, "10.0.0.1", 5.0, false), new HopNode(2, "8.8.8.8", 10.0, false)));
        RecordingSink sink = new RecordingSink();
        SinkRegistry registry = new SinkRegistry();
        registry.register(sink);
        try (TelemetryBus bus = new TelemetryBus(
                        registry, 64, io.pingui.telemetry.DropPolicy.DROP_OLDEST, 8, Duration.ofMillis(5));
                MonitorService service = new MonitorService(0.05, 20, 0.5, new FakeRouteProbe(snapshot))) {
            CountDownLatch data = new CountDownLatch(1);
            service.setTelemetryBus(bus);
            service.setListener(new MonitorService.Listener() {
                @Override
                public void onDataReceived(String host, RouteSnapshot snap) {
                    data.countDown();
                }

                @Override
                public void onRouteChanged(String host, List<String> oldIps, List<String> newIps) {}

                @Override
                public void onProbeError(String host, String message) {}
            });
            service.addHost("8.8.8.8", true);
            assertTrue(data.await(3, TimeUnit.SECONDS));
            assertTrue(await(() -> sink.samples.stream().anyMatch(s -> "pingui_rtt_ms".equals(s.name())), 2_000));
            assertTrue(sink.samples.stream().anyMatch(s -> "pingui_hop_loss_pct".equals(s.name())));
            assertTrue(sink.samples.stream().anyMatch(s -> "pingui_target_reachable".equals(s.name())));
            assertTrue(await(
                    () -> sink.events.stream().anyMatch(e -> TelemetryEvent.ROUTE_CHANGE.equals(e.event())), 2_000));
        }
    }

    @Test
    void offersProbeErrorEventToBus() throws Exception {
        RecordingSink sink = new RecordingSink();
        SinkRegistry registry = new SinkRegistry();
        registry.register(sink);
        try (TelemetryBus bus = new TelemetryBus(
                        registry, 32, io.pingui.telemetry.DropPolicy.DROP_OLDEST, 4, Duration.ofMillis(5));
                MonitorService service = new MonitorService(0.05, 20, 0.5, FailingRouteProbe.io("timeout"))) {
            CountDownLatch error = new CountDownLatch(1);
            service.setTelemetryBus(bus);
            service.setListener(new MonitorService.Listener() {
                @Override
                public void onDataReceived(String host, RouteSnapshot snap) {}

                @Override
                public void onRouteChanged(String host, List<String> oldIps, List<String> newIps) {}

                @Override
                public void onProbeError(String host, String message) {
                    error.countDown();
                }
            });
            service.addHost("8.8.8.8", true);
            assertTrue(error.await(3, TimeUnit.SECONDS));
            assertTrue(await(
                    () -> sink.events.stream().anyMatch(e -> TelemetryEvent.PROBE_ERROR.equals(e.event())), 2_000));
            assertEquals("timeout", sink.events.get(0).message());
        }
    }

    private static boolean await(Check check, long timeoutMs) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            if (check.ok()) {
                return true;
            }
            Thread.sleep(5);
        }
        return check.ok();
    }

    @FunctionalInterface
    private interface Check {
        boolean ok();
    }

    private static final class RecordingSink implements TelemetrySink {
        private final List<MetricSample> samples = new CopyOnWriteArrayList<>();
        private final List<TelemetryEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public String id() {
            return "rec";
        }

        @Override
        public void onSample(MetricSample sample) {
            samples.add(sample);
        }

        @Override
        public void onEvent(TelemetryEvent event) {
            events.add(event);
        }
    }
}
