package io.pingui.probe;

import io.pingui.config.ConfigError;
import io.pingui.config.HostAddressKind;
import io.pingui.config.HostAddressParser;
import io.pingui.config.PingExpertEntry;
import java.util.ArrayList;
import java.util.List;

/**
 * Single address-family policy for trace AUTO routing, traceroute {@code -6}, and expert ping.
 *
 * <p>Literal IPv6 → process {@code traceroute -6} / expert {@code -6}. Literal IPv4 → raw/process
 * without {@code -6} / expert {@code -4}. Hostname AF is left to expert flags or ExpertPingArgs
 * defaults (not forced here for trace).
 */
public final class AddressFamilyPolicy {

    private AddressFamilyPolicy() {}

    /**
     * Normalized kind of a configured host / hop token.
     *
     * @throws ConfigError if the entry is not a valid host address
     */
    public static HostAddressKind kindOf(String entry) {
        return HostAddressParser.kindOf(HostAddressParser.normalize(entry));
    }

    /** True when the entry is an IPv6 literal (bracketed or bare). */
    public static boolean isIpv6Literal(String entry) {
        return kindOf(entry) == HostAddressKind.IPV6;
    }

    /**
     * AUTO probe: IPv6 literals must use subprocess traceroute ({@code -6}).
     *
     * @throws ConfigError if {@code entry} is invalid (callers fail-closed)
     */
    public static boolean requiresProcessTrace(String entry) {
        return isIpv6Literal(entry);
    }

    /**
     * Chain enrich: replace {@code -4}/{@code -6} so hop literal AF wins over the UI AF selection.
     * Hostname hops keep non-AF args; {@link ExpertPingArgs} applies the default AF later.
     */
    public static PingExpertEntry alignExpertToTarget(PingExpertEntry expert, String target) {
        if (expert == null || !expert.isConfigured()) {
            return expert != null ? expert : PingExpertEntry.empty();
        }
        List<String> args = new ArrayList<>();
        for (String token : expert.args()) {
            if (!"-4".equals(token) && !"-6".equals(token)) {
                args.add(token);
            }
        }
        try {
            HostAddressKind kind = kindOf(target);
            if (kind == HostAddressKind.IPV6) {
                args.add(0, "-6");
            } else if (kind == HostAddressKind.IPV4) {
                args.add(0, "-4");
            }
        } catch (ConfigError ignored) {
            // Leave without AF; ExpertPingArgs / enricher fail-soft handles invalid hop tokens.
        }
        return new PingExpertEntry(expert.applyToChain(), List.copyOf(args));
    }
}
