package io.pingui.ui;

import io.pingui.config.ConfigError;
import io.pingui.config.HostAddressKind;
import io.pingui.config.HostAddressParser;

/** UI enablement rules for expert ping options (testable without JavaFX). */
public final class ExpertPingUiRules {
    static final String AF_IPV4 = "IPv4 (-4)";
    static final String AF_IPV6 = "IPv6 (-6)";

    private ExpertPingUiRules() {}

    /**
     * IPv6 flow label (-F) is allowed only for IPv6 literals or when the user selects IPv6 (-6).
     * Default address family is IPv4 (-4).
     */
    public static boolean flowLabelAllowed(String host, String addressFamilyChoice) {
        if (AF_IPV6.equals(addressFamilyChoice)) {
            return true;
        }
        if (AF_IPV4.equals(addressFamilyChoice)) {
            return false;
        }
        try {
            return HostAddressParser.kindOf(HostAddressParser.normalize(host)) == HostAddressKind.IPV6;
        } catch (ConfigError ex) {
            return false;
        }
    }

    public static String flowLabelDisabledHint() {
        return "IPv6 flow label (-F): лише для IPv6 literal або сімейства IPv6 (-6)";
    }
}
