package io.pingui.ui;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;

/** Starts JavaFX toolkit once and runs callbacks on the FX thread in tests. */
final class FxTestSupport {
    private static volatile boolean started;

    private FxTestSupport() {}

    static void ensureStarted() throws InterruptedException {
        if (started) {
            return;
        }
        synchronized (FxTestSupport.class) {
            if (started) {
                return;
            }
            CountDownLatch latch = new CountDownLatch(1);
            try {
                Platform.startup(latch::countDown);
            } catch (IllegalStateException already) {
                // Toolkit already running in this JVM (e.g. another suite).
                started = true;
                return;
            } catch (RuntimeException ex) {
                throw new IllegalStateException(
                        "JavaFX toolkit failed to start (need display or Monocle headless)", ex);
            }
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("JavaFX toolkit failed to start (timeout)");
            }
            started = true;
        }
    }

    static void runOnFxThread(ThrowingRunnable action) throws Exception {
        ensureStarted();
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable ex) {
                error.set(ex);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("FX action timed out");
        }
        if (error.get() != null) {
            if (error.get() instanceof Exception exception) {
                throw exception;
            }
            throw new RuntimeException(error.get());
        }
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }
}
