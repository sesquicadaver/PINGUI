package io.pingui.probe;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.BooleanSupplier;

/**
 * Controlled ICMP payload sweep to estimate max workable size (P17-020).
 *
 * <p>Walks {@code minPayload → startPayload} by {@code step} (ascending), runs {@code
 * probesPerSize} Don't-Fragment echoes at each size, and <strong>stops</strong> when {@code loss% ≥
 * lossThresholdPct}. Recommended MTU = last good payload + IP/ICMP overhead (so the first lossy
 * size is treated as the cliff).
 *
 * <p>Does not touch JavaFX or {@code MonitorService} — UI wiring is P17-021.
 */
public final class MtuDiscovery {
    private final MtuProbeRunner runner;

    public MtuDiscovery(MtuProbeRunner runner) {
        this.runner = Objects.requireNonNull(runner, "runner");
    }

    /**
     * Runs the ascending sweep until loss threshold, max payload, or cancel.
     *
     * @param cancel returns true to abort (checked between probe attempts)
     */
    public MtuDiscoveryResult discover(String target, MtuDiscoveryConfig config, BooleanSupplier cancel)
            throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(config, "config");
        BooleanSupplier stop = cancel != null ? cancel : () -> false;
        if (target.isBlank()) {
            throw new IllegalArgumentException("target must be non-blank");
        }

        List<MtuDiscoveryResult.MtuProbeStep> steps = new ArrayList<>();
        int lastGood = -1;
        boolean stoppedOnLoss = false;
        boolean cancelled = false;

        for (int payload = config.minPayload(); payload <= config.startPayload(); payload += config.step()) {
            if (stop.getAsBoolean()) {
                cancelled = true;
                break;
            }
            int sent = 0;
            int lost = 0;
            for (int i = 0; i < config.probesPerSize(); i++) {
                if (stop.getAsBoolean()) {
                    cancelled = true;
                    break;
                }
                sent++;
                boolean ok = runner.pingOnce(target, payload, config.ipv6(), config.timeoutSeconds());
                if (!ok) {
                    lost++;
                }
            }
            if (cancelled && sent == 0) {
                break;
            }
            double lossPct = sent == 0 ? 100.0 : (100.0 * lost) / sent;
            boolean overThreshold = lossPct + 1e-9 >= config.lossThresholdPct();
            steps.add(new MtuDiscoveryResult.MtuProbeStep(payload, sent, lost, lossPct, overThreshold));
            if (cancelled) {
                break;
            }
            if (overThreshold) {
                stoppedOnLoss = true;
                break;
            }
            lastGood = payload;
        }

        OptionalInt maxGood = lastGood >= 0 ? OptionalInt.of(lastGood) : OptionalInt.empty();
        OptionalInt mtu =
                maxGood.isPresent() ? OptionalInt.of(config.mtuForPayload(maxGood.getAsInt())) : OptionalInt.empty();
        return new MtuDiscoveryResult(maxGood, mtu, steps, stoppedOnLoss, cancelled);
    }

    /** Convenience: no cancel. */
    public MtuDiscoveryResult discover(String target, MtuDiscoveryConfig config) throws IOException {
        return discover(target, config, () -> false);
    }
}
