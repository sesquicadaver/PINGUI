package io.pingui.geoip;

/** ASN + short organization for hop labels. */
public record AsnInfo(int asn, String org) {
    public AsnInfo {
        if (asn < 0) {
            throw new IllegalArgumentException("ASN must be non-negative: " + asn);
        }
        org = org == null ? "" : org.strip();
    }

    /** Label fragment, e.g. {@code AS15169 Google}. */
    public String label() {
        if (org.isEmpty()) {
            return "AS" + asn;
        }
        return "AS" + asn + " " + org;
    }
}
