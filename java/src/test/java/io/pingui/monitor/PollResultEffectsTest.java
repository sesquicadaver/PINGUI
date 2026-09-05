package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.config.EndpointDownRuleConfig;
import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.RouteSnapshot;
import io.pingui.persistence.PersistenceEventType;
import io.pingui.persistence.PersistenceEventWriter;
import io.pingui.persistence.PersistencePolicyHolder;
import io.pingui.persistence.SessionDatabase;
import io.pingui.telemetry.DropPolicy;
import io.pingui.telemetry.MetricSample;
import io.pingui.telemetry.SinkRegistry;
import io.pingui.telemetry.TelemetryBus;
import io.pingui.telemetry.TelemetryEvent;
import io.pingui.telemetry.TelemetrySink;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class PollResultEffectsTest {
    @Test
    void isFirstBaselineDetectsInitialRoute() {
        assertTrue(PollResultEffects.isFirstBaseline(List.of(), List.of("10.0.0.1")));
        assertFalse(PollResultEffects.isFirstBaseline(List.of("10.0.0.1"), List.of("10.0.0.1")));
        assertFalse(PollResultEffects.isFirstBaseline(List.of(), List.of()));
    }

    @Test
    void terminalRttMsPrefersTargetHop() {
        RouteSnapshot snapshot = new RouteSnapshot(
                "8.8.8.8",
                "8.8.8.8",
                List.of(new HopNode(1, "10.0.0.1", 5.0, false), new HopNode(2, "8.8.8.8", 12.0, false)));
        OptionalDouble rtt = PollResultEffects.terminalRttMs(snapshot);
        assertTrue(rtt.isPresent());
        assertEquals(12.0, rtt.getAsDouble(), 0.001);
    }

    @Test
    void resetLatencyBaselineWarmsUpWithoutFalseHigh() {
        AlertRuleEngine engine = new AlertRuleEngine();
        PollResultEffects effects = new PollResultEffects(engine);
        effects.setLatencyHighRule(io.pingui.config.LatencyHighRuleConfig.critical(true));
        RouteSnapshot oldPath =
                new RouteSnapshot("8.8.8.8", "8.8.8.8", List.of(new HopNode(1, "8.8.8.8", 10.0, false)));
        effects.evaluateLatencyHigh("8.8.8.8", oldPath);
        assertEquals(10.0, engine.latencyAvg("8.8.8.8").orElseThrow(), 0.001);

        effects.resetLatencyBaseline("8.8.8.8");
        assertTrue(engine.latencyAvg("8.8.8.8").isEmpty());

        RouteSnapshot newPath =
                new RouteSnapshot("8.8.8.8", "8.8.8.8", List.of(new HopNode(1, "8.8.8.8", 100.0, false)));
        effects.evaluateLatencyHigh("8.8.8.8", newPath);
        assertEquals(100.0, engine.latencyAvg("8.8.8.8").orElseThrow(), 0.001);
        assertTrue(engine.problemSummary("8.8.8.8").isEmpty());
    }

    @Test
    void dispatchRouteChangeAlertPersistsAndDispatches() throws Exception {
        Path dbPath = Files.createTempDirectory("pingui-pre").resolve("effects.db");
        AlertRuleEngine engine = new AlertRuleEngine();
        PollResultEffects effects = new PollResultEffects(engine);
        RecordingAlertDispatcher alerts = new RecordingAlertDispatcher();
        effects.setAlertDispatcher(alerts);
        effects.setAlertProfileName("noc");
        try (SessionDatabase database = new SessionDatabase(dbPath)) {
            PersistencePolicyHolder policy = new PersistencePolicyHolder();
            effects.setPersistenceEventWriter(new PersistenceEventWriter(database, policy));
            effects.dispatchRouteChangeAlert("8.8.8.8", List.of("10.0.0.1"), List.of("192.168.1.1"));
            assertEquals(1, alerts.events().size());
            RouteChangeEvent event = alerts.events().get(0);
            assertEquals("8.8.8.8", event.host());
            assertEquals("noc", event.profile());
            assertEquals(1, database.countEvents(PersistenceEventType.ROUTE_CHANGE));
        }
    }

    @Test
    void silenceSuppressesDispatchButStillPersists() throws Exception {
        Path dbPath = Files.createTempDirectory("pingui-silence").resolve("silence.db");
        AlertRuleEngine engine = new AlertRuleEngine();
        PollResultEffects effects = new PollResultEffects(engine);
        RecordingAlertDispatcher alerts = new RecordingAlertDispatcher();
        effects.setAlertDispatcher(alerts);
        effects.setAlertSilence(new io.pingui.config.AlertSilenceConfig(List.of(new io.pingui.config.AlertSilenceEntry(
                io.pingui.config.AlertSilenceScope.HOST,
                "8.8.8.8",
                java.time.Instant.now().plusSeconds(3600),
                "maint"))));
        try (SessionDatabase database = new SessionDatabase(dbPath)) {
            PersistencePolicyHolder policy = new PersistencePolicyHolder();
            effects.setPersistenceEventWriter(new PersistenceEventWriter(database, policy));
            effects.dispatchRouteChangeAlert("8.8.8.8", List.of("10.0.0.1"), List.of("192.168.1.1"));
            assertEquals(0, alerts.events().size());
            assertEquals(1, database.countEvents(PersistenceEventType.ROUTE_CHANGE));
            effects.dispatchRouteChangeAlert("1.1.1.1", List.of("10.0.0.1"), List.of("9.9.9.9"));
            assertEquals(1, alerts.events().size());
        }
    }

    @Test
    void persistBaselineRouteChangeWritesOnce() throws Exception {
        Path dbPath = Files.createTempDirectory("pingui-baseline-fx").resolve("baseline.db");
        AlertRuleEngine engine = new AlertRuleEngine();
        PollResultEffects effects = new PollResultEffects(engine);
        try (SessionDatabase database = new SessionDatabase(dbPath)) {
            PersistencePolicyHolder policy = new PersistencePolicyHolder();
            effects.setPersistenceEventWriter(new PersistenceEventWriter(database, policy));
            effects.persistBaselineRouteChange("8.8.8.8", List.of("10.0.0.1"));
            effects.persistBaselineRouteChange("8.8.8.8", List.of("10.0.0.2"));
            assertEquals(1, database.countEvents(PersistenceEventType.ROUTE_CHANGE));
        }
    }

    @Test
    void silenceSuppressesQualityDispatchThenFlushesOnceAfterExpiry() throws Exception {
        Path dbPath = Files.createTempDirectory("pingui-silence-q").resolve("silence.db");
        AlertRuleEngine engine = new AlertRuleEngine();
        PollResultEffects effects = new PollResultEffects(engine);
        RecordingAlertDispatcher alerts = new RecordingAlertDispatcher();
        effects.setAlertDispatcher(alerts);
        effects.setEndpointDownRule(new EndpointDownRuleConfig(true, 2, 1, 0));
        Instant t0 = Instant.parse("2026-09-05T12:00:00Z");
        effects.setClock(java.time.Clock.fixed(t0, java.time.ZoneOffset.UTC));
        effects.setAlertSilence(new io.pingui.config.AlertSilenceConfig(List.of(new io.pingui.config.AlertSilenceEntry(
                io.pingui.config.AlertSilenceScope.HOST, "8.8.8.8", t0.plusSeconds(60), "maint"))));
        RouteSnapshot down = new RouteSnapshot(
                "8.8.8.8", "8.8.8.8", List.of(new HopNode(1, "10.0.0.1", 5.0, false), new HopNode(2, "*", null, true)));
        try (SessionDatabase database = new SessionDatabase(dbPath)) {
            PersistencePolicyHolder policy = new PersistencePolicyHolder();
            effects.setPersistenceEventWriter(new PersistenceEventWriter(database, policy));
            effects.evaluateEndpointDown("8.8.8.8", down);
            effects.evaluateEndpointDown("8.8.8.8", down);
            assertEquals(0, alerts.qualityEvents().size());
            assertEquals(1, database.countEvents(PersistenceEventType.ENDPOINT_DOWN));
            assertTrue(effects.qualityDeliveryForTests()
                    .hasPendingForTests("8.8.8.8", QualityAlertEvent.EVENT_ENDPOINT_DOWN));

            effects.setClock(java.time.Clock.fixed(t0.plusSeconds(61), java.time.ZoneOffset.UTC));
            effects.evaluateEndpointDown("8.8.8.8", down);
            assertEquals(1, alerts.qualityEvents().size());
            assertEquals(
                    QualityAlertEvent.STATE_FIRING,
                    alerts.qualityEvents().get(0).state());
            assertFalse(effects.qualityDeliveryForTests()
                    .hasPendingForTests("8.8.8.8", QualityAlertEvent.EVENT_ENDPOINT_DOWN));

            effects.evaluateEndpointDown("8.8.8.8", down);
            assertEquals(1, alerts.qualityEvents().size());
        }
    }

    @Test
    void cooldownSuppressesRepeatDeliveryUntilElapsedThenFlushesOnce() {
        AlertRuleEngine engine = new AlertRuleEngine();
        PollResultEffects effects = new PollResultEffects(engine);
        RecordingAlertDispatcher alerts = new RecordingAlertDispatcher();
        effects.setAlertDispatcher(alerts);
        effects.setEndpointDownRule(new EndpointDownRuleConfig(true, 1, 1, 15));
        effects.setNotifyResolved(true);
        Instant t0 = Instant.parse("2026-09-05T12:00:00Z");
        effects.setClock(java.time.Clock.fixed(t0, java.time.ZoneOffset.UTC));
        RouteSnapshot down = new RouteSnapshot("8.8.8.8", "8.8.8.8", List.of(new HopNode(1, "*", null, true)));
        RouteSnapshot up = new RouteSnapshot("8.8.8.8", "8.8.8.8", List.of(new HopNode(1, "8.8.8.8", 10.0, false)));

        effects.evaluateEndpointDown("8.8.8.8", down);
        assertEquals(1, alerts.qualityEvents().size());

        effects.evaluateEndpointDown("8.8.8.8", up);
        assertEquals(2, alerts.qualityEvents().size());
        assertEquals(
                QualityAlertEvent.STATE_RESOLVED, alerts.qualityEvents().get(1).state());

        effects.evaluateEndpointDown("8.8.8.8", down);
        assertEquals(2, alerts.qualityEvents().size());
        assertTrue(
                effects.qualityDeliveryForTests().hasPendingForTests("8.8.8.8", QualityAlertEvent.EVENT_ENDPOINT_DOWN));

        effects.setClock(java.time.Clock.fixed(t0.plusSeconds(15 * 60L), java.time.ZoneOffset.UTC));
        effects.evaluateEndpointDown("8.8.8.8", down);
        assertEquals(3, alerts.qualityEvents().size());
        assertEquals(
                QualityAlertEvent.STATE_FIRING, alerts.qualityEvents().get(2).state());
        effects.evaluateEndpointDown("8.8.8.8", down);
        assertEquals(3, alerts.qualityEvents().size());
    }

    @Test
    void evaluateEndpointDownDispatchesAfterFailStreak() {
        AlertRuleEngine engine = new AlertRuleEngine();
        PollResultEffects effects = new PollResultEffects(engine);
        RecordingAlertDispatcher alerts = new RecordingAlertDispatcher();
        effects.setAlertDispatcher(alerts);
        effects.setEndpointDownRule(new EndpointDownRuleConfig(true, 2, 2, 15));
        effects.setNotifyResolved(false);
        RouteSnapshot up = new RouteSnapshot(
                "8.8.8.8",
                "8.8.8.8",
                List.of(new HopNode(1, "10.0.0.1", 5.0, false), new HopNode(2, "8.8.8.8", 10.0, false)));
        RouteSnapshot down = new RouteSnapshot(
                "8.8.8.8", "8.8.8.8", List.of(new HopNode(1, "10.0.0.1", 5.0, false), new HopNode(2, "*", null, true)));
        effects.evaluateEndpointDown("8.8.8.8", up);
        assertTrue(alerts.qualityEvents().isEmpty());
        effects.evaluateEndpointDown("8.8.8.8", down);
        assertTrue(alerts.qualityEvents().isEmpty());
        effects.evaluateEndpointDown("8.8.8.8", down);
        assertEquals(1, alerts.qualityEvents().size());
        assertEquals(
                QualityAlertEvent.STATE_FIRING, alerts.qualityEvents().get(0).state());
    }

    @Test
    void persistsEndpointDownEvenWhenNotifyResolvedFalse() throws Exception {
        Path dbPath = Files.createTempDirectory("pingui-ed-fx").resolve("ed.db");
        AlertRuleEngine engine = new AlertRuleEngine();
        PollResultEffects effects = new PollResultEffects(engine);
        RecordingAlertDispatcher alerts = new RecordingAlertDispatcher();
        effects.setAlertDispatcher(alerts);
        effects.setEndpointDownRule(new EndpointDownRuleConfig(true, 2, 2, 15));
        effects.setNotifyResolved(false);
        RouteSnapshot down = new RouteSnapshot(
                "8.8.8.8", "8.8.8.8", List.of(new HopNode(1, "10.0.0.1", 5.0, false), new HopNode(2, "*", null, true)));
        RouteSnapshot up = new RouteSnapshot(
                "8.8.8.8",
                "8.8.8.8",
                List.of(new HopNode(1, "10.0.0.1", 5.0, false), new HopNode(2, "8.8.8.8", 10.0, false)));
        try (SessionDatabase database = new SessionDatabase(dbPath)) {
            PersistencePolicyHolder policy = new PersistencePolicyHolder();
            effects.setPersistenceEventWriter(new PersistenceEventWriter(database, policy));
            effects.evaluateEndpointDown("8.8.8.8", down);
            effects.evaluateEndpointDown("8.8.8.8", down);
            assertEquals(1, alerts.qualityEvents().size());
            assertEquals(1, database.countEvents(PersistenceEventType.ENDPOINT_DOWN));
            effects.evaluateEndpointDown("8.8.8.8", up);
            effects.evaluateEndpointDown("8.8.8.8", up);
            assertEquals(1, alerts.qualityEvents().size());
            assertEquals(2, database.countEvents(PersistenceEventType.ENDPOINT_DOWN));
        }
    }

    @Test
    void offerTelemetrySuccessEmitsSamples() throws Exception {
        AlertRuleEngine engine = new AlertRuleEngine();
        PollResultEffects effects = new PollResultEffects(engine);
        RecordingSink sink = new RecordingSink();
        SinkRegistry registry = new SinkRegistry();
        registry.register(sink);
        RouteSnapshot snapshot = new RouteSnapshot(
                "8.8.8.8",
                "8.8.8.8",
                List.of(new HopNode(1, "10.0.0.1", 5.0, false), new HopNode(2, "8.8.8.8", 10.0, false)));
        try (TelemetryBus bus = new TelemetryBus(registry, 64, DropPolicy.DROP_OLDEST, 8, Duration.ofMillis(5))) {
            effects.setTelemetryBus(bus);
            effects.offerTelemetrySuccess("8.8.8.8", HostProbeMode.TRACE, snapshot, 42.0);
            assertTrue(await(() -> sink.samples.stream().anyMatch(s -> "pingui_rtt_ms".equals(s.name())), 2_000));
            assertTrue(sink.samples.stream().anyMatch(s -> "pingui_target_reachable".equals(s.name())));
            assertTrue(sink.samples.stream().anyMatch(s -> "pingui_trace_duration_ms".equals(s.name())));
        }
        registry.close();
    }

    @Test
    void offerTelemetryFailureEmitsProbeErrorEvent() throws Exception {
        AlertRuleEngine engine = new AlertRuleEngine();
        PollResultEffects effects = new PollResultEffects(engine);
        RecordingSink sink = new RecordingSink();
        SinkRegistry registry = new SinkRegistry();
        registry.register(sink);
        try (TelemetryBus bus = new TelemetryBus(registry, 32, DropPolicy.DROP_OLDEST, 4, Duration.ofMillis(5))) {
            effects.setTelemetryBus(bus);
            effects.offerTelemetryFailure("8.8.8.8", "timeout", HostProbeMode.TRACE, 15.0);
            assertTrue(await(
                    () -> sink.events.stream().anyMatch(e -> TelemetryEvent.PROBE_ERROR.equals(e.event())), 2_000));
            assertEquals("timeout", sink.events.get(0).message());
        }
        registry.close();
    }

    @Test
    void offerTelemetryRouteChangeEmitsEvent() throws Exception {
        AlertRuleEngine engine = new AlertRuleEngine();
        PollResultEffects effects = new PollResultEffects(engine);
        RecordingSink sink = new RecordingSink();
        SinkRegistry registry = new SinkRegistry();
        registry.register(sink);
        try (TelemetryBus bus = new TelemetryBus(registry, 32, DropPolicy.DROP_OLDEST, 4, Duration.ofMillis(5))) {
            effects.setTelemetryBus(bus);
            effects.offerTelemetryRouteChange(
                    "8.8.8.8", List.of("10.0.0.1"), List.of("192.168.1.1"), HostProbeMode.TRACE);
            assertTrue(await(
                    () -> sink.events.stream().anyMatch(e -> TelemetryEvent.ROUTE_CHANGE.equals(e.event())), 2_000));
        }
        registry.close();
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
