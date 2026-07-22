package io.pingui.probe;

import io.pingui.config.PingExpertEntry;
import io.pingui.config.PingPreset;
import io.pingui.config.PingPresets;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Short informational ping batch for Expert presets DF / DSCP / Burst (P17-030).
 *
 * <p>Does not mutate Expert form args and is not an MTU wizard — callers show progress + Alert with
 * {@link PresetSelfCheckResult}.
 */
public final class PresetSelfCheck {
    /** Called after each preset finishes ({@code completed} is 1-based). */
    @FunctionalInterface
    public interface ProgressListener {
        void onPresetCompleted(int completed, int total, String presetId);
    }

    private final ExpertPingOnce ping;

    public PresetSelfCheck(ExpertPingOnce ping) {
        this.ping = Objects.requireNonNull(ping, "ping");
    }

    public PresetSelfCheck() {
        this(new ProcessExpertPing()::pingOnce);
    }

    /**
     * Runs {@code probesPerPreset} one-shot pings for each configured preset id.
     *
     * @throws IllegalArgumentException if a preset id is missing from {@link PingPresets}
     */
    public PresetSelfCheckResult run(String target, PresetSelfCheckConfig config) throws IOException {
        return run(target, config, null);
    }

    /**
     * Same as {@link #run(String, PresetSelfCheckConfig)} with optional per-preset progress (P20-008).
     *
     * @param progress may be {@code null}; invoked after each preset completes
     */
    public PresetSelfCheckResult run(String target, PresetSelfCheckConfig config, ProgressListener progress)
            throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(config, "config");
        if (target.isBlank()) {
            throw new IllegalArgumentException("target must be non-blank");
        }

        List<PresetSelfCheckResult.PresetCheck> checks = new ArrayList<>();
        boolean anyWarn = false;
        List<String> afOnly = List.of(config.ipv6() ? "-6" : "-4");
        List<String> presetIds = config.presetIds();
        int total = presetIds.size();
        int completed = 0;

        for (String presetId : presetIds) {
            PingPreset preset = requirePreset(presetId);
            List<String> args = PingPresets.mergeKeepingAddressFamily(afOnly, preset.args());
            PingExpertEntry expert = new PingExpertEntry(false, args);

            int sent = 0;
            int lost = 0;
            double rttSum = 0.0;
            int rttCount = 0;
            for (int i = 0; i < config.probesPerPreset(); i++) {
                sent++;
                OptionalDouble rtt = ping.pingOnce(target, expert, config.timeoutSeconds());
                if (rtt.isPresent()) {
                    rttSum += rtt.getAsDouble();
                    rttCount++;
                } else {
                    lost++;
                }
            }
            double lossPct = sent == 0 ? 100.0 : (100.0 * lost) / sent;
            boolean warn = lossPct + 1e-9 >= config.lossWarnPct();
            anyWarn = anyWarn || warn;
            OptionalDouble avg = rttCount > 0 ? OptionalDouble.of(rttSum / rttCount) : OptionalDouble.empty();
            checks.add(new PresetSelfCheckResult.PresetCheck(
                    preset.id(), preset.label(), args, sent, lost, lossPct, avg, warn));
            completed++;
            if (progress != null) {
                progress.onPresetCompleted(completed, total, presetId);
            }
        }
        return new PresetSelfCheckResult(checks, anyWarn);
    }

    private static PingPreset requirePreset(String presetId) {
        Optional<PingPreset> found =
                PingPresets.all().stream().filter(p -> p.id().equals(presetId)).findFirst();
        return found.orElseThrow(() -> new IllegalArgumentException("unknown preset id: " + presetId));
    }
}
