package io.pingui.ui;

import io.pingui.config.HostEntry;
import io.pingui.config.TracingProfile;
import io.pingui.monitor.MonitorService;
import io.pingui.monitor.SessionStore;

/** Factory for session {@link MonitorService} wired to the active tracing profile. */
final class MonitorLifecycle {

    private MonitorLifecycle() {}

    static MonitorService create(TracingProfile profile, SessionStore store, MonitorService.Listener listener) {
        MonitorService service = new MonitorService(
                profile.intervalSeconds(), profile.maxHops(), profile.timeoutSeconds(), profile.probeMode());
        service.setExpertResolver(store::getPingExpert);
        service.setPingOnlyResolver(store::isPingOnly);
        service.setListener(listener);
        for (HostEntry entry : profile.hosts()) {
            if (!HostViewRules.matches(entry.address())) {
                service.addHost(entry.address(), entry.enabled(), entry.pingOnly());
            }
        }
        return service;
    }
}
