package io.pingui.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.monitor.HostProbeMode;
import io.pingui.probe.ProbeMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfilesConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void loadSaveRoundTrip_v2Profiles() throws Exception {
        Path path = tempDir.resolve("hosts.yaml");
        Files.writeString(
                path,
                """
                active_profile: lab
                profiles:
                  lab:
                    interval: 30.0
                    max_hops: 15
                    timeout: 1.0
                    probe: process
                    hosts:
                      - address: "8.8.8.8"
                        enabled: true
                        ping_only: false
                """);

        ProfileDocument loaded = ProfilesConfig.load(path);
        assertEquals("lab", loaded.activeProfile());
        TracingProfile lab = loaded.active();
        assertEquals(30.0, lab.intervalSeconds());
        assertEquals(15, lab.maxHops());
        assertEquals(ProbeMode.PROCESS, lab.probeMode());
        assertEquals(1, lab.hosts().size());

        ProfilesConfig.save(path, loaded);
        ProfileDocument reloaded = ProfilesConfig.load(path);
        assertEquals(30.0, reloaded.active().intervalSeconds());
    }

    @Test
    void loadTelemetrySection() throws Exception {
        Path path = tempDir.resolve("telemetry.yaml");
        Files.writeString(
                path,
                """
                active_profile: noc
                profiles:
                  noc:
                    hosts:
                      - "8.8.8.8"
                    telemetry:
                      events_only: true
                      log_aggregates: true
                      sqlite: data/telemetry.db
                      jsonl_dir: data/telemetry
                      syslog:
                        host: 127.0.0.1
                        port: 1514
                        tls: true
                      gelf:
                        host: 10.0.0.5
                        port: 12201
                        transport: udp
                      loki:
                        url: http://127.0.0.1:3100
                        site: lab
                      otlp:
                        endpoint: http://127.0.0.1:4318
                        service_name: pingui-noc
                """);
        TelemetryConfig telemetry = ProfilesConfig.load(path).active().telemetry();
        assertTrue(telemetry.eventsOnly());
        assertTrue(telemetry.logAggregates());
        assertEquals(Path.of("data/telemetry.db"), telemetry.sqlitePath().orElseThrow());
        assertEquals(Path.of("data/telemetry"), telemetry.jsonlDir().orElseThrow());
        assertEquals("127.0.0.1", telemetry.syslog().orElseThrow().host());
        assertEquals(1514, telemetry.syslog().orElseThrow().port());
        assertTrue(telemetry.syslog().orElseThrow().tls());
        assertEquals(
                io.pingui.telemetry.GelfSink.Transport.UDP,
                telemetry.gelf().orElseThrow().transport());
        assertEquals("lab", telemetry.loki().orElseThrow().site());
        assertEquals("http://127.0.0.1:4318", telemetry.otlp().orElseThrow().endpoint());
        assertEquals("pingui-noc", telemetry.otlp().orElseThrow().serviceName());
        assertTrue(telemetry.toSinkConfig().eventsOnly());
    }

    @Test
    void saveTelemetrySectionRoundTrip() throws Exception {
        Path path = tempDir.resolve("telemetry-save.yaml");
        TelemetryConfig telemetry = new TelemetryConfig(
                false,
                true,
                Optional.of(Path.of("data/t.db")),
                Optional.of(Path.of("data/jsonl")),
                Optional.of(new TelemetryConfig.SyslogSinkConfig("syslog.example", 514, false)),
                Optional.of(new TelemetryConfig.GelfSinkConfig(
                        "gelf.example", 12201, io.pingui.telemetry.GelfSink.Transport.TCP)),
                Optional.of(new TelemetryConfig.LokiSinkConfig("http://loki.example:3100", "noc")),
                Optional.of(new TelemetryConfig.OtlpSinkConfig("http://collector:4318", "pingui")));
        TracingProfile profile = TracingProfile.defaults(List.of(HostEntry.basic("8.8.8.8", false)))
                .withTelemetry(telemetry);
        ProfilesConfig.save(path, ProfileDocument.singleDefault(profile));
        TelemetryConfig reloaded = ProfilesConfig.load(path).active().telemetry();
        assertEquals(false, reloaded.eventsOnly());
        assertTrue(reloaded.logAggregates());
        assertEquals(Path.of("data/t.db"), reloaded.sqlitePath().orElseThrow());
        assertEquals("syslog.example", reloaded.syslog().orElseThrow().host());
        assertEquals("http://loki.example:3100", reloaded.loki().orElseThrow().url());
        assertEquals("http://collector:4318", reloaded.otlp().orElseThrow().endpoint());
    }

    @Test
    void loadTelemetryMissingDefaultsOff() throws Exception {
        Path path = tempDir.resolve("no-telemetry.yaml");
        Files.writeString(
                path,
                """
                active_profile: default
                profiles:
                  default:
                    hosts:
                      - "1.1.1.1"
                """);
        assertTrue(ProfilesConfig.load(path).active().telemetry().isDefault());
    }

    @Test
    void loadAlertsSection() throws Exception {
        Path path = tempDir.resolve("alerts.yaml");
        Files.writeString(
                path,
                """
                active_profile: noc
                profiles:
                  noc:
                    hosts:
                      - "8.8.8.8"
                    alerts:
                      desktop: true
                      webhook: https://hooks.example.com/ping
                      rate_limit: 5
                      notify_resolved: true
                      rules:
                        endpoint_down:
                          enabled: true
                          preset: calm
                """);
        TracingProfile profile = ProfilesConfig.load(path).active();
        assertTrue(profile.alerts().desktopAlerts());
        assertEquals("https://hooks.example.com/ping", profile.alerts().normalizedWebhook());
        assertEquals(5, profile.alerts().maxAlertsPerHour());
        assertTrue(profile.alerts().notifyResolved());
        assertTrue(profile.alerts().endpointDown().enabled());
        assertEquals(5, profile.alerts().endpointDown().failAfter());
        assertEquals(3, profile.alerts().endpointDown().clearAfter());
        assertEquals(30, profile.alerts().endpointDown().cooldownMinutes());
    }

    @Test
    void saveAndReloadEndpointDownRules() throws Exception {
        Path path = tempDir.resolve("alerts-rules-save.yaml");
        AlertConfig alerts = new AlertConfig(false, null, 10, true, new EndpointDownRuleConfig(true, 4, 2, 20));
        TracingProfile profile = new TracingProfile(
                1.0,
                20,
                0.5,
                ProbeMode.AUTO,
                List.of(HostEntry.basic("8.8.8.8", false)),
                alerts,
                PersistenceConfig.defaults());
        ProfilesConfig.save(path, ProfileDocument.singleDefault(profile));
        String yaml = Files.readString(path);
        assertTrue(yaml.contains("fail_after: 4"));
        assertTrue(yaml.contains("notify_resolved: true"));
        TracingProfile reloaded = ProfilesConfig.load(path).active();
        assertTrue(reloaded.alerts().notifyResolved());
        assertTrue(reloaded.alerts().endpointDown().enabled());
        assertEquals(4, reloaded.alerts().endpointDown().failAfter());
        assertEquals(2, reloaded.alerts().endpointDown().clearAfter());
        assertEquals(20, reloaded.alerts().endpointDown().cooldownMinutes());
    }

    @Test
    void saveAndReloadLatencyHighRules() throws Exception {
        Path path = tempDir.resolve("alerts-latency-save.yaml");
        AlertConfig alerts = new AlertConfig(
                false,
                null,
                10,
                false,
                EndpointDownRuleConfig.disabled(),
                new LatencyHighRuleConfig(true, 2.0, 3, 2, 15, 500.0));
        TracingProfile profile = new TracingProfile(
                1.0,
                20,
                0.5,
                ProbeMode.AUTO,
                List.of(HostEntry.basic("8.8.8.8", false)),
                alerts,
                PersistenceConfig.defaults());
        ProfilesConfig.save(path, ProfileDocument.singleDefault(profile));
        String yaml = Files.readString(path);
        assertTrue(yaml.contains("latency_high:"));
        assertTrue(yaml.contains("multiplier: 2.0") || yaml.contains("multiplier: 2"));
        assertTrue(yaml.contains("threshold_ms: 500"));
        TracingProfile reloaded = ProfilesConfig.load(path).active();
        assertTrue(reloaded.alerts().latencyHigh().enabled());
        assertEquals(2.0, reloaded.alerts().latencyHigh().multiplier(), 0.0);
        assertEquals(3, reloaded.alerts().latencyHigh().failAfter());
        assertEquals(500.0, reloaded.alerts().latencyHigh().thresholdMs(), 0.0);
    }

    @Test
    void loadEndpointDownNumericFieldsAndRejectBadPreset() throws Exception {
        Path path = tempDir.resolve("alerts-numeric.yaml");
        Files.writeString(
                path,
                """
                active_profile: noc
                profiles:
                  noc:
                    hosts:
                      - "8.8.8.8"
                    alerts:
                      rules:
                        endpoint_down:
                          enabled: true
                          fail_after: 4
                          clear_after: 1
                          cooldown_minutes: 0
                """);
        EndpointDownRuleConfig rule =
                ProfilesConfig.load(path).active().alerts().endpointDown();
        assertTrue(rule.enabled());
        assertEquals(4, rule.failAfter());
        assertEquals(1, rule.clearAfter());
        assertEquals(0, rule.cooldownMinutes());

        Path bad = tempDir.resolve("alerts-bad-preset.yaml");
        Files.writeString(
                bad,
                """
                active_profile: noc
                profiles:
                  noc:
                    hosts:
                      - "8.8.8.8"
                    alerts:
                      rules:
                        endpoint_down:
                          enabled: true
                          preset: aggressive
                """);
        ConfigError error = assertThrows(ConfigError.class, () -> ProfilesConfig.load(bad));
        assertTrue(error.getMessage().contains("preset"));
    }

    @Test
    void saveEndpointDownWritesPresetWhenMatched() throws Exception {
        Path path = tempDir.resolve("alerts-preset-save.yaml");
        AlertConfig alerts = new AlertConfig(false, null, 10, false, EndpointDownRuleConfig.sensitive(true));
        TracingProfile profile = new TracingProfile(
                1.0,
                20,
                0.5,
                ProbeMode.AUTO,
                List.of(HostEntry.basic("8.8.8.8", false)),
                alerts,
                PersistenceConfig.defaults());
        ProfilesConfig.save(path, ProfileDocument.singleDefault(profile));
        String yaml = Files.readString(path);
        assertTrue(yaml.contains("preset: sensitive"));
        assertEquals(
                "sensitive",
                ProfilesConfig.load(path).active().alerts().endpointDown().matchingPreset());
    }

    @Test
    void loadPersistenceEventsSection() throws Exception {
        Path path = tempDir.resolve("persistence.yaml");
        Files.writeString(
                path,
                """
                active_profile: noc
                profiles:
                  noc:
                    hosts:
                      - "8.8.8.8"
                    persistence:
                      events:
                        route_change: false
                        probe_error: true
                """);
        TracingProfile profile = ProfilesConfig.load(path).active();
        assertEquals(false, profile.persistence().routeChange());
        assertTrue(profile.persistence().probeError());
    }

    @Test
    void savePersistenceEventsSection() throws Exception {
        Path path = tempDir.resolve("persist-save.yaml");
        TracingProfile profile = new TracingProfile(
                1.0,
                20,
                0.5,
                ProbeMode.AUTO,
                List.of(HostEntry.basic("8.8.8.8", false)),
                AlertConfig.disabled(),
                PersistenceConfig.eventsOnly(new PersistenceEventsConfig(false, true)));
        ProfilesConfig.save(path, ProfileDocument.singleDefault(profile));
        TracingProfile reloaded = ProfilesConfig.load(path).active();
        assertEquals(false, reloaded.persistence().routeChange());
        assertTrue(reloaded.persistence().probeError());
    }

    @Test
    void loadPersistenceSessionDbSection() throws Exception {
        Path path = tempDir.resolve("session-db.yaml");
        Files.writeString(
                path,
                """
                active_profile: noc
                profiles:
                  noc:
                    hosts:
                      - "8.8.8.8"
                    persistence:
                      session_db: data/ping.db
                      events:
                        route_change: true
                        probe_error: false
                """);
        TracingProfile profile = ProfilesConfig.load(path).active();
        assertEquals(Path.of("data/ping.db"), profile.persistence().sessionDb().orElseThrow());
        assertTrue(profile.persistence().routeChange());
        assertEquals(false, profile.persistence().probeError());
    }

    @Test
    void savePersistenceSessionDbSection() throws Exception {
        Path path = tempDir.resolve("session-db-save.yaml");
        TracingProfile profile = new TracingProfile(
                1.0,
                20,
                0.5,
                ProbeMode.AUTO,
                List.of(HostEntry.basic("8.8.8.8", false)),
                AlertConfig.disabled(),
                new PersistenceConfig(Optional.of(Path.of("data/session.db")), PersistenceEventsConfig.defaults()));
        ProfilesConfig.save(path, ProfileDocument.singleDefault(profile));
        TracingProfile reloaded = ProfilesConfig.load(path).active();
        assertEquals(
                Path.of("data/session.db"), reloaded.persistence().sessionDb().orElseThrow());
    }

    @Test
    void loadLegacyAlertWebhookField() throws Exception {
        Path path = tempDir.resolve("legacy-webhook.yaml");
        Files.writeString(
                path,
                """
                active_profile: default
                profiles:
                  default:
                    hosts:
                      - "1.1.1.1"
                    alert_webhook: https://legacy.example/hook
                """);
        assertEquals(
                "https://legacy.example/hook",
                ProfilesConfig.load(path).active().alerts().normalizedWebhook());
    }

    @Test
    void loadLegacyHostsList() throws Exception {
        Path path = tempDir.resolve("legacy.yaml");
        Files.writeString(
                path,
                """
                hosts:
                  - "1.1.1.1"
                  - "8.8.8.8"
                """);

        ProfileDocument doc = ProfilesConfig.load(path);
        assertEquals(ProfileDocument.DEFAULT_PROFILE, doc.activeProfile());
        assertEquals(2, doc.active().hosts().size());
        assertTrue(doc.active().hostAddresses().contains("1.1.1.1"));
    }

    @Test
    void loadFileNotFound() {
        assertThrows(ConfigError.class, () -> ProfilesConfig.load(tempDir.resolve("missing.yaml")));
    }

    @Test
    void loadInvalidRoot() throws Exception {
        Path path = tempDir.resolve("bad.yaml");
        Files.writeString(path, "- not-a-map\n");
        assertThrows(ConfigError.class, () -> ProfilesConfig.load(path));
    }

    @Test
    void loadEmptyProfiles() throws Exception {
        Path path = tempDir.resolve("empty.yaml");
        Files.writeString(
                path, """
                active_profile: default
                profiles: {}
                """);
        assertThrows(ConfigError.class, () -> ProfilesConfig.load(path));
    }

    @Test
    void loadDuplicateHostRejected() throws Exception {
        Path path = tempDir.resolve("dup.yaml");
        Files.writeString(
                path,
                """
                active_profile: default
                profiles:
                  default:
                    hosts:
                      - "8.8.8.8"
                      - "8.8.8.8"
                """);
        ConfigError error = assertThrows(ConfigError.class, () -> ProfilesConfig.load(path));
        assertTrue(error.getMessage().contains("Duplicate host"));
    }

    @Test
    void loadProbeModeOnProfileAndHost() throws Exception {
        Path path = tempDir.resolve("probe-mode.yaml");
        Files.writeString(
                path,
                """
                active_profile: default
                profiles:
                  default:
                    probe_mode: mtr
                    hosts:
                      - address: "8.8.8.8"
                        enabled: true
                      - address: "1.1.1.1"
                        probe_mode: ping_only
                """);
        TracingProfile profile = ProfilesConfig.load(path).active();
        assertEquals(HostProbeMode.MTR, profile.hostProbeMode());
        HostEntry primary = profile.hosts().stream()
                .filter(h -> h.address().equals("8.8.8.8"))
                .findFirst()
                .orElseThrow();
        HostEntry pingOnlyHost = profile.hosts().stream()
                .filter(h -> h.address().equals("1.1.1.1"))
                .findFirst()
                .orElseThrow();
        assertEquals(HostProbeMode.MTR, primary.effectiveProbeMode(profile.hostProbeMode()));
        assertEquals(HostProbeMode.PING_ONLY, pingOnlyHost.effectiveProbeMode(profile.hostProbeMode()));

        ProfilesConfig.save(path, ProfilesConfig.load(path));
        TracingProfile reloaded = ProfilesConfig.load(path).active();
        assertEquals(HostProbeMode.MTR, reloaded.hostProbeMode());
        HostEntry reloadedPingOnly = reloaded.hosts().stream()
                .filter(h -> h.address().equals("1.1.1.1"))
                .findFirst()
                .orElseThrow();
        assertEquals(HostProbeMode.PING_ONLY, reloadedPingOnly.probeModeOverride());
    }

    @Test
    void loadHostTagsRoundTrip() throws Exception {
        Path path = tempDir.resolve("tags.yaml");
        Files.writeString(
                path,
                """
                active_profile: default
                profiles:
                  default:
                    hosts:
                      - address: "8.8.8.8"
                        enabled: true
                        tags: [DC, vpn, dc]
                      - address: "1.1.1.1"
                """);
        HostEntry tagged = ProfilesConfig.load(path).active().hosts().get(0);
        assertEquals(List.of("dc", "vpn"), tagged.tags());

        ProfilesConfig.save(path, ProfilesConfig.load(path));
        HostEntry reloaded = ProfilesConfig.load(path).active().hosts().get(0);
        assertEquals(List.of("dc", "vpn"), reloaded.tags());
    }

    @Test
    void loadWindowsExamplePreset() throws Exception {
        Path path = Path.of("config/hosts.windows.example.yaml");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isRegularFile(path), "Run from java/ module directory");
        TracingProfile profile = ProfilesConfig.load(path).active();
        assertEquals(60.0, profile.intervalSeconds());
        assertEquals(HostProbeMode.PING_ONLY, profile.hostProbeMode());
        assertTrue(profile.hosts().stream().anyMatch(h -> h.address().equals("8.8.8.8") && h.enabled()));
        // P16-043: Windows telemetry preset — events_only, no high-freq jsonl/sqlite
        TelemetryConfig telemetry = profile.telemetry();
        assertTrue(telemetry.eventsOnly());
        assertEquals(false, telemetry.logAggregates());
        assertTrue(telemetry.jsonlDir().isEmpty());
        assertTrue(telemetry.sqlitePath().isEmpty());
    }

    @Test
    void loadMaxConcurrentTraces() throws Exception {
        Path path = tempDir.resolve("max-traces.yaml");
        Files.writeString(
                path,
                """
                active_profile: default
                profiles:
                  default:
                    max_concurrent_traces: 5
                    hosts:
                      - "8.8.8.8"
                """);
        assertEquals(5, ProfilesConfig.load(path).active().maxConcurrentTraces());

        ProfilesConfig.save(path, ProfilesConfig.load(path));
        assertEquals(5, ProfilesConfig.load(path).active().maxConcurrentTraces());
    }

    @Test
    void loadHostIntervalOverride() throws Exception {
        Path path = tempDir.resolve("host-interval.yaml");
        Files.writeString(
                path,
                """
                active_profile: default
                profiles:
                  default:
                    interval: 30.0
                    hosts:
                      - address: "8.8.8.8"
                        enabled: true
                        interval: 2.5
                """);
        HostEntry host = ProfilesConfig.load(path).active().hosts().get(0);
        assertEquals(2.5, host.intervalSecondsOverride());
        assertEquals(2.5, host.effectiveIntervalSeconds(HostProbeMode.TRACE, 30.0));

        ProfilesConfig.save(path, ProfilesConfig.load(path));
        HostEntry reloaded = ProfilesConfig.load(path).active().hosts().get(0);
        assertEquals(2.5, reloaded.intervalSecondsOverride());
    }

    @Test
    void loadPingExpertWithChainAndArgs() throws Exception {
        Path path = tempDir.resolve("expert.yaml");
        Files.writeString(
                path,
                """
                active_profile: default
                profiles:
                  default:
                    hosts:
                      - address: "8.8.8.8"
                        enabled: true
                        ping_only: true
                        ping_expert:
                          chain: true
                          args: ["-4", "-s", "128"]
                """);
        ProfileDocument doc = ProfilesConfig.load(path);
        HostEntry host = doc.active().hosts().get(0);
        assertTrue(host.pingOnly());
        assertTrue(host.pingExpert().applyToChain());
        assertEquals(3, host.pingExpert().args().size());

        ProfilesConfig.save(path, doc);
        ProfileDocument reloaded = ProfilesConfig.load(path);
        assertEquals("-4", reloaded.active().hosts().get(0).pingExpert().args().get(0));
    }

    @Test
    void loadNegativeIntervalRejected() throws Exception {
        Path path = tempDir.resolve("neg.yaml");
        Files.writeString(
                path,
                """
                active_profile: default
                profiles:
                  default:
                    interval: -1
                    hosts:
                      - "8.8.8.8"
                """);
        assertThrows(ConfigError.class, () -> ProfilesConfig.load(path));
    }

    @Test
    void loadMissingHostsKey() throws Exception {
        Path path = tempDir.resolve("nohosts.yaml");
        Files.writeString(
                path,
                """
                active_profile: default
                profiles:
                  default:
                    interval: 1.0
                """);
        assertThrows(ConfigError.class, () -> ProfilesConfig.load(path));
    }

    @Test
    void loadInvalidProbeMode() throws Exception {
        Path path = tempDir.resolve("probe.yaml");
        Files.writeString(
                path,
                """
                active_profile: default
                profiles:
                  default:
                    probe: not-a-mode
                    hosts:
                      - "8.8.8.8"
                """);
        assertThrows(Exception.class, () -> ProfilesConfig.load(path));
    }

    @Test
    void loadTooManyHostsRejected() throws Exception {
        Path path = tempDir.resolve("many.yaml");
        StringBuilder hosts = new StringBuilder();
        for (int i = 1; i <= 11; i++) {
            hosts.append("      - \"10.0.0.").append(i).append("\"\n");
        }
        Files.writeString(
                path,
                """
                active_profile: default
                profiles:
                  default:
                    hosts:
                """
                        + hosts);
        assertThrows(ConfigError.class, () -> ProfilesConfig.load(path));
    }

    @Test
    void loadStringHostEntryWithFlags() throws Exception {
        Path path = tempDir.resolve("host-flags.yaml");
        Files.writeString(
                path,
                """
                active_profile: default
                profiles:
                  default:
                    hosts:
                      - "1.1.1.1"
                      - address: "8.8.8.8"
                        enabled: true
                        ping_only: true
                """);
        ProfileDocument doc = ProfilesConfig.load(path);
        assertEquals(2, doc.active().hosts().size());
        HostEntry mapped = doc.active().hosts().get(1);
        assertTrue(mapped.enabled());
        assertTrue(mapped.pingOnly());
    }

    @Test
    void loadInvalidBooleanInHostEntry() throws Exception {
        Path path = tempDir.resolve("bad-bool.yaml");
        Files.writeString(
                path,
                """
                active_profile: default
                profiles:
                  default:
                    hosts:
                      - address: "8.8.8.8"
                        enabled: "yes"
                """);
        assertThrows(ConfigError.class, () -> ProfilesConfig.load(path));
    }

    @Test
    void saveAndReloadMinimalProfile() throws Exception {
        Path path = tempDir.resolve("out.yaml");
        ProfileDocument doc =
                new ProfileDocument("default", java.util.Map.of("default", TracingProfile.defaults(List.of())));
        ProfilesConfig.save(path, doc);
        assertEquals("default", ProfilesConfig.load(path).activeProfile());
    }

    @Test
    void loadMissingProfilesAndHosts() throws Exception {
        Path path = tempDir.resolve("empty-root.yaml");
        Files.writeString(path, "active_profile: default\n");
        ConfigError error = assertThrows(ConfigError.class, () -> ProfilesConfig.load(path));
        assertTrue(error.getMessage().contains("profiles") || error.getMessage().contains("hosts"));
    }

    @Test
    void loadProfilesNotMapping() throws Exception {
        Path path = tempDir.resolve("profiles-list.yaml");
        Files.writeString(
                path, """
                active_profile: default
                profiles: []
                """);
        assertThrows(ConfigError.class, () -> ProfilesConfig.load(path));
    }

    @Test
    void loadHostEntryWrongType() throws Exception {
        Path path = tempDir.resolve("host-int.yaml");
        Files.writeString(
                path,
                """
                active_profile: default
                profiles:
                  default:
                    hosts:
                      - 42
                """);
        assertThrows(ConfigError.class, () -> ProfilesConfig.load(path));
    }

    @Test
    void loadHostMissingAddress() throws Exception {
        Path path = tempDir.resolve("no-address.yaml");
        Files.writeString(
                path,
                """
                active_profile: default
                profiles:
                  default:
                    hosts:
                      - enabled: true
                """);
        assertThrows(ConfigError.class, () -> ProfilesConfig.load(path));
    }

    @Test
    void saveRejectsProfileExceedingMaxHosts() {
        List<HostEntry> tooMany = new java.util.ArrayList<>();
        for (int i = 1; i <= HostsConfig.MAX_HOSTS + 1; i++) {
            tooMany.add(HostEntry.basic("10.0.0." + i, false));
        }
        ProfileDocument doc = ProfileDocument.singleDefault(TracingProfile.defaults(tooMany));
        assertThrows(ConfigError.class, () -> ProfilesConfig.save(tempDir.resolve("many.yaml"), doc));
    }

    @Test
    void loadNonNumberTimeout() throws Exception {
        Path path = tempDir.resolve("maxhops.yaml");
        Files.writeString(
                path,
                """
                active_profile: default
                profiles:
                  default:
                    max_hops: fast
                    hosts:
                      - "8.8.8.8"
                """);
        assertThrows(ConfigError.class, () -> ProfilesConfig.load(path));
    }

    @Test
    void loadPingExpertArgsNotList() throws Exception {
        Path path = tempDir.resolve("args.yaml");
        Files.writeString(
                path,
                """
                active_profile: default
                profiles:
                  default:
                    hosts:
                      - address: "8.8.8.8"
                        ping_expert:
                          args: "-4"
                """);
        assertThrows(ConfigError.class, () -> ProfilesConfig.load(path));
    }

    @Test
    void loadMixedIpv4AndIpv6Hosts() throws Exception {
        Path path = tempDir.resolve("dual.yaml");
        Files.writeString(
                path,
                """
                active_profile: default
                profiles:
                  default:
                    hosts:
                      - "8.8.8.8"
                      - "2001:db8::1"
                      - address: "2001:4860:4860::8888"
                        enabled: true
                """);
        ProfileDocument doc = ProfilesConfig.load(path);
        assertEquals(3, doc.active().hosts().size());
        assertEquals("2001:db8::1", doc.active().hosts().get(1).address());
        ProfilesConfig.save(path, doc);
        assertEquals(3, ProfilesConfig.load(path).active().hosts().size());
    }
}
