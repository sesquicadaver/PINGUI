package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.RouteSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class PollSampleScopeSessionStoreTest {
    @Test
    void appendPingSamplesRespectsFreshHopOnly() {
        SessionStore store = new SessionStore(List.of("8.8.8.8"));
        RouteSnapshot snapshot = new RouteSnapshot(
                "8.8.8.8",
                "8.8.8.8",
                List.of(new HopNode(1, "10.0.0.1", 4.0, false), new HopNode(2, "8.8.8.8", 8.0, false)));
        store.updateRoute("8.8.8.8", snapshot);
        store.appendPingSamples("8.8.8.8", snapshot, PollSampleScope.mtr(2, true));

        assertEquals(1, store.get("8.8.8.8").getPingHistory().get("8.8.8.8").size());
        assertFalse(store.get("8.8.8.8").getPingHistory().containsKey("10.0.0.1"));
        assertEquals(1, store.get("8.8.8.8").getHopStats().get(2).getProbes());
        assertNull(store.get("8.8.8.8").getHopStats().get(1));
    }
}
