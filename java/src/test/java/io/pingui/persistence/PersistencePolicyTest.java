package io.pingui.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PersistencePolicyTest {
    @Test
    void defaultsEnableBothEventTypes() {
        PersistencePolicy policy = PersistencePolicy.defaults();
        assertTrue(policy.allows(PersistenceEventType.ROUTE_CHANGE));
        assertTrue(policy.allows(PersistenceEventType.PROBE_ERROR));
    }

    @Test
    void allowsPerType() {
        PersistencePolicy policy = PersistencePolicy.of(true, false);
        assertTrue(policy.allows(PersistenceEventType.ROUTE_CHANGE));
        assertFalse(policy.allows(PersistenceEventType.PROBE_ERROR));
    }
}
