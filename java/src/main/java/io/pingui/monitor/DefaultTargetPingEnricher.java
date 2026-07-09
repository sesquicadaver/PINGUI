package io.pingui.monitor;

import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.RouteSnapshot;
import io.pingui.probe.ProcessHostPing;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Fills missing RTT on the terminal hop via one-shot {@code ping} when Expert ping is not configured.
 *
 * <p>Traceroute often returns hop IPs without RTT; without this step {@code ping_history_json} stays empty.
 */
public final class DefaultTargetPingEnricher {
    @FunctionalInterface
    interface HostPinger {
        OptionalDouble pingOnce(String target, double timeoutSeconds) throws IOException;
    }

    private final HostPinger hostPing;

    public DefaultTargetPingEnricher() {
        this(new ProcessHostPing()::pingOnce);
    }

    DefaultTargetPingEnricher(HostPinger hostPing) {
        this.hostPing = hostPing;
    }

    public RouteSnapshot enrich(RouteSnapshot snapshot, double timeoutSeconds) {
        List<HopNode> nodes = snapshot.nodes();
        if (nodes.isEmpty()) {
            return snapshot;
        }
        int lastIndex = nodes.size() - 1;
        HopNode terminal = nodes.get(lastIndex);
        if (!terminal.isReachable() || terminal.pingMs() != null) {
            return snapshot;
        }
        String pingTarget =
                snapshot.target() != null && !snapshot.target().isBlank() ? snapshot.target() : terminal.ip();
        HopNode enriched = enrichTerminal(terminal, pingTarget, timeoutSeconds);
        if (enriched == terminal) {
            return snapshot;
        }
        List<HopNode> updated = new ArrayList<>(nodes);
        updated.set(lastIndex, enriched);
        return new RouteSnapshot(snapshot.target(), snapshot.targetIp(), updated, snapshot.timestamp());
    }

    private HopNode enrichTerminal(HopNode terminal, String pingTarget, double timeoutSeconds) {
        if (pingTarget == null || pingTarget.isBlank()) {
            return terminal;
        }
        try {
            OptionalDouble rtt = hostPing.pingOnce(pingTarget, timeoutSeconds);
            if (rtt.isPresent()) {
                return new HopNode(terminal.hop(), terminal.ip(), rtt.getAsDouble(), false);
            }
            return new HopNode(terminal.hop(), terminal.ip(), null, true);
        } catch (IOException ex) {
            return new HopNode(terminal.hop(), terminal.ip(), null, true);
        } catch (RuntimeException ex) {
            return new HopNode(terminal.hop(), terminal.ip(), null, true);
        }
    }
}
