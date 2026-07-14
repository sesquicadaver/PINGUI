package io.pingui.probe;

import io.pingui.config.PingExpertEntry;
import java.io.IOException;
import java.util.List;

/**
 * {@link MtuProbeRunner} backed by {@link ProcessExpertPing} with {@code -M do -s &lt;payload&gt;}
 * (P17-020).
 */
public final class ProcessMtuProbeRunner implements MtuProbeRunner {
    private final ProcessExpertPing ping;

    public ProcessMtuProbeRunner() {
        this(new ProcessExpertPing());
    }

    public ProcessMtuProbeRunner(ProcessExpertPing ping) {
        this.ping = ping != null ? ping : new ProcessExpertPing();
    }

    @Override
    public boolean pingOnce(String target, int payloadBytes, boolean ipv6, double timeoutSeconds) throws IOException {
        List<String> args = List.of(ipv6 ? "-6" : "-4", "-M", "do", "-s", String.valueOf(payloadBytes));
        PingExpertEntry expert = new PingExpertEntry(false, PingExpertValidator.validateAndNormalize(args));
        return ping.pingOnce(target, expert, timeoutSeconds).isPresent();
    }
}
