package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertTrue(engine.observeEndpointDown("h", true, t0, "p", rule, false).isEmpty());
        assertTrue(engine.observeEndpointDown("h", true, t0.plusSeconds(1), "p", rule, false)
                .isEmpty());
        Optional<QualityAlertEvent> firing = engine.observeEndpointDown("h", true, t0.plusSeconds(2), "p", rule, false);
        assertTrue(firing.isPresent());
        assertEquals(QualityAlertEvent.STATE_FIRING, firing.get().state());
        assertEquals(QualityAlertEvent.EVENT_ENDPOINT_DOWN, firing.get().event());
        assertTrue(firing.get().toJson().contains("\"state\":\"firing\""));
    }

    @Test
    void disabledRuleNeverFires() {
        EndpointDownRuleConfig off = EndpointDownRuleConfig.disabled();
        for (int i = 0; i < 5; i++) {
            assertTrue(engine.observeEndpointDown("h", true, t0.plusSeconds(i), "p", off, true)
                    .isEmpty());
        }
    }

    @Test
    void noRepeatWhileStillDownThenResolvedOptional() {
        fireOnce();
        assertTrue(engine.observeEndpointDown("h", true, t0.plusSeconds(10), "p", rule, true)
                .isEmpty());
        assertTrue(engine.observeEndpointDown("h", false, t0.plusSeconds(11), "p", rule, true)
                .isEmpty());
        Optional<QualityAlertEvent> resolved =
                engine.observeEndpointDown("h", false, t0.plusSeconds(12), "p", rule, true);
        assertTrue(resolved.isPresent());
        assertEquals(QualityAlertEvent.STATE_RESOLVED, resolved.get().state());
    }

    @Test
    void resolvedSuppressedWhenNotifyResolvedFalse() {
        fireOnce();
        assertTrue(engine.observeEndpointDown("h", false, t0.plusSeconds(11), "p", rule, false)
                .isEmpty());
        assertTrue(engine.observeEndpointDown("h", false, t0.plusSeconds(12), "p", rule, false)
                .isEmpty());
    }

    @Test
    void cooldownBlocksImmediateRefireAfterRecovery() {
        fireOnce();
        // recover
        engine.observeEndpointDown("h", false, t0.plusSeconds(11), "p", rule, false);
        engine.observeEndpointDown("h", false, t0.plusSeconds(12), "p", rule, false);
        // down again within cooldown
        assertTrue(engine.observeEndpointDown("h", true, t0.plusSeconds(13), "p", rule, false)
                .isEmpty());
        assertTrue(engine.observeEndpointDown("h", true, t0.plusSeconds(14), "p", rule, false)
                .isEmpty());
        Optional<QualityAlertEvent> blocked =
                engine.observeEndpointDown("h", true, t0.plusSeconds(15), "p", rule, false);
        assertTrue(blocked.isEmpty());
        // after cooldown
        Instant later = t0.plusSeconds(15).plusSeconds(15 * 60L);
        // already in FIRING from blocked transition — need recover then fail again after cooldown
        engine.observeEndpointDown("h", false, later, "p", rule, false);
        engine.observeEndpointDown("h", false, later.plusSeconds(1), "p", rule, false);
        assertTrue(engine.observeEndpointDown("h", true, later.plusSeconds(2), "p", rule, false)
                .isEmpty());
        assertTrue(engine.observeEndpointDown("h", true, later.plusSeconds(3), "p", rule, false)
                .isEmpty());
        Optional<QualityAlertEvent> again =
                engine.observeEndpointDown("h", true, later.plusSeconds(4), "p", rule, false);
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

    private void fireOnce() {
        engine.observeEndpointDown("h", true, t0, "p", rule, false);
        engine.observeEndpointDown("h", true, t0.plusSeconds(1), "p", rule, false);
        Optional<QualityAlertEvent> firing = engine.observeEndpointDown("h", true, t0.plusSeconds(2), "p", rule, false);
        assertTrue(firing.isPresent());
    }
}
