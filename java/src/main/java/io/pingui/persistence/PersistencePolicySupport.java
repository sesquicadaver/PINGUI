package io.pingui.persistence;

import java.util.ArrayList;
import java.util.List;

/** Helpers for persistence policy transitions (P11-014 purge rules). */
public final class PersistencePolicySupport {
    private PersistencePolicySupport() {}

    /** Event types that transition from enabled to disabled between {@code before} and {@code after}. */
    public static List<PersistenceEventType> typesBeingDisabled(PersistencePolicy before, PersistencePolicy after) {
        List<PersistenceEventType> disabled = new ArrayList<>();
        if (before.routeChange() && !after.routeChange()) {
            disabled.add(PersistenceEventType.ROUTE_CHANGE);
        }
        if (before.probeError() && !after.probeError()) {
            disabled.add(PersistenceEventType.PROBE_ERROR);
        }
        return List.copyOf(disabled);
    }

    public static String labelUk(PersistenceEventType type) {
        return switch (type) {
            case ROUTE_CHANGE -> "зміни маршруту";
            case PROBE_ERROR -> "помилки probe";
        };
    }
}
