package io.pingui.monitor;

import io.pingui.config.PingExpertEntry;
import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.RouteSnapshot;
import io.pingui.probe.ProcessExpertPing;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

/** Replaces hop RTT with expert {@code ping} measurements when configured. */
public final class ExpertPingEnricher {
    private final ProcessExpertPing ping;

    public ExpertPingEnricher() {
        this(new ProcessExpertPing());
    }

    ExpertPingEnricher(ProcessExpertPing ping) {
        this.ping = ping;
    }

    public RouteSnapshot enrich(RouteSnapshot snapshot, PingExpertEntry expert, double timeoutSeconds) {
        if (expert == null || !expert.isConfigured()) {
            return snapshot;
        }
        List<HopNode> nodes = snapshot.nodes();
        if (nodes.isEmpty()) {
            return snapshot;
        }
        List<HopNode> updated = new ArrayList<>(nodes.size());
        if (expert.applyToChain()) {
            for (HopNode node : nodes) {
                updated.add(enrichNode(node, node.ip(), expert, timeoutSeconds));
            }
        } else {
            for (int i = 0; i < nodes.size(); i++) {
                HopNode node = nodes.get(i);
                if (i == nodes.size() - 1) {
                    String target = snapshot.target() != null ? snapshot.target() : node.ip();
                    updated.add(enrichNode(node, target, expert, timeoutSeconds));
                } else {
                    updated.add(node);
                }
            }
        }
        return new RouteSnapshot(snapshot.target(), snapshot.targetIp(), updated, snapshot.timestamp());
    }

    private HopNode enrichNode(HopNode node, String pingTarget, PingExpertEntry expert, double timeoutSeconds) {
        if (!node.isReachable() || pingTarget == null || pingTarget.isBlank()) {
            return node;
        }
        try {
            OptionalDouble rtt = ping.pingOnce(pingTarget, expert, timeoutSeconds);
            if (rtt.isPresent()) {
                return new HopNode(node.hop(), node.ip(), rtt.getAsDouble(), false);
            }
            return new HopNode(node.hop(), node.ip(), null, true);
        } catch (IOException ex) {
            return new HopNode(node.hop(), node.ip(), null, true);
        }
    }
}
