package io.pingui.config;

import io.pingui.probe.PingExpertValidator;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/**
 * Loads expert-ping quick presets from {@code ping_presets.yaml} (P14-040).
 *
 * <p>Default resource is bundled; an optional file path overrides it (same pattern as ASN/GeoIP
 * hints).
 */
public final class PingPresets {
    public static final String DEFAULT_RESOURCE = "ping_presets.yaml";
    public static final int EXPECTED_COUNT = 4;

    private static volatile List<PingPreset> cached = loadFromResource(DEFAULT_RESOURCE);

    private PingPresets() {}

    /** Reloads from classpath resource (tests / default). */
    public static void configureDefault() {
        cached = loadFromResource(DEFAULT_RESOURCE);
    }

    /** Reloads from a YAML file when present; otherwise falls back to the bundled resource. */
    public static void configure(Path presetsPath) {
        if (presetsPath != null && Files.isRegularFile(presetsPath)) {
            cached = loadFromFile(presetsPath);
        } else {
            configureDefault();
        }
    }

    /**
     * Resolves the on-disk presets file: sibling of the hosts config, else {@code
     * config/ping_presets.yaml}, else {@code null} (use bundled resource).
     */
    public static Path resolvePath(Path hostsConfigPath) {
        if (hostsConfigPath != null) {
            Path parent = hostsConfigPath.getParent();
            if (parent != null) {
                Path sibling = parent.resolve("ping_presets.yaml");
                if (Files.isRegularFile(sibling)) {
                    return sibling;
                }
            }
        }
        Path cwdDefault = Path.of("config/ping_presets.yaml");
        if (Files.isRegularFile(cwdDefault)) {
            return cwdDefault;
        }
        return null;
    }

    public static List<PingPreset> all() {
        return cached;
    }

    /**
     * Merges a preset into the current expert args: keeps address family from {@code currentArgs}
     * (default {@code -4}), replaces all other flags with the preset body.
     */
    public static List<String> mergeKeepingAddressFamily(List<String> currentArgs, List<String> presetArgs) {
        boolean ipv6 = currentArgs != null && currentArgs.contains("-6") && !currentArgs.contains("-4");
        List<String> merged = new ArrayList<>();
        merged.add(ipv6 ? "-6" : "-4");
        if (presetArgs != null) {
            for (String arg : presetArgs) {
                if ("-4".equals(arg) || "-6".equals(arg)) {
                    continue;
                }
                merged.add(arg);
            }
        }
        return PingExpertValidator.validateAndNormalize(merged);
    }

    static List<PingPreset> loadFromResource(String resourceName) {
        InputStream stream = PingPresets.class.getClassLoader().getResourceAsStream(resourceName);
        if (stream == null) {
            throw new ConfigError("Missing resource: " + resourceName);
        }
        try (stream) {
            return parseYaml(new String(stream.readAllBytes(), StandardCharsets.UTF_8), resourceName);
        } catch (IOException ex) {
            throw new ConfigError("Failed to read resource " + resourceName + ": " + ex.getMessage());
        }
    }

    static List<PingPreset> loadFromFile(Path path) {
        try {
            return parseYaml(Files.readString(path), path.toString());
        } catch (IOException ex) {
            throw new ConfigError("Failed to read " + path + ": " + ex.getMessage());
        }
    }

    static List<PingPreset> parseYaml(String yamlText, String source) {
        Object root = new Yaml().load(yamlText);
        if (!(root instanceof Map<?, ?> map)) {
            throw new ConfigError(source + ": root must be a mapping");
        }
        Object rawPresets = map.get("presets");
        if (!(rawPresets instanceof List<?> list) || list.isEmpty()) {
            throw new ConfigError(source + ": presets must be a non-empty list");
        }
        Map<String, PingPreset> byId = new LinkedHashMap<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> entry)) {
                throw new ConfigError(source + ": each preset must be a mapping");
            }
            String id = stringField(entry, "id", source);
            String label = stringField(entry, "label", source);
            String summary = requireNonBlankField(entry, "summary", source, id);
            String expect = requireNonBlankField(entry, "expect", source, id);
            String caution = optionalField(entry, "caution");
            Object rawArgs = entry.get("args");
            List<String> args = new ArrayList<>();
            if (rawArgs instanceof List<?> argList) {
                for (Object arg : argList) {
                    if (arg == null) {
                        continue;
                    }
                    args.add(String.valueOf(arg).strip());
                }
            } else if (rawArgs != null) {
                throw new ConfigError(source + ": preset '" + id + "' args must be a list");
            }
            List<String> validated = PingExpertValidator.validateAndNormalize(args);
            PingPreset preset = new PingPreset(id, label, validated, summary, expect, caution);
            if (byId.put(id, preset) != null) {
                throw new ConfigError(source + ": duplicate preset id '" + id + "'");
            }
        }
        List<PingPreset> presets = List.copyOf(byId.values());
        if (presets.size() != EXPECTED_COUNT) {
            throw new ConfigError(source + ": expected " + EXPECTED_COUNT + " presets, got " + presets.size());
        }
        return presets;
    }

    private static String stringField(Map<?, ?> entry, String key, String source) {
        Object value = entry.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new ConfigError(source + ": preset missing '" + key + "'");
        }
        return String.valueOf(value).strip();
    }

    private static String requireNonBlankField(Map<?, ?> entry, String key, String source, String id) {
        Object value = entry.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new ConfigError(source + ": preset '" + id + "' missing '" + key + "'");
        }
        return String.valueOf(value).strip();
    }

    private static String optionalField(Map<?, ?> entry, String key) {
        Object value = entry.get(key);
        if (value == null) {
            return "";
        }
        return String.valueOf(value).strip();
    }
}
