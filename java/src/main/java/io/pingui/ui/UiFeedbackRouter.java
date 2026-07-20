package io.pingui.ui;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Routes {@link UserFeedback} by view mode.
 *
 * <ul>
 *   <li>Simple/info → status only
 *   <li>Extended/info → log only (does not touch status)
 *   <li>Simple/error → status + Alert
 *   <li>Extended/error → log only (no Alert)
 * </ul>
 */
final class UiFeedbackRouter implements UserFeedback {
    private final BooleanSupplier extended;
    private final Consumer<String> setStatus;
    private final Consumer<String> appendLogLine;
    private final Consumer<String> showErrorAlert;

    UiFeedbackRouter(
            BooleanSupplier extended,
            Consumer<String> setStatus,
            Consumer<String> appendLogLine,
            Consumer<String> showErrorAlert) {
        this.extended = Objects.requireNonNull(extended, "extended");
        this.setStatus = Objects.requireNonNull(setStatus, "setStatus");
        this.appendLogLine = Objects.requireNonNull(appendLogLine, "appendLogLine");
        this.showErrorAlert = Objects.requireNonNull(showErrorAlert, "showErrorAlert");
    }

    @Override
    public void info(String message) {
        String text = nullToEmpty(message);
        if (extended.getAsBoolean()) {
            appendLogLine.accept(text);
        } else {
            setStatus.accept(text);
        }
    }

    @Override
    public void error(String message) {
        String text = nullToEmpty(message);
        if (extended.getAsBoolean()) {
            appendLogLine.accept(text);
        } else {
            setStatus.accept(text);
            showErrorAlert.accept(text);
        }
    }

    private static String nullToEmpty(String message) {
        return message == null ? "" : message;
    }
}
