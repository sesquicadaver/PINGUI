package io.pingui.geoip;

/**
 * Composes two offline override providers (P36-012).
 *
 * <p>Primary wins for geo fields; secondary fills missing ASN/organization (typically {@code
 * geoip_hints.yaml} + {@code asn_hints.yaml}).
 */
public final class MergingIpMetadataProvider implements IpMetadataProvider {
    private final IpMetadataProvider primary;
    private final IpMetadataProvider secondary;

    public MergingIpMetadataProvider(IpMetadataProvider primary, IpMetadataProvider secondary) {
        if (primary == null || secondary == null) {
            throw new IllegalArgumentException("primary and secondary are required");
        }
        this.primary = primary;
        this.secondary = secondary;
    }

    /** Returns {@code primary} when {@code secondary} is null. */
    public static IpMetadataProvider of(IpMetadataProvider primary, IpMetadataProvider secondary) {
        if (primary == null) {
            return secondary;
        }
        if (secondary == null) {
            return primary;
        }
        return new MergingIpMetadataProvider(primary, secondary);
    }

    @Override
    public IpMetadata lookup(String ip) {
        IpMetadata first = primary.lookup(ip);
        IpMetadata second = secondary.lookup(ip);
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        if (first.source() == IpMetadataSource.NONE && second.source() == IpMetadataSource.NONE) {
            return first;
        }
        String country = first.countryIso() != null ? first.countryIso() : second.countryIso();
        String subdivision = first.subdivision() != null ? first.subdivision() : second.subdivision();
        String city = first.city() != null ? first.city() : second.city();
        Double latitude = first.latitude() != null ? first.latitude() : second.latitude();
        Double longitude = first.longitude() != null ? first.longitude() : second.longitude();
        Double accuracy = first.accuracyRadiusKm() != null ? first.accuracyRadiusKm() : second.accuracyRadiusKm();
        Integer asn = first.asn() != null ? first.asn() : second.asn();
        String organization = first.organization() != null ? first.organization() : second.organization();
        Long epoch = first.datasetEpochSeconds() != null ? first.datasetEpochSeconds() : second.datasetEpochSeconds();
        boolean hasPayload = country != null
                || subdivision != null
                || city != null
                || latitude != null
                || asn != null
                || organization != null
                || epoch != null;
        if (!hasPayload) {
            return IpMetadata.unknown(first.ip(), first.scope());
        }
        IpMetadataSource source =
                first.source() == IpMetadataSource.OVERRIDE || second.source() == IpMetadataSource.OVERRIDE
                        ? IpMetadataSource.OVERRIDE
                        : first.source() == IpMetadataSource.MMDB || second.source() == IpMetadataSource.MMDB
                                ? IpMetadataSource.MMDB
                                : IpMetadataSource.OVERRIDE;
        return new IpMetadata(
                first.ip(),
                first.scope(),
                country,
                subdivision,
                city,
                latitude,
                longitude,
                accuracy,
                asn,
                organization,
                source,
                epoch);
    }
}
