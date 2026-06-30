package io.pingui.config;

import io.pingui.probe.ProbeMode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/** Load and save multi-profile tracing config (legacy {@code hosts:} list supported). */
public final class ProfilesConfig {
    private ProfilesConfig() {}

    public static ProfileDocument load(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new ConfigError("Config file not found: " + path);
        }
        Yaml yaml = new Yaml();
        Object raw = yaml.load(Files.readString(path, StandardCharsets.UTF_8));
        if (!(raw instanceof Map<?, ?> root)) {
            throw new ConfigError("Config root must be a mapping");
        }
        if (root.containsKey("profiles")) {
            return parseProfilesFormat(root);
        }
        if (root.containsKey("hosts")) {
            return migrateLegacyHosts(root);
        }
        throw new ConfigError("Config must contain 'profiles' or legacy 'hosts' list");
    }

    public static void save(Path path, ProfileDocument document) throws IOException {
        validateDocument(document);
        Files.createDirectories(path.getParent() != null ? path.getParent() : Path.of("."));
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("active_profile", document.activeProfile());
        Map<String, Object> profilesOut = new LinkedHashMap<>();
        for (Map.Entry<String, TracingProfile> entry : document.profiles().entrySet()) {
            profilesOut.put(entry.getKey(), profileToMap(entry.getValue()));
        }
        root.put("profiles", profilesOut);
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        Yaml yaml = new Yaml(options);
        Files.writeString(path, yaml.dump(root), StandardCharsets.UTF_8);
    }

    private static ProfileDocument parseProfilesFormat(Map<?, ?> root) {
        Object activeObj = root.get("active_profile");
        String active = activeObj instanceof String s && !s.isBlank() ? s.strip() : ProfileDocument.DEFAULT_PROFILE;
        Object profilesObj = root.get("profiles");
        if (!(profilesObj instanceof Map<?, ?> profilesMap)) {
            throw new ConfigError("'profiles' must be a mapping");
        }
        if (profilesMap.isEmpty()) {
            throw new ConfigError("'profiles' cannot be empty");
        }
        Map<String, TracingProfile> profiles = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : profilesMap.entrySet()) {
            if (!(entry.getKey() instanceof String name)) {
                throw new ConfigError("Profile name must be a string");
            }
            if (!(entry.getValue() instanceof Map<?, ?> profileMap)) {
                throw new ConfigError("Profile '" + name + "' must be a mapping");
            }
            profiles.put(name.strip(), parseProfile(name.strip(), profileMap));
        }
        return new ProfileDocument(active, profiles);
    }

    private static ProfileDocument migrateLegacyHosts(Map<?, ?> root) {
        List<String> hosts = HostsConfig.loadHostsList(root.get("hosts"));
        List<HostEntry> entries = new ArrayList<>();
        for (String host : hosts) {
            entries.add(HostEntry.basic(host, false));
        }
        return ProfileDocument.singleDefault(TracingProfile.defaults(entries));
    }

    private static TracingProfile parseProfile(String name, Map<?, ?> map) {
        double interval = readDouble(map, "interval", 1.0, name);
        int maxHops = readInt(map, "max_hops", 20, name);
        double timeout = readDouble(map, "timeout", 0.5, name);
        ProbeMode probe = ProbeMode.AUTO;
        Object probeObj = map.get("probe");
        if (probeObj instanceof String probeStr && !probeStr.isBlank()) {
            probe = ProbeMode.parse(probeStr);
        }
        Object hostsObj = map.get("hosts");
        if (!(hostsObj instanceof List<?> hostsList)) {
            throw new ConfigError("Profile '" + name + "' must contain 'hosts' list");
        }
        List<HostEntry> hosts = parseHostEntries(hostsList, name);
        return new TracingProfile(interval, maxHops, timeout, probe, hosts);
    }

    private static List<HostEntry> parseHostEntries(List<?> hostsList, String profileName) {
        if (hostsList.size() < HostsConfig.MIN_HOSTS || hostsList.size() > HostsConfig.MAX_HOSTS) {
            throw new ConfigError(
                    "Profile '"
                            + profileName
                            + "' hosts count must be between "
                            + HostsConfig.MIN_HOSTS
                            + " and "
                            + HostsConfig.MAX_HOSTS
                            + ", got "
                            + hostsList.size());
        }
        List<HostEntry> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Object entry : hostsList) {
            HostEntry parsed = parseHostEntry(entry, profileName);
            String key = parsed.address().toLowerCase(Locale.ROOT);
            if (!seen.add(key)) {
                throw new ConfigError("Duplicate host in profile '" + profileName + "': " + parsed.address());
            }
            out.add(parsed);
        }
        return List.copyOf(out);
    }

    private static HostEntry parseHostEntry(Object entry, String profileName) {
        if (entry instanceof String hostStr) {
            return HostEntry.basic(HostsConfig.normalizeHostEntry(hostStr), false);
        }
        if (entry instanceof Map<?, ?> hostMap) {
            Object addressObj = hostMap.get("address");
            if (!(addressObj instanceof String address)) {
                throw new ConfigError("Host in profile '" + profileName + "' must have string 'address'");
            }
            String normalized = HostsConfig.normalizeHostEntry(address);
            boolean enabled = readBoolean(hostMap.get("enabled"), false);
            boolean pingOnly = readBoolean(hostMap.get("ping_only"), false);
            PingExpertEntry expert = PingExpertEntry.empty();
            Object expertObj = hostMap.get("ping_expert");
            if (expertObj instanceof Map<?, ?> expertMap) {
                boolean chain = readBoolean(expertMap.get("chain"), false);
                List<String> args = readStringList(expertMap.get("args"), "ping_expert.args", profileName);
                expert = new PingExpertEntry(chain, args);
            }
            return new HostEntry(normalized, enabled, pingOnly, expert);
        }
        throw new ConfigError(
                "Each host in profile '" + profileName + "' must be a string or mapping, got "
                        + (entry == null ? "null" : entry.getClass().getSimpleName()));
    }

    private static Map<String, Object> profileToMap(TracingProfile profile) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("interval", profile.intervalSeconds());
        map.put("max_hops", profile.maxHops());
        map.put("timeout", profile.timeoutSeconds());
        map.put("probe", profile.probeMode().cliValue());
        List<Object> hostsOut = new ArrayList<>();
        for (HostEntry host : profile.hosts()) {
            hostsOut.add(hostEntryToMap(host));
        }
        map.put("hosts", hostsOut);
        return map;
    }

    private static Map<String, Object> hostEntryToMap(HostEntry host) {
        PingExpertEntry expert = host.pingExpert();
        if (!host.enabled() && !host.pingOnly() && !expert.isConfigured()) {
            return Map.of("address", host.address());
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("address", host.address());
        if (host.enabled()) {
            map.put("enabled", true);
        }
        if (host.pingOnly()) {
            map.put("ping_only", true);
        }
        if (expert.isConfigured()) {
            Map<String, Object> expertMap = new LinkedHashMap<>();
            if (expert.applyToChain()) {
                expertMap.put("chain", true);
            }
            expertMap.put("args", expert.args());
            map.put("ping_expert", expertMap);
        }
        return map;
    }

    private static void validateDocument(ProfileDocument document) {
        if (document.profiles().isEmpty()) {
            throw new ConfigError("At least one profile is required");
        }
        for (Map.Entry<String, TracingProfile> entry : document.profiles().entrySet()) {
            TracingProfile profile = entry.getValue();
            if (profile.hosts().size() > HostsConfig.MAX_HOSTS) {
                throw new ConfigError(
                        "Profile '" + entry.getKey() + "' exceeds " + HostsConfig.MAX_HOSTS + " hosts");
            }
        }
    }

    private static double readDouble(Map<?, ?> map, String key, double fallback, String profileName) {
        Object value = map.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            double parsed = number.doubleValue();
            if (parsed <= 0) {
                throw new ConfigError("Profile '" + profileName + "' " + key + " must be positive");
            }
            return parsed;
        }
        throw new ConfigError("Profile '" + profileName + "' " + key + " must be a number");
    }

    private static int readInt(Map<?, ?> map, String key, int fallback, String profileName) {
        Object value = map.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            int parsed = number.intValue();
            if (parsed < 1) {
                throw new ConfigError("Profile '" + profileName + "' " + key + " must be >= 1");
            }
            return parsed;
        }
        throw new ConfigError("Profile '" + profileName + "' " + key + " must be an integer");
    }

    private static boolean readBoolean(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw new ConfigError("Expected boolean, got " + value.getClass().getSimpleName());
    }

    private static List<String> readStringList(Object value, String label, String profileName) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new ConfigError("Profile '" + profileName + "' " + label + " must be a list");
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof String str)) {
                throw new ConfigError("Profile '" + profileName + "' " + label + " entries must be strings");
            }
            out.add(str);
        }
        return List.copyOf(out);
    }
}
