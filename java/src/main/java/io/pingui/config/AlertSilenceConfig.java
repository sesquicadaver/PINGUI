package io.pingui.config;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Profile alert-silence rules: host / tag / whole profile until a timestamp (P29-003). Independent of
 * host {@code enabled} (monitoring continues).
 */
public record AlertSilenceConfig(List<AlertSilenceEntry> entries) {
    public AlertSilenceConfig {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public static AlertSilenceConfig none() {
        return new AlertSilenceConfig(List.of());
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * @param host monitored address
     * @param tags host tags (may be empty)
     * @param now evaluation clock
     * @return true when any active rule matches
     */
    public boolean isSilenced(String host, List<String> tags, Instant now) {
        if (host == null || host.isBlank() || entries.isEmpty()) {
            return false;
        }
        Instant at = now != null ? now : Instant.now();
        List<String> hostTags = tags == null ? List.of() : tags;
        String normalizedHost = host.strip();
        for (AlertSilenceEntry entry : entries) {
            if (!entry.isActive(at)) {
                continue;
            }
            switch (entry.scope()) {
                case PROFILE -> {
                    return true;
                }
                case HOST -> {
                    if (normalizedHost.equalsIgnoreCase(entry.match())) {
                        return true;
                    }
                }
                case TAG -> {
                    String tag = entry.match().strip().toLowerCase(java.util.Locale.ROOT);
                    if (HostTags.matchesFilter(hostTags, tag)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Parses compact GUI lines: {@code scope|match|untilIso|reason} (reason optional). Blank lines and
     * {@code #} comments ignored.
     */
    public static AlertSilenceConfig parseLines(String text) {
        if (text == null || text.isBlank()) {
            return none();
        }
        List<AlertSilenceEntry> parsed = new ArrayList<>();
        for (String rawLine : text.split("\\R")) {
            String line = rawLine.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\\|", 4);
            if (parts.length < 3) {
                throw new IllegalArgumentException("silence line must be scope|match|untilIso[|reason]: " + line);
            }
            AlertSilenceScope scope = AlertSilenceScope.fromId(parts[0]);
            String match = parts[1].strip();
            Instant until;
            try {
                until = Instant.parse(parts[2].strip());
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("silence until must be ISO-8601 instant: " + parts[2], ex);
            }
            String reason = parts.length > 3 ? parts[3].strip() : "";
            parsed.add(new AlertSilenceEntry(scope, match, until, reason));
        }
        return new AlertSilenceConfig(parsed);
    }

    /** Serializes entries for the alerts settings text area. */
    public String toLines() {
        if (entries.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (AlertSilenceEntry entry : entries) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(entry.scope().id())
                    .append('|')
                    .append(entry.match())
                    .append('|')
                    .append(entry.until())
                    .append('|')
                    .append(entry.reason() == null ? "" : entry.reason());
        }
        return sb.toString();
    }
}
