package io.pingui;

/**
 * Process entry point that does <em>not</em> extend {@link javafx.application.Application}.
 *
 * <p>Required for {@code installDist} / jpackage classpath launches: when the JVM main class
 * extends {@code Application}, JavaFX must be on {@code --module-path}. A thin launcher avoids that
 * check so JavaFX JARs on the classpath work (same pattern as OpenJFX non-modular apps).
 */
public final class PinguiLauncher {
    private PinguiLauncher() {}

    public static void main(String[] args) {
        PinguiApplication.main(args);
    }
}
