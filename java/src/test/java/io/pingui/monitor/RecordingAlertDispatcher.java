package io.pingui.monitor;

import java.util.ArrayList;
import java.util.List;

final class RecordingAlertDispatcher implements AlertDispatcher {
    private final List<RouteChangeEvent> events = new ArrayList<>();
    private final List<QualityAlertEvent> qualityEvents = new ArrayList<>();

    @Override
    public void dispatch(RouteChangeEvent event) {
        events.add(event);
    }

    @Override
    public void dispatchQuality(QualityAlertEvent event) {
        qualityEvents.add(event);
    }

    List<RouteChangeEvent> events() {
        return List.copyOf(events);
    }

    List<QualityAlertEvent> qualityEvents() {
        return List.copyOf(qualityEvents);
    }
}
