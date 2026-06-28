package io.pingui.ui;

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
}
