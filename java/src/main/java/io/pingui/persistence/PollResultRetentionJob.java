package io.pingui.persistence;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Bounded retention for {@code poll_result} with rollup tiers (P30-005 / P32-004):
 *
 * <ul>
 *   <li>0–7 days: keep raw {@code poll_result}
 *   <li>8–90 days: 5-minute {@code metric_rollup} (bucket 300s)
 *   <li>&gt;90 days: hourly {@code metric_rollup} (bucket 3600s); drop 5-minute buckets
 * </ul>
 *
 * <p>Upserts and deletes run in <strong>one</strong> SQLite transaction (crash-safe). Incidents and
 * deduped routes are not purged.
 */
public final class PollResultRetentionJob {
    public static final int RAW_RETENTION_DAYS = 7;
    public static final int FIVE_MIN_RETENTION_DAYS = 90;
    public static final int BUCKET_5_MIN_SECONDS = 300;
    public static final int BUCKET_1_HOUR_SECONDS = 3600;

    /** Test-only: thrown after upserts and before deletes to prove full rollback. */
    static volatile Runnable afterUpsertBeforeDeleteHook;

    private PollResultRetentionJob() {}

    public static Result run(SessionDatabase database, Clock clock) {
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(clock, "clock");
        Instant now = clock.instant();
        Instant rawKeepFrom = now.minus(RAW_RETENTION_DAYS, ChronoUnit.DAYS);
        Instant fiveMinKeepFrom = now.minus(FIVE_MIN_RETENTION_DAYS, ChronoUnit.DAYS);

        return database.inTransaction(() -> {
            List<PollResultRecord> oldPolls = database.listPollResultsBefore(rawKeepFrom);
            Map<RollupKey, BucketAcc> fiveMin = new HashMap<>();
            Map<RollupKey, BucketAcc> hourly = new HashMap<>();
            for (PollResultRecord poll : oldPolls) {
                if (poll.observedAt().isBefore(fiveMinKeepFrom)) {
                    accumulate(hourly, poll, BUCKET_1_HOUR_SECONDS);
                } else {
                    accumulate(fiveMin, poll, BUCKET_5_MIN_SECONDS);
                }
            }
            int rolledTo5Min = upsertAll(database, fiveMin, BUCKET_5_MIN_SECONDS);
            int rolledToHour = upsertAll(database, hourly, BUCKET_1_HOUR_SECONDS);

            List<MetricRollupRecord> oldFiveMin =
                    database.listMetricRollupsBefore(BUCKET_5_MIN_SECONDS, fiveMinKeepFrom);
            Map<RollupKey, BucketAcc> promoted = new HashMap<>();
            for (MetricRollupRecord row : oldFiveMin) {
                Instant hourStart = bucketStart(row.bucketStart(), BUCKET_1_HOUR_SECONDS);
                RollupKey key = new RollupKey(row.hostId(), hourStart);
                promoted.computeIfAbsent(key, ignored -> new BucketAcc()).mergeRollup(row);
            }
            int promotedHour = upsertAll(database, promoted, BUCKET_1_HOUR_SECONDS);

            Runnable hook = afterUpsertBeforeDeleteHook;
            if (hook != null) {
                hook.run();
            }

            int deletedRaw = database.deletePollResultsBefore(rawKeepFrom);
            int deletedFiveMin = database.deleteMetricRollupsBefore(BUCKET_5_MIN_SECONDS, fiveMinKeepFrom);
            return new Result(rolledTo5Min, rolledToHour + promotedHour, deletedRaw, deletedFiveMin);
        });
    }

    static Instant bucketStart(Instant instant, int bucketSeconds) {
        long epoch = instant.getEpochSecond();
        long aligned = epoch - Math.floorMod(epoch, bucketSeconds);
        return Instant.ofEpochSecond(aligned);
    }

    private static void accumulate(Map<RollupKey, BucketAcc> target, PollResultRecord poll, int bucketSeconds) {
        Instant start = bucketStart(poll.observedAt(), bucketSeconds);
        RollupKey key = new RollupKey(poll.hostId(), start);
        target.computeIfAbsent(key, ignored -> new BucketAcc()).addPoll(poll);
    }

    private static int upsertAll(SessionDatabase database, Map<RollupKey, BucketAcc> buckets, int bucketSeconds) {
        int count = 0;
        for (Map.Entry<RollupKey, BucketAcc> entry : buckets.entrySet()) {
            BucketAcc acc = entry.getValue();
            if (acc.sampleCount < 1) {
                continue;
            }
            database.upsertMetricRollup(
                    entry.getKey().hostId(),
                    entry.getKey().bucketStart(),
                    bucketSeconds,
                    acc.sampleCount,
                    acc.reachableSamples,
                    acc.reachableCount,
                    acc.rttSamples,
                    acc.rttSum,
                    acc.rttMin,
                    acc.rttMax,
                    acc.lossSamples,
                    acc.lossSum);
            count++;
        }
        return count;
    }

    private record RollupKey(long hostId, Instant bucketStart) {}

    private static final class BucketAcc {
        private int sampleCount;
        private int reachableSamples;
        private int reachableCount;
        private int rttSamples;
        private double rttSum;
        private Double rttMin;
        private Double rttMax;
        private int lossSamples;
        private double lossSum;

        void addPoll(PollResultRecord poll) {
            sampleCount++;
            if (poll.reachable() != null) {
                reachableSamples++;
                if (poll.reachable()) {
                    reachableCount++;
                }
            }
            if (poll.terminalRttMs() != null) {
                double rtt = poll.terminalRttMs();
                rttMin = rttMin == null ? rtt : Math.min(rttMin, rtt);
                rttMax = rttMax == null ? rtt : Math.max(rttMax, rtt);
                rttSum += rtt;
                rttSamples++;
            }
            if (poll.lossPercent() != null) {
                lossSum += poll.lossPercent();
                lossSamples++;
            }
        }

        void mergeRollup(MetricRollupRecord row) {
            sampleCount += row.sampleCount();
            reachableSamples += row.reachableSamples();
            reachableCount += row.reachableCount();
            rttSamples += row.rttSamples();
            rttSum += row.rttSum();
            lossSamples += row.lossSamples();
            lossSum += row.lossSum();
            if (row.rttMin() != null) {
                rttMin = rttMin == null ? row.rttMin() : Math.min(rttMin, row.rttMin());
            }
            if (row.rttMax() != null) {
                rttMax = rttMax == null ? row.rttMax() : Math.max(rttMax, row.rttMax());
            }
        }
    }

    /** Counts of rollup upserts and deletions. */
    public record Result(
            int rolledFiveMinBuckets, int rolledHourlyBuckets, int deletedRawPolls, int deletedFiveMinRollups) {}
}
