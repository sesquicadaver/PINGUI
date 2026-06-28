package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.pingui.config.PingExpertEntry;
import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.RouteSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExpertPingEnricherTest {
    private final ExpertPingEnricher enricher = new ExpertPingEnricher();

    @Test
    void returnsSnapshotWhenExpertNotConfigured() {
        RouteSnapshot snapshot =
                new RouteSnapshot(
                        "8.8.8.8",
                        "8.8.8.8",
                        List.of(new HopNode(1, "192.168.1.1", 1.0, false), new HopNode(2, "8.8.8.8", 2.0, false)));
        RouteSnapshot result = enricher.enrich(snapshot, PingExpertEntry.empty(), 0.5);
        assertEquals(snapshot.nodes(), result.nodes());
    }
}
