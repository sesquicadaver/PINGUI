package io.pingui.probe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.config.ConfigError;
import io.pingui.config.PingExpertEntry;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExpertPingArgsTest {

    @Test
    void autoAddsIpv6FlagForV6Literal() {
        List<String> args = ExpertPingArgs.forTarget("2001:db8::1", PingExpertEntry.empty());
        assertEquals(List.of("-6"), args);
    }

    @Test
    void preservesExplicitExpertArgs() {
        PingExpertEntry expert = new PingExpertEntry(false, List.of("-n", "-s", "64"));
        List<String> args = ExpertPingArgs.forTarget("2001:db8::1", expert);
        assertTrue(args.contains("-6"));
        assertTrue(args.contains("-n"));
        assertTrue(args.contains("-s"));
    }

    @Test
    void rejectsIpv4FlagOnV6Target() {
        PingExpertEntry expert = new PingExpertEntry(false, List.of("-4"));
        assertThrows(ConfigError.class, () -> ExpertPingArgs.forTarget("2001:db8::1", expert));
    }

    @Test
    void rejectsIpv6FlagOnV4Target() {
        PingExpertEntry expert = new PingExpertEntry(false, List.of("-6"));
        assertThrows(ConfigError.class, () -> ExpertPingArgs.forTarget("8.8.8.8", expert));
    }

    @Test
    void noAutoFlagForHostname() {
        assertEquals(List.of(), ExpertPingArgs.forTarget("example.com", PingExpertEntry.empty()));
    }
}
