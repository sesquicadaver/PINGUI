package io.pingui;

import static org.junit.jupiter.api.Assertions.assertFalse;

import javafx.application.Application;
import org.junit.jupiter.api.Test;

/** P26-004: entry point must not extend Application (classpath JavaFX / installDist). */
class PinguiLauncherTest {
    @Test
    void launcherDoesNotExtendJavaFxApplication() {
        assertFalse(Application.class.isAssignableFrom(PinguiLauncher.class));
    }
}
