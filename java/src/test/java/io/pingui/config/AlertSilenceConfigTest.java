package io.pingui.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link AlertSilenceConfig} (P29-003). */
class AlertSilenceConfigTest {
    private static final Instant NOW = Instant.parse("2026-09-03T12:00:00Z");

    @Test
    void hostSilenceMatchesCaseInsensitive() {
        AlertSilenceConfig silence = new AlertSilenceConfig(List.of(new AlertSilenceEntry(
                AlertSilenceScope.HOST, "8.8.8.8", Instant.parse("2026-09-03T18:00:00Z"), "swap")));
        assertTrue(silence.isSilenced("8.8.8.8", List.of(), NOW));
        assertTrue(silence.isSilenced("8.8.8.8", List.of("lab"), NOW));
        assertFalse(silence.isSilenced("1.1.1.1", List.of(), NOW));
    }

    @Test
    void tagAndProfileSilence() {
        AlertSilenceConfig silence = new AlertSilenceConfig(List.of(
                new AlertSilenceEntry(
                        AlertSilenceScope.TAG, "lab", Instant.parse("2026-09-03T18:00:00Z"), "lab window"),
                new AlertSilenceEntry(
                        AlertSilenceScope.PROFILE, "*", Instant.parse("2026-09-03T13:00:00Z"), "global")));
        assertTrue(silence.isSilenced("8.8.8.8", List.of("lab"), NOW));
        assertFalse(silence.isSilenced("8.8.8.8", List.of("prod"), NOW.plusSeconds(3700)));
        assertTrue(silence.isSilenced("any", List.of(), NOW));
    }

    @Test
    void expiredRulesDoNotSilence() {
        AlertSilenceConfig silence = new AlertSilenceConfig(List.of(new AlertSilenceEntry(
                AlertSilenceScope.HOST, "8.8.8.8", Instant.parse("2026-09-03T11:00:00Z"), "done")));
        assertFalse(silence.isSilenced("8.8.8.8", List.of(), NOW));
    }

    @Test
    void parseLinesRoundTrip() {
        String text =
                """
                # comment
                host|8.8.8.8|2026-09-03T18:00:00Z|swap
                tag|lab|2026-09-04T00:00:00Z|
                profile|*|2026-09-03T20:00:00Z|global
                """;
        AlertSilenceConfig silence = AlertSilenceConfig.parseLines(text);
        assertEquals(3, silence.entries().size());
        assertTrue(silence.toLines().contains("host|8.8.8.8|"));
        assertThrows(IllegalArgumentException.class, () -> AlertSilenceConfig.parseLines("host|only-two"));
    }
}
