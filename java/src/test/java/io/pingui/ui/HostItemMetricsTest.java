package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.monitor.HostPollCounters;
import io.pingui.monitor.HostTargetStats;
import org.junit.jupiter.api.Test;

class HostItemMetricsTest {
    @Test
    void pollCountersUseRoleLabelsNotModeNames() {
        String text = HostItem.formatPollCounters(new HostPollCounters(12, 3));
        assertEquals("спроб 12  помилки 3  25%", text);
        assertFalse(text.toLowerCase().contains("ping"));
        assertFalse(text.toLowerCase().contains("trace"));
    }

    @Test
    void rttMetricsStayOnSeparateFormat() {
        HostTargetStats stats = new HostTargetStats(0.0, 10.0, 12.0, 15.0, false);
        assertEquals("loss 0%  min 10  avg 12  max 15 ms", HostItem.formatRttMetrics(stats));
    }

    @Test
    void applyMetricsPutsCountersAndRttOnSeparateProperties() {
        HostItem item = new HostItem("8.8.8.8", true);
        item.applyMetrics(new HostTargetStats(0.0, 10.0, 12.0, 15.0, false), new HostPollCounters(4, 1));
        assertTrue(item.showPollCountersProperty().get());
        assertTrue(item.showMetricsProperty().get());
        assertEquals("спроб 4  помилки 1  25%", item.pollCountersTextProperty().get());
        assertEquals(
                "loss 0%  min 10  avg 12  max 15 ms", item.metricsTextProperty().get());
    }

    @Test
    void applyMetricsShowsCountersWithoutRttYet() {
        HostItem item = new HostItem("8.8.8.8", true);
        item.applyMetrics(null, new HostPollCounters(2, 0));
        assertTrue(item.showPollCountersProperty().get());
        assertFalse(item.showMetricsProperty().get());
        assertEquals("спроб 2  помилки 0  0%", item.pollCountersTextProperty().get());
    }

    @Test
    void applyMetricsClearsWhenNoCountersAndNoStats() {
        HostItem item = new HostItem("8.8.8.8", true);
        item.applyMetrics(null, new HostPollCounters(1, 0));
        item.applyMetrics(null, HostPollCounters.ZERO);
        assertFalse(item.showPollCountersProperty().get());
        assertFalse(item.showMetricsProperty().get());
        assertEquals("", item.pollCountersTextProperty().get());
        assertEquals("", item.metricsTextProperty().get());
    }
}
