package io.pingui.ui;

import io.pingui.i18n.UiI18n;

/**
 * Shared MenuItem accelerator strings ({@link javafx.scene.input.KeyCombination#valueOf}).
 * {@code Shortcut} maps to Ctrl (Linux/Windows) or Meta (macOS) — does not steal bare TextField typing.
 */
public final class AppAccelerators {
    /** Save YAML config. */
    public static final String SAVE = "Shortcut+S";

    /** Add host from the input field. */
    public static final String ADD_HOST = "Shortcut+N";

    /** Open Help dialog. */
    public static final String HELP = "F1";

    private AppAccelerators() {}

    /** Help section body (unit-tested; P20-006 / P25). */
    static String helpSection() {
        return UiI18n.get("help.accelerators");
    }
}
