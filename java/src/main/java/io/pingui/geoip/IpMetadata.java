package io.pingui.geoip;

/**
 * Immutable offline enrichment snapshot for one IP literal (P36-002).
 *
 * <p>All fields except {@code ip}, {@code scope}, and {@code source} are nullable — GeoIP is
 * inherently approximate. Country <em>names</em> are not stored; UI derives them from ISO + locale.
 *
 * <p>Providers must not invent coordinates: {@link IpMetadataSource#NONE} forbids geo/ASN payload.
 */
public record IpMetadata(
        String ip,
        IpAddressScope scope,
        String countryIso,
        String subdivision,
        String city,
        Double latitude,
        Double longitude,
        Double accuracyRadiusKm,
        Integer asn,
        String organization,
        IpMetadataSource source,
        Long datasetEpochSeconds) {

    public IpMetadata {
        if (ip == null || ip.isBlank()) {
            throw new IllegalArgumentException("ip must be non-blank");
        }
        if (scope == null) {
            throw new IllegalArgumentException("scope is required");
        }
        if (source == null) {
            throw new IllegalArgumentException("source is required");
        }
        ip = ip.strip();
        countryIso = normalizeCountryIso(countryIso);
        subdivision = blankToNull(subdivision);
        city = blankToNull(city);
        organization = blankToNull(organization);
        if (asn != null && asn < 0) {
            throw new IllegalArgumentException("asn must be non-negative: " + asn);
        }
        if (accuracyRadiusKm != null && accuracyRadiusKm < 0) {
            throw new IllegalArgumentException("accuracyRadiusKm must be non-negative: " + accuracyRadiusKm);
        }
        if ((latitude == null) != (longitude == null)) {
            throw new IllegalArgumentException("latitude and longitude must both be set or both null");
        }
        if (latitude != null) {
            if (latitude < -90.0 || latitude > 90.0) {
                throw new IllegalArgumentException("latitude out of range: " + latitude);
            }
            if (longitude < -180.0 || longitude > 180.0) {
                throw new IllegalArgumentException("longitude out of range: " + longitude);
            }
        }
        if (source == IpMetadataSource.NONE
                && hasEnrichmentPayload(
                        countryIso,
                        subdivision,
                        city,
                        latitude,
                        longitude,
                        accuracyRadiusKm,
                        asn,
                        organization,
                        datasetEpochSeconds)) {
            throw new IllegalArgumentException("source NONE must not carry enrichment payload");
        }
    }

    /** Unknown / disabled enrichment for a canonical IP literal. */
    public static IpMetadata unknown(String ip, IpAddressScope scope) {
        return new IpMetadata(ip, scope, null, null, null, null, null, null, null, null, IpMetadataSource.NONE, null);
    }

    public boolean hasCoordinates() {
        return latitude != null && longitude != null;
    }

    public boolean hasCountry() {
        return countryIso != null;
    }

    public boolean hasAsn() {
        return asn != null;
    }

    private static boolean hasEnrichmentPayload(
            String countryIso,
            String subdivision,
            String city,
            Double latitude,
            Double longitude,
            Double accuracyRadiusKm,
            Integer asn,
            String organization,
            Long datasetEpochSeconds) {
        return countryIso != null
                || subdivision != null
                || city != null
                || latitude != null
                || longitude != null
                || accuracyRadiusKm != null
                || asn != null
                || organization != null
                || datasetEpochSeconds != null;
    }

    private static String normalizeCountryIso(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.strip().toUpperCase();
        if (normalized.length() != 2 || !normalized.chars().allMatch(Character::isLetter)) {
            throw new IllegalArgumentException("Invalid country ISO: " + code);
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
