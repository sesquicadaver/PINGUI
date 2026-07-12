package io.pingui.telemetry;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Canonical telemetry / scrape metric names and bus labels (P16-014 / ADR_TELEMETRY).
 *
 * <p>{@code host} and {@code hop} are first-class {@link MetricSample} fields, not label map
 * entries. Bus labels always include {@code profile}, {@code probe_mode}, and {@code edition}.
 */
public final class MetricNames {
    public static final String RTT_MS = "pingui_rtt_ms";
    public static final String TARGET_REACHABLE = "pingui_target_reachable";
    public static final String TRACE_DURATION_MS = "pingui_trace_duration_ms";
    public static final String HOP_LOSS_PCT = "pingui_hop_loss_pct";
    public static final String ROUTE_CHANGE_TOTAL = "pingui_route_change_total";

    public static final String LABEL_PROFILE = "profile";
    public static final String LABEL_PROBE_MODE = "probe_mode";
    public static final String LABEL_EDITION = "edition";

    public static final String EDITION_JAVA = "java";
    public static final String EDITION_PYTHON = "python";

    private MetricNames() {}

    /**
     * Builds the standard bus label set (keys sorted for stable JSON).
     *
     * @param profile active profile name (default {@code default})
     * @param probeMode YAML probe mode value ({@code trace}/{@code mtr}/{@code ping_only})
     * @param edition {@link #EDITION_JAVA} or {@link #EDITION_PYTHON}
     */
    public static Map<String, String> labels(String profile, String probeMode, String edition) {
        String safeProfile = profile == null || profile.isBlank() ? "default" : profile;
        String safeMode = Objects.requireNonNull(probeMode, "probeMode");
        if (safeMode.isBlank()) {
            throw new IllegalArgumentException("probeMode must be non-blank");
        }
        String safeEdition = Objects.requireNonNull(edition, "edition");
        if (safeEdition.isBlank()) {
            throw new IllegalArgumentException("edition must be non-blank");
        }
        Map<String, String> out = new TreeMap<>();
        out.put(LABEL_EDITION, safeEdition);
        out.put(LABEL_PROBE_MODE, safeMode);
        out.put(LABEL_PROFILE, safeProfile);
        return Map.copyOf(out);
    }

    /** Convenience for the Java edition bus. */
    public static Map<String, String> javaLabels(String profile, String probeMode) {
        return labels(profile, probeMode, EDITION_JAVA);
    }
}
