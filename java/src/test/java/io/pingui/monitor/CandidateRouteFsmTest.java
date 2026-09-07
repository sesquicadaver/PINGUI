package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.model.Models.HopNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class CandidateRouteFsmTest {
    @Test
    void firstConfirmedPathIsBaselineNotChange() {
        CandidateRouteFsm fsm = new CandidateRouteFsm();
        CandidateRouteFsm.Decision d =
                fsm.observe(RouteIdentity.fromReachableIps(List.of("10.0.0.1", "8.8.8.8")), true);
        assertFalse(d.changed());
        assertEquals(List.of("10.0.0.1", "8.8.8.8"), fsm.active().reachableIps());
    }

    @Test
    void unconfirmedDivergenceStaysCandidate() {
        CandidateRouteFsm fsm = new CandidateRouteFsm();
        fsm.observe(RouteIdentity.fromReachableIps(List.of("10.0.0.1", "8.8.8.8")), true);
        CandidateRouteFsm.Decision mid =
                fsm.observe(RouteIdentity.fromHops(List.of(new HopNode(1, "9.9.9.9", 1.0, false))), false);
        assertFalse(mid.changed());
        assertEquals(List.of("9.9.9.9"), fsm.candidate().reachableIps());
        assertEquals(List.of("10.0.0.1", "8.8.8.8"), fsm.active().reachableIps());
    }

    @Test
    void confirmEmitsSingleChange() {
        CandidateRouteFsm fsm = new CandidateRouteFsm();
        fsm.observe(RouteIdentity.fromReachableIps(List.of("10.0.0.1", "8.8.8.8")), true);
        fsm.observe(RouteIdentity.fromHops(List.of(new HopNode(1, "9.9.9.9", 1.0, false))), false);
        CandidateRouteFsm.Decision done =
                fsm.observe(RouteIdentity.fromReachableIps(List.of("9.9.9.9", "8.8.8.8")), true);
        assertTrue(done.changed());
        assertEquals(List.of("10.0.0.1", "8.8.8.8"), done.oldIps());
        assertEquals(List.of("9.9.9.9", "8.8.8.8"), done.newIps());
        assertTrue(fsm.candidate().isEmpty());
    }
}
