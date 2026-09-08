package io.pingui.geoip;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Offline GeoIP helpers for API / route_change / export (P36-009).
 *
 * <p>Safe to call {@link IpMetadataService#resolve(String)} here — not on the probe critical path.
 * Default API/CSV shapes stay unchanged; enrichment is opt-in or additive optional JSON fields.
 */
public final class RouteGeoEnrichment {
    private RouteGeoEnrichment() {}

    /** Resolve enrichment for a hop IP literal; {@code null} when not a literal (e.g. {@code *}). */
    public static IpMetadata resolveLiteral(String ip) {
        return IpMetadataRuntime.get().resolve(ip);
    }

    /**
     * Append nullable hop geo fields to an open hop JSON object (caller supplies leading comma
     * context). Used when {@code GET /routes/{host}?include=geo}.
     */
    public static void appendHopGeoFields(StringBuilder json, IpMetadata meta) {
        Objects.requireNonNull(json, "json");
        json.append(",\"country_iso\":").append(quoteOrNull(meta != null ? meta.countryIso() : null));
        json.append(",\"asn\":");
        if (meta != null && meta.asn() != null) {
            json.append(meta.asn());
        } else {
            json.append("null");
        }
        json.append(",\"organization\":").append(quoteOrNull(meta != null ? meta.organization() : null));
        json.append(",\"source\":")
                .append(quoteOrNull(
                        meta != null && meta.source() != null ? meta.source().name() : null));
        json.append(",\"scope\":")
                .append(quoteOrNull(
                        meta != null && meta.scope() != null ? meta.scope().name() : null));
        json.append(",\"city\":").append(quoteOrNull(meta != null ? meta.city() : null));
        json.append(",\"subdivision\":").append(quoteOrNull(meta != null ? meta.subdivision() : null));
        json.append(",\"latitude\":");
        if (meta != null && meta.latitude() != null) {
            json.append(meta.latitude());
        } else {
            json.append("null");
        }
        json.append(",\"longitude\":");
        if (meta != null && meta.longitude() != null) {
            json.append(meta.longitude());
        } else {
            json.append("null");
        }
    }

    /**
     * Compact {@code detail_json} for persisted route_change (existing column; no schema change).
     *
     * <p>Shape: {@code {"geo_diff":{"old":[...],"new":[...]},"asn_diff":{"old":[...],"new":[...]}}}.
     */
    public static String routeChangeDetailJson(List<String> oldIps, List<String> newIps) {
        StringBuilder sb = new StringBuilder(128);
        sb.append('{');
        appendDiffObjects(sb, oldIps, newIps);
        sb.append('}');
        return sb.toString();
    }

    /**
     * Append {@code geo_diff} / {@code asn_diff} into a route_change JSON object that already ends
     * with {@code }}.
     */
    public static String appendRouteChangeDiffs(String baseJson, List<String> oldIps, List<String> newIps) {
        Objects.requireNonNull(baseJson, "baseJson");
        String trimmed = baseJson.strip();
        if (trimmed.isEmpty() || trimmed.charAt(trimmed.length() - 1) != '}') {
            throw new IllegalArgumentException("baseJson must be a JSON object");
        }
        StringBuilder sb = new StringBuilder(trimmed.length() + 128);
        sb.append(trimmed, 0, trimmed.length() - 1);
        if (trimmed.length() > 2) {
            sb.append(',');
        }
        appendDiffObjects(sb, oldIps, newIps);
        sb.append('}');
        return sb.toString();
    }

    /** CSV cells: country_iso, asn, organization, source (empty when unknown). */
    public static String[] csvGeoCells(IpMetadata meta) {
        if (meta == null) {
            return new String[] {"", "", "", ""};
        }
        return new String[] {
            meta.countryIso() != null ? meta.countryIso() : "",
            meta.asn() != null ? Integer.toString(meta.asn()) : "",
            meta.organization() != null ? meta.organization() : "",
            meta.source() != null ? meta.source().name() : ""
        };
    }

    private static void appendDiffObjects(StringBuilder sb, List<String> oldIps, List<String> newIps) {
        List<IpMetadata> oldMeta = resolveAll(oldIps);
        List<IpMetadata> newMeta = resolveAll(newIps);
        sb.append("\"geo_diff\":{\"old\":");
        appendCountryArray(sb, oldMeta);
        sb.append(",\"new\":");
        appendCountryArray(sb, newMeta);
        sb.append("},\"asn_diff\":{\"old\":");
        appendAsnArray(sb, oldMeta);
        sb.append(",\"new\":");
        appendAsnArray(sb, newMeta);
        sb.append('}');
    }

    private static List<IpMetadata> resolveAll(List<String> ips) {
        List<String> items = ips != null ? ips : List.of();
        List<IpMetadata> out = new ArrayList<>(items.size());
        for (String ip : items) {
            out.add(resolveLiteral(ip));
        }
        return out;
    }

    private static void appendCountryArray(StringBuilder sb, List<IpMetadata> metas) {
        sb.append('[');
        for (int i = 0; i < metas.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            IpMetadata meta = metas.get(i);
            sb.append(quoteOrNull(meta != null ? meta.countryIso() : null));
        }
        sb.append(']');
    }

    private static void appendAsnArray(StringBuilder sb, List<IpMetadata> metas) {
        sb.append('[');
        for (int i = 0; i < metas.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            IpMetadata meta = metas.get(i);
            if (meta != null && meta.asn() != null) {
                sb.append(meta.asn());
            } else {
                sb.append("null");
            }
        }
        sb.append(']');
    }

    private static String quoteOrNull(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder(value.length() + 8);
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        out.append('"');
        return out.toString();
    }
}
