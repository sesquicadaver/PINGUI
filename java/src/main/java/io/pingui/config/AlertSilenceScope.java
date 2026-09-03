package io.pingui.config;

/** Scope of an alert silence / maintenance window (P29-003). */
public enum AlertSilenceScope {
    HOST,
    TAG,
    PROFILE;

    public static AlertSilenceScope fromId(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("silence scope required");
        }
        String key = raw.strip().toLowerCase();
        return switch (key) {
            case "host" -> HOST;
            case "tag" -> TAG;
            case "profile" -> PROFILE;
            default -> throw new IllegalArgumentException("silence scope must be host, tag, or profile");
        };
    }

    public String id() {
        return name().toLowerCase();
    }
}
