package io.pingui.probe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.model.Models;
import io.pingui.model.Models.HopNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Direct unit tests for {@link WindowsTraceOutputParser} (P19-003). */
class WindowsTraceOutputParserTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "win_ok.txt",
                "win_timeout.txt",
                "win_hostname.txt",
                "win_v6_ok.txt",
                "win_v6_timeout.txt",
            })
    void fixtureParsesAtLeastOneHop(String fixture) throws IOException {
        List<HopNode> hops = WindowsTraceOutputParser.parse(loadLines(fixture));
        assertFalse(hops.isEmpty(), "fixture must parse hops: " + fixture);
    }

    @Test
    void okFixtureExtractsIpsAndRtt() throws IOException {
        List<HopNode> hops = WindowsTraceOutputParser.parse(loadLines("win_ok.txt"));
        assertEquals(3, hops.size());
        assertEquals("192.168.0.1", hops.get(0).ip());
        assertEquals(0.5, hops.get(0).pingMs(), 0.001);
        assertEquals("8.8.8.8", hops.get(1).ip());
        assertNotNull(hops.get(1).pingMs());
    }

    @Test
    void timeoutLineMarkedAsTimeout() throws IOException {
        List<HopNode> hops = WindowsTraceOutputParser.parse(loadLines("win_timeout.txt"));
        assertEquals(3, hops.size());
        assertTrue(hops.get(1).timeout());
        assertEquals(Models.TIMEOUT_IP, hops.get(1).ip());
    }

    @Test
    void hostnameWithBracketIp() throws IOException {
        List<HopNode> hops = WindowsTraceOutputParser.parse(loadLines("win_hostname.txt"));
        assertEquals("142.250.185.78", hops.get(1).ip());
    }

    @Test
    void ipv6BareAndBracket() throws IOException {
        List<HopNode> hops = WindowsTraceOutputParser.parse(loadLines("win_v6_ok.txt"));
        assertEquals("2001:db8:1::1", hops.get(0).ip());
        assertEquals("2001:4860:4860::8888", hops.get(2).ip());
    }

    @Test
    void isTimeoutLineDetectsEnglishAndUkrainian() {
        assertTrue(WindowsTraceOutputParser.isTimeoutLine("* * *     Request timed out."));
        assertTrue(WindowsTraceOutputParser.isTimeoutLine("* * *     Перевищено час очікування."));
        assertFalse(WindowsTraceOutputParser.isTimeoutLine("dns.google [8.8.8.8]"));
    }

    @Test
    void extractIpPrefersBracketOverBare() {
        assertEquals("128.241.219.117", WindowsTraceOutputParser.extractIp("ae-39.example.net [128.241.219.117]"));
        assertEquals("2001:4860:4860::8888", WindowsTraceOutputParser.extractIp("dns [2001:4860:4860::8888]"));
        assertEquals("2001:db8:1::1", WindowsTraceOutputParser.extractIp("2001:db8:1::1"));
        assertNull(WindowsTraceOutputParser.extractIp("no address here"));
    }

    @Test
    void parseRttHandlesSubMillisecondAndNumeric() {
        assertEquals(0.5, WindowsTraceOutputParser.parseRtt("  1    <1 ms    <1 ms    <1 ms  10.0.0.1 "));
        assertEquals(50.0, WindowsTraceOutputParser.parseRtt("  9    50 ms    52 ms    47 ms  host [1.2.3.4]"));
        assertNull(WindowsTraceOutputParser.parseRtt("  1     *        *        *     Request timed out."));
    }

    private static List<String> loadLines(String resource) throws IOException {
        try (var stream = WindowsTraceOutputParserTest.class.getResourceAsStream("/trace/" + resource)) {
            if (stream == null) {
                throw new IOException("Missing fixture: " + resource);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .toList();
        }
    }
}
