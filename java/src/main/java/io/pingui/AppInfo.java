package io.pingui;

/** Application metadata for About dialog and menus. */
public final class AppInfo {
    public static final String NAME = "PINGUI";
    public static final String EDITION = "Java";
    public static final String REPOSITORY = "https://github.com/sesquicadaver/PINGUI";

    private AppInfo() {}

    /** Reads {@code Implementation-Version} from the JAR manifest, else Gradle default. */
    public static String version() {
        Package pkg = PinguiApplication.class.getPackage();
        if (pkg != null) {
            String implementation = pkg.getImplementationVersion();
            if (implementation != null && !implementation.isBlank()) {
                return implementation;
            }
        }
        return "0.1.0-SNAPSHOT";
    }

    public static String runtimeJavaVersion() {
        return System.getProperty("java.version", "?");
    }

    public static String runtimeOsName() {
        return System.getProperty("os.name", "?");
    }
}
