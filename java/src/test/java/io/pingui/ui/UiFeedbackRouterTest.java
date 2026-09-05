package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class UiFeedbackRouterTest {
    @Test
    void simpleInfoUpdatesOpsOnly() {
        Recording rec = new Recording(false);
        rec.router.info("ok");
        assertEquals(List.of("ok"), rec.ops);
        assertTrue(rec.log.isEmpty());
        assertTrue(rec.alerts.isEmpty());
    }

    @Test
    void extendedInfoUpdatesLogAndOps() {
        Recording rec = new Recording(true);
        rec.router.info("ok");
        assertEquals(List.of("ok"), rec.log);
        assertEquals(List.of("ok"), rec.ops);
        assertTrue(rec.alerts.isEmpty());
    }

    @Test
    void simpleErrorUpdatesOpsAndAlert() {
        Recording rec = new Recording(false);
        rec.router.error("fail");
        assertEquals(List.of("fail"), rec.errors);
        assertEquals(List.of("fail"), rec.alerts);
        assertTrue(rec.log.isEmpty());
    }

    @Test
    void extendedErrorUpdatesLogAndOpsWithoutAlert() {
        Recording rec = new Recording(true);
        rec.router.error("fail");
        assertEquals(List.of("fail"), rec.log);
        assertEquals(List.of("fail"), rec.errors);
        assertTrue(rec.alerts.isEmpty());
    }

    private static final class Recording {
        final List<String> ops = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        final List<String> log = new ArrayList<>();
        final List<String> alerts = new ArrayList<>();
        final UiFeedbackRouter router;

        Recording(boolean extended) {
            AtomicBoolean mode = new AtomicBoolean(extended);
            this.router = new UiFeedbackRouter(mode::get, ops::add, errors::add, log::add, alerts::add);
        }
    }
}
