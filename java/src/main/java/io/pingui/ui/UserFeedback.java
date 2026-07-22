package io.pingui.ui;

/**
 * Operator-visible feedback channel.
 *
 * <p>Call sites choose {@link #info(String)} vs {@link #error(String)}; routing by Simple/Extended is
 * owned by {@link UiFeedbackRouter}.
 */
interface UserFeedback {
    /** Non-blocking notice (success, hints, route-change log). */
    void info(String message);

    /** Failure path that must be visible in Simple (status + Alert). */
    void error(String message);
}
