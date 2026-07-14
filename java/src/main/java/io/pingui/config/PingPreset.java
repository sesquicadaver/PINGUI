package io.pingui.config;

import java.util.List;

/** One expert-ping quick preset (P14-040 / P17-010). */
public record PingPreset(String id, String label, List<String> args, String summary, String expect, String caution) {
    public PingPreset {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("preset id required");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("preset label required");
        }
        args = args != null ? List.copyOf(args) : List.of();
        summary = summary != null ? summary.strip() : "";
        expect = expect != null ? expect.strip() : "";
        caution = caution != null ? caution.strip() : "";
    }

    /** Compact status line for Exten. dialog after applying the preset. */
    public String statusLine() {
        StringBuilder sb = new StringBuilder();
        sb.append("Застосовано «").append(label).append("»: ").append(String.join(" ", args));
        if (!summary.isBlank()) {
            sb.append(" — ").append(summary);
        }
        if (!expect.isBlank()) {
            sb.append(" | Дивись: ").append(expect);
        }
        if (!caution.isBlank()) {
            sb.append(" | ⚠ ").append(caution);
        }
        return sb.toString();
    }

    /** Tooltip text: args + UX copy. */
    public String tooltipText() {
        StringBuilder sb = new StringBuilder(String.join(" ", args));
        if (!summary.isBlank()) {
            sb.append("\n").append(summary);
        }
        if (!expect.isBlank()) {
            sb.append("\nДивись: ").append(expect);
        }
        if (!caution.isBlank()) {
            sb.append("\n⚠ ").append(caution);
        }
        return sb.toString();
    }
}
