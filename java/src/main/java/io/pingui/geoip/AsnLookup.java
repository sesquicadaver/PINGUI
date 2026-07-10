package io.pingui.geoip;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/**
 * Offline ASN hints for hop labels (longest-prefix match).
 *
 * <p>{@code timeoutMs} is reserved for a future whois/network fallback (ROADMAP 2s); offline
 * lookups are synchronous and do not use the network.
 */
public final class AsnLookup {
    public static final int DEFAULT_TIMEOUT_MS = 2000;
    private static final String DEFAULT_RESOURCE = "asn_hints.yaml";

    private static boolean enabled = true;
    private static int timeoutMs = DEFAULT_TIMEOUT_MS;
    private static AsnTable lookup = AsnTable.fromResource(DEFAULT_RESOURCE);

    private AsnLookup() {}

    public static void configure(boolean asnEnabled, Path hintsPath) {
        configure(asnEnabled, hintsPath, DEFAULT_TIMEOUT_MS);
    }

    public static void configure(boolean asnEnabled, Path hintsPath, int asnTimeoutMs) {
        enabled = asnEnabled;
        timeoutMs = asnTimeoutMs > 0 ? asnTimeoutMs : DEFAULT_TIMEOUT_MS;
        if (!asnEnabled) {
            lookup = null;
            return;
        }
        if (hintsPath != null && Files.isRegularFile(hintsPath)) {
            lookup = AsnTable.fromFile(hintsPath);
        } else {
            lookup = AsnTable.fromResource(DEFAULT_RESOURCE);
        }
    }

    public static int timeoutMs() {
        return timeoutMs;
    }

    public static AsnInfo lookup(String ip) {
        if (!enabled || lookup == null || ip == null || ip.isBlank()) {
            return null;
        }
        return lookup.resolve(ip.trim());
    }

    /** Formats {@link #lookup(String)} for hop labels, or empty string when unknown. */
    public static String labelLine(String ip) {
        AsnInfo info = lookup(ip);
        return info != null ? "\n" + info.label() : "";
    }

    static final class PrefixEntry {
        final int prefixBits;
        final int networkInt;
        final AsnInfo info;

        PrefixEntry(int prefixBits, int networkInt, AsnInfo info) {
            this.prefixBits = prefixBits;
            this.networkInt = networkInt;
            this.info = info;
        }
    }

    static final class PrefixEntry6 {
        final int prefixBits;
        final byte[] network;
        final AsnInfo info;

        PrefixEntry6(int prefixBits, byte[] network, AsnInfo info) {
            this.prefixBits = prefixBits;
            this.network = network;
            this.info = info;
        }
    }

    static final class PrefixTables {
        final List<PrefixEntry> v4;
        final List<PrefixEntry6> v6;

        PrefixTables(List<PrefixEntry> v4, List<PrefixEntry6> v6) {
            this.v4 = v4;
            this.v6 = v6;
        }
    }

    static final class AsnTable {
        private final List<PrefixEntry> v4Prefixes;
        private final List<PrefixEntry6> v6Prefixes;

        AsnTable(PrefixTables tables) {
            this.v4Prefixes = tables.v4.stream()
                    .sorted(Comparator.comparingInt((PrefixEntry e) -> e.prefixBits)
                            .reversed())
                    .toList();
            this.v6Prefixes = tables.v6.stream()
                    .sorted(Comparator.comparingInt((PrefixEntry6 e) -> e.prefixBits)
                            .reversed())
                    .toList();
        }

        static AsnTable fromResource(String resourceName) {
            InputStream stream = AsnLookup.class.getClassLoader().getResourceAsStream(resourceName);
            if (stream == null) {
                return new AsnTable(embeddedDefaults());
            }
            try (stream) {
                String payload = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                return new AsnTable(parseYaml(payload));
            } catch (IOException exc) {
                throw new IllegalStateException("Failed to read ASN hints resource: " + resourceName, exc);
            }
        }

        static AsnTable fromFile(Path path) {
            try {
                return new AsnTable(parseYaml(Files.readString(path, StandardCharsets.UTF_8)));
            } catch (IOException exc) {
                throw new IllegalStateException("Failed to read ASN hints: " + path, exc);
            }
        }

        AsnInfo resolve(String ip) {
            InetAddress parsed = IpLiterals.parseLiteralOrNull(ip);
            if (parsed instanceof Inet4Address ipv4) {
                return resolveV4(ipv4);
            }
            if (parsed instanceof Inet6Address ipv6) {
                return resolveV6(ipv6);
            }
            return null;
        }

        private AsnInfo resolveV4(Inet4Address addr) {
            if (addr.isLoopbackAddress()
                    || addr.isLinkLocalAddress()
                    || addr.isSiteLocalAddress()
                    || addr.isMulticastAddress()) {
                return null;
            }
            int value = ipv4ToInt(addr);
            for (PrefixEntry entry : v4Prefixes) {
                if (matchesV4(value, entry)) {
                    return entry.info;
                }
            }
            return null;
        }

        private AsnInfo resolveV6(Inet6Address addr) {
            if (isIpv6Lan(addr) || addr.isMulticastAddress()) {
                return null;
            }
            byte[] value = addr.getAddress();
            for (PrefixEntry6 entry : v6Prefixes) {
                if (matchesV6(value, entry)) {
                    return entry.info;
                }
            }
            return null;
        }

        private static boolean isIpv6Lan(Inet6Address addr) {
            if (addr.isLoopbackAddress() || addr.isLinkLocalAddress()) {
                return true;
            }
            byte[] octets = addr.getAddress();
            return (octets[0] & (byte) 0xfe) == (byte) 0xfc;
        }

        private static boolean matchesV4(int ip, PrefixEntry entry) {
            if (entry.prefixBits == 0) {
                return true;
            }
            int mask = prefixMask(entry.prefixBits);
            return (ip & mask) == (entry.networkInt & mask);
        }

        private static boolean matchesV6(byte[] ip, PrefixEntry6 entry) {
            if (entry.prefixBits == 0) {
                return true;
            }
            for (int bit = 0; bit < entry.prefixBits; bit++) {
                int byteIndex = bit / 8;
                int bitInByte = 7 - (bit % 8);
                int ipBit = (ip[byteIndex] >> bitInByte) & 1;
                int netBit = (entry.network[byteIndex] >> bitInByte) & 1;
                if (ipBit != netBit) {
                    return false;
                }
            }
            return true;
        }

        private static int prefixMask(int bits) {
            if (bits <= 0) {
                return 0;
            }
            if (bits >= 32) {
                return -1;
            }
            return -1 << (32 - bits);
        }

        private static int ipv4ToInt(Inet4Address addr) {
            byte[] octets = addr.getAddress();
            return ((octets[0] & 0xFF) << 24)
                    | ((octets[1] & 0xFF) << 16)
                    | ((octets[2] & 0xFF) << 8)
                    | (octets[3] & 0xFF);
        }

        @SuppressWarnings("unchecked")
        private static PrefixTables parseYaml(String payload) {
            Yaml yaml = new Yaml();
            Object raw = yaml.load(payload);
            if (!(raw instanceof Map<?, ?> root)) {
                throw new IllegalArgumentException("ASN hints YAML root must be a mapping");
            }
            Object v4Raw = root.get("prefixes");
            Object v6Raw = root.get("prefixes_v6");
            if (v4Raw == null && v6Raw == null) {
                return embeddedDefaults();
            }
            List<PrefixEntry> v4 = v4Raw == null ? List.of() : parseV4Mapping(v4Raw);
            List<PrefixEntry6> v6 = v6Raw == null ? List.of() : parseV6Mapping(v6Raw);
            return new PrefixTables(v4, v6);
        }

        private static List<PrefixEntry> parseV4Mapping(Object prefixesRaw) {
            if (!(prefixesRaw instanceof Map<?, ?> prefixes)) {
                throw new IllegalArgumentException("ASN hints 'prefixes' must be a mapping");
            }
            List<PrefixEntry> entries = new ArrayList<>();
            for (Map.Entry<?, ?> item : prefixes.entrySet()) {
                String cidr = String.valueOf(item.getKey()).trim();
                AsnInfo info = parseAsnValue(item.getValue());
                entries.add(parseV4Cidr(cidr, info));
            }
            return entries;
        }

        private static List<PrefixEntry6> parseV6Mapping(Object prefixesRaw) {
            if (!(prefixesRaw instanceof Map<?, ?> prefixes)) {
                throw new IllegalArgumentException("ASN hints 'prefixes_v6' must be a mapping");
            }
            List<PrefixEntry6> entries = new ArrayList<>();
            for (Map.Entry<?, ?> item : prefixes.entrySet()) {
                String cidr = String.valueOf(item.getKey()).trim();
                AsnInfo info = parseAsnValue(item.getValue());
                entries.add(parseV6Cidr(cidr, info));
            }
            return entries;
        }

        private static AsnInfo parseAsnValue(Object value) {
            if (value instanceof Map<?, ?> map) {
                Object asnRaw = map.get("asn");
                if (asnRaw == null) {
                    throw new IllegalArgumentException("ASN hint mapping requires 'asn'");
                }
                int asn = asnRaw instanceof Number number
                        ? number.intValue()
                        : Integer.parseInt(String.valueOf(asnRaw).trim());
                String org = map.get("org") == null ? "" : String.valueOf(map.get("org"));
                return new AsnInfo(asn, org);
            }
            throw new IllegalArgumentException("ASN hint value must be {asn, org} mapping, got: " + value);
        }

        private static PrefixEntry parseV4Cidr(String cidr, AsnInfo info) {
            String[] parts = cidr.split("/", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid CIDR: " + cidr);
            }
            int prefixBits = Integer.parseInt(parts[1].trim());
            if (prefixBits < 0 || prefixBits > 32) {
                throw new IllegalArgumentException("Invalid prefix length in: " + cidr);
            }
            try {
                InetAddress network = InetAddress.getByName(parts[0].trim());
                if (!(network instanceof Inet4Address ipv4Network)) {
                    throw new IllegalArgumentException("Invalid IPv4 CIDR: " + cidr);
                }
                return new PrefixEntry(prefixBits, ipv4ToInt(ipv4Network), info);
            } catch (UnknownHostException exc) {
                throw new IllegalArgumentException("Invalid network in CIDR: " + cidr, exc);
            }
        }

        private static PrefixEntry6 parseV6Cidr(String cidr, AsnInfo info) {
            String[] parts = cidr.split("/", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid IPv6 CIDR: " + cidr);
            }
            int prefixBits = Integer.parseInt(parts[1].trim());
            if (prefixBits < 0 || prefixBits > 128) {
                throw new IllegalArgumentException("Invalid IPv6 prefix length in: " + cidr);
            }
            try {
                InetAddress network = InetAddress.getByName(parts[0].trim());
                if (!(network instanceof Inet6Address ipv6Network)) {
                    throw new IllegalArgumentException("Invalid IPv6 CIDR: " + cidr);
                }
                return new PrefixEntry6(prefixBits, ipv6Network.getAddress(), info);
            } catch (UnknownHostException exc) {
                throw new IllegalArgumentException("Invalid IPv6 network in CIDR: " + cidr, exc);
            }
        }

        private static PrefixTables embeddedDefaults() {
            return new PrefixTables(
                    List.of(
                            parseV4Cidr("8.8.8.0/24", new AsnInfo(15169, "Google")),
                            parseV4Cidr("1.1.1.0/24", new AsnInfo(13335, "Cloudflare"))),
                    List.of(parseV6Cidr("2001:4860:4860::/48", new AsnInfo(15169, "Google"))));
        }
    }
}
