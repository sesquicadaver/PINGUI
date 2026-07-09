package io.pingui.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PersistencePolicySupportTest {
    @Test
    void typesBeingDisabledDetectsTransitions() {
        PersistencePolicy before = PersistencePolicy.of(true, true);
        PersistencePolicy after = PersistencePolicy.of(false, true);
        List<PersistenceEventType> disabled = PersistencePolicySupport.typesBeingDisabled(before, after);
        assertEquals(List.of(PersistenceEventType.ROUTE_CHANGE), disabled);
    }

    @Test
    void typesBeingDisabledEmptyWhenEnabling() {
        PersistencePolicy before = PersistencePolicy.of(false, false);
        PersistencePolicy after = PersistencePolicy.defaults();
        assertTrue(PersistencePolicySupport.typesBeingDisabled(before, after).isEmpty());
    }
}
