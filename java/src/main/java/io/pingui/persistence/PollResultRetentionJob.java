package io.pingui.persistence;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Bounded retention for {@code poll_result} with rollup tiers (P30-005 / P32-004 / P33-007):
 *
 * <ul>
 *   <li>0–7 days: keep raw {@code poll_result}
 *   <li>8–90 days: 5-minute {@code metric_rollup} (bucket 300s)
 *   <li>&gt;90 days: hourly {@code metric_rollup} (bucket 3600s); drop 5-minute buckets
 * </ul>
 *
 * <p>Work runs in <strong>chunked</strong> transactions (default {@link #DEFAULT_CHUNK_SIZE} rows):
 * each chunk upserts then deletes only the processed keys so large databases do not hold one giant
 * lock. Chunks are crash-safe and idempotent when re-run. Incidents and deduped routes are not
 * purged.
 */
public final class PollResultRetentionJob {
    public static final int RAW_RETENTION_DAYS = 7;
    public static final int FIVE_MIN_RETENTION_DAYS = 90;
    public static final int BUCKET_5_MIN_SECONDS = 300;
    public static final int BUCKET_1_HOUR_SECONDS = 3600;

    /** Default rows processed per transaction (P33-007). */
    public static final int DEFAULT_CHUNK_SIZE = 500;

    /** Test-only: thrown after upserts and before deletes to prove full chunk rollback. */
    static volatile Runnable afterUpsertBeforeDeleteHook;

    private PollResultRetentionJob() {}

    public static Result run(SessionDatabase database, Clock clock) {
        return run(database, clock, DEFAULT_CHUNK_SIZE);
    }

    public static Result run(SessionDatabase database, Clock clock, int chunkSize) {
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(clock, "clock");
        if (chunkSize < 1) {
            throw new IllegalArgumentException("chunkSize must be >= 1");
        }
        Instant now = clock.instant();
        Instant rawKeepFrom = now.minus(RAW_RETENTION_DAYS, ChronoUnit.DAYS);
        Instant fiveMinKeepFrom = now.minus(FIVE_MIN_RETENTION_DAYS, ChronoUnit.DAYS);

        int rolledTo5Min = 0;
        int rolledToHour = 0;
        int deletedRaw = 0;
        int deletedFiveMin = 0;

        while (true) {
            Result chunk =
                    database.inTransaction(() -> processRawChunk(database, rawKeepFrom, fiveMinKeepFrom, chunkSize));
            rolledTo5Min += chunk.rolledFiveMinBuckets();
            rolledToHour += chunk.rolledHourlyBuckets();
            deletedRaw += chunk.deletedRawPolls();
            if (chunk.deletedRawPolls() == 0) {
                break;
            }
        }

        while (true) {
            Result chunk = database.inTransaction(() -> processFiveMinChunk(database, fiveMinKeepFrom, chunkSize));
            rolledToHour += chunk.rolledHourlyBuckets();
            deletedFiveMin += chunk.deletedFiveMinRollups();
            if (chunk.deletedFiveMinRollups() == 0) {
                break;
            }
        }

        return new Result(rolledTo5Min, rolledToHour, deletedRaw, deletedFiveMin);
    }

    private static Result processRawChunk(
            SessionDatabase database, Instant rawKeepFrom, Instant fiveMinKeepFrom, int chunkSize) {
        List<PollResultRecord> oldPolls = database.listPollResultsBefore(rawKeepFrom, chunkSize);
        if (oldPolls.isEmpty()) {
            return new Result(0, 0, 0, 0);
        }
        Map<RollupKey, BucketAcc> fiveMin = new HashMap<>();
        Map<RollupKey, BucketAcc> hourly = new HashMap<>();
        List<Long> ids = new ArrayList<>(oldPolls.size());
        for (PollResultRecord poll : oldPolls) {
            ids.add(poll.id());
            if (poll.observedAt().isBefore(fiveMinKeepFrom)) {
                accumulate(hourly, poll, BUCKET_1_HOUR_SECONDS);
            } else {
                accumulate(fiveMin, poll, BUCKET_5_MIN_SECONDS);
            }
        }
        int rolledTo5Min = upsertAll(database, fiveMin, BUCKET_5_MIN_SECONDS);
        int rolledToHour = upsertAll(database, hourly, BUCKET_1_HOUR_SECONDS);

        Runnable hook = afterUpsertBeforeDeleteHook;
        if (hook != null) {
            hook.run();
        }

        int deletedRaw = database.deletePollResultsByIds(ids);
        return new Result(rolledTo5Min, rolledToHour, deletedRaw, 0);
    }

    private static Result processFiveMinChunk(SessionDatabase database, Instant fiveMinKeepFrom, int chunkSize) {
        List<MetricRollupRecord> oldFiveMin =
                database.listMetricRollupsBefore(BUCKET_5_MIN_SECONDS, fiveMinKeepFrom, chunkSize);
        if (oldFiveMin.isEmpty()) {
            return new Result(0, 0, 0, 0);
        }
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

        int deletedFiveMin = database.deleteMetricRollups(oldFiveMin);
        return new Result(0, promotedHour, 0, deletedFiveMin);
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
