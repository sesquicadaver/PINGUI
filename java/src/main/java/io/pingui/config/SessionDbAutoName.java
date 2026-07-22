package io.pingui.config;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Builds auto session-DB paths {@code data/YYYY-MM-DD_HH-mm-ss_&lt;ip&gt;.db} (P22-005 /
 * ADR_HOST_PROBLEM_INDICATOR).
 */
public final class SessionDbAutoName {
    public static final Path DEFAULT_DIR = Path.of("data");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private SessionDbAutoName() {}

    /** Uses system default zone clock and {@link LocalIpv4#resolveOperatorLan()}. */
    public static Path generate() {
        return generate(Clock.systemDefaultZone(), LocalIpv4.resolveOperatorLan());
    }

    public static Path generate(Clock clock, String localIp) {
        Objects.requireNonNull(clock, "clock");
        String stamp = LocalDateTime.now(clock).format(STAMP);
        String ipPart = LocalIpv4.sanitizeForFilename(localIp);
        return DEFAULT_DIR.resolve(stamp + "_" + ipPart + ".db");
    }
}
