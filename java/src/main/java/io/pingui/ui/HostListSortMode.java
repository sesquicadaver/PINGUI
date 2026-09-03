package io.pingui.ui;

import io.pingui.i18n.UiI18n;
import java.util.Locale;

/** Host-list sort keys for P31-005 / pingui-evo-gui §5. */
public enum HostListSortMode {
    CONFIG,
    SEVERITY,
    RTT,
    LOSS,
    LAST_CHANGE;

    /** Localized combo label. */
    public String label() {
        return UiI18n.get(i18nKey());
    }

    String i18nKey() {
        return switch (this) {
            case CONFIG -> "host.sort.config";
            case SEVERITY -> "host.sort.severity";
            case RTT -> "host.sort.rtt";
            case LOSS -> "host.sort.loss";
            case LAST_CHANGE -> "host.sort.last_change";
        };
    }

    static HostListSortMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return CONFIG;
        }
        try {
            return HostListSortMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return CONFIG;
        }
    }
}
