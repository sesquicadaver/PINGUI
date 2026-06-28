package io.pingui.config;

/** One monitored target inside a tracing profile. */
public record HostEntry(String address, boolean enabled, PingExpertEntry pingExpert) {
    public HostEntry {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("address required");
        }
        pingExpert = pingExpert != null ? pingExpert.normalized() : PingExpertEntry.empty();
    }

    public static HostEntry basic(String address, boolean enabled) {
        return new HostEntry(address, enabled, PingExpertEntry.empty());
    }

    public HostEntry withPingExpert(PingExpertEntry expert) {
        return new HostEntry(address, enabled, expert != null ? expert.normalized() : PingExpertEntry.empty());
    }
}
