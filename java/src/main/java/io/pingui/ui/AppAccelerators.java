package io.pingui.ui;

/**
 * Shared MenuItem accelerator strings ({@link javafx.scene.input.KeyCombination#valueOf}).
 * {@code Shortcut} maps to Ctrl (Linux/Windows) or Meta (macOS) — does not steal bare TextField typing.
 */
final class AppAccelerators {
    /** Save YAML config. */
    static final String SAVE = "Shortcut+S";

    /** Add host from the input field. */
    static final String ADD_HOST = "Shortcut+N";

    /** Open Help dialog. */
    static final String HELP = "F1";

    private AppAccelerators() {}

    /** Help section body (unit-tested; P20-006). */
    static String helpSection() {
        return """
                Гарячі клавіші
                • F1 — Довідка
                • Ctrl/Cmd+S — Зберегти (YAML)
                • Ctrl/Cmd+N — Додати ціль (з поля вводу)
                """;
    }
}
