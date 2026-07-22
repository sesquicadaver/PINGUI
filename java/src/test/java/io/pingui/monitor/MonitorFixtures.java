package io.pingui.monitor;

/** Shared MonitorService factories for UI tests outside the monitor package. */
public final class MonitorFixtures {
    private MonitorFixtures() {}

    /** Idle service (no polling started) for host CRUD wiring tests. */
    public static MonitorService idle() {
        return new MonitorService(1.0, 20, 0.5, new FakeRouteProbe(null));
    }
}
