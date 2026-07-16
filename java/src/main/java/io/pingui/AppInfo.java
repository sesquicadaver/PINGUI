package io.pingui;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Application metadata for About dialog and menus. */
public final class AppInfo {
    public static final String NAME = "PINGUI";
    public static final String EDITION = "Java";
    public static final String REPOSITORY = "https://github.com/sesquicadaver/PINGUI";

    private static final Properties BUILD = loadBuildProperties();

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
        String fromBuild = BUILD.getProperty("version");
        if (fromBuild != null && !fromBuild.isBlank()) {
            return fromBuild;
        }
        return "0.2.0-SNAPSHOT";
    }

    /** Short git commit from {@code pingui/build.properties} (Gradle-generated). */
    public static String gitSha() {
        return BUILD.getProperty("gitSha", "unknown");
    }

    /** CI run number or {@code local} from build properties. */
    public static String buildNumber() {
        return BUILD.getProperty("buildNumber", "local");
    }

    /** Version string for About: {@code 0.2.0-SNAPSHOT (ea3d507)} or with CI build. */
    public static String versionDetail() {
        String sha = gitSha();
        String base = version();
        if (sha.isBlank() || "unknown".equals(sha)) {
            return base;
        }
        String build = buildNumber();
        if (build != null && !build.isBlank() && !"local".equals(build)) {
            return base + " (" + sha + ", build " + build + ")";
        }
        return base + " (" + sha + ")";
    }

    public static String runtimeJavaVersion() {
        return System.getProperty("java.version", "?");
    }

    public static String runtimeOsName() {
        return System.getProperty("os.name", "?");
    }

    private static Properties loadBuildProperties() {
        Properties properties = new Properties();
        try (InputStream in = AppInfo.class.getResourceAsStream("/pingui/build.properties")) {
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException ignored) {
            // dev classpath without processResources
        }
        return properties;
    }
}
