package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.persistence.PersistenceEventRecord;
import io.pingui.persistence.PersistenceEventType;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link IncidentTimelineBuilder} (P29-002). */
class IncidentTimelineBuilderTest {
    private static final Instant T0 = Instant.parse("2026-09-03T10:00:00Z");

    @Test
    void pairsDownFiringAndResolvedWithDuration() {
        List<PersistenceEventRecord> rows = List.of(
                quality(1, PersistenceEventType.ENDPOINT_DOWN, "firing", T0),
                quality(2, PersistenceEventType.ENDPOINT_DOWN, "resolved", T0.plusSeconds(90)),
                route(3, T0.plusSeconds(30)));

        IncidentTimeline timeline =
                IncidentTimelineBuilder.build("8.8.8.8", rows, Optional.empty(), T0.plusSeconds(120));

        assertEquals(3, timeline.entries().size());
        IncidentTimelineEntry resolved = timeline.entries().stream()
                .filter(e -> e.kind() == IncidentTimelineKind.ENDPOINT_DOWN
                        && HostProblemSummary.STATE_RESOLVED.equals(e.state()))
                .findFirst()
                .orElseThrow();
        assertEquals(Duration.ofSeconds(90), resolved.duration());
        assertEquals(Duration.ofSeconds(90), timeline.totalIncidentDuration());
        assertTrue(timeline.entries().stream().anyMatch(e -> e.kind() == IncidentTimelineKind.ROUTE_CHANGE));
        assertTrue(timeline.entries().stream().anyMatch(e -> e.canReplayRoute()));
    }

    @Test
    void attachesOpenDurationFromLiveSummary() {
        List<PersistenceEventRecord> rows = List.of(quality(1, PersistenceEventType.ENDPOINT_DOWN, "firing", T0));
        HostProblemSummary live = new HostProblemSummary(
                "8.8.8.8",
                QualityAlertEvent.EVENT_ENDPOINT_DOWN,
                true,
                1,
                Duration.ofSeconds(45),
                T0,
                null,
                HostProblemSummary.STATE_FIRING,
                HostProblemSummary.DESCRIPTION_ENDPOINT_DOWN);

        IncidentTimeline timeline =
                IncidentTimelineBuilder.build("8.8.8.8", rows, Optional.of(live), T0.plusSeconds(45));

        assertEquals(1, timeline.entries().size());
        assertEquals(Duration.ofSeconds(45), timeline.entries().get(0).duration());
        assertEquals(Duration.ofSeconds(45), timeline.totalIncidentDuration());
    }

    @Test
    void includesAckAndKeepsNewestFirst() {
        List<PersistenceEventRecord> rows = List.of(
                new PersistenceEventRecord(
                        1,
                        PersistenceEventType.PROBLEM_ACK,
                        "8.8.8.8",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        T0.plusSeconds(10)),
                quality(2, PersistenceEventType.LATENCY_HIGH, "firing", T0));

        IncidentTimeline timeline =
                IncidentTimelineBuilder.build("8.8.8.8", rows, Optional.empty(), T0.plusSeconds(20));

        assertEquals(IncidentTimelineKind.PROBLEM_ACK, timeline.entries().get(0).kind());
        assertEquals(
                IncidentTimelineKind.LATENCY_HIGH, timeline.entries().get(1).kind());
    }

    private static PersistenceEventRecord quality(long id, PersistenceEventType type, String state, Instant when) {
        return new PersistenceEventRecord(id, type, "8.8.8.8", "default", state, null, null, null, "{}", when);
    }

    private static PersistenceEventRecord route(long id, Instant when) {
        return new PersistenceEventRecord(
                id,
                PersistenceEventType.ROUTE_CHANGE,
                "8.8.8.8",
                "default",
                null,
                null,
                "[\"10.0.0.1\"]",
                "[\"10.0.0.1\",\"8.8.8.8\"]",
                null,
                when);
    }
}
