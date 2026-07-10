package io.pingui.config;

/** One expert-ping quick preset (P14-040). */
public record PingPreset(String id, String label, java.util.List<String> args) {
    public PingPreset {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("preset id required");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("preset label required");
        }
        args = args != null ? java.util.List.copyOf(args) : java.util.List.of();
    }
}
