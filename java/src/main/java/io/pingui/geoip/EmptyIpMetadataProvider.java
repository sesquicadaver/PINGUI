package io.pingui.geoip;

import java.net.InetAddress;

/**
 * Baseline {@link IpMetadataProvider}: valid literals → {@link IpMetadataSource#NONE} with
 * classified {@link IpAddressScope} (P36-003); no network I/O. YAML/MMDB providers land in
 * P36-004+.
 */
public final class EmptyIpMetadataProvider implements IpMetadataProvider {
    public static final EmptyIpMetadataProvider INSTANCE = new EmptyIpMetadataProvider();

    private EmptyIpMetadataProvider() {}

    @Override
    public IpMetadata lookup(String ip) {
        InetAddress address = IpLiterals.parseLiteralOrNull(ip);
        if (address == null) {
            return null;
        }
        return IpMetadata.unknown(address.getHostAddress(), IpAddressClassifier.scopeOf(address));
    }
}
