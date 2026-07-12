package io.pingui.persistence.timeseries;

/** Configuration error for optional time-series backends (P15-020). */
public final class TimeSeriesConfigException extends IllegalArgumentException {
    public TimeSeriesConfigException(String message) {
        super(message);
    }

    public TimeSeriesConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
