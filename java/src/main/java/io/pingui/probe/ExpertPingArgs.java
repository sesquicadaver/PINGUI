package io.pingui.probe;

import io.pingui.config.ConfigError;
import io.pingui.config.HostAddressKind;
import io.pingui.config.HostAddressParser;
import io.pingui.config.PingExpertEntry;
import java.util.ArrayList;
import java.util.List;

/** Merges expert ping flags with target address family (auto {@code -6} for v6 literals). */
final class ExpertPingArgs {

    private ExpertPingArgs() {}

    static List<String> forTarget(String target, PingExpertEntry expert) {
        List<String> args = new ArrayList<>();
        if (expert != null && expert.isConfigured()) {
            args.addAll(expert.args());
        }
        HostAddressKind kind = kindOfTarget(target);
        if (kind != null) {
            validateFamily(kind, target, args);
        }
        if (kind == HostAddressKind.IPV6 && !args.contains("-4") && !args.contains("-6")) {
            args.add("-6");
        }
        return List.copyOf(args);
    }

    private static HostAddressKind kindOfTarget(String target) {
        if (target == null || target.isBlank()) {
            return null;
        }
        try {
            return HostAddressParser.kindOf(HostAddressParser.normalize(target));
        } catch (ConfigError ex) {
            return null;
        }
    }

    private static void validateFamily(HostAddressKind kind, String target, List<String> args) {
        if (kind == HostAddressKind.IPV6 && args.contains("-4")) {
            throw new ConfigError("Expert ping -4 cannot be used with IPv6 target: " + target);
        }
        if (kind == HostAddressKind.IPV4 && args.contains("-6")) {
            throw new ConfigError("Expert ping -6 cannot be used with IPv4 target: " + target);
        }
    }
}
