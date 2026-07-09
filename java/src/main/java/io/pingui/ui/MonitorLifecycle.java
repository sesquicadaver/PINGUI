package io.pingui.ui;

import io.pingui.config.AlertConfig;
import io.pingui.config.HostEntry;
import io.pingui.config.TracingProfile;
import io.pingui.monitor.AlertDispatchers;
import io.pingui.monitor.MonitorService;
import io.pingui.monitor.SessionStore;
import io.pingui.persistence.PersistenceEventWriter;
import io.pingui.persistence.SessionDatabase;
import java.util.List;

/** Factory for session {@link MonitorService} wired to the active tracing profile. */
final class MonitorLifecycle {

    private MonitorLifecycle() {}

    static MonitorService create(
            TracingProfile profile,
            String profileName,
            SessionStore store,
            MonitorService.Listener listener,
            AlertConfig alerts) {
        return create(
                profile, profileName, store, listener, alerts, null, HostViewRules.sessionEntries(profile.hosts()));
    }

    static MonitorService create(
            TracingProfile profile,
            String profileName,
            SessionStore store,
            MonitorService.Listener listener,
            AlertConfig alerts,
            SessionDatabase sessionDatabase,
            List<HostEntry> sessionHosts) {
        MonitorService service = new MonitorService(
                profile.intervalSeconds(), profile.maxHops(), profile.timeoutSeconds(), profile.probeMode());
        service.setAlertProfileName(profileName);
        service.setAlertDispatcher(AlertDispatchers.build(alerts));
        service.setExpertResolver(store::getPingExpert);
        service.setPingOnlyResolver(store::isPingOnly);
        if (sessionDatabase != null) {
            service.setPersistenceEventWriter(new PersistenceEventWriter(sessionDatabase, service.persistencePolicy()));
        }
        service.setListener(listener);
        for (HostEntry entry : sessionHosts) {
            if (!HostViewRules.matches(entry.address())) {
                service.addHost(entry.address(), entry.enabled(), entry.pingOnly());
            }
        }
        return service;
    }
}
