package io.pingui.probe;

/** Route probe backend selection. */
public enum ProbeMode {
    /** Linux: raw ICMP when permitted, else traceroute/tracert subprocess. */
    AUTO,
    /** Always use OS traceroute/tracert. */
    PROCESS,
    /** Linux raw ICMP only (requires cap_net_raw). */
    RAW;

    public static ProbeMode parse(String value) {
        if (value == null || value.isBlank()) {
            return AUTO;
        }
        return switch (value.strip().toLowerCase()) {
            case "auto" -> AUTO;
            case "process", "traceroute", "tracert" -> PROCESS;
            case "raw", "icmp" -> RAW;
            default -> throw new IllegalArgumentException("Unknown probe mode: " + value);
        };
    }

    /** YAML / CLI serialization value. */
    public String cliValue() {
        return switch (this) {
            case AUTO -> "auto";
            case PROCESS -> "process";
            case RAW -> "raw";
        };
    }
}
