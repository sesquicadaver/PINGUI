package io.pingui.probe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.pingui.model.Models.RouteSnapshot;
import io.pingui.probe.icmp.IcmpProbeTransport;
import io.pingui.probe.icmp.ProbeResult;
import io.pingui.probe.icmp.ProbeResult;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RawIcmpRouteProbeTest {
    @Test
    void traceUsesTransportPerTtl() throws Exception {
        Map<Integer, ProbeResult> responses = new HashMap<>();
        responses.put(1, new ProbeResult("10.0.0.1", 5.0, false));
        responses.put(2, new ProbeResult("8.8.8.8", 10.0, true));
        RawIcmpRouteProbe probe = new RawIcmpRouteProbe(() -> new MapIcmpTransport(responses));
        var snapshot = probe.trace("8.8.8.8", 5, 0.5);
        assertEquals(2, snapshot.nodes().size());
        assertEquals("10.0.0.1", snapshot.nodes().get(0).ip());
        assertEquals("8.8.8.8", snapshot.nodes().get(1).ip());
        assertFalse(snapshot.nodes().get(0).timeout());
    }

    private static final class MapIcmpTransport implements IcmpProbeTransport {
        private final Map<Integer, ProbeResult> responses;

        MapIcmpTransport(Map<Integer, ProbeResult> responses) {
            this.responses = responses;
        }

        @Override
        public ProbeResult sendProbe(String targetIp, int ttl, double timeoutSeconds) {
            return responses.get(ttl);
        }

        @Override
        public void close() {}
    }
}
