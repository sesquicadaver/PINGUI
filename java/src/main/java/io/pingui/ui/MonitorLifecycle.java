package io.pingui.ui;

import io.pingui.config.AlertConfig;
import io.pingui.config.HostEntry;
import io.pingui.config.TracingProfile;
import io.pingui.monitor.AlertDispatchers;
import io.pingui.monitor.DesktopAlertSink;
import io.pingui.monitor.MonitorService;
import io.pingui.monitor.SessionStore;
import io.pingui.persistence.PersistenceEventWriter;
import io.pingui.persistence.SessionDatabase;
import java.util.List;
import java.util.function.Supplier;
import javafx.stage.Window;

/** Factory for session {@link MonitorService} wired to the active tracing profile. */
public final class MonitorLifecycle {

    private MonitorLifecycle() {}

    public static MonitorService create(
            TracingProfile profile,
            String profileName,
            SessionStore store,
            MonitorService.Listener listener,
            AlertConfig alerts) {
        return create(
                profile,
                profileName,
                store,
                listener,
                alerts,
                null,
                HostViewRules.sessionEntries(profile.hosts()),
                DesktopAlertSink.noop());
    }

    public static MonitorService create(
            TracingProfile profile,
            String profileName,
            SessionStore store,
            MonitorService.Listener listener,
            AlertConfig alerts,
            SessionDatabase sessionDatabase,
            List<HostEntry> sessionHosts) {
        return create(
                profile, profileName, store, listener, alerts, sessionDatabase, sessionHosts, DesktopAlertSink.noop());
    }

    public static MonitorService create(
            TracingProfile profile,
            String profileName,
            SessionStore store,
            MonitorService.Listener listener,
            AlertConfig alerts,
            SessionDatabase sessionDatabase,
            List<HostEntry> sessionHosts,
            DesktopAlertSink desktopSink) {
        MonitorService service = new MonitorService(
                profile.intervalSeconds(),
                profile.maxHops(),
                profile.timeoutSeconds(),
                profile.probeMode(),
                profile.maxConcurrentTraces());
        service.setAlertProfileName(profileName);
        service.setAlertDispatcher(AlertDispatchers.build(alerts, desktopSink));
        applyAlertRules(service, alerts);
        service.setExpertResolver(store::getPingExpert);
        service.setProfileProbeMode(profile.hostProbeMode());
        service.setHostProbeModeResolver(store::getProbeMode);
        service.setHostPollIntervalResolver(store::getIntervalOverride);
        if (sessionDatabase != null) {
            service.setPersistenceEventWriter(new PersistenceEventWriter(sessionDatabase, service.persistencePolicy()));
        }
        service.setListener(listener);
        for (HostEntry entry : sessionHosts) {
            if (!HostViewRules.matches(entry.address())) {
                service.addHost(entry.address(), entry.enabled(), entry.effectiveProbeMode(profile.hostProbeMode()));
            }
        }
        return service;
    }

    /** Builds the rate-limited alert pipeline with the given desktop popup sink. */
    public static void applyAlertDispatcher(MonitorService service, AlertConfig alerts, DesktopAlertSink desktopSink) {
        if (service == null) {
            return;
        }
        service.setAlertDispatcher(AlertDispatchers.build(alerts, desktopSink));
    }

    /** Applies {@code endpoint_down} / {@code latency_high} + {@code notify_resolved} from profile alerts. */
    public static void applyAlertRules(MonitorService service, AlertConfig alerts) {
        AlertConfig effective = alerts != null ? alerts : AlertConfig.disabled();
        service.setNotifyResolved(effective.notifyResolved());
        service.setEndpointDownRule(effective.endpointDown());
        service.setLatencyHighRule(effective.latencyHigh());
    }

    /** JavaFX popup sink; {@code ownerSupplier} may return null until the scene exists. */
    public static DesktopAlertSink javaFxDesktopSink(Supplier<Window> ownerSupplier) {
        return new JavaFxDesktopAlertSink(ownerSupplier);
    }
}
