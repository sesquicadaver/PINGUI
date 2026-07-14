package io.pingui.probe;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.config.PingExpertEntry;
import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ProcessMtuProbeRunnerTest {
    @Test
    void usesDontFragmentAndPayloadSizeWithAddressFamily() throws Exception {
        AtomicReference<PingExpertEntry> seen = new AtomicReference<>();
        ProcessExpertPing stub = new ProcessExpertPing() {
            @Override
            public OptionalDouble pingOnce(String target, PingExpertEntry expert, double timeoutSeconds) {
                seen.set(expert);
                return OptionalDouble.of(1.0);
            }
        };
        ProcessMtuProbeRunner runner = new ProcessMtuProbeRunner(stub);
        assertTrue(runner.pingOnce("8.8.8.8", 1472, false, 0.5));
        List<String> args = seen.get().args();
        assertTrue(args.contains("-4"));
        assertTrue(args.contains("-M"));
        assertTrue(args.contains("do"));
        assertTrue(args.contains("-s"));
        assertTrue(args.contains("1472"));
    }

    @Test
    void ipv6FlagWhenRequested() throws Exception {
        AtomicReference<PingExpertEntry> seen = new AtomicReference<>();
        ProcessExpertPing stub = new ProcessExpertPing() {
            @Override
            public OptionalDouble pingOnce(String target, PingExpertEntry expert, double timeoutSeconds) {
                seen.set(expert);
                return OptionalDouble.empty();
            }
        };
        assertTrue(!new ProcessMtuProbeRunner(stub).pingOnce("2001:db8::1", 1452, true, 0.5));
        assertTrue(seen.get().args().contains("-6"));
        assertTrue(seen.get().args().contains("1452"));
    }
}
