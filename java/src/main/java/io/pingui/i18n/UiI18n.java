package io.pingui.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Runtime UI strings (P25). Canon bundle is Ukrainian; missing keys fall back to UK then the key
 * itself. Properties files are UTF-8.
 */
public final class UiI18n {
    private static final String BASE = "io.pingui.i18n.messages";
    private static final Object LOCK = new Object();
    private static volatile UiLocale active = UiLocale.UK;
    private static volatile ResourceBundle bundle = load(UiLocale.UK);
    private static volatile ResourceBundle canonBundle = load(UiLocale.UK);
    private static final CopyOnWriteArrayList<Consumer<UiLocale>> listeners = new CopyOnWriteArrayList<>();

    private UiI18n() {}

    public static UiLocale locale() {
        return active;
    }

    public static void setLocale(UiLocale locale) {
        UiLocale next = locale != null ? locale : UiLocale.UK;
        synchronized (LOCK) {
            if (next == active && bundle != null) {
                return;
            }
            active = next;
            bundle = load(next);
            canonBundle = load(UiLocale.UK);
        }
        for (Consumer<UiLocale> listener : listeners) {
            listener.accept(next);
        }
    }

    /** Register a callback after locale change (chrome refresh). */
    public static void addListener(Consumer<UiLocale> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public static void removeListener(Consumer<UiLocale> listener) {
        listeners.remove(listener);
    }

    public static String get(String key) {
        return format(key);
    }

    public static String get(String key, Object... args) {
        return format(key, args);
    }

    private static String format(String key, Object... args) {
        String pattern = resolve(key);
        if (args == null || args.length == 0) {
            return pattern;
        }
        return new MessageFormat(pattern, active.toLocale()).format(args);
    }

    private static String resolve(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        String fromActive = lookup(bundle, key);
        if (fromActive != null) {
            return fromActive;
        }
        String fromCanon = lookup(canonBundle, key);
        if (fromCanon != null) {
            return fromCanon;
        }
        return key;
    }

    private static String lookup(ResourceBundle resourceBundle, String key) {
        if (resourceBundle == null) {
            return null;
        }
        try {
            return resourceBundle.getString(key);
        } catch (MissingResourceException | ClassCastException ex) {
            return null;
        }
    }

    private static ResourceBundle load(UiLocale locale) {
        try {
            ResourceBundle loaded = ResourceBundle.getBundle(
                    BASE, locale.toLocale(), UiI18n.class.getClassLoader(), Utf8Control.INSTANCE);
            if (loaded != null) {
                return loaded;
            }
        } catch (MissingResourceException ignored) {
            // fall through to UK
        }
        return ResourceBundle.getBundle(
                BASE, UiLocale.UK.toLocale(), UiI18n.class.getClassLoader(), Utf8Control.INSTANCE);
    }

    /** UTF-8 properties; no JVM-default locale fallback (missing keys handled in resolve). */
    static final class Utf8Control extends ResourceBundle.Control {
        static final Utf8Control INSTANCE = new Utf8Control();

        @Override
        public Locale getFallbackLocale(String baseName, Locale locale) {
            return null;
        }

        @Override
        public ResourceBundle newBundle(
                String baseName, Locale locale, String format, ClassLoader loader, boolean reload)
                throws IllegalAccessException, InstantiationException, IOException {
            String bundleName = toBundleName(baseName, locale);
            String resourceName = toResourceName(bundleName, "properties");
            InputStream stream = loader.getResourceAsStream(resourceName);
            if (stream == null) {
                return null;
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return new PropertyResourceBundle(reader);
            }
        }
    }
}
