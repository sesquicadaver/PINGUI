package io.pingui.probe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MtuDiscoveryTest {

    @Test
    void ipv4MtuAdds28BytesOverhead() {
        assertEquals(1500, MtuDiscoveryConfig.ipv4Defaults().mtuForPayload(1472));
        assertEquals(28, MtuDiscoveryConfig.IPV4_ICMP_OVERHEAD);
    }

    @Test
    void ipv6MtuAdds48BytesOverhead() {
        assertEquals(1500, MtuDiscoveryConfig.ipv6Defaults().mtuForPayload(1452));
        assertEquals(48, MtuDiscoveryConfig.IPV6_ICMP_OVERHEAD);
    }

    @Test
    void ascendingStopsOnFirstLossySizeAndKeepsMaxGood() throws Exception {
        // Good for 48, 64, 80; fail for 96+
        MtuDiscoveryConfig config = new MtuDiscoveryConfig(96, 48, 16, 10, 1.0, false, 0.5);
        MtuProbeRunner runner = (target, payload, ipv6, timeout) -> payload <= 80;
        MtuDiscoveryResult result = new MtuDiscovery(runner).discover("1.1.1.1", config);

        assertTrue(result.stoppedOnLoss());
        assertEquals(80, result.maxGoodPayload().orElseThrow());
        assertEquals(108, result.recommendedMtu().orElseThrow()); // 80 + 28
        assertEquals(4, result.steps().size()); // 48,64,80 ok + 96 stop
        assertTrue(result.steps().get(3).stoppedHere());
        assertFalse(result.steps().get(2).stoppedHere());
    }

    @Test
    void allSizesSucceedRecommendedIsStartPayload() throws Exception {
        MtuDiscoveryConfig config = new MtuDiscoveryConfig(80, 48, 16, 5, 1.0, false, 0.5);
        MtuProbeRunner runner = (target, payload, ipv6, timeout) -> true;
        MtuDiscoveryResult result = new MtuDiscovery(runner).discover("8.8.8.8", config);

        assertFalse(result.stoppedOnLoss());
        assertEquals(80, result.maxGoodPayload().orElseThrow());
        assertEquals(108, result.recommendedMtu().orElseThrow());
        assertEquals(3, result.steps().size()); // 48, 64, 80
    }

    @Test
    void floorFailsStopsWithEmptyRecommendation() throws Exception {
        // Fail at min payload → stop immediately (no lastGood); larger "ok" sizes never probed
        MtuDiscoveryConfig config = new MtuDiscoveryConfig(96, 48, 16, 10, 1.0, false, 0.5);
        MtuProbeRunner runner = (target, payload, ipv6, timeout) -> payload >= 64;
        MtuDiscoveryResult result = new MtuDiscovery(runner).discover("host", config);

        assertTrue(result.stoppedOnLoss());
        assertTrue(result.maxGoodPayload().isEmpty());
        assertTrue(result.recommendedMtu().isEmpty());
        assertEquals(1, result.steps().size());
    }

    @Test
    void onePercentLossStopsEvenIfMostProbesSucceed() throws Exception {
        MtuDiscoveryConfig config = new MtuDiscoveryConfig(64, 64, 16, 10, 1.0, false, 0.5);
        AtomicInteger n = new AtomicInteger();
        MtuProbeRunner runner = (target, payload, ipv6, timeout) -> n.getAndIncrement() != 0;
        MtuDiscoveryResult result = new MtuDiscovery(runner).discover("host", config);

        assertTrue(result.stoppedOnLoss());
        assertTrue(result.maxGoodPayload().isEmpty());
        assertEquals(10.0, result.steps().get(0).lossPct(), 1e-9);
    }

    @Test
    void cancelAbortsBetweenSizes() throws Exception {
        MtuDiscoveryConfig config = new MtuDiscoveryConfig(96, 48, 16, 2, 1.0, false, 0.5);
        AtomicInteger calls = new AtomicInteger();
        MtuProbeRunner runner = (target, payload, ipv6, timeout) -> {
            calls.incrementAndGet();
            return true;
        };
        MtuDiscoveryResult result = new MtuDiscovery(runner).discover("t", config, () -> calls.get() >= 2);

        assertTrue(result.cancelled());
        assertEquals(2, calls.get());
        assertEquals(48, result.maxGoodPayload().orElseThrow());
        assertEquals(1, result.steps().size());
    }

    @Test
    void rejectsBlankTarget() {
        MtuDiscovery discovery = new MtuDiscovery((t, p, v6, to) -> true);
        assertThrows(IllegalArgumentException.class, () -> discovery.discover("  ", MtuDiscoveryConfig.ipv4Defaults()));
    }

    @Test
    void configRejectsInvalidStep() {
        assertThrows(IllegalArgumentException.class, () -> new MtuDiscoveryConfig(100, 50, 0, 10, 1.0, false, 1.0));
    }
}
