package io.pingui.ui;

/**
 * Persisted host-list navigation state (text filter, sort, problems-first) for P31-005.
 *
 * @param textFilter free-text query (name / address / tag)
 * @param sortMode primary sort key
 * @param problemsFirst when true, severity rank precedes {@code sortMode}
 */
public record HostListNavPrefs(String textFilter, HostListSortMode sortMode, boolean problemsFirst) {
    public HostListNavPrefs {
        textFilter = textFilter != null ? textFilter : "";
        sortMode = sortMode != null ? sortMode : HostListSortMode.CONFIG;
    }

    public static HostListNavPrefs defaults() {
        return new HostListNavPrefs("", HostListSortMode.CONFIG, false);
    }
}
