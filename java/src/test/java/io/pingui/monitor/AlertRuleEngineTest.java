package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.config.EndpointDownRuleConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AlertRuleEngineTest {
    private AlertRuleEngine engine;
    private EndpointDownRuleConfig rule;
    private Instant t0;

    @BeforeEach
    void setUp() {
        engine = new AlertRuleEngine();
        rule = new EndpointDownRuleConfig(true, 3, 2, 15);
        t0 = Instant.parse("2026-07-22T12:00:00Z");
    }

    @Test
    void requiresConsecutiveFailsBeforeFiring() {
        assertTrue(engine.observeEndpointDown("h", true, t0, "p", rule).isEmpty());
        assertTrue(engine.observeEndpointDown("h", true, t0.plusSeconds(1), "p", rule)
                .isEmpty());
        Optional<QualityAlertEvent> firing = engine.observeEndpointDown("h", true, t0.plusSeconds(2), "p", rule);
        assertTrue(firing.isPresent());
        assertEquals(QualityAlertEvent.STATE_FIRING, firing.get().state());
        assertEquals(QualityAlertEvent.EVENT_ENDPOINT_DOWN, firing.get().event());
        assertTrue(firing.get().toJson().contains("\"state\":\"firing\""));
    }

    @Test
    void disabledRuleNeverFires() {
        EndpointDownRuleConfig off = EndpointDownRuleConfig.disabled();
        for (int i = 0; i < 5; i++) {
            assertTrue(engine.observeEndpointDown("h", true, t0.plusSeconds(i), "p", off)
                    .isEmpty());
        }
    }

    @Test
    void noRepeatWhileStillDownThenResolvedOptional() {
        fireOnce();
        assertTrue(engine.observeEndpointDown("h", true, t0.plusSeconds(10), "p", rule)
                .isEmpty());
        assertTrue(engine.observeEndpointDown("h", false, t0.plusSeconds(11), "p", rule)
                .isEmpty());
        Optional<QualityAlertEvent> resolved = engine.observeEndpointDown("h", false, t0.plusSeconds(12), "p", rule);
        assertTrue(resolved.isPresent());
        assertEquals(QualityAlertEvent.STATE_RESOLVED, resolved.get().state());
    }

    @Test
    void resolvedLifecycleEdgeAlwaysEmittedForPersistence() {
        fireOnce();
        assertTrue(engine.observeEndpointDown("h", false, t0.plusSeconds(11), "p", rule)
                .isEmpty());
        Optional<QualityAlertEvent> resolved = engine.observeEndpointDown("h", false, t0.plusSeconds(12), "p", rule);
        assertTrue(resolved.isPresent());
        assertEquals(QualityAlertEvent.STATE_RESOLVED, resolved.get().state());
    }

    @Test
    void cooldownBlocksImmediateRefireAfterRecovery() {
        fireOnce();
        // recover
        engine.observeEndpointDown("h", false, t0.plusSeconds(11), "p", rule);
        engine.observeEndpointDown("h", false, t0.plusSeconds(12), "p", rule);
        // down again within cooldown
        assertTrue(engine.observeEndpointDown("h", true, t0.plusSeconds(13), "p", rule)
                .isEmpty());
        assertTrue(engine.observeEndpointDown("h", true, t0.plusSeconds(14), "p", rule)
                .isEmpty());
        Optional<QualityAlertEvent> blocked = engine.observeEndpointDown("h", true, t0.plusSeconds(15), "p", rule);
        assertTrue(blocked.isEmpty());
        // after cooldown
        Instant later = t0.plusSeconds(15).plusSeconds(15 * 60L);
        // already in FIRING from blocked transition — need recover then fail again after cooldown
        engine.observeEndpointDown("h", false, later, "p", rule);
        engine.observeEndpointDown("h", false, later.plusSeconds(1), "p", rule);
        assertTrue(engine.observeEndpointDown("h", true, later.plusSeconds(2), "p", rule)
                .isEmpty());
        assertTrue(engine.observeEndpointDown("h", true, later.plusSeconds(3), "p", rule)
                .isEmpty());
        Optional<QualityAlertEvent> again = engine.observeEndpointDown("h", true, later.plusSeconds(4), "p", rule);
        assertTrue(again.isPresent());
        assertEquals(QualityAlertEvent.STATE_FIRING, again.get().state());
    }

    @Test
    void presetsMatchAdr() {
        assertEquals(5, EndpointDownRuleConfig.calm(true).failAfter());
        assertEquals(3, EndpointDownRuleConfig.balanced(true).failAfter());
        assertEquals(2, EndpointDownRuleConfig.sensitive(true).failAfter());
        assertFalse(EndpointDownRuleConfig.fromPreset("balanced", false).enabled());
    }

    @Test
    void sessionStatsTrackFireCountMaxDurationAndAck() {
        assertTrue(engine.problemSummary("h").isEmpty());

        fireOnce();
        HostProblemSummary firing =
                engine.problemSummary("h", t0.plusSeconds(2)).orElseThrow();
        assertTrue(firing.unread());
        assertTrue(firing.showBadge());
        assertEquals(1, firing.fireCount());
        assertEquals(HostProblemSummary.STATE_FIRING, firing.lastState());
        assertEquals(t0.plusSeconds(2), firing.lastStartedAt());
        assertEquals(Duration.ZERO, firing.maxDuration());

        // still firing — open duration extends max
        HostProblemSummary open =
                engine.problemSummary("h", t0.plusSeconds(2).plusSeconds(90)).orElseThrow();
        assertEquals(Duration.ofSeconds(90), open.maxDuration());

        // clear closes incident; RESOLVED edge always emitted (channel gating is MonitorService)
        assertTrue(engine.observeEndpointDown("h", false, t0.plusSeconds(100), "p", rule)
                .isEmpty());
        Optional<QualityAlertEvent> resolvedEdge =
                engine.observeEndpointDown("h", false, t0.plusSeconds(101), "p", rule);
        assertTrue(resolvedEdge.isPresent());
        assertEquals(QualityAlertEvent.STATE_RESOLVED, resolvedEdge.get().state());
        HostProblemSummary resolved =
                engine.problemSummary("h", t0.plusSeconds(101)).orElseThrow();
        assertTrue(resolved.unread());
        assertEquals(HostProblemSummary.STATE_RESOLVED, resolved.lastState());
        assertEquals(t0.plusSeconds(101), resolved.lastResolvedAt());
        assertEquals(Duration.ofSeconds(99), resolved.maxDuration());

        assertTrue(engine.ack("h"));
        HostProblemSummary acked = engine.problemSummary("h").orElseThrow();
        assertFalse(acked.unread());
        assertFalse(acked.showBadge());
        assertEquals(HostProblemSummary.STATE_OK, acked.lastState());
        assertEquals(1, acked.fireCount());
        assertEquals(Duration.ofSeconds(99), acked.maxDuration());

        // second FIRING after cooldown window
        Instant later = t0.plusSeconds(101).plusSeconds(15 * 60L);
        engine.observeEndpointDown("h", true, later, "p", rule);
        engine.observeEndpointDown("h", true, later.plusSeconds(1), "p", rule);
        Optional<QualityAlertEvent> again = engine.observeEndpointDown("h", true, later.plusSeconds(2), "p", rule);
        assertTrue(again.isPresent());
        HostProblemSummary second =
                engine.problemSummary("h", later.plusSeconds(2)).orElseThrow();
        assertTrue(second.unread());
        assertEquals(2, second.fireCount());
        assertEquals(HostProblemSummary.STATE_FIRING, second.lastState());
    }

    @Test
    void ackWhileFiringKeepsStateFiringButHidesBadge() {
        fireOnce();
        assertTrue(engine.ack("h"));
        HostProblemSummary acked = engine.problemSummary("h", t0.plusSeconds(2)).orElseThrow();
        assertFalse(acked.unread());
        assertEquals(HostProblemSummary.STATE_FIRING, acked.lastState());
    }

    private void fireOnce() {
        engine.observeEndpointDown("h", true, t0, "p", rule);
        engine.observeEndpointDown("h", true, t0.plusSeconds(1), "p", rule);
        Optional<QualityAlertEvent> firing = engine.observeEndpointDown("h", true, t0.plusSeconds(2), "p", rule);
        assertTrue(firing.isPresent());
    }
}
