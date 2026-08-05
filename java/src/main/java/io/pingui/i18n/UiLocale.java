package io.pingui.i18n;

import java.util.Locale;
import java.util.Optional;

/** Supported UI locales (P25). Ukrainian is the canon bundle. */
public enum UiLocale {
    UK("uk", "Українська"),
    EN("en", "English"),
    ES("es", "Español"),
    IT("it", "Italiano"),
    PL("pl", "Polski"),
    CS("cs", "Čeština"),
    LV("lv", "Latviešu"),
    LT("lt", "Lietuvių"),
    ET("et", "Eesti");

    private final String code;
    private final String displayName;

    UiLocale(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String code() {
        return code;
    }

    /** Native name for the Language menu. */
    public String displayName() {
        return displayName;
    }

    public Locale toLocale() {
        return Locale.forLanguageTag(code);
    }

    public static UiLocale canon() {
        return UK;
    }

    public static Optional<UiLocale> fromCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.strip().toLowerCase(Locale.ROOT).replace('_', '-');
        if (normalized.startsWith("uk") || normalized.equals("ua")) {
            return Optional.of(UK);
        }
        for (UiLocale locale : values()) {
            if (locale.code.equals(normalized) || normalized.startsWith(locale.code + "-")) {
                return Optional.of(locale);
            }
        }
        return Optional.empty();
    }
}
