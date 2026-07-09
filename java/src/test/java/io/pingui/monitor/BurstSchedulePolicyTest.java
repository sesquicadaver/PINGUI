package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class BurstSchedulePolicyTest {

    @Test
    void shouldArmBurstSkipsIncrementalHopDiscovery() {
        assertFalse(BurstSchedulePolicy.shouldArmBurst(List.of("10.0.0.1"), List.of("10.0.0.1", "8.8.8.8")));
        assertTrue(
                BurstSchedulePolicy.shouldArmBurst(List.of("10.0.0.1", "8.8.8.8"), List.of("192.168.1.1", "8.8.8.8")));
        assertFalse(BurstSchedulePolicy.shouldArmBurst(List.of(), List.of("10.0.0.1")));
    }

    @Test
    void shortensIntervalDuringBurstWindow() {
        BurstSchedulePolicy policy = new BurstSchedulePolicy();
        Instant start = Instant.parse("2026-07-09T10:00:00Z");
        policy.onRouteChange("8.8.8.8", start);
        assertEquals(7.5, policy.effectiveInterval("8.8.8.8", 30.0, start.plusSeconds(1)));
        assertTrue(policy.isBurstActive("8.8.8.8", start.plusSeconds(1)));
    }

    @Test
    void restoresIntervalAfterBurstExpires() {
        BurstSchedulePolicy policy = new BurstSchedulePolicy();
        Instant start = Instant.parse("2026-07-09T10:00:00Z");
        policy.onRouteChange("8.8.8.8", start);
        Instant afterBurst = start.plus(BurstSchedulePolicy.BURST_DURATION).plusSeconds(1);
        assertEquals(30.0, policy.effectiveInterval("8.8.8.8", 30.0, afterBurst));
        assertFalse(policy.isBurstActive("8.8.8.8", afterBurst));
    }

    @Test
    void repeatedRouteChangeExtendsBurstWindow() {
        BurstSchedulePolicy policy = new BurstSchedulePolicy();
        Instant first = Instant.parse("2026-07-09T10:00:00Z");
        policy.onRouteChange("8.8.8.8", first);
        Instant second = first.plusSeconds(240);
        policy.onRouteChange("8.8.8.8", second);
        Instant betweenOldAndNewEnd = first.plusSeconds(360);
        assertTrue(policy.isBurstActive("8.8.8.8", betweenOldAndNewEnd));
        assertEquals(2.5, policy.effectiveInterval("8.8.8.8", 10.0, betweenOldAndNewEnd));
    }

    @Test
    void enforcesMinimumTickInterval() {
        BurstSchedulePolicy policy = new BurstSchedulePolicy();
        Instant start = Instant.parse("2026-07-09T10:00:00Z");
        policy.onRouteChange("1.1.1.1", start);
        assertEquals(HostPollSchedule.TICK_SECONDS, policy.effectiveInterval("1.1.1.1", 0.5, start.plusSeconds(1)));
    }

    @Test
    void renameHostMovesBurstState() {
        BurstSchedulePolicy policy = new BurstSchedulePolicy();
        Instant start = Instant.parse("2026-07-09T10:00:00Z");
        policy.onRouteChange("old", start);
        policy.renameHost("old", "new");
        assertTrue(policy.isBurstActive("new", start.plusSeconds(30)));
        assertFalse(policy.isBurstActive("old", start.plusSeconds(30)));
    }

    @Test
    void rejectsInvalidInput() {
        BurstSchedulePolicy policy = new BurstSchedulePolicy();
        assertThrows(IllegalArgumentException.class, () -> policy.onRouteChange(" ", Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> policy.effectiveInterval("8.8.8.8", 0, Instant.now()));
    }
}
