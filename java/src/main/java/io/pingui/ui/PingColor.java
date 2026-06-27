package io.pingui.ui;

import io.pingui.model.Models;
import io.pingui.model.Models.HopNode;
import java.util.function.Function;

/** RTT → fill color mapping (parity with Python graph_canvas.ping_color). */
public final class PingColor {
    private PingColor() {}

    public static String pingColor(Double avgMs, boolean isTimeout) {
        if (isTimeout || avgMs == null) {
            return "#d3d3d3";
        }
        if (avgMs < 50) {
            return "#98fb98";
        }
        if (avgMs < 150) {
            return "#ffa500";
        }
        return "#ff6347";
    }

    public static String nodeColor(HopNode node, Function<String, Double> avgPingFn, boolean inactive) {
        if (inactive) {
            return RouteGraphLayout.INACTIVE_NODE;
        }
        if (node.timeout() || Models.TIMEOUT_IP.equals(node.ip())) {
            return pingColor(null, true);
        }
        Double avg = avgPingFn.apply(node.ip());
        double sample = avg != null ? avg : (node.pingMs() != null ? node.pingMs() : Double.NaN);
        return pingColor(Double.isNaN(sample) ? null : sample, false);
    }

    public static String nodeLabel(HopNode node, Function<String, Double> avgPingFn) {
        if (node.timeout() || Models.TIMEOUT_IP.equals(node.ip())) {
            return "Hop " + node.hop() + "\n*";
        }
        Double avg = avgPingFn.apply(node.ip());
        if (avg != null) {
            return "Hop " + node.hop() + "\n" + node.ip() + "\n" + avg.intValue() + " ms";
        }
        if (node.pingMs() != null) {
            return "Hop " + node.hop() + "\n" + node.ip() + "\n" + node.pingMs().intValue() + " ms";
        }
        return "Hop " + node.hop() + "\n" + node.ip();
    }
}
