package io.pingui.dns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for forward DNS control (P29-004). */
class DnsControlTest {
    private static final Instant T0 = Instant.parse("2026-09-03T12:00:00Z");

    @Test
    void classifyMapsUnknownHostToNxdomain() {
        assertEquals(DnsLookupOutcome.NXDOMAIN, DnsControl.classify(new UnknownHostException("no such host")));
    }

    @Test
    void classifyMapsTimeoutAndServfailFromMessage() {
        assertEquals(DnsLookupOutcome.TIMEOUT, DnsControl.classify(new UnknownHostException("lookup timed out for x")));
        assertEquals(
                DnsLookupOutcome.SERVFAIL, DnsControl.classify(new UnknownHostException("SERVFAIL from resolver")));
        assertEquals(DnsLookupOutcome.TIMEOUT, DnsControl.classify(new SocketTimeoutException("dns")));
    }

    @Test
    void lookupReturnsSortedAddressSetAndOk() throws Exception {
        InetAddress v4 = InetAddress.getByName("1.1.1.1");
        InetAddress v6 = InetAddress.getByName("2001:db8::1");
        DnsObservation observation = DnsControl.lookup("example.test", hostname -> new InetAddress[] {v6, v4}, T0);
        assertEquals(DnsLookupOutcome.OK, observation.outcome());
        assertEquals(2, observation.addresses().size());
        assertEquals("1.1.1.1", observation.addresses().get(0));
        assertTrue(observation.addresses().get(1).contains("2001:db8"));
    }

    @Test
    void trackerSkipsIpLiterals() {
        DnsControlTracker tracker = new DnsControlTracker(hostname -> {
            throw new AssertionError("must not resolve literals");
        });
        assertTrue(tracker.observe("8.8.8.8", T0).isEmpty());
        assertTrue(tracker.observe("2001:db8::1", T0).isEmpty());
    }

    @Test
    void trackerEmitsOkThenSuppressesUnchangedThenChangeAndNxdomain() throws Exception {
        InetAddress first = InetAddress.getByName("9.9.9.9");
        InetAddress second = InetAddress.getByName("1.0.0.1");
        ForwardDnsLookup[] lookups = new ForwardDnsLookup[1];
        lookups[0] = hostname -> new InetAddress[] {first};
        DnsControlTracker tracker = new DnsControlTracker(hostname -> lookups[0].resolve(hostname));

        Optional<DnsControlEvent> baseline = tracker.observe("dns.example", T0);
        assertTrue(baseline.isPresent());
        assertEquals("ok", baseline.get().state());
        assertTrue(baseline.get().message().contains("9.9.9.9"));
        assertTrue(baseline.get().detailJson().contains("resolve_ms"));

        assertTrue(tracker.observe("dns.example", T0.plusSeconds(1)).isEmpty());

        lookups[0] = hostname -> new InetAddress[] {second};
        Optional<DnsControlEvent> change = tracker.observe("dns.example", T0.plusSeconds(2));
        assertTrue(change.isPresent());
        assertEquals("change", change.get().state());
        assertEquals(List.of("9.9.9.9"), change.get().previousAddresses());
        assertEquals(List.of("1.0.0.1"), change.get().addresses());

        lookups[0] = hostname -> {
            throw new UnknownHostException("nxdomain");
        };
        Optional<DnsControlEvent> nx = tracker.observe("dns.example", T0.plusSeconds(3));
        assertTrue(nx.isPresent());
        assertEquals("nxdomain", nx.get().state());
        assertTrue(nx.get().addresses().isEmpty());
        assertFalse(nx.get().message().isBlank());
    }
}
