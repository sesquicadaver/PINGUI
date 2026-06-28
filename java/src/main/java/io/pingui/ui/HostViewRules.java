package io.pingui.ui;

import io.pingui.config.HostEntry;
import java.util.List;

/** Canvas display rules for specific host entries. */
public final class HostViewRules {
    static final String HOST = "fuck.you";
    static final String MESSAGE = "fuck yourself, mazafaka";

    private HostViewRules() {}

    public static boolean matches(String host) {
        return host != null && HOST.equalsIgnoreCase(host.strip());
    }

    public static String messageFor(String host) {
        return matches(host) ? MESSAGE : null;
    }

    /** Host entries kept in RAM only — omitted from YAML save/load. */
    public static List<String> hostsForConfig(List<String> hosts) {
        return hosts.stream().filter(h -> !matches(h)).toList();
    }

    public static List<HostEntry> entriesForConfig(List<HostEntry> entries) {
        return entries.stream().filter(e -> !matches(e.address())).toList();
    }

    /** Host entries shown in UI and session; easter-egg trigger is excluded. */
    public static List<HostEntry> sessionEntries(List<HostEntry> entries) {
        return entriesForConfig(entries);
    }
}
