package io.pingui.export;

/** One hop row in an exported session report (parity with Python RouteRow). */
public record SessionReportRouteRow(
        String host,
        boolean enabled,
        String routeKind,
        int hop,
        String ip,
        Double pingMs,
        Double avgPingMs,
        Double jitterMs,
        Double lossPct,
        boolean timeout) {}
