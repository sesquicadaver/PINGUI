package io.pingui.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ModelsTest {
    @Test
    void hopNodeRejectsInvalidHop() {
        assertThrows(IllegalArgumentException.class, () -> new Models.HopNode(0, "1.1.1.1", 1.0, false));
    }

    @Test
    void timeoutFactoryMarksUnreachable() {
        Models.HopNode node = Models.timeout(3);
        assertEquals(Models.TIMEOUT_IP, node.ip());
        assertTrue(node.timeout());
        assertFalse(node.isReachable());
    }

    @Test
    void routeSnapshotFiltersUnreachableHops() {
        Models.RouteSnapshot snapshot =
                new Models.RouteSnapshot(
                        "8.8.8.8",
                        "8.8.8.8",
                        List.of(
                                new Models.HopNode(1, "10.0.0.1", 5.0, false),
                                Models.timeout(2),
                                new Models.HopNode(3, "8.8.8.8", 10.0, false)));
        assertEquals(List.of("10.0.0.1", "8.8.8.8"), snapshot.routeIps());
    }
}
