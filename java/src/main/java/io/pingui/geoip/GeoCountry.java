package io.pingui.geoip;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
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

    static final class CountryLookup {
        private final List<PrefixEntry> prefixes;

        CountryLookup(List<PrefixEntry> prefixes) {
            this.prefixes = prefixes.stream()
                    .sorted(Comparator.comparingInt((PrefixEntry e) -> e.prefixBits)
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
            Inet4Address addr;
            try {
                InetAddress parsed = InetAddress.getByName(ip);
                if (!(parsed instanceof Inet4Address ipv4)) {
                    return null;
                }
                addr = ipv4;
            } catch (UnknownHostException exc) {
                return null;
            }
            if (addr.isLoopbackAddress() || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()) {
                return LAN_TAG;
            }
            if (addr.isMulticastAddress()) {
                return null;
            }
            int value = ipv4ToInt(addr);
            for (PrefixEntry entry : prefixes) {
                if (matches(value, entry)) {
                    return entry.code;
                }
            }
            return null;
        }

        private static boolean matches(int ip, PrefixEntry entry) {
            if (entry.prefixBits == 0) {
                return true;
            }
            int mask = prefixMask(entry.prefixBits);
            return (ip & mask) == (entry.networkInt & mask);
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
        private static List<PrefixEntry> parseYaml(String payload) {
            Yaml yaml = new Yaml();
            Object raw = yaml.load(payload);
            if (!(raw instanceof Map<?, ?> root)) {
                throw new IllegalArgumentException("GeoIP hints YAML root must be a mapping");
            }
            Object prefixesRaw = root.get("prefixes");
            if (prefixesRaw == null) {
                return embeddedDefaults();
            }
            if (!(prefixesRaw instanceof Map<?, ?> prefixes)) {
                throw new IllegalArgumentException("GeoIP hints 'prefixes' must be a mapping");
            }
            List<PrefixEntry> entries = new ArrayList<>();
            for (Map.Entry<?, ?> item : prefixes.entrySet()) {
                String cidr = String.valueOf(item.getKey()).trim();
                String code = String.valueOf(item.getValue()).trim().toUpperCase();
                if (code.length() != 2 || !code.chars().allMatch(Character::isLetter)) {
                    throw new IllegalArgumentException("Invalid country code: " + code);
                }
                entries.add(parseCidr(cidr, code));
            }
            return entries;
        }

        private static PrefixEntry parseCidr(String cidr, String code) {
            String[] parts = cidr.split("/", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid CIDR: " + cidr);
            }
            int prefixBits = Integer.parseInt(parts[1].trim());
            if (prefixBits < 0 || prefixBits > 32) {
                throw new IllegalArgumentException("Invalid prefix length in: " + cidr);
            }
            try {
                Inet4Address network = (Inet4Address) InetAddress.getByName(parts[0].trim());
                return new PrefixEntry(prefixBits, ipv4ToInt(network), code);
            } catch (UnknownHostException exc) {
                throw new IllegalArgumentException("Invalid network in CIDR: " + cidr, exc);
            }
        }

        private static List<PrefixEntry> embeddedDefaults() {
            return List.of(
                    parseCidr("8.8.8.0/24", "US"),
                    parseCidr("8.8.4.0/24", "US"),
                    parseCidr("1.1.1.0/24", "AU"),
                    parseCidr("1.0.0.0/24", "AU"));
        }
    }
}
