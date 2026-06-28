package io.pingui.probe.icmp;

/** Result of a single TTL-limited ICMP probe. */
public record ProbeResult(String sourceIp, double rttMs, boolean target) {}
