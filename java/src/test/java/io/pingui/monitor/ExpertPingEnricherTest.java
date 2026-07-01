package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.config.PingExpertEntry;
import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.RouteSnapshot;
import io.pingui.probe.ProcessExpertPing;
import java.io.IOException;
import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class ExpertPingEnricherTest {

    @Test
    void returnsSnapshotWhenExpertNotConfigured() {
        ExpertPingEnricher enricher = new ExpertPingEnricher();
        RouteSnapshot snapshot =
                snapshot(List.of(new HopNode(1, "192.168.1.1", 1.0, false), new HopNode(2, "8.8.8.8", 2.0, false)));
        RouteSnapshot result = enricher.enrich(snapshot, PingExpertEntry.empty(), 0.5);
        assertEquals(snapshot.nodes(), result.nodes());
    }

    @Test
    void returnsSnapshotWhenNodesEmpty() {
        StubExpertPing stub = new StubExpertPing();
        ExpertPingEnricher enricher = new ExpertPingEnricher(stub);
        RouteSnapshot snapshot = snapshot(List.of());
        PingExpertEntry expert = new PingExpertEntry(false, List.of("-4"));
        assertEquals(snapshot.nodes(), enricher.enrich(snapshot, expert, 0.5).nodes());
        assertEquals(0, stub.calls);
    }

    @Test
    void chainModeEnrichesEveryReachableHop() {
        StubExpertPing stub = new StubExpertPing().withRtt(3.3);
        ExpertPingEnricher enricher = new ExpertPingEnricher(stub);
        RouteSnapshot snapshot =
                snapshot(List.of(new HopNode(1, "192.168.1.1", 1.0, false), new HopNode(2, "8.8.8.8", 2.0, false)));
        PingExpertEntry expert = new PingExpertEntry(true, List.of("-4"));
        RouteSnapshot result = enricher.enrich(snapshot, expert, 0.5);
        assertEquals(2, stub.calls);
        assertEquals(3.3, result.nodes().get(0).pingMs());
        assertEquals(3.3, result.nodes().get(1).pingMs());
    }

    @Test
    void targetOnlyEnrichesLastHopUsingSnapshotTarget() {
        StubExpertPing stub = new StubExpertPing().withRtt(5.5);
        ExpertPingEnricher enricher = new ExpertPingEnricher(stub);
        RouteSnapshot snapshot = new RouteSnapshot(
                "dns.google",
                "8.8.8.8",
                List.of(new HopNode(1, "192.168.1.1", 1.0, false), new HopNode(2, "8.8.4.4", 2.0, false)));
        PingExpertEntry expert = new PingExpertEntry(false, List.of("-4"));
        RouteSnapshot result = enricher.enrich(snapshot, expert, 0.5);
        assertEquals(1, stub.calls);
        assertEquals("dns.google", stub.lastTarget);
        assertEquals(1.0, result.nodes().get(0).pingMs());
        assertEquals(5.5, result.nodes().get(1).pingMs());
    }

    @Test
    void skipsUnreachableHops() {
        StubExpertPing stub = new StubExpertPing().withRtt(1.0);
        ExpertPingEnricher enricher = new ExpertPingEnricher(stub);
        RouteSnapshot snapshot =
                snapshot(List.of(new HopNode(1, "*", null, true), new HopNode(2, "8.8.8.8", 2.0, false)));
        RouteSnapshot result = enricher.enrich(snapshot, new PingExpertEntry(true, List.of("-4")), 0.5);
        assertEquals(1, stub.calls);
        assertTrue(result.nodes().get(0).timeout());
        assertEquals(1.0, result.nodes().get(1).pingMs());
    }

    @Test
    void emptyPingResultMarksTimeout() {
        StubExpertPing stub = new StubExpertPing().withEmpty();
        ExpertPingEnricher enricher = new ExpertPingEnricher(stub);
        RouteSnapshot snapshot = snapshot(List.of(new HopNode(1, "8.8.8.8", 2.0, false)));
        RouteSnapshot result = enricher.enrich(snapshot, new PingExpertEntry(false, List.of("-4")), 0.5);
        assertNull(result.nodes().get(0).pingMs());
        assertTrue(result.nodes().get(0).timeout());
    }

    @Test
    void ioExceptionMarksTimeout() {
        StubExpertPing stub = new StubExpertPing().withIoError();
        ExpertPingEnricher enricher = new ExpertPingEnricher(stub);
        RouteSnapshot snapshot = snapshot(List.of(new HopNode(1, "8.8.8.8", 2.0, false)));
        RouteSnapshot result = enricher.enrich(snapshot, new PingExpertEntry(false, List.of("-4")), 0.5);
        assertTrue(result.nodes().get(0).timeout());
    }

    private static RouteSnapshot snapshot(List<HopNode> nodes) {
        return new RouteSnapshot("8.8.8.8", "8.8.8.8", nodes);
    }

    private static final class StubExpertPing extends ProcessExpertPing {
        private OptionalDouble rtt = OptionalDouble.empty();
        private boolean throwIo;
        int calls;
        String lastTarget;

        StubExpertPing withRtt(double value) {
            rtt = OptionalDouble.of(value);
            return this;
        }

        StubExpertPing withEmpty() {
            rtt = OptionalDouble.empty();
            return this;
        }

        StubExpertPing withIoError() {
            throwIo = true;
            return this;
        }

        @Override
        public OptionalDouble pingOnce(String target, PingExpertEntry expert, double timeoutSeconds)
                throws IOException {
            calls++;
            lastTarget = target;
            if (throwIo) {
                throw new IOException("stub failure");
            }
            return rtt;
        }
    }
}
