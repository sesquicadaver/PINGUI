package io.pingui.config;

import java.util.ArrayList;
import java.util.List;

/** Per-host expert ping flags (see {@code ping(8)} / pingMan.txt). */
public record PingExpertEntry(boolean applyToChain, List<String> args) {
    public PingExpertEntry {
        args = List.copyOf(args != null ? args : List.of());
    }

    public static PingExpertEntry empty() {
        return new PingExpertEntry(false, List.of());
    }

    public boolean isConfigured() {
        return !args.isEmpty();
    }

    /** YAML-friendly copy without empty args. */
    public PingExpertEntry normalized() {
        List<String> cleaned = new ArrayList<>();
        for (String token : args) {
            if (token != null && !token.isBlank()) {
                cleaned.add(token.strip());
            }
        }
        return new PingExpertEntry(applyToChain, List.copyOf(cleaned));
    }
}
