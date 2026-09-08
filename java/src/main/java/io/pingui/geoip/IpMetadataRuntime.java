package io.pingui.geoip;

/**
 * Process-wide {@link IpMetadataService} holder (P36-007).
 *
 * <p>Keeps enrichment off {@code MainController} LOC budget while GUI/daemon share one service.
 * Probe paths must use {@link IpMetadataService#cached(String)} / {@link
 * IpMetadataService#offer(String)} only.
 */
public final class IpMetadataRuntime {
    private static final Object LOCK = new Object();
    private static IpMetadataService service = IpMetadataService.disabled();

    private IpMetadataRuntime() {}

    /** Replace the active service; previous instance is closed. */
    public static void install(IpMetadataService next) {
        IpMetadataService previous;
        synchronized (LOCK) {
            previous = service;
            service = next != null ? next : IpMetadataService.disabled();
        }
        if (previous != null && previous != service) {
            previous.close();
        }
    }

    public static IpMetadataService get() {
        synchronized (LOCK) {
            return service;
        }
    }

    /** Close and reset to a disabled service. */
    public static void close() {
        install(IpMetadataService.disabled());
    }
}
