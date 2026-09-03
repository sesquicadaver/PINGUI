package io.pingui.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.model.Models.HostSessionData;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PollResultRetentionJobTest {
    @TempDir
    Path tempDir;

    @Test
    void rollsRawIntoFiveMinAndDeletesOlderThanSevenDays() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-03T12:00:00Z"), ZoneOffset.UTC);
        Path dbPath = tempDir.resolve("ret-5m.db");
        try (SessionDatabase db = new SessionDatabase(dbPath)) {
            seedHost(db, "8.8.8.8");
            Instant mid = Instant.parse("2026-08-20T10:02:30Z"); // ~14d old → 5m rollup
            Instant recent = Instant.parse("2026-09-02T12:00:00Z"); // within 7d → keep raw
            db.insertPollResult("8.8.8.8", mid, "ping_only", true, 10.0, null, 0.0, 40.0, null, null);
            db.insertPollResult("8.8.8.8", mid.plusSeconds(60), "ping_only", true, 20.0, null, 1.0, 41.0, null, null);
            db.insertPollResult("8.8.8.8", recent, "ping_only", true, 5.0, null, 0.0, 30.0, null, null);

            PollResultRetentionJob.Result result = PollResultRetentionJob.run(db, clock);
            assertEquals(1, result.rolledFiveMinBuckets());
            assertEquals(0, result.rolledHourlyBuckets());
            assertEquals(2, result.deletedRawPolls());
            assertEquals(0, result.deletedFiveMinRollups());
            assertEquals(1, db.countPollResults());
            assertEquals(1, db.countMetricRollups());

            MetricRollupRecord rollup = db.listMetricRollups("8.8.8.8", PollResultRetentionJob.BUCKET_5_MIN_SECONDS, 5)
                    .get(0);
            assertEquals(2, rollup.samples());
            assertEquals(10.0, rollup.rttMin());
            assertEquals(20.0, rollup.rttMax());
            assertEquals(15.0, rollup.rttAvg());
            assertEquals(0.5, rollup.lossAvg());
            assertEquals(1.0, rollup.uptimeRatio());
            assertEquals(Instant.parse("2026-08-20T10:00:00Z"), rollup.bucketStart());
            assertEquals(recent, db.listPollResults("8.8.8.8", 5).get(0).observedAt());
        }
    }

    @Test
    void rollsVeryOldRawIntoHourlyAndPromotesStaleFiveMin() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-03T12:00:00Z"), ZoneOffset.UTC);
        Path dbPath = tempDir.resolve("ret-1h.db");
        try (SessionDatabase db = new SessionDatabase(dbPath)) {
            seedHost(db, "1.1.1.1");
            long hostId = db.hostId("1.1.1.1").orElseThrow();
            Instant ancient = Instant.parse("2026-05-01T03:10:00Z"); // >90d → hourly from raw
            db.insertPollResult("1.1.1.1", ancient, "trace", true, 30.0, null, 2.0, 100.0, null, null);

            Instant staleFiveMin = Instant.parse("2026-05-15T04:05:00Z");
            db.upsertMetricRollup(
                    hostId, staleFiveMin, PollResultRetentionJob.BUCKET_5_MIN_SECONDS, 3, 1.0, 8.0, 9.0, 10.0, 0.0);

            PollResultRetentionJob.Result result = PollResultRetentionJob.run(db, clock);
            assertEquals(0, result.rolledFiveMinBuckets());
            assertTrue(result.rolledHourlyBuckets() >= 1);
            assertEquals(1, result.deletedRawPolls());
            assertEquals(1, result.deletedFiveMinRollups());
            assertEquals(0, db.countPollResults());
            assertEquals(
                    0,
                    db.listMetricRollups("1.1.1.1", PollResultRetentionJob.BUCKET_5_MIN_SECONDS, 10)
                            .size());

            List<MetricRollupRecord> hourly =
                    db.listMetricRollups("1.1.1.1", PollResultRetentionJob.BUCKET_1_HOUR_SECONDS, 10);
            assertTrue(hourly.size() >= 1);
            assertTrue(hourly.stream().anyMatch(r -> r.bucketStart().equals(Instant.parse("2026-05-01T03:00:00Z"))));
            assertTrue(hourly.stream().anyMatch(r -> r.bucketStart().equals(Instant.parse("2026-05-15T04:00:00Z"))));
        }
    }

    @Test
    void doesNotPurgeIncidentsOrRoutes() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-03T12:00:00Z"), ZoneOffset.UTC);
        Path dbPath = tempDir.resolve("ret-keep.db");
        try (SessionDatabase db = new SessionDatabase(dbPath)) {
            seedHost(db, "9.9.9.9");
            db.openOrRefreshIncident(
                    "9.9.9.9",
                    IncidentRecord.KIND_ENDPOINT_DOWN,
                    IncidentRecord.SEVERITY_CRITICAL,
                    Instant.parse("2026-01-01T00:00:00Z"),
                    null,
                    "{}");
            db.upsertRoute(
                    "9.9.9.9", "1.1.1.1|2.2.2.2", "[\"1.1.1.1\",\"2.2.2.2\"]", Instant.parse("2026-01-02T00:00:00Z"));
            db.insertPollResult(
                    "9.9.9.9",
                    Instant.parse("2026-01-03T00:00:00Z"),
                    "ping_only",
                    false,
                    null,
                    null,
                    null,
                    10.0,
                    null,
                    "timeout");

            PollResultRetentionJob.run(db, clock);
            assertEquals(1, db.listIncidents("9.9.9.9", 10).size());
            assertEquals(1, db.countRoutes());
            assertEquals(0, db.countPollResults());
        }
    }

    @Test
    void bucketStartAlignsToBucketSize() {
        assertEquals(
                Instant.parse("2026-08-20T10:00:00Z"),
                PollResultRetentionJob.bucketStart(Instant.parse("2026-08-20T10:02:30Z"), 300));
        assertEquals(
                Instant.parse("2026-05-01T03:00:00Z"),
                PollResultRetentionJob.bucketStart(Instant.parse("2026-05-01T03:10:00Z"), 3600));
    }

    private static void seedHost(SessionDatabase db, String address) {
        HostSessionData data = new HostSessionData();
        data.setEnabled(true);
        db.save(address, data);
    }
}
