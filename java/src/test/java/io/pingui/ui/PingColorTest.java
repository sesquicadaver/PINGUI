package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.model.Models.HopNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class PingColorTest {
    @Test
    void pingColorThresholds() {
        assertEquals("#98fb98", PingColor.pingColor(10.0, false));
        assertEquals("#ffa500", PingColor.pingColor(100.0, false));
        assertEquals("#ff6347", PingColor.pingColor(200.0, false));
        assertEquals("#d3d3d3", PingColor.pingColor(null, true));
    }

    @Test
    void nodeLabelUsesAvgPing() {
        HopNode node = new HopNode(3, "192.168.0.1", 12.0, false);
        assertEquals("Hop 3\n192.168.0.1\n12 ms", PingColor.nodeLabel(node, ip -> 12.0));
        assertEquals("Hop 2\n*", PingColor.nodeLabel(new HopNode(2, "*", null, true), ip -> null));
    }
}
