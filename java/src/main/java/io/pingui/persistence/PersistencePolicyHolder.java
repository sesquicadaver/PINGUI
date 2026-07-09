package io.pingui.persistence;

/**
 * Active vs pending persistence policy (SPIKE P11-002).
 *
 * <p>Writers read {@link #active()}; UI/YAML/CLI set {@link #pending()}. {@link #applyPendingAfterCycle()}
 * runs at the end of each {@code MonitorService} poll cycle.
 */
public final class PersistencePolicyHolder {
    private volatile PersistencePolicy active = PersistencePolicy.defaults();
    private volatile PersistencePolicy pending = PersistencePolicy.defaults();

    public PersistencePolicy active() {
        return active;
    }

    public PersistencePolicy pending() {
        return pending;
    }

    public void setPending(PersistencePolicy policy) {
        this.pending = policy != null ? policy : PersistencePolicy.defaults();
    }

    /** Promotes pending policy to active after a completed poll cycle. */
    public void applyPendingAfterCycle() {
        this.active = this.pending;
    }
}
