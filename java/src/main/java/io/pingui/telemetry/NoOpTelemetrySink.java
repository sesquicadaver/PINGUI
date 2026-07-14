package io.pingui.telemetry;

/** Default no-op {@link TelemetrySink} (P16-011). */
enum NoOpTelemetrySink implements TelemetrySink {
    INSTANCE;

    static final String ID = "noop";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void onSample(MetricSample sample) {
        // intentionally empty
    }

    @Override
    public void onEvent(TelemetryEvent event) {
        // intentionally empty
    }
}
