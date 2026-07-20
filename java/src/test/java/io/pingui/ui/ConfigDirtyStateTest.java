package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ConfigDirtyStateTest {
    @Test
    void markClearAndIdempotentCallbacks() {
        AtomicInteger changes = new AtomicInteger();
        ConfigDirtyState state = new ConfigDirtyState(changes::incrementAndGet);
        assertFalse(state.isDirty());

        state.mark();
        assertTrue(state.isDirty());
        assertEquals(1, changes.get());

        state.mark();
        assertEquals(1, changes.get());

        state.clear();
        assertFalse(state.isDirty());
        assertEquals(2, changes.get());

        state.clear();
        assertEquals(2, changes.get());
    }
}
