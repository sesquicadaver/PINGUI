package io.pingui.probe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.pingui.model.Models;
import io.pingui.model.Models.HopNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProcessRouteProbeParserTest {
    @Test
    void parseUnixLines() {
        List<String> lines = List.of(
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
    void parseUnixLinesWithoutRtt() {
        List<String> lines = List.of(" 1  10.0.0.2");
        List<HopNode> nodes = ProcessRouteProbe.parseUnix(lines);
        assertEquals(1, nodes.size());
        assertEquals("10.0.0.2", nodes.get(0).ip());
        assertEquals(null, nodes.get(0).pingMs());
    }

    @Test
    void parseGnuInetutilsLines() {
        List<String> lines =
                List.of("traceroute to 8.8.8.8 (8.8.8.8), 5 hops max", "  1   10.6.0.1  31.477ms ", "  2   *");
        List<HopNode> nodes = ProcessRouteProbe.parseUnix(lines);
        assertEquals(2, nodes.size());
        assertEquals("10.6.0.1", nodes.get(0).ip());
        assertEquals(31.477, nodes.get(0).pingMs());
        assertEquals(Models.TIMEOUT_IP, nodes.get(1).ip());
    }

    @Test
    void parseWindowsLines() {
        List<String> lines = List.of(
                "Tracing route to 8.8.8.8 over a maximum of 30 hops",
                "  1     1 ms     1 ms     1 ms  192.168.1.1",
                "  2     *        *        *     Request timed out.");
        List<HopNode> nodes = ProcessRouteProbe.parseWindows(lines);
        assertEquals(2, nodes.size());
        assertEquals("192.168.1.1", nodes.get(0).ip());
        assertEquals(Models.TIMEOUT_IP, nodes.get(1).ip());
    }

    @Test
    void parseWindowsLinesWithRtt() {
        List<String> lines = List.of("  1     2 ms     3 ms     4 ms  192.168.1.1");
        List<HopNode> nodes = ProcessRouteProbe.parseWindows(lines);
        assertEquals(1, nodes.size());
        assertEquals("192.168.1.1", nodes.get(0).ip());
        assertEquals(2.0, nodes.get(0).pingMs());
    }

    @Test
    void parseWindowsLinesWithSubMillisecondRtt() {
        List<String> lines = List.of("  1    <1 ms    <1 ms    <1 ms  192.168.0.1");
        List<HopNode> nodes = ProcessRouteProbe.parseWindows(lines);
        assertEquals(1, nodes.size());
        assertEquals("192.168.0.1", nodes.get(0).ip());
        assertEquals(0.5, nodes.get(0).pingMs());
    }

    @Test
    void parseWindowsLinesWithHostnameAndBracketIp() {
        List<String> lines = List.of("  9    50 ms    52 ms    47 ms  ae-39.example.net [128.241.219.117]");
        List<HopNode> nodes = ProcessRouteProbe.parseWindows(lines);
        assertEquals(1, nodes.size());
        assertEquals("128.241.219.117", nodes.get(0).ip());
        assertEquals(50.0, nodes.get(0).pingMs());
    }
}
