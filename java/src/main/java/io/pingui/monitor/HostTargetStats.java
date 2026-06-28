package io.pingui.monitor;

/** Aggregated ping metrics for the terminal hop of a monitored host. */
public record HostTargetStats(double lossPct, Double minMs, Double avgMs, Double maxMs, boolean timeout) {}
