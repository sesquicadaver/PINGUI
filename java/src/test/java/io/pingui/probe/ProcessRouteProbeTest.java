package io.pingui.probe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.model.Models;
import io.pingui.model.Models.HopNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProcessRouteProbeTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "unix_ok.txt",
                "unix_timeout.txt",
                "unix_hostname.txt",
                "win_ok.txt",
                "win_timeout.txt",
                "win_hostname.txt",
            })
    void v4FixturesRemainGreen(String fixture) throws IOException {
        List<HopNode> hops = fixture.startsWith("unix")
                ? ProcessRouteProbe.parseUnix(loadLines(fixture))
                : ProcessRouteProbe.parseWindows(loadLines(fixture));
        assertFalse(hops.isEmpty(), "fixture must parse hops: " + fixture);
    }

    @Test
    void parseUnix_okFixture() throws IOException {
        List<HopNode> hops = ProcessRouteProbe.parseUnix(loadLines("unix_ok.txt"));
        assertEquals(3, hops.size());
        assertEquals(1, hops.get(0).hop());
        assertEquals("192.168.0.1", hops.get(0).ip());
        assertEquals(1.234, hops.get(0).pingMs(), 0.001);
        assertEquals("8.8.8.8", hops.get(2).ip());
    }

    @Test
    void parseUnix_timeoutHop() throws IOException {
        List<HopNode> hops = ProcessRouteProbe.parseUnix(loadLines("unix_timeout.txt"));
        assertEquals(3, hops.size());
        assertTrue(hops.get(1).timeout());
        assertEquals("*", hops.get(1).ip());
    }

    @Test
    void parseUnix_hostnameToken() throws IOException {
        List<HopNode> hops = ProcessRouteProbe.parseUnix(loadLines("unix_hostname.txt"));
        assertEquals(2, hops.size());
        assertEquals("gw.example.net", hops.get(0).ip());
    }

    @Test
    void parseWindows_okFixture() throws IOException {
        List<HopNode> hops = ProcessRouteProbe.parseWindows(loadLines("win_ok.txt"));
        assertEquals(3, hops.size());
        assertEquals("192.168.0.1", hops.get(0).ip());
        assertEquals(0.5, hops.get(0).pingMs(), 0.001);
        assertEquals("8.8.8.8", hops.get(1).ip());
        assertNotNull(hops.get(1).pingMs());
    }

    @Test
    void parseWindows_timeoutLine() throws IOException {
        List<HopNode> hops = ProcessRouteProbe.parseWindows(loadLines("win_timeout.txt"));
        assertEquals(3, hops.size());
        assertTrue(hops.get(1).timeout());
    }

    @Test
    void parseWindows_hostnameWithBracketIp() throws IOException {
        List<HopNode> hops = ProcessRouteProbe.parseWindows(loadLines("win_hostname.txt"));
        assertEquals(2, hops.size());
        assertEquals("192.168.1.1", hops.get(0).ip());
        assertEquals("142.250.185.78", hops.get(1).ip());
    }

    @Test
    void windowsTracertWaitMs_minimum4000() {
        assertEquals(4000, ProcessRouteProbe.windowsTracertWaitMs(0.5));
        assertEquals(4000, ProcessRouteProbe.windowsTracertWaitMs(1.0));
        assertEquals(5000, ProcessRouteProbe.windowsTracertWaitMs(5.0));
    }

    @Test
    void computeProcessWaitMs_windowsUsesThreeProbesPerHop() {
        long wait = ProcessRouteProbe.computeProcessWaitMs(true, 20, 0.5);
        assertTrue(wait >= 20L * 3 * 4000);
    }

    @Test
    void parseWindowsRtt_lessThanOneMs() {
        Double rtt = ProcessRouteProbe.parseWindowsRtt("  1    <1 ms    <1 ms    <1 ms  10.0.0.1 ");
        assertNotNull(rtt);
        assertEquals(0.5, rtt, 0.001);
    }

    @Test
    void parseUnixIpv6Fixture() throws IOException {
        List<HopNode> hops = ProcessRouteProbe.parseUnix(loadLines("unix_v6_ok.txt"));
        assertEquals(2, hops.size());
        assertEquals("2001:db8:1::1", hops.get(0).ip());
        assertEquals("2001:4860:4860::8888", hops.get(1).ip());
    }

    @Test
    void parseUnixIpv6BracketFixture() throws IOException {
        List<HopNode> hops = ProcessRouteProbe.parseUnix(loadLines("unix_v6_bracket.txt"));
        assertEquals("2001:4860:4860::8888", hops.get(1).ip());
    }

    @Test
    void parseUnixIpv6TimeoutFixture() throws IOException {
        List<HopNode> hops = ProcessRouteProbe.parseUnix(loadLines("unix_v6_timeout.txt"));
        assertEquals(3, hops.size());
        assertEquals(Models.TIMEOUT_IP, hops.get(1).ip());
    }

    @Test
    void parseWindowsIpv6Fixture() throws IOException {
        List<HopNode> hops = ProcessRouteProbe.parseWindows(loadLines("win_v6_ok.txt"));
        assertEquals(3, hops.size());
        assertEquals("2001:db8:1::1", hops.get(0).ip());
        assertEquals("2001:4860:4860::8888", hops.get(2).ip());
    }

    @Test
    void parseWindowsIpv6TimeoutFixture() throws IOException {
        List<HopNode> hops = ProcessRouteProbe.parseWindows(loadLines("win_v6_timeout.txt"));
        assertEquals(3, hops.size());
        assertEquals(Models.TIMEOUT_IP, hops.get(1).ip());
    }

    private static List<String> loadLines(String resource) throws IOException {
        try (var stream = ProcessRouteProbeTest.class.getResourceAsStream("/trace/" + resource)) {
            if (stream == null) {
                throw new IOException("Missing fixture: " + resource);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .toList();
        }
    }
}
