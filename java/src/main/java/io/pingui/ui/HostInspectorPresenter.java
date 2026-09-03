package io.pingui.ui;

import io.pingui.i18n.UiI18n;
import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.HopStatsSummary;
import io.pingui.monitor.HostProblemSummary;
import io.pingui.monitor.HostTargetStats;
import io.pingui.monitor.MonitorService;
import io.pingui.monitor.ProblemCorrelation;
import io.pingui.monitor.SessionStore;
import io.pingui.persistence.PersistenceEventRecord;
import io.pingui.persistence.PersistenceEventType;
import io.pingui.persistence.SessionDatabase;
import io.pingui.ui.view.HostInspectorPanel;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.Window;

/** Selected-host inspector: binds {@link HostInspectorPanel} to session/monitor state (P31-003). */
final class HostInspectorPresenter {
    private static final int ROUTE_CHANGE_LOOKBACK_DAYS = 7;
    private static final int ROUTE_CHANGE_SCAN_LIMIT = 40;

    private final HostInspectorPanel panel;
    private final Supplier<SessionStore> store;
    private final Supplier<MonitorService> monitor;
    private final Supplier<Window> dialogOwner;
    private final UserFeedback userFeedback;
    private HostItem bound;

    HostInspectorPresenter(
            HostInspectorPanel panel,
            Supplier<SessionStore> store,
            Supplier<MonitorService> monitor,
            Supplier<Window> dialogOwner,
            UserFeedback userFeedback) {
        this.panel = panel;
        this.store = store;
        this.monitor = monitor;
        this.dialogOwner = dialogOwner;
        this.userFeedback = userFeedback;
        panel.copyButton().setOnAction(e -> copyAddress());
        panel.ackButton().setOnAction(e -> acknowledgeProblem());
        panel.diagnosticsButton().setOnAction(e -> openDiagnostics());
    }

    void clear() {
        bound = null;
        panel.showEmpty();
    }

    void show(HostItem item) {
        if (item == null) {
            clear();
            return;
        }
        bound = item;
        refresh();
    }

    void refreshIfHost(String host) {
        if (bound != null && host != null && host.equals(bound.getHost())) {
            refresh();
        }
    }

    void refresh() {
        HostItem item = bound;
        if (item == null) {
            panel.showEmpty();
            return;
        }
        SessionStore session = store.get();
        MonitorService service = monitor.get();
        List<HopNode> hops = List.of();
        HostTargetStats stats = null;
        HopStatsSummary hopStats = null;
        Instant lastPoll = null;
        Instant lastRouteChange = null;
        HostProblemSummary problem = item.problemSummary();
        if (session != null && session.containsHost(item.getHost())) {
            hops = session.get(item.getHost()).getCurrentRoute();
            stats = session.targetStats(item.getHost());
            if (!hops.isEmpty()) {
                hopStats = session.hopStatsSummary(
                        item.getHost(), hops.get(hops.size() - 1).hop());
            }
            lastRouteChange = findLastRouteChange(session.database(), item.getHost());
        }
        if (service != null) {
            lastPoll = service.lastPollAt(item.getHost()).orElse(null);
            problem = service.hostProblemSummary(item.getHost()).orElse(problem);
            item.applyProblem(problem);
        }
        String resolved = HostInspectorFormatter.resolvedIpFromHops(hops);
        HostInspectorFormatter.Snapshot snap = HostInspectorFormatter.from(
                item.getHost(),
                resolved,
                item.getProbeMode(),
                lastPoll,
                stats,
                hopStats,
                item.endpointState(),
                item.routeState(),
                lastRouteChange,
                problem);
        boolean canAck = problem != null && problem.showBadge();
        boolean canDiagnose = problem != null;
        panel.apply(
                snap.address(),
                snap.resolvedIp(),
                snap.mode(),
                snap.lastPoll(),
                snap.rtt(),
                snap.jitter(),
                snap.loss(),
                snap.endpoint(),
                snap.route(),
                snap.lastRouteChange(),
                snap.problem(),
                canAck,
                canDiagnose);
    }

    private void copyAddress() {
        if (bound == null) {
            return;
        }
        String text = bound.getHost();
        SessionStore session = store.get();
        if (session != null && session.containsHost(text)) {
            String resolved =
                    HostInspectorFormatter.resolvedIpFromHops(session.get(text).getCurrentRoute());
            if (!resolved.isBlank() && !resolved.equals(text)) {
                text = text + " (" + resolved + ")";
            }
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
        userFeedback.info(UiI18n.get("inspector.copied", text));
    }

    private void acknowledgeProblem() {
        if (bound == null) {
            return;
        }
        MonitorService service = monitor.get();
        if (service == null) {
            return;
        }
        service.ackHostProblem(bound.getHost());
        bound.applyProblem(service.hostProblemSummary(bound.getHost()).orElse(null));
        refresh();
        userFeedback.info(UiI18n.get("inspector.acked", bound.getHost()));
    }

    private void openDiagnostics() {
        if (bound == null) {
            return;
        }
        HostProblemSummary summary = bound.problemSummary();
        MonitorService service = monitor.get();
        if (service != null) {
            summary = service.hostProblemSummary(bound.getHost()).orElse(summary);
        }
        if (summary == null) {
            userFeedback.info(UiI18n.get("inspector.diagnostics_none"));
            return;
        }
        Optional<ProblemCorrelation> correlation = Optional.empty();
        if (service != null) {
            SessionStore session = store.get();
            if (session != null) {
                correlation = service.correlateActiveProblems(session);
            }
        }
        ProblemDetailsDialog.show(dialogOwner.get(), summary, correlation);
        if (service != null) {
            service.ackHostProblem(bound.getHost());
            bound.applyProblem(service.hostProblemSummary(bound.getHost()).orElse(null));
            refresh();
        }
    }

    private static Instant findLastRouteChange(SessionDatabase database, String host) {
        if (database == null || host == null || host.isBlank()) {
            return null;
        }
        Instant since = Instant.now().minus(ROUTE_CHANGE_LOOKBACK_DAYS, ChronoUnit.DAYS);
        List<PersistenceEventRecord> rows = database.listHostEvents(host, since, ROUTE_CHANGE_SCAN_LIMIT);
        for (PersistenceEventRecord row : rows) {
            if (row.eventType() == PersistenceEventType.ROUTE_CHANGE) {
                return row.observedAt();
            }
        }
        return null;
    }
}
