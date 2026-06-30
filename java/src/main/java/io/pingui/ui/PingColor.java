package io.pingui.ui;

import io.pingui.geoip.GeoCountry;
import io.pingui.model.Models;
import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.HopStatsSummary;
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
        return nodeLabel(node, avgPingFn, hop -> null);
    }

    public static String nodeLabel(
            HopNode node, Function<String, Double> avgPingFn, Function<Integer, HopStatsSummary> hopStatsFn) {
        if (node.timeout() || Models.TIMEOUT_IP.equals(node.ip())) {
            String statsLine = statsLine(node.hop(), hopStatsFn);
            if (!statsLine.isEmpty()) {
                return "Hop " + node.hop() + "\n*" + statsLine;
            }
            return "Hop " + node.hop() + "\n*";
        }
        String countryLine = countryLine(node.ip());
        String statsLine = statsLine(node.hop(), hopStatsFn);
        Double avg = avgPingFn.apply(node.ip());
        if (avg != null) {
            return "Hop " + node.hop() + "\n" + node.ip() + countryLine + "\n" + avg.intValue() + " ms" + statsLine;
        }
        if (node.pingMs() != null) {
            return "Hop " + node.hop() + "\n" + node.ip() + countryLine + "\n"
                    + node.pingMs().intValue() + " ms" + statsLine;
        }
        if (!countryLine.isEmpty() || !statsLine.isEmpty()) {
            return "Hop " + node.hop() + "\n" + node.ip() + countryLine + statsLine;
        }
        return "Hop " + node.hop() + "\n" + node.ip();
    }

    private static String countryLine(String ip) {
        String code = GeoCountry.lookup(ip);
        return code != null ? "\n" + code : "";
    }

    private static String statsLine(int hop, Function<Integer, HopStatsSummary> hopStatsFn) {
        if (hopStatsFn == null) {
            return "";
        }
        HopStatsSummary summary = hopStatsFn.apply(hop);
        if (summary == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder("\n");
        if (summary.jitterMs() != null) {
            builder.append("j:").append(summary.jitterMs().intValue()).append(' ');
        }
        builder.append("loss:").append((int) summary.lossPct()).append('%');
        return builder.toString();
    }
}
