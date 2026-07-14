package io.pingui.probe;

/**
 * Parameters for path MTU payload sweep (P17-020).
 *
 * <p>Defaults: IPv4 Ethernet-ish max payload 1472, min 64, step 16, 10 probes/size, stop at ≥1%
 * loss. Sweep direction is ascending ({@code minPayload → startPayload}).
 */
public record MtuDiscoveryConfig(
        int startPayload,
        int minPayload,
        int step,
        int probesPerSize,
        double lossThresholdPct,
        boolean ipv6,
        double timeoutSeconds) {

    public static final int IPV4_ICMP_OVERHEAD = 28;
    public static final int IPV6_ICMP_OVERHEAD = 48;

    public MtuDiscoveryConfig {
        if (startPayload < 0 || minPayload < 0) {
            throw new IllegalArgumentException("payload sizes must be >= 0");
        }
        if (startPayload < minPayload) {
            throw new IllegalArgumentException("startPayload must be >= minPayload");
        }
        if (step < 1) {
            throw new IllegalArgumentException("step must be >= 1");
        }
        if (probesPerSize < 1) {
            throw new IllegalArgumentException("probesPerSize must be >= 1");
        }
        if (lossThresholdPct < 0.0 || lossThresholdPct > 100.0) {
            throw new IllegalArgumentException("lossThresholdPct must be 0..100");
        }
        if (timeoutSeconds <= 0.0) {
            throw new IllegalArgumentException("timeoutSeconds must be positive");
        }
    }

    /** IPv4 defaults: start 1472, min 64, step 16, 10 probes, 1% loss. */
    public static MtuDiscoveryConfig ipv4Defaults() {
        return new MtuDiscoveryConfig(1472, 64, 16, 10, 1.0, false, 1.0);
    }

    /** IPv6 defaults: start 1452, min 64, step 16, 10 probes, 1% loss. */
    public static MtuDiscoveryConfig ipv6Defaults() {
        return new MtuDiscoveryConfig(1452, 64, 16, 10, 1.0, true, 1.0);
    }

    public int icmpOverhead() {
        return ipv6 ? IPV6_ICMP_OVERHEAD : IPV4_ICMP_OVERHEAD;
    }

    /** IP/ICMP MTU for a successful payload size. */
    public int mtuForPayload(int payloadBytes) {
        return payloadBytes + icmpOverhead();
    }
}
