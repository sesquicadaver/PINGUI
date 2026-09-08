package io.pingui.geoip;

/**
 * Baseline {@link IpMetadataProvider}: valid literals → {@link IpMetadataSource#NONE}; no network
 * I/O. Used until YAML/MMDB providers land (P36-004+). Scope is {@link IpAddressScope#PUBLIC} for
 * every literal — refined classification is P36-003.
 */
public final class EmptyIpMetadataProvider implements IpMetadataProvider {
    public static final EmptyIpMetadataProvider INSTANCE = new EmptyIpMetadataProvider();

    private EmptyIpMetadataProvider() {}

    @Override
    public IpMetadata lookup(String ip) {
        String canonical = IpLiterals.canonicalLiteralOrNull(ip);
        if (canonical == null) {
            return null;
        }
        return IpMetadata.unknown(canonical, IpAddressScope.PUBLIC);
    }
}
