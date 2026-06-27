package io.pingui.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/** Load and save monitored host targets from YAML. */
public final class HostsConfig {
    public static final int MIN_HOSTS = 0;
    public static final int MAX_HOSTS = 10;

    private static final Pattern HOST_PATTERN =
            Pattern.compile("^[a-zA-Z0-9.-]+$");

    private HostsConfig() {}

    public static String normalizeHostEntry(String entry) {
        String host = entry.strip();
        if (!isValidHost(host)) {
            throw new ConfigError("Invalid host entry: '" + entry + "'");
        }
        return host;
    }

    public static String validateSessionHost(String host, List<String> existing) {
        String normalized = normalizeHostEntry(host);
        String key = normalized.toLowerCase(Locale.ROOT);
        Set<String> seen = new HashSet<>();
        for (String h : existing) {
            seen.add(h.toLowerCase(Locale.ROOT));
        }
        if (seen.contains(key)) {
            throw new ConfigError("Duplicate host: " + normalized);
        }
        if (existing.size() >= MAX_HOSTS) {
            throw new ConfigError("Maximum " + MAX_HOSTS + " hosts allowed in one session");
        }
        return normalized;
    }

    public static List<String> load(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new ConfigError("Config file not found: " + path);
        }
        Yaml yaml = new Yaml();
        Object raw = yaml.load(Files.readString(path, StandardCharsets.UTF_8));
        if (!(raw instanceof Map<?, ?> root)) {
            throw new ConfigError("Config root must be a mapping");
        }
        Object hostsObj = root.get("hosts");
        if (!(hostsObj instanceof List<?> hosts)) {
            throw new ConfigError("Config must contain 'hosts' as a list");
        }
        if (hosts.size() < MIN_HOSTS || hosts.size() > MAX_HOSTS) {
            throw new ConfigError(
                    "hosts count must be between " + MIN_HOSTS + " and " + MAX_HOSTS
                            + ", got "
                            + hosts.size());
        }
        List<String> normalized = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Object entry : hosts) {
            if (!(entry instanceof String hostStr)) {
                throw new ConfigError("Each host must be a string, got " + entry.getClass().getSimpleName());
            }
            String host = normalizeHostEntry(hostStr);
            String key = host.toLowerCase(Locale.ROOT);
            if (!seen.add(key)) {
                throw new ConfigError("Duplicate host: " + host);
            }
            normalized.add(host);
        }
        return List.copyOf(normalized);
    }

    public static void save(Path path, List<String> hosts) throws IOException {
        if (hosts.size() < MIN_HOSTS || hosts.size() > MAX_HOSTS) {
            throw new ConfigError(
                    "hosts count must be between " + MIN_HOSTS + " and " + MAX_HOSTS
                            + ", got "
                            + hosts.size());
        }
        List<String> normalized = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String entry : hosts) {
            String host = normalizeHostEntry(entry);
            String key = host.toLowerCase(Locale.ROOT);
            if (!seen.add(key)) {
                throw new ConfigError("Duplicate host: " + host);
            }
            normalized.add(host);
        }
        Files.createDirectories(path.getParent() != null ? path.getParent() : Path.of("."));
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        Yaml yaml = new Yaml(options);
        String payload = yaml.dump(Map.of("hosts", normalized));
        Files.writeString(path, payload, StandardCharsets.UTF_8);
    }

    static boolean isValidHost(String value) {
        if (value.isBlank() || value.length() > 253) {
            return false;
        }
        if (isIpv4(value)) {
            return true;
        }
        return HOST_PATTERN.matcher(value).matches();
    }

    private static boolean isIpv4(String value) {
        String[] parts = value.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        try {
            for (String part : parts) {
                int octet = Integer.parseInt(part);
                if (octet < 0 || octet > 255) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
