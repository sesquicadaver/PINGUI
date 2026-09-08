package io.pingui.geoip;

/** Provenance of an {@link IpMetadata} enrichment result (P36-002). */
public enum IpMetadataSource {
    /** Manual YAML / corporate override. */
    OVERRIDE,
    /** Local MaxMind-style MMDB. */
    MMDB,
    /** No enrichment available (unknown or disabled). */
    NONE
}
