package io.pingui.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class HostTagsTest {

    @Test
    void normalizeLowercasesAndDedupes() {
        assertEquals(List.of("dc", "vpn"), HostTags.normalize(List.of("DC", " vpn ", "dc")));
    }

    @Test
    void rejectsInvalidTag() {
        assertThrows(ConfigError.class, () -> HostTags.normalize(List.of("Bad Tag!")));
        assertThrows(ConfigError.class, () -> HostTags.normalize(List.of("-leading")));
    }

    @Test
    void matchesFilter() {
        assertTrue(HostTags.matchesFilter(List.of("dc", "vpn"), null));
        assertTrue(HostTags.matchesFilter(List.of("dc", "vpn"), "dc"));
        assertFalse(HostTags.matchesFilter(List.of("dc"), "vpn"));
    }

    @Test
    void collectUniqueSorted() {
        assertEquals(List.of("a", "b", "z"), HostTags.collectUnique(List.of(List.of("z", "a"), List.of("b", "a"))));
    }
}
