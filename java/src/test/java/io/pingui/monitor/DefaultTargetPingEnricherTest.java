package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.RouteSnapshot;
import java.io.IOException;
import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class DefaultTargetPingEnricherTest {

    @Test
    void enrichesTerminalHopWhenRttMissing() {
        RecordingPinger pinger = new RecordingPinger().withRtt(12.5);
        DefaultTargetPingEnricher enricher = new DefaultTargetPingEnricher(pinger);
        RouteSnapshot snapshot = new RouteSnapshot(
                "8.8.8.8",
                "8.8.8.8",
                List.of(new HopNode(1, "10.0.0.1", null, false), new HopNode(2, "8.8.8.8", null, false)));

        RouteSnapshot result = enricher.enrich(snapshot, 1.0);

        assertEquals(1, pinger.calls);
        assertEquals("8.8.8.8", pinger.lastTarget);
        assertNull(result.nodes().get(0).pingMs());
        assertEquals(12.5, result.nodes().get(1).pingMs());
    }

    @Test
    void leavesSnapshotWhenTerminalAlreadyHasRtt() {
        RecordingPinger pinger = new RecordingPinger().withRtt(1.0);
        DefaultTargetPingEnricher enricher = new DefaultTargetPingEnricher(pinger);
        RouteSnapshot snapshot =
                new RouteSnapshot("8.8.8.8", "8.8.8.8", List.of(new HopNode(1, "8.8.8.8", 5.0, false)));

        RouteSnapshot result = enricher.enrich(snapshot, 1.0);

        assertEquals(0, pinger.calls);
        assertEquals(5.0, result.nodes().get(0).pingMs());
    }

    @Test
    void usesSnapshotTargetHostnameForPing() {
        RecordingPinger pinger = new RecordingPinger().withRtt(3.0);
        DefaultTargetPingEnricher enricher = new DefaultTargetPingEnricher(pinger);
        RouteSnapshot snapshot = new RouteSnapshot(
                "dns.google",
                "8.8.8.8",
                List.of(new HopNode(1, "10.0.0.1", null, false), new HopNode(2, "8.8.4.4", null, false)));

        enricher.enrich(snapshot, 1.0);

        assertEquals("dns.google", pinger.lastTarget);
    }

    @Test
    void pingFailureMarksTerminalTimeout() {
        DefaultTargetPingEnricher enricher = new DefaultTargetPingEnricher((target, timeout) -> {
            throw new io.pingui.config.ConfigError("no route");
        });
        RouteSnapshot snapshot =
                new RouteSnapshot("8.8.8.8", "8.8.8.8", List.of(new HopNode(1, "8.8.8.8", null, false)));

        RouteSnapshot result = enricher.enrich(snapshot, 1.0);

        assertTrue(result.nodes().get(0).timeout());
    }

    private static final class RecordingPinger implements DefaultTargetPingEnricher.HostPinger {
        private OptionalDouble rtt = OptionalDouble.empty();
        int calls;
        String lastTarget;

        RecordingPinger withRtt(double value) {
            rtt = OptionalDouble.of(value);
            return this;
        }

        @Override
        public OptionalDouble pingOnce(String target, double timeoutSeconds) throws IOException {
            calls++;
            lastTarget = target;
            return rtt;
        }
    }
}
