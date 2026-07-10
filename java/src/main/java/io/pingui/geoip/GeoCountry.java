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

/** Offline country hints for hop labels (longest-prefix match). */
public final class GeoCountry {
    public static final String LAN_TAG = "LAN";
    private static final String DEFAULT_RESOURCE = "geoip_hints.yaml";

    private static boolean enabled = true;
    private static CountryLookup lookup = CountryLookup.fromResource(DEFAULT_RESOURCE);

    private GeoCountry() {}

    public static void configure(boolean geoipEnabled, Path hintsPath) {
        enabled = geoipEnabled;
        if (!geoipEnabled) {
            lookup = null;
            return;
        }
        if (hintsPath != null && Files.isRegularFile(hintsPath)) {
            lookup = CountryLookup.fromFile(hintsPath);
        } else {
            lookup = CountryLookup.fromResource(DEFAULT_RESOURCE);
        }
    }

    public static String lookup(String ip) {
        if (!enabled || lookup == null || ip == null || ip.isBlank()) {
            return null;
        }
        return lookup.resolve(ip.trim());
    }

    static final class PrefixEntry {
        final int prefixBits;
        final int networkInt;
        final String code;

        PrefixEntry(int prefixBits, int networkInt, String code) {
            this.prefixBits = prefixBits;
            this.networkInt = networkInt;
            this.code = code;
        }
    }

    static final class PrefixEntry6 {
        final int prefixBits;
        final byte[] network;
        final String code;

        PrefixEntry6(int prefixBits, byte[] network, String code) {
            this.prefixBits = prefixBits;
            this.network = network;
            this.code = code;
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

    static final class CountryLookup {
        private final List<PrefixEntry> v4Prefixes;
        private final List<PrefixEntry6> v6Prefixes;

        CountryLookup(PrefixTables tables) {
            this.v4Prefixes = tables.v4.stream()
                    .sorted(Comparator.comparingInt((PrefixEntry e) -> e.prefixBits)
                            .reversed())
                    .toList();
            this.v6Prefixes = tables.v6.stream()
                    .sorted(Comparator.comparingInt((PrefixEntry6 e) -> e.prefixBits)
                            .reversed())
                    .toList();
        }

        static CountryLookup fromResource(String resourceName) {
            InputStream stream = GeoCountry.class.getClassLoader().getResourceAsStream(resourceName);
            if (stream == null) {
                return new CountryLookup(embeddedDefaults());
            }
            try (stream) {
                String payload = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                return new CountryLookup(parseYaml(payload));
            } catch (IOException exc) {
                throw new IllegalStateException("Failed to read GeoIP hints resource: " + resourceName, exc);
            }
        }

        static CountryLookup fromFile(Path path) {
            try {
                return new CountryLookup(parseYaml(Files.readString(path, StandardCharsets.UTF_8)));
            } catch (IOException exc) {
                throw new IllegalStateException("Failed to read GeoIP hints: " + path, exc);
            }
        }

        String resolve(String ip) {
            InetAddress parsed = IpLiterals.parseLiteralOrNull(ip);
            if (parsed instanceof Inet4Address ipv4) {
                return resolveV4(ipv4);
            }
            if (parsed instanceof Inet6Address ipv6) {
                return resolveV6(ipv6);
            }
            return null;
        }

        private String resolveV4(Inet4Address addr) {
            if (addr.isLoopbackAddress() || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()) {
                return LAN_TAG;
            }
            if (addr.isMulticastAddress()) {
                return null;
            }
            int value = ipv4ToInt(addr);
            for (PrefixEntry entry : v4Prefixes) {
                if (matchesV4(value, entry)) {
                    return entry.code;
                }
            }
            return null;
        }

        private String resolveV6(Inet6Address addr) {
            if (isIpv6Lan(addr)) {
                return LAN_TAG;
            }
            if (addr.isMulticastAddress()) {
                return null;
            }
            byte[] value = addr.getAddress();
            for (PrefixEntry6 entry : v6Prefixes) {
                if (matchesV6(value, entry)) {
                    return entry.code;
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
                throw new IllegalArgumentException("GeoIP hints YAML root must be a mapping");
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
                throw new IllegalArgumentException("GeoIP hints 'prefixes' must be a mapping");
            }
            List<PrefixEntry> entries = new ArrayList<>();
            for (Map.Entry<?, ?> item : prefixes.entrySet()) {
                String cidr = String.valueOf(item.getKey()).trim();
                String code = normalizeCountryCode(String.valueOf(item.getValue()));
                entries.add(parseV4Cidr(cidr, code));
            }
            return entries;
        }

        private static List<PrefixEntry6> parseV6Mapping(Object prefixesRaw) {
            if (!(prefixesRaw instanceof Map<?, ?> prefixes)) {
                throw new IllegalArgumentException("GeoIP hints 'prefixes_v6' must be a mapping");
            }
            List<PrefixEntry6> entries = new ArrayList<>();
            for (Map.Entry<?, ?> item : prefixes.entrySet()) {
                String cidr = String.valueOf(item.getKey()).trim();
                String code = normalizeCountryCode(String.valueOf(item.getValue()));
                entries.add(parseV6Cidr(cidr, code));
            }
            return entries;
        }

        private static String normalizeCountryCode(String code) {
            String normalized = code.trim().toUpperCase();
            if (normalized.length() != 2 || !normalized.chars().allMatch(Character::isLetter)) {
                throw new IllegalArgumentException("Invalid country code: " + code);
            }
            return normalized;
        }

        private static PrefixEntry parseV4Cidr(String cidr, String code) {
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
                return new PrefixEntry(prefixBits, ipv4ToInt(ipv4Network), code);
            } catch (UnknownHostException exc) {
                throw new IllegalArgumentException("Invalid network in CIDR: " + cidr, exc);
            }
        }

        private static PrefixEntry6 parseV6Cidr(String cidr, String code) {
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
                return new PrefixEntry6(prefixBits, ipv6Network.getAddress(), code);
            } catch (UnknownHostException exc) {
                throw new IllegalArgumentException("Invalid IPv6 network in CIDR: " + cidr, exc);
            }
        }

        private static PrefixTables embeddedDefaults() {
            return new PrefixTables(
                    List.of(
                            parseV4Cidr("8.8.8.0/24", "US"),
                            parseV4Cidr("8.8.4.0/24", "US"),
                            parseV4Cidr("1.1.1.0/24", "AU"),
                            parseV4Cidr("1.0.0.0/24", "AU")),
                    List.of(parseV6Cidr("2001:db8::/32", "US")));
        }
    }
}
