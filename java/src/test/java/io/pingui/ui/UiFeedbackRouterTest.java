package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class UiFeedbackRouterTest {
    @Test
    void simpleInfoUpdatesStatusOnly() {
        Recording rec = new Recording(false);
        rec.router.info("ok");
        assertEquals(List.of("ok"), rec.status);
        assertTrue(rec.log.isEmpty());
        assertTrue(rec.alerts.isEmpty());
    }

    @Test
    void extendedInfoUpdatesLogOnly() {
        Recording rec = new Recording(true);
        rec.router.info("ok");
        assertEquals(List.of("ok"), rec.log);
        assertTrue(rec.status.isEmpty());
        assertTrue(rec.alerts.isEmpty());
    }

    @Test
    void simpleErrorUpdatesStatusAndAlert() {
        Recording rec = new Recording(false);
        rec.router.error("fail");
        assertEquals(List.of("fail"), rec.status);
        assertEquals(List.of("fail"), rec.alerts);
        assertTrue(rec.log.isEmpty());
    }

    @Test
    void extendedErrorUpdatesLogWithoutAlert() {
        Recording rec = new Recording(true);
        rec.router.error("fail");
        assertEquals(List.of("fail"), rec.log);
        assertTrue(rec.status.isEmpty());
        assertTrue(rec.alerts.isEmpty());
    }

    private static final class Recording {
        final List<String> status = new ArrayList<>();
        final List<String> log = new ArrayList<>();
        final List<String> alerts = new ArrayList<>();
        final UiFeedbackRouter router;

        Recording(boolean extended) {
            AtomicBoolean mode = new AtomicBoolean(extended);
            this.router = new UiFeedbackRouter(mode::get, status::add, log::add, alerts::add);
        }
    }
}
