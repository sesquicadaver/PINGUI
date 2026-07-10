package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.pingui.config.ConfigError;
import java.util.List;
import org.junit.jupiter.api.Test;

class HostTagsDialogTest {
    @Test
    void parseInputSplitsAndNormalizes() {
        assertEquals(List.of("dc", "vpn"), HostTagsDialog.parseInput(" DC, vpn ,DC "));
    }

    @Test
    void parseInputBlankIsEmpty() {
        assertEquals(List.of(), HostTagsDialog.parseInput("  , , "));
        assertEquals(List.of(), HostTagsDialog.parseInput(null));
    }

    @Test
    void parseInputRejectsInvalid() {
        assertThrows(ConfigError.class, () -> HostTagsDialog.parseInput("ok, Bad!"));
    }
}
