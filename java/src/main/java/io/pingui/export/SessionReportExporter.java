package io.pingui.export;

import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.HopProbeStats;
import io.pingui.model.Models.HopStatsSummary;
import io.pingui.model.Models.HostSessionData;
import io.pingui.monitor.HopStats;
import io.pingui.monitor.RouteHistory;
import io.pingui.persistence.SessionDatabase;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** CSV/HTML session reports from SQLite {@code host_session} (P11-030). */
public final class SessionReportExporter {
    private static final DateTimeFormatter ISO_UTC = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

    private static final String[] CSV_FIELDS = {
        "host", "enabled", "route_kind", "hop", "ip", "ping_ms", "avg_ping_ms", "jitter_ms", "loss_pct", "is_timeout"
    };

    private SessionReportExporter() {}

    /**
     * True when the path should be written as HTML ({@code .html}/{@code .htm}); otherwise CSV.
     * Shared by CLI {@code --export-report} and GUI «Експорт зараз…».
     */
    public static boolean isHtmlReport(Path path) {
        Path fileName = path.getFileName();
        if (fileName == null) {
            return false;
        }
        String name = fileName.toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".html") || name.endsWith(".htm");
    }

    /** Export CSV or HTML based on {@link #isHtmlReport(Path)}. */
    public static void export(SessionDatabase database, Path path) throws IOException {
        if (isHtmlReport(path)) {
            exportHtml(database, path);
        } else {
            exportCsv(database, path);
        }
    }

    public static void exportCsv(SessionDatabase database, Path path) throws IOException {
        List<SessionReportRouteRow> rows = buildRouteRows(database);
        Files.createDirectories(path.getParent() != null ? path.getParent() : Path.of("."));
        StringBuilder body = new StringBuilder();
        body.append(String.join(",", CSV_FIELDS)).append('\n');
        for (SessionReportRouteRow row : rows) {
            body.append(csvRow(row)).append('\n');
        }
        Files.writeString(path, body.toString(), StandardCharsets.UTF_8);
    }

    public static void exportHtml(SessionDatabase database, Path path) throws IOException {
        List<String> hosts = database.listHosts();
        List<SessionReportRouteRow> rows = buildRouteRows(database);
        String generated = ISO_UTC.format(Instant.now());
        Files.createDirectories(path.getParent() != null ? path.getParent() : Path.of("."));
        Files.writeString(path, renderHtml(hosts, rows, generated), StandardCharsets.UTF_8);
    }

    public static List<SessionReportRouteRow> buildRouteRows(SessionDatabase database) {
        List<SessionReportRouteRow> rows = new ArrayList<>();
        for (String host : database.listHosts()) {
            HostSessionData data = database.load(host);
            if (data == null) {
                continue;
            }
            rows.addAll(rowsForRoute(host, data.isEnabled(), "current", data.getCurrentRoute(), data));
            List<HopNode> inactive =
                    RouteHistory.routeWithLastKnownIps(data.getPreviousRoute(), data.getLastKnownByHop());
            if (!inactive.isEmpty()) {
                rows.addAll(rowsForRoute(host, data.isEnabled(), "inactive", inactive, data));
            } else if (!data.getPreviousRoute().isEmpty()) {
                rows.addAll(rowsForRoute(host, data.isEnabled(), "previous", data.getPreviousRoute(), data));
            }
        }
        return List.copyOf(rows);
    }

    private static List<SessionReportRouteRow> rowsForRoute(
            String host, boolean enabled, String routeKind, List<HopNode> route, HostSessionData data) {
        List<SessionReportRouteRow> rows = new ArrayList<>();
        for (HopNode node : route) {
            Double avg = null;
            if (node.isReachable()) {
                List<Double> samples = data.getPingHistory().get(node.ip());
                if (samples != null && !samples.isEmpty()) {
                    avg = samples.stream()
                            .mapToDouble(Double::doubleValue)
                            .average()
                            .orElse(Double.NaN);
                }
            }
            HopProbeStats stats = data.getHopStats().get(node.hop());
            HopStatsSummary summary = stats != null ? HopStats.summarize(stats) : null;
            rows.add(new SessionReportRouteRow(
                    host,
                    enabled,
                    routeKind,
                    node.hop(),
                    node.ip(),
                    node.pingMs(),
                    avg,
                    summary != null ? summary.jitterMs() : null,
                    summary != null ? summary.lossPct() : null,
                    node.timeout()));
        }
        return rows;
    }

    private static String csvRow(SessionReportRouteRow row) {
        return String.join(
                ",",
                csvCell(row.host()),
                csvCell(row.enabled() ? "1" : "0"),
                csvCell(row.routeKind()),
                csvCell(Integer.toString(row.hop())),
                csvCell(row.ip()),
                csvCell(formatDouble(row.pingMs())),
                csvCell(formatDouble(row.avgPingMs())),
                csvCell(formatDouble(row.jitterMs())),
                csvCell(formatDouble(row.lossPct())),
                csvCell(row.timeout() ? "1" : "0"));
    }

    private static String formatDouble(Double value) {
        return value == null ? "" : Double.toString(value);
    }

    private static String csvCell(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static String renderHtml(List<String> hosts, List<SessionReportRouteRow> rows, String generated) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"uk\">\n<head>\n");
        html.append("<meta charset=\"utf-8\">\n<title>PINGUI session report</title>\n");
        html.append("<style>body{font-family:sans-serif;margin:2rem;}");
        html.append("table{border-collapse:collapse;margin-bottom:1.5rem;width:100%;}");
        html.append("th,td{border:1px solid #ccc;padding:0.4rem 0.6rem;text-align:left;}");
        html.append("th{background:#f4f4f4;}</style>\n</head>\n<body>\n");
        html.append("<h1>PINGUI session report</h1><p>Generated: ")
                .append(escapeHtml(generated))
                .append("</p>\n");

        Map<String, List<SessionReportRouteRow>> byHost = new java.util.LinkedHashMap<>();
        for (String host : hosts) {
            byHost.put(host, new ArrayList<>());
        }
        for (SessionReportRouteRow row : rows) {
            byHost.computeIfAbsent(row.host(), ignored -> new ArrayList<>()).add(row);
        }

        for (String host : hosts) {
            html.append("<h2>").append(escapeHtml(host)).append("</h2>\n");
            List<SessionReportRouteRow> hostRows = byHost.getOrDefault(host, List.of());
            if (hostRows.isEmpty()) {
                html.append("<p>No route data collected yet.</p>\n");
                continue;
            }
            html.append("<table>\n<tr><th>Route</th><th>Hop</th><th>IP</th>");
            html.append("<th>Ping ms</th><th>Avg ms</th><th>Jitter ms</th><th>Loss %</th><th>Timeout</th></tr>\n");
            for (SessionReportRouteRow row : hostRows) {
                html.append("<tr><td>")
                        .append(escapeHtml(row.routeKind()))
                        .append("</td><td>")
                        .append(row.hop())
                        .append("</td><td>")
                        .append(escapeHtml(row.ip()))
                        .append("</td><td>")
                        .append(formatDouble(row.pingMs()))
                        .append("</td><td>")
                        .append(formatDouble(row.avgPingMs()))
                        .append("</td><td>")
                        .append(formatDouble(row.jitterMs()))
                        .append("</td><td>")
                        .append(formatDouble(row.lossPct()))
                        .append("</td><td>")
                        .append(row.timeout() ? "yes" : "no")
                        .append("</td></tr>\n");
            }
            html.append("</table>\n");
        }
        html.append("</body>\n</html>\n");
        return html.toString();
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
