package io.pingui.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SessionDbAutoNameTest {
    @Test
    void generateUsesLocalStampAndSanitizedIp() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-22T13:39:05Z"), ZoneOffset.ofHours(3));
        Path path = SessionDbAutoName.generate(clock, "192.168.1.10");
        assertEquals(Path.of("data", "2026-07-22_16-39-05_192-168-1-10.db"), path);
    }

    @Test
    void generateUsesUnknownFallbackIp() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-22T10:00:00Z"), ZoneOffset.UTC);
        Path path = SessionDbAutoName.generate(clock, " ");
        assertEquals(Path.of("data", "2026-07-22_10-00-00_unknown.db"), path);
    }
}
