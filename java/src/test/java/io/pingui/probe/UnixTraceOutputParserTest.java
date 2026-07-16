package io.pingui.probe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.pingui.model.Models;
import io.pingui.model.Models.HopNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Direct unit tests for {@link UnixTraceOutputParser} (P19-003). */
class UnixTraceOutputParserTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "unix_ok.txt",
                "unix_timeout.txt",
                "unix_hostname.txt",
                "unix_v6_ok.txt",
                "unix_v6_bracket.txt",
                "unix_v6_timeout.txt",
            })
    void fixtureParsesAtLeastOneHop(String fixture) throws IOException {
        List<HopNode> hops = UnixTraceOutputParser.parse(loadLines(fixture));
        assertFalse(hops.isEmpty(), "fixture must parse hops: " + fixture);
    }

    @Test
    void okFixtureExtractsIpsAndRtt() throws IOException {
        List<HopNode> hops = UnixTraceOutputParser.parse(loadLines("unix_ok.txt"));
        assertEquals(3, hops.size());
        assertEquals(1, hops.get(0).hop());
        assertEquals("192.168.0.1", hops.get(0).ip());
        assertEquals(1.234, hops.get(0).pingMs(), 0.001);
        assertEquals("8.8.8.8", hops.get(2).ip());
    }

    @Test
    void timeoutHopUsesStarToken() throws IOException {
        List<HopNode> hops = UnixTraceOutputParser.parse(loadLines("unix_timeout.txt"));
        assertEquals(3, hops.size());
        assertEquals(Models.TIMEOUT_IP, hops.get(1).ip());
        assertEquals(true, hops.get(1).timeout());
    }

    @Test
    void hostnameTokenPreserved() throws IOException {
        List<HopNode> hops = UnixTraceOutputParser.parse(loadLines("unix_hostname.txt"));
        assertEquals("gw.example.net", hops.get(0).ip());
    }

    @Test
    void ipv6BracketStripped() throws IOException {
        List<HopNode> hops = UnixTraceOutputParser.parse(loadLines("unix_v6_bracket.txt"));
        assertEquals("2001:4860:4860::8888", hops.get(1).ip());
    }

    @Test
    void lineWithoutRttLeavesPingNull() {
        List<HopNode> hops = UnixTraceOutputParser.parse(List.of(" 1  10.0.0.2"));
        assertEquals(1, hops.size());
        assertEquals("10.0.0.2", hops.get(0).ip());
        assertNull(hops.get(0).pingMs());
    }

    @Test
    void gnuInetutilsMsSuffixParsed() {
        List<String> lines = List.of("  1   10.6.0.1  31.477ms ", "  2   *");
        List<HopNode> hops = UnixTraceOutputParser.parse(lines);
        assertEquals(2, hops.size());
        assertEquals(31.477, hops.get(0).pingMs(), 0.001);
        assertEquals(Models.TIMEOUT_IP, hops.get(1).ip());
    }

    private static List<String> loadLines(String resource) throws IOException {
        try (var stream = UnixTraceOutputParserTest.class.getResourceAsStream("/trace/" + resource)) {
            if (stream == null) {
                throw new IOException("Missing fixture: " + resource);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .toList();
        }
    }
}
