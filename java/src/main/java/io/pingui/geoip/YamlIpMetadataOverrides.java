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
import java.util.Locale;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/**
 * Offline YAML CIDR overrides for {@link IpMetadata} (P36-004).
 *
 * <p>Supports the legacy country-hint form ({@code CIDR: US}) and the extended mapping form
 * ({@code country}, {@code city}, coordinates, {@code asn}, …). Longest-prefix match; no DNS/HTTP.
 *
 * <p>Returns {@code null} for non-literals and for addresses with no matching prefix (composed by
 * {@link IpMetadataService}). Matching overrides apply even to private/special scopes so corporate
 * WAN/docs ranges can carry explicit metadata.
 */
public final class YamlIpMetadataOverrides implements IpMetadataProvider {
    private final List<OverrideEntry4> v4;
    private final List<OverrideEntry6> v6;

    YamlIpMetadataOverrides(List<OverrideEntry4> v4, List<OverrideEntry6> v6) {
        this.v4 = List.copyOf(v4);
        this.v6 = List.copyOf(v6);
    }

    public static YamlIpMetadataOverrides fromFile(Path path) {
        try {
            return fromYaml(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException exc) {
            throw new IllegalStateException("Failed to read IP metadata overrides: " + path, exc);
        }
    }

    public static YamlIpMetadataOverrides fromResource(String resourceName) {
        InputStream stream = YamlIpMetadataOverrides.class.getClassLoader().getResourceAsStream(resourceName);
        if (stream == null) {
            throw new IllegalArgumentException("Missing IP metadata overrides resource: " + resourceName);
        }
        try (stream) {
            return fromYaml(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException exc) {
            throw new IllegalStateException("Failed to read IP metadata overrides resource: " + resourceName, exc);
        }
    }

    public static YamlIpMetadataOverrides fromYaml(String payload) {
        Yaml yaml = new Yaml();
        Object raw = yaml.load(payload);
        if (raw == null) {
            return new YamlIpMetadataOverrides(List.of(), List.of());
        }
        if (!(raw instanceof Map<?, ?> root)) {
            throw new IllegalArgumentException("IP metadata overrides YAML root must be a mapping");
        }
        Object v4Raw = root.get("prefixes");
        Object v6Raw = root.get("prefixes_v6");
        List<OverrideEntry4> v4 = v4Raw == null ? List.of() : parseV4Mapping(v4Raw);
        List<OverrideEntry6> v6 = v6Raw == null ? List.of() : parseV6Mapping(v6Raw);
        v4 = v4.stream()
                .sorted(Comparator.comparingInt((OverrideEntry4 e) -> e.prefixBits)
                        .reversed())
                .toList();
        v6 = v6.stream()
                .sorted(Comparator.comparingInt((OverrideEntry6 e) -> e.prefixBits)
                        .reversed())
                .toList();
        return new YamlIpMetadataOverrides(v4, v6);
    }

    /** Number of loaded IPv4 + IPv6 prefix rules (for tests / ops). */
    public int ruleCount() {
        return v4.size() + v6.size();
    }

    @Override
    public IpMetadata lookup(String ip) {
        InetAddress address = IpLiterals.parseLiteralOrNull(ip);
        if (address == null) {
            return null;
        }
        IpAddressScope scope = IpAddressClassifier.scopeOf(address);
        OverrideFields fields = match(address);
        if (fields == null) {
            return null;
        }
        return fields.toMetadata(address.getHostAddress(), scope);
    }

    private OverrideFields match(InetAddress address) {
        if (address instanceof Inet4Address ipv4) {
            int value = ipv4ToInt(ipv4);
            for (OverrideEntry4 entry : v4) {
                if (matchesV4(value, entry)) {
                    return entry.fields;
                }
            }
            return null;
        }
        if (address instanceof Inet6Address ipv6) {
            byte[] value = ipv6.getAddress();
            for (OverrideEntry6 entry : v6) {
                if (matchesV6(value, entry)) {
                    return entry.fields;
                }
            }
            return null;
        }
        return null;
    }

    private static List<OverrideEntry4> parseV4Mapping(Object prefixesRaw) {
        if (!(prefixesRaw instanceof Map<?, ?> prefixes)) {
            throw new IllegalArgumentException("IP metadata overrides 'prefixes' must be a mapping");
        }
        List<OverrideEntry4> entries = new ArrayList<>();
        for (Map.Entry<?, ?> item : prefixes.entrySet()) {
            String cidr = String.valueOf(item.getKey()).trim();
            OverrideFields fields = parseFields(item.getValue());
            entries.add(parseV4Cidr(cidr, fields));
        }
        return entries;
    }

    private static List<OverrideEntry6> parseV6Mapping(Object prefixesRaw) {
        if (!(prefixesRaw instanceof Map<?, ?> prefixes)) {
            throw new IllegalArgumentException("IP metadata overrides 'prefixes_v6' must be a mapping");
        }
        List<OverrideEntry6> entries = new ArrayList<>();
        for (Map.Entry<?, ?> item : prefixes.entrySet()) {
            String cidr = String.valueOf(item.getKey()).trim();
            OverrideFields fields = parseFields(item.getValue());
            entries.add(parseV6Cidr(cidr, fields));
        }
        return entries;
    }

    private static OverrideFields parseFields(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Override value must not be null");
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return OverrideFields.legacyCountry(String.valueOf(value));
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Override value must be a country code or a mapping");
        }
        String country = stringField(map, "country");
        if (country == null) {
            country = stringField(map, "country_iso");
        }
        String subdivision = firstString(map, "subdivision", "region");
        String city = stringField(map, "city");
        Double latitude = doubleField(map, "latitude");
        Double longitude = doubleField(map, "longitude");
        Double accuracy = firstDouble(map, "accuracy_radius_km", "accuracy_radius", "accuracyRadiusKm");
        Integer asn = intField(map, "asn");
        String organization = firstString(map, "organization", "org");
        String label = stringField(map, "label");
        if (organization == null && label != null) {
            organization = label;
        }
        if (country == null
                && subdivision == null
                && city == null
                && latitude == null
                && longitude == null
                && accuracy == null
                && asn == null
                && organization == null) {
            throw new IllegalArgumentException("Override mapping must define at least one field");
        }
        return new OverrideFields(country, subdivision, city, latitude, longitude, accuracy, asn, organization);
    }

    private static String firstString(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            String value = stringField(map, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Double firstDouble(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Double value = doubleField(map, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String stringField(Map<?, ?> map, String key) {
        Object raw = map.get(key);
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).strip();
        return value.isEmpty() ? null : value;
    }

    private static Double doubleField(Map<?, ?> map, String key) {
        Object raw = map.get(key);
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(raw).strip());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid numeric field '" + key + "': " + raw, ex);
        }
    }

    private static Integer intField(Map<?, ?> map, String key) {
        Object raw = map.get(key);
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(raw).strip());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid integer field '" + key + "': " + raw, ex);
        }
    }

    private static OverrideEntry4 parseV4Cidr(String cidr, OverrideFields fields) {
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
            return new OverrideEntry4(prefixBits, ipv4ToInt(ipv4Network), fields);
        } catch (UnknownHostException exc) {
            throw new IllegalArgumentException("Invalid network in CIDR: " + cidr, exc);
        }
    }

    private static OverrideEntry6 parseV6Cidr(String cidr, OverrideFields fields) {
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
            return new OverrideEntry6(prefixBits, ipv6Network.getAddress(), fields);
        } catch (UnknownHostException exc) {
            throw new IllegalArgumentException("Invalid IPv6 network in CIDR: " + cidr, exc);
        }
    }

    private static boolean matchesV4(int ip, OverrideEntry4 entry) {
        if (entry.prefixBits == 0) {
            return true;
        }
        int mask = prefixMask(entry.prefixBits);
        return (ip & mask) == (entry.networkInt & mask);
    }

    private static boolean matchesV6(byte[] ip, OverrideEntry6 entry) {
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
        return ((octets[0] & 0xFF) << 24) | ((octets[1] & 0xFF) << 16) | ((octets[2] & 0xFF) << 8) | (octets[3] & 0xFF);
    }

    private record OverrideEntry4(int prefixBits, int networkInt, OverrideFields fields) {}

    private record OverrideEntry6(int prefixBits, byte[] network, OverrideFields fields) {}

    private record OverrideFields(
            String countryIso,
            String subdivision,
            String city,
            Double latitude,
            Double longitude,
            Double accuracyRadiusKm,
            Integer asn,
            String organization) {

        static OverrideFields legacyCountry(String code) {
            String normalized = code.strip().toUpperCase(Locale.ROOT);
            if (normalized.length() != 2 || !normalized.chars().allMatch(Character::isLetter)) {
                throw new IllegalArgumentException("Invalid country code: " + code);
            }
            return new OverrideFields(normalized, null, null, null, null, null, null, null);
        }

        IpMetadata toMetadata(String canonicalIp, IpAddressScope scope) {
            return new IpMetadata(
                    canonicalIp,
                    scope,
                    countryIso,
                    subdivision,
                    city,
                    latitude,
                    longitude,
                    accuracyRadiusKm,
                    asn,
                    organization,
                    IpMetadataSource.OVERRIDE,
                    null);
        }
    }
}
