package io.pingui.ui;

import java.util.Locale;

/** Hidden UI response for a specific host entry (Java edition only). */
public final class HostEasterEgg {
    static final String HOST = "fuck.you";
    static final String MESSAGE = "fuck yourself, mazafaka";

    private HostEasterEgg() {}

    public static boolean matches(String host) {
        return host != null && HOST.equalsIgnoreCase(host.strip());
    }

    public static String messageFor(String host) {
        return matches(host) ? MESSAGE : null;
    }
}
