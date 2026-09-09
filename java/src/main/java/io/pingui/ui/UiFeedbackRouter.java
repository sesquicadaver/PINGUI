package io.pingui.ui;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Routes {@link UserFeedback} by view mode.
 *
 * <ul>
 *   <li>Simple/info → transient ops line only
 *   <li>Extended/info → transient ops only (no left-column event log spam)
 *   <li>Simple/error → ops + Alert
 *   <li>Extended/error → ops only (no Alert); optional log sink kept for diagnostics appenders
 * </ul>
 */
final class UiFeedbackRouter implements UserFeedback {
    private final BooleanSupplier extended;
    private final Consumer<String> showTransient;
    private final Consumer<String> showError;
    private final Consumer<String> appendLogLine;
    private final Consumer<String> showErrorAlert;

    UiFeedbackRouter(
            BooleanSupplier extended,
            Consumer<String> showTransient,
            Consumer<String> showError,
            Consumer<String> appendLogLine,
            Consumer<String> showErrorAlert) {
        this.extended = Objects.requireNonNull(extended, "extended");
        this.showTransient = Objects.requireNonNull(showTransient, "showTransient");
        this.showError = Objects.requireNonNull(showError, "showError");
        this.appendLogLine = Objects.requireNonNull(appendLogLine, "appendLogLine");
        this.showErrorAlert = Objects.requireNonNull(showErrorAlert, "showErrorAlert");
    }

    /** Backward-compatible ctor: status setter used for both info and error. */
    UiFeedbackRouter(
            BooleanSupplier extended,
            Consumer<String> setStatus,
            Consumer<String> appendLogLine,
            Consumer<String> showErrorAlert) {
        this(extended, setStatus, setStatus, appendLogLine, showErrorAlert);
    }

    @Override
    public void info(String message) {
        showTransient.accept(nullToEmpty(message));
    }

    @Override
    public void error(String message) {
        String text = nullToEmpty(message);
        if (extended.getAsBoolean()) {
            appendLogLine.accept(text);
            showError.accept(text);
        } else {
            showError.accept(text);
            showErrorAlert.accept(text);
        }
    }

    private static String nullToEmpty(String message) {
        return message == null ? "" : message;
    }
}
