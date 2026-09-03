package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Ensures desktop popups reuse one window per host instead of stacking windows. */
class JavaFxDesktopAlertSinkTest {
    @BeforeAll
    static void startFx() throws Exception {
        FxTestSupport.ensureStarted();
    }

    @Test
    void sameHostUpdatesExistingWindowInsteadOfOpeningAnother() throws Exception {
        JavaFxDesktopAlertSink sink = JavaFxDesktopAlertSink.forTests();
        FxTestSupport.runOnFxThread(() -> {
            sink.show("8.8.8.8", "PINGUI endpoint_down", "8.8.8.8: firing");
            assertEquals(1, sink.trackedHostCountForTests());
            sink.show("8.8.8.8", "PINGUI endpoint_down", "8.8.8.8: resolved");
            assertEquals(1, sink.trackedHostCountForTests());
            assertEquals("8.8.8.8: resolved", sink.openBodyForTests("8.8.8.8"));
            assertEquals(
                    "PINGUI endpoint_down", sink.openStageForTests("8.8.8.8").getTitle());
            sink.closeForTests("8.8.8.8");
            assertEquals(0, sink.trackedHostCountForTests());
        });
    }

    @Test
    void differentHostsGetSeparateWindows() throws Exception {
        JavaFxDesktopAlertSink sink = JavaFxDesktopAlertSink.forTests();
        FxTestSupport.runOnFxThread(() -> {
            sink.show("8.8.8.8", "a", "body-a");
            sink.show("1.1.1.1", "b", "body-b");
            assertEquals(2, sink.trackedHostCountForTests());
            assertEquals("body-a", sink.openBodyForTests("8.8.8.8"));
            assertEquals("body-b", sink.openBodyForTests("1.1.1.1"));
            sink.closeForTests("8.8.8.8");
            sink.closeForTests("1.1.1.1");
            assertEquals(0, sink.trackedHostCountForTests());
        });
    }
}
