package io.pingui.ui;

import io.pingui.monitor.RouteChangeEvent;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** One route-change row in the history list (P11-020). */
public record RouteHistoryItem(long id, RouteChangeEvent event) {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    public String summary() {
        String route = event.newIps().isEmpty() ? "—" : String.join(" → ", event.newIps());
        return TIME_FMT.format(event.timestamp()) + "  " + route;
    }
}
