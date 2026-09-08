package io.pingui.ui;

import io.pingui.geoip.IpAddressClassifier;
import io.pingui.geoip.IpAddressScope;
import io.pingui.geoip.IpLiterals;
import io.pingui.geoip.IpMetadata;
import io.pingui.geoip.IpMetadataRuntime;
import io.pingui.geoip.IpMetadataService;
import io.pingui.geoip.IpMetadataSource;
import io.pingui.model.Models;
import io.pingui.model.Models.HopNode;
import java.net.InetAddress;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Cache-first hop GeoIP/ASN label helpers for the graph (P36-008 / P36-012).
 *
 * <p>Reads {@link IpMetadataRuntime} cache only (offers background fill on miss). No legacy {@code
 * GeoCountry}/{@code AsnLookup} gap-fill — enrichment comes from {@link IpMetadataService} only.
 */
public final class HopGeoLabels {
    static final String LAN_TAG = "LAN";

    private static final DateTimeFormatter EPOCH_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private HopGeoLabels() {}

    /**
     * Cache-only lookup; schedules {@link IpMetadataService#offer(String)} on miss.
     *
     * @return cached snapshot, or {@code null} when missing / non-literal
     */
    public static IpMetadata cachedOrOffer(String ip) {
        if (ip == null || ip.isBlank() || Models.TIMEOUT_IP.equals(ip)) {
            return null;
        }
        IpMetadataService service = IpMetadataRuntime.get();
        IpMetadata cached = service.cached(ip);
        if (cached != null) {
            return cached;
        }
        service.offer(ip);
        return null;
    }

    /**
     * Compact enrichment line for node labels, e.g. {@code US · AS15169 Google} or {@code LAN}.
     */
    public static String compactLine(String ip, IpMetadata meta) {
        String country = countryCode(ip, meta);
        String asn = asnLabel(meta);
        if (country != null && asn != null) {
            return country + " · " + asn;
        }
        if (country != null) {
            return country;
        }
        if (asn != null) {
            return asn;
        }
        return "";
    }

    /** Geographic strip token: {@code LAN}, {@code US/AS15169}, {@code US}, or empty. */
    public static String stripToken(String ip, IpMetadata meta) {
        if (ip == null || ip.isBlank() || Models.TIMEOUT_IP.equals(ip)) {
            return "";
        }
        String country = countryCode(ip, meta);
        Integer asn = asnNumber(meta);
        if (country != null && asn != null) {
            return country + "/AS" + asn;
        }
        if (country != null) {
            return country;
        }
        if (asn != null) {
            return "AS" + asn;
        }
        return "";
    }

    /**
     * Compact route strip {@code LAN → UA/AS6849 → DE/AS3320}. Skips empty/timeout hops; returns
     * empty when fewer than one token.
     */
    public static String formatStrip(List<HopNode> hops) {
        if (hops == null || hops.isEmpty()) {
            return "";
        }
        List<String> tokens = new ArrayList<>();
        for (HopNode hop : hops) {
            if (hop == null || hop.timeout() || Models.TIMEOUT_IP.equals(hop.ip())) {
                continue;
            }
            IpMetadata meta = cachedOrOffer(hop.ip());
            String token = stripToken(hop.ip(), meta);
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        if (tokens.isEmpty()) {
            return "";
        }
        return String.join(" → ", tokens);
    }

    /** Multi-line tooltip details (country/city/ASN/source/epoch/accuracy). */
    public static String details(IpMetadata meta) {
        if (meta == null || meta.source() == IpMetadataSource.NONE) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        if (meta.countryIso() != null) {
            line(out, "country: " + meta.countryIso());
        }
        if (meta.subdivision() != null) {
            line(out, "region: " + meta.subdivision());
        }
        if (meta.city() != null) {
            line(out, "city: " + meta.city());
        }
        if (meta.asn() != null) {
            String org = meta.organization() != null ? " " + meta.organization() : "";
            line(out, "ASN: AS" + meta.asn() + org);
        } else if (meta.organization() != null) {
            line(out, "org: " + meta.organization());
        }
        line(out, "source: " + meta.source());
        if (meta.datasetEpochSeconds() != null) {
            line(out, "dataset: " + EPOCH_FMT.format(Instant.ofEpochSecond(meta.datasetEpochSeconds())));
        }
        if (meta.accuracyRadiusKm() != null) {
            line(out, "accuracy: ~" + meta.accuracyRadiusKm().intValue() + " km");
            line(out, "approximate");
        } else if (meta.hasCoordinates()) {
            line(out, "approximate");
        }
        return out.toString();
    }

    private static String countryCode(String ip, IpMetadata meta) {
        if (meta != null && meta.countryIso() != null) {
            return meta.countryIso();
        }
        if (meta != null && meta.scope() == IpAddressScope.PRIVATE) {
            return LAN_TAG;
        }
        if (meta == null) {
            InetAddress address = IpLiterals.parseLiteralOrNull(ip);
            if (address != null && IpAddressClassifier.scopeOf(address) == IpAddressScope.PRIVATE) {
                return LAN_TAG;
            }
        }
        return null;
    }

    private static String asnLabel(IpMetadata meta) {
        if (meta == null || meta.asn() == null) {
            return null;
        }
        if (meta.organization() == null || meta.organization().isBlank()) {
            return "AS" + meta.asn();
        }
        return "AS" + meta.asn() + " " + meta.organization();
    }

    private static Integer asnNumber(IpMetadata meta) {
        return meta != null ? meta.asn() : null;
    }

    private static void line(StringBuilder out, String value) {
        if (!out.isEmpty()) {
            out.append('\n');
        }
        out.append(value);
    }
}
