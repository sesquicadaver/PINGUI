package io.pingui.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PersistencePolicyHolderTest {
    @Test
    void pendingAppliesAfterCycle() {
        PersistencePolicyHolder holder = new PersistencePolicyHolder();
        assertTrue(holder.active().allows(PersistenceEventType.ROUTE_CHANGE));

        holder.setPending(PersistencePolicy.of(false, true));
        assertTrue(holder.active().allows(PersistenceEventType.ROUTE_CHANGE));
        assertFalse(holder.pending().allows(PersistenceEventType.ROUTE_CHANGE));

        holder.applyPendingAfterCycle();
        assertFalse(holder.active().allows(PersistenceEventType.ROUTE_CHANGE));
        assertEquals(holder.pending(), holder.active());
    }

    @Test
    void nullPendingFallsBackToDefaults() {
        PersistencePolicyHolder holder = new PersistencePolicyHolder();
        holder.setPending(PersistencePolicy.of(false, false));
        holder.applyPendingAfterCycle();
        assertFalse(holder.active().routeChange());

        holder.setPending(null);
        holder.applyPendingAfterCycle();
        assertTrue(holder.active().routeChange());
        assertTrue(holder.active().probeError());
    }
}
