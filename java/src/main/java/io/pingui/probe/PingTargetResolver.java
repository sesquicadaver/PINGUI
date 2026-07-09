package io.pingui.probe;

import io.pingui.config.HostAddressResolver;
import io.pingui.config.PingExpertEntry;

/** Chooses the ping argv target from configured host + expert address-family flags. */
final class PingTargetResolver {
    private PingTargetResolver() {}

    static String resolve(String target, PingExpertEntry expert) {
        boolean ipv6 = ExpertPingArgs.forTarget(target, expert).contains("-6");
        return HostAddressResolver.resolveForPing(target, ipv6);
    }
}
