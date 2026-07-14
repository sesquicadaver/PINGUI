package io.pingui.config;

import io.pingui.monitor.HostProbeMode;
import io.pingui.probe.ProbeMode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        HostProbeMode hostProbeMode = HostProbeMode.TRACE;
        Object hostProbeModeObj = map.get("probe_mode");
        if (hostProbeModeObj instanceof String hostProbeModeStr && !hostProbeModeStr.isBlank()) {
            hostProbeMode = HostProbeMode.parse(hostProbeModeStr);
        }
        Object hostsObj = map.get("hosts");
        if (!(hostsObj instanceof List<?> hostsList)) {
            throw new ConfigError("Profile '" + name + "' must contain 'hosts' list");
        }
        List<HostEntry> hosts = parseHostEntries(hostsList, name);
        AlertConfig alerts = parseAlerts(map, name);
        PersistenceConfig persistence = parsePersistence(map, name);
        TelemetryConfig telemetry = parseTelemetry(map, name);
        int maxConcurrentTraces =
                readInt(map, "max_concurrent_traces", io.pingui.monitor.TraceConcurrencyLimiter.DEFAULT_MAX, name);
        return new TracingProfile(
                interval,
                maxHops,
                timeout,
                probe,
                hostProbeMode,
                hosts,
                alerts,
                persistence,
                maxConcurrentTraces,
                telemetry);
    }

    private static TelemetryConfig parseTelemetry(Map<?, ?> map, String profileName) {
        Object telemetryObj = map.get("telemetry");
        if (!(telemetryObj instanceof Map<?, ?> telemetryMap)) {
            return TelemetryConfig.defaults();
        }
        boolean eventsOnly = readBoolean(telemetryMap.get("events_only"), true);
        boolean logAggregates = readBoolean(telemetryMap.get("log_aggregates"), false);
        java.util.Optional<java.nio.file.Path> sqlite =
                optionalPath(telemetryMap.get("sqlite"), profileName, "telemetry.sqlite");
        java.util.Optional<java.nio.file.Path> jsonlDir =
                optionalPath(telemetryMap.get("jsonl_dir"), profileName, "telemetry.jsonl_dir");
        java.util.Optional<TelemetryConfig.SyslogSinkConfig> syslog =
                parseSyslog(telemetryMap.get("syslog"), profileName);
        java.util.Optional<TelemetryConfig.GelfSinkConfig> gelf = parseGelf(telemetryMap.get("gelf"), profileName);
        java.util.Optional<TelemetryConfig.LokiSinkConfig> loki = parseLoki(telemetryMap.get("loki"), profileName);
        java.util.Optional<TelemetryConfig.OtlpSinkConfig> otlp = parseOtlp(telemetryMap.get("otlp"), profileName);
        return new TelemetryConfig(eventsOnly, logAggregates, sqlite, jsonlDir, syslog, gelf, loki, otlp);
    }

    private static java.util.Optional<java.nio.file.Path> optionalPath(Object value, String profileName, String label) {
        if (value == null) {
            return java.util.Optional.empty();
        }
        if (!(value instanceof String path) || path.isBlank()) {
            throw new ConfigError("Profile '" + profileName + "' " + label + " must be a non-empty string path");
        }
        return java.util.Optional.of(java.nio.file.Path.of(path.strip()));
    }

    private static java.util.Optional<TelemetryConfig.SyslogSinkConfig> parseSyslog(Object value, String profileName) {
        if (value == null) {
            return java.util.Optional.empty();
        }
        if (!(value instanceof Map<?, ?> sinkMap)) {
            throw new ConfigError("Profile '" + profileName + "' telemetry.syslog must be a mapping");
        }
        String host = requireStringField(sinkMap.get("host"), profileName, "telemetry.syslog.host");
        int port = readPositiveInt(sinkMap.get("port"), 514, profileName, "telemetry.syslog.port");
        boolean tls = readBoolean(sinkMap.get("tls"), false);
        return java.util.Optional.of(new TelemetryConfig.SyslogSinkConfig(host, port, tls));
    }

    private static java.util.Optional<TelemetryConfig.GelfSinkConfig> parseGelf(Object value, String profileName) {
        if (value == null) {
            return java.util.Optional.empty();
        }
        if (!(value instanceof Map<?, ?> sinkMap)) {
            throw new ConfigError("Profile '" + profileName + "' telemetry.gelf must be a mapping");
        }
        String host = requireStringField(sinkMap.get("host"), profileName, "telemetry.gelf.host");
        int port = readPositiveInt(sinkMap.get("port"), 12201, profileName, "telemetry.gelf.port");
        Object transportObj = sinkMap.get("transport");
        String transportRaw = transportObj instanceof String s ? s : null;
        try {
            return java.util.Optional.of(new TelemetryConfig.GelfSinkConfig(
                    host, port, TelemetryConfig.GelfSinkConfig.parseTransport(transportRaw)));
        } catch (ConfigError ex) {
            throw new ConfigError("Profile '" + profileName + "' " + ex.getMessage());
        }
    }

    private static java.util.Optional<TelemetryConfig.LokiSinkConfig> parseLoki(Object value, String profileName) {
        if (value == null) {
            return java.util.Optional.empty();
        }
        if (!(value instanceof Map<?, ?> sinkMap)) {
            throw new ConfigError("Profile '" + profileName + "' telemetry.loki must be a mapping");
        }
        String url = requireStringField(sinkMap.get("url"), profileName, "telemetry.loki.url");
        String site = "default";
        Object siteObj = sinkMap.get("site");
        if (siteObj instanceof String siteStr && !siteStr.isBlank()) {
            site = siteStr.strip();
        }
        return java.util.Optional.of(new TelemetryConfig.LokiSinkConfig(url, site));
    }

    private static java.util.Optional<TelemetryConfig.OtlpSinkConfig> parseOtlp(Object value, String profileName) {
        if (value == null) {
            return java.util.Optional.empty();
        }
        if (!(value instanceof Map<?, ?> sinkMap)) {
            throw new ConfigError("Profile '" + profileName + "' telemetry.otlp must be a mapping");
        }
        String endpoint = requireStringField(sinkMap.get("endpoint"), profileName, "telemetry.otlp.endpoint");
        String serviceName = "pingui";
        Object serviceObj = sinkMap.get("service_name");
        if (serviceObj instanceof String serviceStr && !serviceStr.isBlank()) {
            serviceName = serviceStr.strip();
        }
        return java.util.Optional.of(new TelemetryConfig.OtlpSinkConfig(endpoint, serviceName));
    }

    private static String requireStringField(Object value, String profileName, String label) {
        if (!(value instanceof String str) || str.isBlank()) {
            throw new ConfigError("Profile '" + profileName + "' " + label + " must be a non-empty string");
        }
        return str.strip();
    }

    private static PersistenceConfig parsePersistence(Map<?, ?> map, String profileName) {
        java.util.Optional<java.nio.file.Path> sessionDb = java.util.Optional.empty();
        boolean routeChange = true;
        boolean probeError = true;
        Object persistenceObj = map.get("persistence");
        if (persistenceObj instanceof Map<?, ?> persistenceMap) {
            Object sessionDbObj = persistenceMap.get("session_db");
            if (sessionDbObj instanceof String sessionDbStr && !sessionDbStr.isBlank()) {
                sessionDb = java.util.Optional.of(java.nio.file.Path.of(sessionDbStr.strip()));
            }
            Object eventsObj = persistenceMap.get("events");
            if (eventsObj instanceof Map<?, ?> eventsMap) {
                routeChange = readBoolean(eventsMap.get("route_change"), routeChange);
                probeError = readBoolean(eventsMap.get("probe_error"), probeError);
            }
        }
        return new PersistenceConfig(sessionDb, new PersistenceEventsConfig(routeChange, probeError));
    }

    private static AlertConfig parseAlerts(Map<?, ?> map, String profileName) {
        boolean desktop = false;
        String webhook = null;
        int rateLimit = 10;
        Object topWebhook = map.get("alert_webhook");
        if (topWebhook instanceof String topStr && !topStr.isBlank()) {
            webhook = topStr.strip();
        }
        Object alertsObj = map.get("alerts");
        if (alertsObj instanceof Map<?, ?> alertsMap) {
            desktop = readBoolean(alertsMap.get("desktop"), desktop);
            Object webhookObj = alertsMap.get("webhook");
            if (webhookObj instanceof String webhookStr && !webhookStr.isBlank()) {
                webhook = webhookStr.strip();
            }
            rateLimit = readPositiveInt(alertsMap.get("rate_limit"), rateLimit, profileName, "alerts.rate_limit");
        }
        return new AlertConfig(desktop, webhook, rateLimit);
    }

    private static int readPositiveInt(Object value, int fallback, String profileName, String label) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            int parsed = number.intValue();
            if (parsed < 1) {
                throw new ConfigError("Profile '" + profileName + "' " + label + " must be >= 1");
            }
            return parsed;
        }
        throw new ConfigError("Profile '" + profileName + "' " + label + " must be an integer");
    }

    private static List<HostEntry> parseHostEntries(List<?> hostsList, String profileName) {
        if (hostsList.size() < HostsConfig.MIN_HOSTS || hostsList.size() > HostsConfig.MAX_HOSTS) {
            throw new ConfigError("Profile '"
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
            String key = HostAddressParser.duplicateKey(parsed.address());
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
            HostProbeMode probeModeOverride = null;
            Object hostProbeModeObj = hostMap.get("probe_mode");
            if (hostProbeModeObj instanceof String hostProbeModeStr && !hostProbeModeStr.isBlank()) {
                probeModeOverride = HostProbeMode.parse(hostProbeModeStr);
            }
            PingExpertEntry expert = PingExpertEntry.empty();
            Object expertObj = hostMap.get("ping_expert");
            if (expertObj instanceof Map<?, ?> expertMap) {
                boolean chain = readBoolean(expertMap.get("chain"), false);
                List<String> args = readStringList(expertMap.get("args"), "ping_expert.args", profileName);
                expert = new PingExpertEntry(chain, args);
            }
            Double intervalOverride = null;
            Object intervalObj = hostMap.get("interval");
            if (intervalObj != null) {
                if (!(intervalObj instanceof Number number)) {
                    throw new ConfigError("Host in profile '" + profileName + "' interval must be a number");
                }
                double parsed = number.doubleValue();
                if (parsed <= 0) {
                    throw new ConfigError("Host in profile '" + profileName + "' interval must be positive");
                }
                intervalOverride = parsed;
            }
            List<String> tags = List.of();
            Object tagsObj = hostMap.get("tags");
            if (tagsObj != null) {
                tags = readStringList(tagsObj, "tags", profileName);
            }
            return new HostEntry(normalized, enabled, pingOnly, expert, probeModeOverride, intervalOverride, tags);
        }
        throw new ConfigError("Each host in profile '" + profileName + "' must be a string or mapping, got "
                + (entry == null ? "null" : entry.getClass().getSimpleName()));
    }

    private static Map<String, Object> profileToMap(TracingProfile profile) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("interval", profile.intervalSeconds());
        map.put("max_hops", profile.maxHops());
        map.put("timeout", profile.timeoutSeconds());
        map.put("probe", profile.probeMode().cliValue());
        if (profile.hostProbeMode() != HostProbeMode.TRACE) {
            map.put("probe_mode", profile.hostProbeMode().yamlValue());
        }
        if (profile.maxConcurrentTraces() != io.pingui.monitor.TraceConcurrencyLimiter.DEFAULT_MAX) {
            map.put("max_concurrent_traces", profile.maxConcurrentTraces());
        }
        List<Object> hostsOut = new ArrayList<>();
        for (HostEntry host : profile.hosts()) {
            hostsOut.add(hostEntryToMap(host));
        }
        map.put("hosts", hostsOut);
        if (profile.alerts().isEnabled() || profile.alerts().maxAlertsPerHour() != 10) {
            Map<String, Object> alertsOut = new LinkedHashMap<>();
            if (profile.alerts().desktopAlerts()) {
                alertsOut.put("desktop", true);
            }
            if (profile.alerts().normalizedWebhook() != null) {
                alertsOut.put("webhook", profile.alerts().normalizedWebhook());
            }
            if (profile.alerts().maxAlertsPerHour() != 10) {
                alertsOut.put("rate_limit", profile.alerts().maxAlertsPerHour());
            }
            map.put("alerts", alertsOut);
        }
        if (!profile.persistence().isDefault()) {
            Map<String, Object> persistenceOut = new LinkedHashMap<>();
            profile.persistence().sessionDb().ifPresent(path -> persistenceOut.put("session_db", path.toString()));
            Map<String, Object> eventsOut = new LinkedHashMap<>();
            if (!profile.persistence().routeChange()) {
                eventsOut.put("route_change", false);
            }
            if (!profile.persistence().probeError()) {
                eventsOut.put("probe_error", false);
            }
            if (!eventsOut.isEmpty()) {
                persistenceOut.put("events", eventsOut);
            }
            map.put("persistence", persistenceOut);
        }
        if (!profile.telemetry().isDefault()) {
            map.put("telemetry", telemetryToMap(profile.telemetry()));
        }
        return map;
    }

    private static Map<String, Object> telemetryToMap(TelemetryConfig telemetry) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (!telemetry.eventsOnly()) {
            out.put("events_only", false);
        }
        if (telemetry.logAggregates()) {
            out.put("log_aggregates", true);
        }
        telemetry.sqlitePath().ifPresent(path -> out.put("sqlite", path.toString()));
        telemetry.jsonlDir().ifPresent(path -> out.put("jsonl_dir", path.toString()));
        telemetry.syslog().ifPresent(syslog -> {
            Map<String, Object> sink = new LinkedHashMap<>();
            sink.put("host", syslog.host());
            sink.put("port", syslog.port());
            if (syslog.tls()) {
                sink.put("tls", true);
            }
            out.put("syslog", sink);
        });
        telemetry.gelf().ifPresent(gelf -> {
            Map<String, Object> sink = new LinkedHashMap<>();
            sink.put("host", gelf.host());
            sink.put("port", gelf.port());
            if (gelf.transport() != io.pingui.telemetry.GelfSink.Transport.TCP) {
                sink.put("transport", gelf.transport().name().toLowerCase(java.util.Locale.ROOT));
            }
            out.put("gelf", sink);
        });
        telemetry.loki().ifPresent(loki -> {
            Map<String, Object> sink = new LinkedHashMap<>();
            sink.put("url", loki.url());
            sink.put("site", loki.site());
            out.put("loki", sink);
        });
        telemetry.otlp().ifPresent(otlp -> {
            Map<String, Object> sink = new LinkedHashMap<>();
            sink.put("endpoint", otlp.endpoint());
            if (!"pingui".equals(otlp.serviceName())) {
                sink.put("service_name", otlp.serviceName());
            }
            out.put("otlp", sink);
        });
        return out;
    }

    private static Map<String, Object> hostEntryToMap(HostEntry host) {
        PingExpertEntry expert = host.pingExpert();
        if (!host.enabled()
                && !host.pingOnly()
                && host.probeModeOverride() == null
                && host.intervalSecondsOverride() == null
                && host.tags().isEmpty()
                && !expert.isConfigured()) {
            return Map.of("address", host.address());
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("address", host.address());
        if (host.enabled()) {
            map.put("enabled", true);
        }
        if (host.pingOnly()) {
            map.put("ping_only", true);
        } else if (host.probeModeOverride() != null && host.probeModeOverride() != HostProbeMode.PING_ONLY) {
            map.put("probe_mode", host.probeModeOverride().yamlValue());
        } else if (host.probeModeOverride() == HostProbeMode.PING_ONLY) {
            map.put("probe_mode", HostProbeMode.PING_ONLY.yamlValue());
        }
        if (host.intervalSecondsOverride() != null) {
            map.put("interval", host.intervalSecondsOverride());
        }
        if (!host.tags().isEmpty()) {
            map.put("tags", host.tags());
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
                throw new ConfigError("Profile '" + entry.getKey() + "' exceeds " + HostsConfig.MAX_HOSTS + " hosts");
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
