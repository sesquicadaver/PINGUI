package io.pingui.monitor;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fan-out to multiple dispatchers; failures are logged, not raised (ADR_ALERTS). */
public final class CompositeAlertDispatcher implements AlertDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(CompositeAlertDispatcher.class);

    private final List<AlertDispatcher> dispatchers;

    public CompositeAlertDispatcher(List<AlertDispatcher> dispatchers) {
        if (dispatchers == null || dispatchers.isEmpty()) {
            throw new IllegalArgumentException("dispatchers must not be empty");
        }
        this.dispatchers = List.copyOf(dispatchers);
    }

    @Override
    public void dispatch(RouteChangeEvent event) {
        for (AlertDispatcher dispatcher : dispatchers) {
            try {
                dispatcher.dispatch(event);
            } catch (RuntimeException ex) {
                LOG.warn("Alert dispatcher failed for {}: {}", event.host(), ex.getMessage());
            }
        }
    }

    @Override
    public void dispatchQuality(QualityAlertEvent event) {
        if (event == null) {
            return;
        }
        for (AlertDispatcher dispatcher : dispatchers) {
            try {
                dispatcher.dispatchQuality(event);
            } catch (RuntimeException ex) {
                LOG.warn("Quality alert dispatcher failed for {}: {}", event.host(), ex.getMessage());
            }
        }
    }
}
