package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SeverityClassifierTest {
    @Test
    void criticalOnlyForEndpointDown() {
        assertEquals(Severity.CRITICAL, SeverityClassifier.forHost(true, EndpointState.DOWN, RouteState.STABLE, null));
        assertEquals(Severity.NOTICE, SeverityClassifier.forHost(true, EndpointState.UP, RouteState.CHANGED, null));
        assertEquals(Severity.MUTED, SeverityClassifier.forHost(false, EndpointState.DOWN, RouteState.CHANGED, null));
    }

    @Test
    void warningForDegradedAndLatencyBadge() {
        assertEquals(
                Severity.WARNING, SeverityClassifier.forHost(true, EndpointState.DEGRADED, RouteState.STABLE, null));
        HostProblemSummary latency = new HostProblemSummary(
                "8.8.8.8",
                QualityAlertEvent.EVENT_LATENCY_HIGH,
                true,
                1,
                Duration.ZERO,
                Instant.now(),
                null,
                HostProblemSummary.STATE_FIRING,
                HostProblemSummary.DESCRIPTION_LATENCY_HIGH);
        assertEquals(Severity.WARNING, SeverityClassifier.forHost(true, EndpointState.UP, RouteState.STABLE, latency));
    }

    @Test
    void unreadEndpointDownBadgeIsCritical() {
        HostProblemSummary down = new HostProblemSummary(
                "8.8.8.8",
                QualityAlertEvent.EVENT_ENDPOINT_DOWN,
                true,
                1,
                Duration.ZERO,
                Instant.now(),
                null,
                HostProblemSummary.STATE_FIRING,
                HostProblemSummary.DESCRIPTION_ENDPOINT_DOWN);
        assertEquals(Severity.CRITICAL, SeverityClassifier.forHost(true, EndpointState.UP, RouteState.STABLE, down));
    }

    @Test
    void timelineAndAlertMappings() {
        assertEquals(Severity.CRITICAL, SeverityClassifier.forTimeline(IncidentTimelineKind.ENDPOINT_DOWN, "firing"));
        assertEquals(Severity.INFO, SeverityClassifier.forTimeline(IncidentTimelineKind.ENDPOINT_DOWN, "resolved"));
        assertEquals(Severity.NOTICE, SeverityClassifier.forTimeline(IncidentTimelineKind.ROUTE_CHANGE, ""));
        assertEquals(Severity.CRITICAL, SeverityClassifier.forAlertEventType("endpoint_down"));
        assertEquals(Severity.WARNING, SeverityClassifier.forAlertEventType("latency_high"));
        assertEquals(Severity.NOTICE, SeverityClassifier.forAlertEventType("route_change"));
    }

    @Test
    void sortRankOrdersProblemsFirst() {
        assertTrue(Severity.CRITICAL.sortRank() < Severity.WARNING.sortRank());
        assertTrue(Severity.WARNING.sortRank() < Severity.NOTICE.sortRank());
        assertTrue(Severity.NOTICE.sortRank() < Severity.INFO.sortRank());
        assertTrue(Severity.INFO.sortRank() < Severity.MUTED.sortRank());
    }
}
