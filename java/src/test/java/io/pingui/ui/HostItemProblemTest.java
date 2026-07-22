package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.monitor.HostProblemSummary;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class HostItemProblemTest {
    @Test
    void applyProblemDrivesUnreadBadge() {
        HostItem item = new HostItem("8.8.8.8", true);
        assertFalse(item.isProblemUnread());
        HostProblemSummary unread = new HostProblemSummary(
                "8.8.8.8",
                "endpoint_down",
                true,
                1,
                Duration.ZERO,
                Instant.parse("2026-07-22T12:00:00Z"),
                null,
                HostProblemSummary.STATE_FIRING,
                HostProblemSummary.DESCRIPTION_ENDPOINT_DOWN);
        item.applyProblem(unread);
        assertTrue(item.isProblemUnread());
        assertEquals(unread, item.problemSummary());

        HostProblemSummary acked = new HostProblemSummary(
                "8.8.8.8",
                "endpoint_down",
                false,
                1,
                Duration.ofSeconds(10),
                Instant.parse("2026-07-22T12:00:00Z"),
                Instant.parse("2026-07-22T12:00:10Z"),
                HostProblemSummary.STATE_OK,
                HostProblemSummary.DESCRIPTION_ENDPOINT_DOWN);
        item.applyProblem(acked);
        assertFalse(item.isProblemUnread());
        item.clearProblem();
        assertFalse(item.isProblemUnread());
    }
}
