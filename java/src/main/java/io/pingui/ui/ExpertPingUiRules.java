package io.pingui.ui;

import io.pingui.config.ConfigError;
import io.pingui.config.HostAddressKind;
import io.pingui.config.HostAddressParser;
import io.pingui.i18n.UiI18n;

/** UI enablement rules for expert ping options (testable without JavaFX). */
public final class ExpertPingUiRules {
    static String afIpv4() {
        return UiI18n.get("expert.af_ipv4");
    }

    static String afIpv6() {
        return UiI18n.get("expert.af_ipv6");
    }

    private ExpertPingUiRules() {}

    /**
     * IPv6 flow label (-F) is allowed only for IPv6 literals or when the user selects IPv6 (-6).
     * Default address family is IPv4 (-4).
     */
    public static boolean flowLabelAllowed(String host, String addressFamilyChoice) {
        if (afIpv6().equals(addressFamilyChoice)) {
            return true;
        }
        if (afIpv4().equals(addressFamilyChoice)) {
            return false;
        }
        try {
            return HostAddressParser.kindOf(HostAddressParser.normalize(host)) == HostAddressKind.IPV6;
        } catch (ConfigError ex) {
            return false;
        }
    }

    public static String flowLabelDisabledHint() {
        return UiI18n.get("expert.flow_disabled");
    }
}
