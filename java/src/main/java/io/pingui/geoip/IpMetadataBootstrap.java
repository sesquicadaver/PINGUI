package io.pingui.geoip;

import io.pingui.AppOptions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Builds {@link IpMetadataService} from {@link AppOptions} (P36-007).
 *
 * <p>Rules:
 *
 * <ul>
 *   <li>{@code --no-geoip} → disabled service (no MMDB / overrides);
 *   <li>explicit {@code --geoip-db} / {@code --geoip-asn-db} that is missing or unreadable → config
 *       error ({@link IllegalArgumentException});
 *   <li>MMDB unset → YAML overrides only (bundled defaults when the hints file is absent);
 *   <li>{@code --geoip-asn-db} requires {@code --geoip-db};
 *   <li>{@code --no-asn} skips opening the ASN MMDB even when {@code --geoip-asn-db} is set.
 * </ul>
 */
public final class IpMetadataBootstrap {
    private static final String DEFAULT_HINTS_RESOURCE = "geoip_hints.yaml";

    private IpMetadataBootstrap() {}

    /**
     * Open a bounded enrichment service for the given options.
     *
     * @throws IllegalArgumentException on contradictory flags or an explicit but broken MMDB/hints
     *     path
     */
    public static IpMetadataService open(AppOptions options) {
        Objects.requireNonNull(options, "options");
        if (!options.geoipEnabled()) {
            if (options.geoipDbPath().isPresent() || options.geoipAsnDbPath().isPresent()) {
                throw new IllegalArgumentException("--geoip-db/--geoip-asn-db cannot be used with --no-geoip");
            }
            return IpMetadataService.disabled();
        }
        if (options.geoipAsnDbPath().isPresent() && options.geoipDbPath().isEmpty()) {
            throw new IllegalArgumentException("--geoip-asn-db requires --geoip-db PATH");
        }

        IpMetadataProvider overrides = loadOverrides(options.geoipHintsPath());
        MmdbIpMetadataProvider mmdb = null;
        if (options.geoipDbPath().isPresent()) {
            Path geoDb = options.geoipDbPath().get();
            if (!Files.isRegularFile(geoDb)) {
                throw new IllegalArgumentException("--geoip-db file not found: " + geoDb);
            }
            Path asnDb = null;
            if (options.asnEnabled() && options.geoipAsnDbPath().isPresent()) {
                asnDb = options.geoipAsnDbPath().get();
                if (!Files.isRegularFile(asnDb)) {
                    throw new IllegalArgumentException("--geoip-asn-db file not found: " + asnDb);
                }
            }
            try {
                mmdb = MmdbIpMetadataProvider.open(geoDb, asnDb);
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("Failed to open GeoIP MMDB (" + geoDb + "): " + rootMessage(ex), ex);
            }
        }
        return new IpMetadataService(overrides, mmdb);
    }

    static IpMetadataProvider loadOverrides(Path hintsPath) {
        if (hintsPath != null && Files.isRegularFile(hintsPath)) {
            try {
                return YamlIpMetadataOverrides.fromFile(hintsPath);
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException(
                        "Failed to load --geoip-hints (" + hintsPath + "): " + rootMessage(ex), ex);
            }
        }
        return YamlIpMetadataOverrides.fromResource(DEFAULT_HINTS_RESOURCE);
    }

    private static String rootMessage(Throwable ex) {
        Throwable cur = ex;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String message = cur.getMessage();
        return message == null || message.isBlank() ? cur.getClass().getSimpleName() : message;
    }
}
