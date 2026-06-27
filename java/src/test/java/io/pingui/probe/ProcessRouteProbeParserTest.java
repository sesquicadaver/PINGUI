package io.pingui.probe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.pingui.model.Models;
import io.pingui.model.Models.HopNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProcessRouteProbeParserTest {
    @Test
    void parseUnixLines() {
        List<String> lines =
                List.of(
                        "traceroute to 8.8.8.8 (8.8.8.8), 30 hops max",
                        " 1  10.0.0.1  5.678 ms",
                        " 2  * * *",
                        " 3  8.8.8.8  10.123 ms");
        List<HopNode> nodes = ProcessRouteProbe.parseUnix(lines);
        assertEquals(3, nodes.size());
        assertEquals("10.0.0.1", nodes.get(0).ip());
        assertEquals(5.678, nodes.get(0).pingMs());
        assertEquals(Models.TIMEOUT_IP, nodes.get(1).ip());
        assertEquals("8.8.8.8", nodes.get(2).ip());
    }

    @Test
    void parseWindowsLines() {
        List<String> lines =
                List.of(
                        "Tracing route to 8.8.8.8 over a maximum of 30 hops",
                        "  1     1 ms     1 ms     1 ms  192.168.1.1",
                        "  2     *        *        *     Request timed out.");
        List<HopNode> nodes = ProcessRouteProbe.parseWindows(lines);
        assertEquals(2, nodes.size());
        assertEquals("192.168.1.1", nodes.get(0).ip());
        assertEquals(Models.TIMEOUT_IP, nodes.get(1).ip());
    }
}
