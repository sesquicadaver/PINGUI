package io.pingui.monitor;

import io.pingui.persistence.PersistenceEventRecord;
import io.pingui.persistence.PersistenceEventType;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds a compact per-host incident timeline from SQLite events + optional live problem summary
 * (P29-002). No new probes; DNS rows appear only when {@link PersistenceEventType} DNS events exist
 * (P29-004).
 */
public final class IncidentTimelineBuilder {
    private IncidentTimelineBuilder() {}

    /**
     * @param host target host
     * @param records persistence rows for the host (any order)
     * @param live optional in-memory problem summary for open-incident duration
     * @param now clock for open-incident duration
     */
    public static IncidentTimeline build(
            String host, List<PersistenceEventRecord> records, Optional<HostProblemSummary> live, Instant now) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host required");
        }
        Instant at = now != null ? now : Instant.now();
        List<PersistenceEventRecord> rows = records == null ? List.of() : records;

        List<PersistenceEventRecord> chronological = rows.stream()
                .sorted(Comparator.comparing(PersistenceEventRecord::observedAt)
                        .thenComparingLong(PersistenceEventRecord::id))
                .toList();

        Map<String, Instant> openStarts = new HashMap<>();
        List<IncidentTimelineEntry> built = new ArrayList<>();
        Duration totalClosed = Duration.ZERO;

        for (PersistenceEventRecord row : chronological) {
            Mapped mapped = mapRow(row, openStarts);
            if (mapped == null) {
                continue;
            }
            if (mapped.closedDuration != null && !mapped.closedDuration.isZero()) {
                totalClosed = totalClosed.plus(mapped.closedDuration);
            }
            built.add(mapped.entry);
        }

        Duration openExtra = Duration.ZERO;
        if (live.isPresent()) {
            HostProblemSummary summary = live.get();
            if (HostProblemSummary.STATE_FIRING.equals(summary.lastState()) && summary.lastStartedAt() != null) {
                Duration open = Duration.between(summary.lastStartedAt(), at);
                if (!open.isNegative()) {
                    openExtra = open;
                    String ruleKey = summary.rule();
                    boolean alreadyOpenRow = chronological.stream().anyMatch(r -> isOpenQuality(r, ruleKey));
                    if (!alreadyOpenRow) {
                        built.add(new IncidentTimelineEntry(
                                -1L,
                                kindForRule(ruleKey),
                                HostProblemSummary.STATE_FIRING,
                                summary.lastStartedAt(),
                                open,
                                summary.description(),
                                Optional.empty()));
                    } else {
                        // Attach live duration onto the latest matching open firing entry.
                        for (int i = built.size() - 1; i >= 0; i--) {
                            IncidentTimelineEntry entry = built.get(i);
                            if (entry.kind() == kindForRule(ruleKey)
                                    && HostProblemSummary.STATE_FIRING.equals(entry.state())
                                    && entry.duration().isZero()) {
                                built.set(
                                        i,
                                        new IncidentTimelineEntry(
                                                entry.id(),
                                                entry.kind(),
                                                entry.state(),
                                                entry.observedAt(),
                                                open,
                                                entry.detail(),
                                                entry.routeReplay()));
                                break;
                            }
                        }
                    }
                }
            }
        }

        built.sort(Comparator.comparing(IncidentTimelineEntry::observedAt)
                .thenComparingLong(IncidentTimelineEntry::id)
                .reversed());
        Duration total = totalClosed.plus(openExtra);
        return new IncidentTimeline(host, built, total);
    }

    private static boolean isOpenQuality(PersistenceEventRecord row, String rule) {
        if (!QualityAlertEvent.STATE_FIRING.equals(row.state())) {
            return false;
        }
        if (QualityAlertEvent.EVENT_LATENCY_HIGH.equals(rule)) {
            return row.eventType() == PersistenceEventType.LATENCY_HIGH;
        }
        return row.eventType() == PersistenceEventType.ENDPOINT_DOWN;
    }

    private static IncidentTimelineKind kindForRule(String rule) {
        if (QualityAlertEvent.EVENT_LATENCY_HIGH.equals(rule)) {
            return IncidentTimelineKind.LATENCY_HIGH;
        }
        return IncidentTimelineKind.ENDPOINT_DOWN;
    }

    private static Mapped mapRow(PersistenceEventRecord row, Map<String, Instant> openStarts) {
        return switch (row.eventType()) {
            case ROUTE_CHANGE -> mapRoute(row);
            case ENDPOINT_DOWN -> mapQuality(row, IncidentTimelineKind.ENDPOINT_DOWN, openStarts);
            case LATENCY_HIGH -> mapQuality(row, IncidentTimelineKind.LATENCY_HIGH, openStarts);
            case PROBLEM_ACK -> new Mapped(
                    new IncidentTimelineEntry(
                            row.id(),
                            IncidentTimelineKind.PROBLEM_ACK,
                            "",
                            row.observedAt(),
                            Duration.ZERO,
                            "",
                            Optional.empty()),
                    null);
            case PROBE_ERROR -> new Mapped(
                    new IncidentTimelineEntry(
                            row.id(),
                            IncidentTimelineKind.PROBE_ERROR,
                            "",
                            row.observedAt(),
                            Duration.ZERO,
                            row.message() == null ? "" : row.message(),
                            Optional.empty()),
                    null);
            case DNS_CHANGE -> new Mapped(
                    new IncidentTimelineEntry(
                            row.id(),
                            IncidentTimelineKind.DNS_CHANGE,
                            row.state() == null ? "" : row.state(),
                            row.observedAt(),
                            Duration.ZERO,
                            row.message() == null ? "" : row.message(),
                            Optional.empty()),
                    null);
        };
    }

    private static Mapped mapRoute(PersistenceEventRecord row) {
        try {
            RouteChangeEvent event = RouteChangeEvent.fromJson(row.payloadJson());
            String detail = event.newIps().isEmpty() ? "—" : String.join(" → ", event.newIps());
            return new Mapped(
                    new IncidentTimelineEntry(
                            row.id(),
                            IncidentTimelineKind.ROUTE_CHANGE,
                            "",
                            row.observedAt(),
                            Duration.ZERO,
                            detail,
                            Optional.of(event)),
                    null);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static Mapped mapQuality(
            PersistenceEventRecord row, IncidentTimelineKind kind, Map<String, Instant> openStarts) {
        String state = row.state() == null ? "" : row.state();
        String key = kind.name();
        Duration closed = null;
        Duration shown = Duration.ZERO;
        if (QualityAlertEvent.STATE_FIRING.equals(state)) {
            openStarts.put(key, row.observedAt());
        } else if (QualityAlertEvent.STATE_RESOLVED.equals(state)) {
            Instant start = openStarts.remove(key);
            if (start != null && !row.observedAt().isBefore(start)) {
                closed = Duration.between(start, row.observedAt());
                shown = closed;
            }
        }
        return new Mapped(
                new IncidentTimelineEntry(row.id(), kind, state, row.observedAt(), shown, "", Optional.empty()),
                closed);
    }

    private record Mapped(IncidentTimelineEntry entry, Duration closedDuration) {}
}
