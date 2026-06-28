package io.pingui.ui;

import io.pingui.monitor.HostTargetStats;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/** Observable host row for the JavaFX list. */
public final class HostItem {
    private static final String DISABLED_ROW = "#f5f5f5";
    private static final String WAITING_ROW = "#d3d3d3";

    private final StringProperty host = new SimpleStringProperty();
    private final BooleanProperty enabled = new SimpleBooleanProperty(false);
    private final BooleanProperty showMetrics = new SimpleBooleanProperty(false);
    private final BooleanProperty expertConfigured = new SimpleBooleanProperty(false);
    private final StringProperty metricsText = new SimpleStringProperty("");
    private final StringProperty rowColor = new SimpleStringProperty(DISABLED_ROW);

    public HostItem(String host, boolean enabled) {
        this.host.set(host);
        this.enabled.set(enabled);
        if (!enabled) {
            clearMetrics();
        } else {
            rowColor.set(WAITING_ROW);
        }
    }

    public StringProperty hostProperty() {
        return host;
    }

    public BooleanProperty enabledProperty() {
        return enabled;
    }

    public BooleanProperty showMetricsProperty() {
        return showMetrics;
    }

    public BooleanProperty expertConfiguredProperty() {
        return expertConfigured;
    }

    public StringProperty metricsTextProperty() {
        return metricsText;
    }

    public StringProperty rowColorProperty() {
        return rowColor;
    }

    public String getHost() {
        return host.get();
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public boolean isExpertConfigured() {
        return expertConfigured.get();
    }

    public void setExpertConfigured(boolean configured) {
        expertConfigured.set(configured);
    }

    public void clearMetrics() {
        showMetrics.set(false);
        metricsText.set("");
        rowColor.set(isEnabled() ? WAITING_ROW : DISABLED_ROW);
    }

    public void applyMetrics(HostTargetStats stats) {
        showMetrics.set(true);
        metricsText.set(formatMetrics(stats));
        rowColor.set(PingColor.pingColor(stats.avgMs(), stats.timeout() && stats.avgMs() == null));
    }

    static String formatMetrics(HostTargetStats stats) {
        return String.format(
                "loss %.0f%%  min %s  avg %s  max %s ms",
                stats.lossPct(),
                formatMs(stats.minMs()),
                formatMs(stats.avgMs()),
                formatMs(stats.maxMs()));
    }

    private static String formatMs(Double value) {
        return value == null ? "—" : String.valueOf(value.intValue());
    }
}
