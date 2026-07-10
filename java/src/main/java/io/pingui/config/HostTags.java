package io.pingui.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Host tag normalization and validation (P14-020). */
public final class HostTags {
    public static final int MAX_TAGS_PER_HOST = 8;
    public static final int MAX_TAG_LENGTH = 32;
    private static final Pattern TAG_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9._-]{0,31}$");

    private HostTags() {}

    /** Lowercases, trims, dedupes; rejects invalid tokens. */
    public static List<String> normalize(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String item : raw) {
            if (item == null || item.isBlank()) {
                throw new ConfigError("Host tag must be a non-blank string");
            }
            String tag = item.strip().toLowerCase(Locale.ROOT);
            if (tag.length() > MAX_TAG_LENGTH) {
                throw new ConfigError("Host tag exceeds " + MAX_TAG_LENGTH + " characters: " + tag);
            }
            if (!TAG_PATTERN.matcher(tag).matches()) {
                throw new ConfigError(
                        "Invalid host tag '" + item.strip() + "' (use a-z, 0-9, '.', '_', '-', start with alnum)");
            }
            unique.add(tag);
            if (unique.size() > MAX_TAGS_PER_HOST) {
                throw new ConfigError("At most " + MAX_TAGS_PER_HOST + " tags per host");
            }
        }
        return List.copyOf(unique);
    }

    public static boolean matchesFilter(List<String> tags, String filterTag) {
        if (filterTag == null || filterTag.isBlank()) {
            return true;
        }
        return tags != null && tags.contains(filterTag);
    }

    /** Sorted unique tags across hosts for filter ComboBox. */
    public static List<String> collectUnique(Iterable<? extends List<String>> hostTags) {
        Set<String> unique = new LinkedHashSet<>();
        for (List<String> tags : hostTags) {
            if (tags != null) {
                unique.addAll(tags);
            }
        }
        List<String> sorted = new ArrayList<>(unique);
        sorted.sort(String::compareTo);
        return List.copyOf(sorted);
    }
}
