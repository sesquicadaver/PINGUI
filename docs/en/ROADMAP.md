> **Language:** English · [Українська](../ROADMAP.md)

# ROADMAP — PINGUI Java (`main` / `beta`)

**Official project work plan.** Update on task closure: `[x]` + date in `CHANGELOG.md`.

Post-MVP roadmap (2026-06-26) for **professional users** (NOC/SRE, network engineers, WAN/MPLS admins).

**Legend**

| Field | Value |
|-------|-------|
| **Branch** | `main` — Java + docs; `beta` — + Python, tests, CI |
| **Priority** | P0 critical · P1 important · P2 nice-to-have |
| **DoD** | Definition of Done — task closure condition |

Tasks are **atomic**: one task ≈ one MR/commit, ≤ 1 day of work.

---

## Phase 0 — Quick fixes (`main`, P0)

| ID | Task | Files | DoD |
|----|------|-------|-----|
| **M-001** | [x] Remove duplicate `import java.io.IOException` | `probe/RawIcmpRouteProbe.java` | `./gradlew compileJava` OK; one import |
| **M-002** | [x] Document **IPv4-only** (validator + raw ICMP) | `README.md`, `docs/JAVA.md`, `docs/DEPLOYMENT.md`, `AppMenuDialogs` help | Explicit note «IPv6 not supported»; examples IPv4/hostname only |
| **M-003** | [x] CHANGELOG: entry about roadmap and IPv4-only | `CHANGELOG.md` | `[Unreleased]` section updated |

---

## Phase 1 — CLI override (`main`, P0)

**Problem:** `applyCliOverridesToActiveProfile()` always substitutes `AppOptions.defaults()`, overwriting YAML on start without CLI.

| ID | Task | Files | DoD |
|----|------|-------|-----|
| **M-010** | [x] Introduce `CliOverrides` (record with `Optional` fields: interval, maxHops, timeout, probe) | `CliProfileOverrides.java`, `AppOptions.java`, `PinguiApplication.java` | CLI parser fills `Optional.empty()` for non-passed flags |
| **M-011** | [x] `parseOptions`: distinguish «not passed» vs «default» | `PinguiApplication.java` | `--interval 2` → override; without `--interval` → empty |
| **M-012** | [x] `applyCliOverridesToActiveProfile`: merge only present fields | `MainController.java` | Start without CLI preserves YAML `interval`/`max_hops`/`timeout`/`probe` |
| **M-013** | [x] Document CLI vs YAML behavior | `java/README.md`, `docs/JAVA.md` | Table «CLI overwrites profile field only if passed» |
| **M-014** | [x] Manual smoke: profile `interval: 30` + `./pingui-java.sh` | — | Unit test M-014 + CHECKLIST § CLI interval |

---

## Phase 2 — Hygiene / static checks (`main` → `beta`, P1)

| ID | Task | Files | DoD |
|----|------|-------|-----|
| **M-020** | [x] Add Spotless (Google Java Format or Palantir) | `java/build.gradle.kts`, `settings.gradle.kts` | `./gradlew spotlessCheck` passes |
| **M-021** | [x] `./gradlew spotlessApply` + format existing `.java` | `java/src/main/**` | `spotlessCheck` green; diff formatting only |
| **M-022** | [x] Gradle task `check` = `compileJava` + `spotlessCheck` | `java/build.gradle.kts` | `./gradlew check` on `main` |
| **M-023** | [x] Checkstyle — minimal ruleset | `java/build.gradle.kts`, `config/checkstyle/checkstyle.xml` | RedundantImport, UnusedImports; `./gradlew check` |

---

## Phase 3 — Test layer (`beta`, P0)

| ID | Task | Files | DoD |
|----|------|-------|-----|
| **B-001** | [x] JUnit 5 + test deps in `java/build.gradle.kts` | `build.gradle.kts` | `./gradlew test` runs |
| **B-002** | [x] Fixtures: `traceroute` output samples (Linux/macOS) | `src/test/resources/trace/unix_*.txt` | ≥ 3 files (ok, timeout, hostname) |
| **B-003** | [x] Fixtures: `tracert` samples (Windows) | `src/test/resources/trace/win_*.txt` | ≥ 3 files (`<1 ms`, `host [IP]`, timeout) |
| **B-004** | [x] Unit: `ProcessRouteProbe.parseUnix` | `ProcessRouteProbeTest.java` | Hop count, IP, RTT for each fixture |
| **B-005** | [x] Unit: `ProcessRouteProbe.parseWindows` | same test class | Windows line parsing without `No hops parsed` |
| **B-006** | [x] Unit: `windowsTracertWaitMs` / `-w` ≥ 4000 | `ProcessRouteProbeTest.java` | Assert minimum wait |
| **B-007** | [x] Unit: `HostsConfig.validateSessionHost` | `HostsConfigTest.java` | duplicate, max 10, invalid chars, IPv4 ok |
| **B-008** | [x] Unit: `ProfilesConfig` v2 + legacy migration | `ProfilesConfigTest.java` | load/save round-trip; `active_profile` |
| **B-009** | [x] Unit: `PingExpertValidator` | `PingExpertValidatorTest.java` | invalid flags → `ConfigError` |
| **B-010** | [x] Unit: CLI override merge | `PinguiApplicationTest.java` | optional fields do not overwrite profile |

---

## Phase 4 — CI (`beta`, P0)

| ID | Task | Files | DoD |
|----|------|-------|-----|
| **B-020** | [x] GitHub Actions: JDK 21, venv not needed for Java job | `.github/workflows/java.yml` | `compileJava` + `test` + `spotlessCheck` on push/PR |
| **B-021** | [x] CI matrix: `ubuntu-latest` (required); Windows optional | workflow | Linux green; Windows job `continue-on-error` |
| **B-022** | [x] Badge / status in README | `README.md` | CI badge visible |
| **B-023** | [x] Living spec: matrix «spec → module → test» | `docs/LIVING_SPEC.md` | Rows for probe, config, CLI override, CI |

---

## Phase 5 — UI split (`beta`, P1)

**Goal:** `MainController` ≤ ~300 lines; SRP.

| ID | Task | Extract from | DoD |
|----|------|--------------|-----|
| **B-030** | [x] `ProfileUiActions` — new/delete/select profile, combo sync | `MainController` | Profile CRUD extracted; controller delegates |
| **B-031** | [x] `HostListPresenter` — add/edit/remove, toggles, list height | `MainController` | Host ops + `HostListCell` callbacks |
| **B-032** | [x] `MonitorLifecycle` — create/close monitor, reload profile | `MainController` | `reloadActiveProfile` + `createMonitor` |
| **B-033** | [x] `ViewModeController` — Simple/Extended, `fitWindowToContent` | `MainController` | Easter egg remains or → `HostViewRules` helper |
| **B-034** | [x] `RouteGraphPresenter` — `redrawRouteIfExtended`, graph panel | `MainController` | Extended mode graph + status label |
| **B-035** | [x] GUI smoke: profile, host, save, F1/About | `docs/CHECKLIST.md` § GUI smoke | Checklist B-035; manual run on Linux |

---

## Phase 6 — Probe / OS strategy (`beta`, P1)

| ID | Task | Files | DoD |
|----|------|-------|-----|
| **B-040** | [x] Interface `TraceCommandBuilder` (OS → argv[]) | `probe/` | Linux/macOS/Windows implementations |
| **B-041** | [x] Move commands from `ProcessRouteProbe` | `LinuxTracerouteCommand`, `MacTracerouteCommand`, `WindowsTracertCommand` | Parity with current behavior; tests B-004/B-005 green |
| **B-042** | [x] Unix parser: separate `UnixTraceOutputParser` | `probe/` | Unit tests on fixtures |
| **B-043** | [x] Windows parser: `WindowsTraceOutputParser` | `probe/` | Localized timeout lines in fixtures |
| **B-044** | [x] Document parser limitations (IPv6 trace output, ASN) | `docs/JAVA.md` | Known limitations |

---

## Phase 7 — IPv6 SPIKE (closed, P2)

| ID | Task | DoD |
|----|------|-----|
| **B-050** | [x] SPIKE: IPv6 trace + ping — scope of work | `docs/SPIKE_IPV6.md` | Decision: wontfix (MVP) |
| **B-051** | — (cancelled) `HostsConfig` — IPv6 literal | — | Moved → **V6-010…V6-019** |
| **B-052** | — (cancelled) Raw ICMP IPv6 | — | Moved → **V6-040…V6-049** |
| **B-053** | [x] Close B-050 with status «IPv4-only by design» | `HostsConfig`, `SPIKE_IPV6.md` | Explicit error for IPv6 literal |

> **Decision review (2026-06):** wontfix lifted per product request; implementation — **Phase 9 (V6-*)**.

---

## Phase 9 — IPv6 implementation (`beta` → `main`, P1)

**Goal:** dual-stack monitoring — IPv6 literal, subprocess trace/ping, GeoIP v6; raw ICMP v6 — Linux-only (P2).

**Prerequisites:** `./gradlew check` green; B-064 JaCoCo gate ≥80%.

**Out of phase 9 scope (separate ticket):** Python IPv6 — **Phase PY.4 (PY-050…PY-052)** ✅ on `beta`; full Windows expert-ping parity (see backlog after V6-059).

### 9.0 — Design gate

| ID | Task | Files | DoD |
|----|------|-------|-----|
| **V6-001** | [x] Update SPIKE: status **planned**, phase 9 goals, OS matrix | `docs/SPIKE_IPV6.md` | Table «layer → v4 → v6 → OS»; links to V6-* |
| **V6-002** | [x] ADR: dual-stack policy (literal v6, hostname→AAAA, mixed profile) | `docs/ADR_IPV6.md` | Decision: bracket YAML, canonical RFC 5952, probe fallback |
| **V6-003** | [x] `HostAddressKind` + `HostAddressParser` (IPv4 / IPv6 / hostname) | `config/HostAddress*.java` | Unit test: parse/normalize without UI |

### 9.1 — Config / validator (P0)

| ID | Task | Files | DoD |
|----|------|-------|-----|
| **V6-010** | [x] RFC 5952 normalize for IPv6 literal | `HostsConfig.java`, `HostAddressParser` | `2001:db8::1` → canonical; `[::1]` strip brackets |
| **V6-011** | [x] Accept IPv6 in `normalizeHostEntry` / `isValidHost` | `HostsConfig.java` | Remove blanket `:` → error; keep reject invalid |
| **V6-012** | [x] Duplicate key: canonical v6 (case-insensitive hex) | `HostsConfig.java`, `ProfilesConfig` | `HostsConfigTest`: dup `2001:DB8::1` vs `2001:db8:0:0:0:0:0:1` |
| **V6-013** | [x] Bracket notation in YAML examples | `java/README.md`, `docs/DEPLOYMENT.md` | Example `address: "2001:db8::1"` |
| **V6-014** | [x] Mixed profile: IPv4 + IPv6 hosts in one profile | `ProfilesConfigTest` | load/save round-trip 2+2 hosts |
| **V6-015** | [x] LIVING_SPEC: HostAddress / v6 validator rows | `docs/LIVING_SPEC.md` | Matrix updated |

### 9.2 — Process trace (subprocess, P0)

| ID | Task | Files | DoD |
|----|------|-------|-----|
| **V6-020** | [x] `TraceTarget` — address family from literal | `probe/TraceTarget.java` | Unit test: v4/v6/hostname |
| **V6-021** | [x] `LinuxTracerouteCommand`: `-6` for v6 literal | `LinuxTracerouteCommand.java` | Test: argv contains `-6` |
| **V6-022** | [x] `MacTracerouteCommand`: `-6` for v6 literal | `MacTracerouteCommand.java` | Test: argv contains `-6` |
| **V6-023** | [x] `WindowsTracertCommand`: `-6` for v6 literal | `WindowsTracertCommand.java` | Test: argv contains `-6` |
| **V6-024** | [x] `UnixTraceOutputParser`: hop token `[2001:db8::n]` | `UnixTraceOutputParser.java` | Regex + unit test |
| **V6-025** | [x] `UnixTraceOutputParser`: compressed v6 without brackets (GNU) | `UnixTraceOutputParser.java` | Fixture + test |
| **V6-026** | [x] `WindowsTraceOutputParser`: IPv6 tracert lines | `WindowsTraceOutputParser.java` | Fixture + test |
| **V6-027** | [x] Fixtures `trace/unix_v6_*.txt` (≥3) | `src/test/resources/trace/` | ok / timeout / multihop |
| **V6-028** | [x] Fixtures `trace/win_v6_*.txt` (≥2) | `src/test/resources/trace/` | ok / timeout |
| **V6-029** | [x] `ProcessRouteProbeTest` — v6 fixtures green | `ProcessRouteProbeTest.java` | Hop count + IP match |
| **V6-030** | [x] Doc: hostname AAAA — OS resolve, not PINGUI | `docs/JAVA.md` | Known limitations updated |

### 9.3 — GeoIP v6 (P1)

| ID | Task | Files | DoD |
|----|------|-------|-----|
| **V6-035** | [x] `GeoCountry`: `Inet6Address` — loopback/link-local/ULA → `LAN` | `GeoCountry.java` | `GeoCountryTest` |
| **V6-036** | [x] `GeoCountry`: longest-prefix for IPv6 CIDR | `GeoCountry.java`, `geoip_hints.yaml` | Test: `2001:db8::/32` |
| **V6-037** | [x] YAML schema: optional `prefixes_v6` (or unified map) | `GeoCountry.java`, docs | Backward compat v4 hints |

### 9.4 — Raw ICMP v6 (Linux only, P2)

| ID | Task | Files | DoD |
|----|------|-------|-----|
| **V6-040** | [x] JNA: `AF_INET6`, `sockaddr_in6` | `LinuxSocketConstants`, `LinuxCLibrary` | Compile + struct layout test |
| **V6-041** | [x] ICMPv6 echo request/reply parse | `IcmpV6Packet.java` | Unit test without cap (build packet) |
| **V6-042** | [x] `LinuxJnaIcmpTransport` dual: v4/v6 socket | `LinuxJnaIcmpTransport.java` | Integration test optional; mock-friendly unit |
| **V6-043** | [x] `RawIcmpRouteProbe`: hop limit for v6 | `RawIcmpRouteProbe.java` | v6 target → trace hops |
| **V6-044** | [ ] `RouteProbeFactory`: v6 + non-Linux → process fallback | `RouteProbeFactory.java` | Test: AUTO on macOS → process |
| **V6-045** | [x] DEPLOYMENT: cap note for ICMPv6 | `docs/DEPLOYMENT.md`, `docs/en/DEPLOYMENT.md` | Linux-only raw v6 documented |

### 9.5 — Expert ping v6 (P1)

| ID | Task | Files | DoD |
|----|------|-------|-----|
| **V6-050** | [ ] Auto `-6` in `ProcessExpertPing.buildCommand` for v6 target | `ProcessExpertPing.java` | Test: v6 target → `-6` in argv |
| **V6-051** | [ ] `ProcessHostPing`: expert args + v6 on Linux/macOS | `ProcessHostPing.java` | Test: args appended |
| **V6-052** | [ ] Validator: `-4` + v6 target → `ConfigError` (profile save) | `PingExpertValidator` or host-level check | Unit test |
| **V6-053** | [x] `-F` flow label — only with v6 target (UI hint) | `PingExpertDialog.java`, `ExpertPingUiRules.java` | Tooltip / disable when target v4 |

### 9.6 — UI / docs (P1)

| ID | Task | Files | DoD |
|----|------|-------|-----|
| **V6-060** | [x] Help/About: dual-stack instead of «IPv4-only» | `AppMenuDialogs.java`, `README.md` | Text updated |
| **V6-061** | [x] `GraphCanvas` / labels: bracket display for long v6 | `HopDisplay.java`, `PingColor.java` | Manual smoke note in CHECKLIST |
| **V6-062** | [x] Input validation in Add Host dialog for v6 | `HostListPresenter` | Invalid v6 → log error |
| **V6-063** | [x] CHANGELOG + ROADMAP `[x]` on subphase closure | `CHANGELOG.md` | Per-sprint notes |

### 9.7 — QA / release gate (P0)

| ID | Task | Files | DoD |
|----|------|-------|-----|
| **V6-070** | [x] CHECKLIST § IPv6 smoke (Linux process trace) | `docs/CHECKLIST.md` | literal v6 + ping-only |
| **V6-071** | [x] CHECKLIST § IPv6 smoke (Windows tracert -6) | `docs/CHECKLIST.md` | optional OS job |
| **V6-072** | [x] Regression: all v4 fixtures remain green | `ProcessRouteProbeTest`, CI | `./gradlew check` |
| **V6-073** | [x] JaCoCo: new modules in bundle or documented exclusion | `build.gradle.kts` | Gate ≥80% |
| **V6-074** | [x] Release note: «IPv6 beta» / feature flag if needed | `CHANGELOG.md` | Semver minor bump note |

### Recommended order (phase 9)

```mermaid
flowchart TD
  V6001[V6-001 SPIKE planned] --> V6003[V6-003 HostAddressParser]
  V6003 --> V6010[V6-010 RFC5952]
  V6010 --> V6014[V6-014 mixed profile]
  V6014 --> V6020[V6-020 TraceTarget]
  V6020 --> V6021[V6-021 Linux -6]
  V6021 --> V6024[V6-024 Unix v6 parser]
  V6024 --> V6027[V6-027 v6 fixtures]
  V6027 --> V6029[V6-029 probe tests]
  V6029 --> V6035[V6-035 GeoIP v6]
  V6029 --> V6050[V6-050 expert ping v6]
  V6029 --> V6040[V6-040 raw ICMP v6]
  V6035 --> V6070[V6-070 CHECKLIST smoke]
  V6050 --> V6070
  V6040 --> V6070
  V6070 --> V6074[V6-074 release]
```

**Estimate:** 3–5 sprint × 3–5 tasks; raw ICMP (V6-040…) can be deferred after process+GeoIP MVP.

| Sprint (proposed) | Tasks |
|-------------------|-------|
| IPv6-S1 | V6-001…V6-015 (config) |
| IPv6-S2 | V6-020…V6-030 (process trace) |
| IPv6-S3 | V6-035…V6-037, V6-050…V6-053 (GeoIP + expert) |
| IPv6-S4 | V6-060…V6-063, V6-070…V6-074 (UI + QA) |
| IPv6-S5 (opt.) | V6-040…V6-045 (raw ICMP v6 Linux) |

---

## Phase PY — Python CLI/NOC hardening (`beta`, P0–P1)

**Goal:** turn the post-MVP Python stack into a full NOC tool: launcher, documentation, headless monitor, daemon; aligned with phases P10–P12.

**Context:** B-01…B-06 ✅ (`--session-db`, export, GeoIP, geo-map, timeseries, jitter/loss). Phases 10–16 are mostly Java-oriented; Python **leads** on P11/P15 but **lags** on launcher, docs, daemon, alerts.

**Branch:** `beta` only (Python tree is not added to `main`).

**Links:** PY-S1 — before Pro-S2; PY-S2 — Python prerequisite for P12; PY-S3 — Python parity for P10.

### PY.0 — CLI launcher and documentation (P0)

| ID | Task | Files | DoD |
|----|------|-------|-----|
| **PY-010** | [x] `pingui.sh`: forward CLI flags to `python -m pingui` | `pingui.sh` | `./pingui.sh --export-csv out.csv` or `./pingui.sh -- --session-db db.sqlite` works; `--deploy`/`--help` unchanged |
| **PY-011** | [x] `CONFIGURATION.md`: full CLI reference + env | `docs/CONFIGURATION.md`, `docs/en/CONFIGURATION.md` | All flags from `__main__.py`; `INFLUXDB_*`, `PINGUI_TIMESCALE_DSN` |
| **PY-012** | [x] `MODULES.md`: post-MVP Python modules | `docs/MODULES.md`, `docs/en/MODULES.md` | `session_db`, `export`, `geoip`, `timeseries`, `hop_stats` |
| **PY-013** | [x] Python Living Spec: module → test matrix | `docs/LIVING_SPEC.md`, `docs/en/LIVING_SPEC.md` | Section «Python (`beta`)»; closes B-023 gap for Python |
| **PY-014** | [x] Unit: CLI `--export-html` without GUI | `tests/unit/test_main_export.py` | `main([...,"--export-html", path])` → file exists |
| **PY-015** | [x] Deploy gate = CI gate | `pingui.sh`, `scripts/ci_venv.sh` | `./pingui.sh --deploy` runs `check_doc_parity.py` |
| **PY-016** | [x] `TESTING.md`: post-MVP test table | `docs/TESTING.md`, `docs/en/TESTING.md` | `test_session_db`, `test_timeseries`, `test_geo_*`, `test_main_export` |

**Estimate:** 1 sprint (≤ 1 day per ticket).

### PY.1 — Monitor loop without Qt (P1)

**Problem:** `LightweightMonitorWorker` is a `QThread`; headless/daemon is impossible without PyQt.

| ID | Task | Files | DoD |
|----|------|-------|-----|
| **PY-020** | [x] `MonitorLoop` on stdlib `threading` | `monitor/monitor_loop.py` | Loop enabled hosts → `poll_host_route()`; callbacks instead of Qt signals |
| **PY-021** | [x] `LightweightMonitorWorker` — thin wrapper | `monitor/worker.py` | Worker delegates to `MonitorLoop`; existing Qt tests green |
| **PY-022** | [x] Single `enabled` state | `session_store.py`, `worker.py`, `ui/main_window.py` | Remove duplicate `worker._enabled` vs `HostSessionData.enabled` |
| **PY-023** | [x] CLI subcommands: `run` \| `export` \| `monitor` \| `daemon` \| `stop` \| `status` | `src/pingui/__main__.py` | `argparse` subparsers; backward compat: no subcommand = `run` |

**Estimate:** 1–2 sprint. **Prerequisite** for PY-030…034 and Python P12.

### PY.2 — Headless daemon (P1) — Python parity with P12

| ID | Task | Files | DoD |
|----|------|-------|-----|
| **PY-030** | [x] `daemon`: no PyQt, `MonitorLoop` only | `__main__.py`, `monitor/daemon_runner.py` | `python -m pingui daemon --session-db PATH` — process stays running |
| **PY-031** | [x] PID file + `stop` / `status` | `monitor/daemon_runner.py` | Contract test start/stop |
| **PY-032** | [x] `systemd/pingui.service.example` | `systemd/` | `Type=simple`, `Restart=on-failure`, `User=` |
| **PY-033** | [x] DEPLOYMENT § Python NOC headless | `docs/DEPLOYMENT.md` | `--session-db`, daemon flags |
| **PY-034** | [x] CHECKLIST § Python daemon smoke | `docs/CHECKLIST.md` | start → status → stop |

**Estimate:** 1–2 sprint. **Parallel** with P12-001…P12-040 (Java).

### PY.3 — Python alerts (P0) — parity with P10

| ID | Task | Files | DoD |
|----|------|-------|-----|
| **PY-040** | [x] `RouteChangeEvent` in `models.py` | `models.py`, `tests/unit/test_route_change_event.py` | JSON serialize/deserialize; shared contract with P10-010 |
| **PY-041** | [x] `AlertDispatcher` + `WebhookAlertDispatcher` | `monitor/alert_dispatcher.py` | POST JSON; contract test with mock HTTP |
| **PY-042** | [x] CLI `--alert-webhook URL` | `__main__.py` | Secret not logged; network error → log, no crash |
| **PY-043** | [x] Desktop notify (`notify-send`) | `monitor/desktop_notifier.py` | Linux smoke: route change → notification |
| **PY-044** | [x] Alert rate limit | `monitor/alert_rate_limiter.py` | Unit test burst per host |
| **PY-045** | [x] Daemon + alerts | `daemon_runner.py` | Route change → webhook without GUI (Python P12-030) |

**Estimate:** 1–2 sprint. **Can run in parallel** with Pro-S2 (P10).

### PY.4 — Python IPv6 (P2) — parity with V6

| ID | Task | Files | DoD |
|----|------|-------|-----|
| **PY-050** | [x] IPv6 literal in `config.py` | `config.py`, `tests/unit/test_config.py` | RFC 5952 normalize; mixed v4+v6 profile |
| **PY-051** | [x] Dual-stack ICMP trace | `icmp/tracer.py`, `icmp/process_tracer.py` | v6 literal → `traceroute -6`; `@pytest.mark.network` optional |
| **PY-052** | [x] GeoIP `prefixes_v6` | `geoip/country.py`, `config/geoip_hints.yaml` | Parity with V6-036…V6-037 |

**Estimate:** 2–3 sprint after V6-015. **Out of scope v1:** raw ICMPv6 (see V6-040…V6-045).

### PY.5 — Packaging and CI hygiene (P1–P2)

| ID | Task | Files | DoD |
|----|------|-------|-----|
| **PY-060** | [x] `optional-dependencies.gui` | `pyproject.toml` | Base: `scapy`, `PyYAML`, `networkx`; extra: PyQt6, WebEngine, folium, matplotlib |
| **PY-061** | [x] CI push on `beta` | `.github/workflows/ci.yml` | `branches: [main, master, beta]` |
| **PY-062** | [x] Python CI badge in README | `README.md`, `README.en.md` | Badge next to Java CI |
| **PY-063** | [x] mypy `python_version` aligned with CI runtime | `pyproject.toml` | CI `setup-python` 3.11; mypy 3.12 (numpy stubs); numpy `ignore_errors` |
| **PY-064** | [x] Coverage: `__main__.py` in gate | `pyproject.toml`, `tests/unit/test_main_dispatch.py` | omit removed; ≥80% total gate |

**Estimate:** 1 sprint; PY-060…064 can be cherry-picked into PY-S1.

### Recommended order (phase PY)

```mermaid
flowchart TD
  PY010[PY-010 pingui.sh CLI] --> PY011[PY-011 CONFIGURATION]
  PY011 --> PY013[PY-013 Living Spec]
  PY013 --> PY020[PY-020 MonitorLoop]
  PY020 --> PY030[PY-030 --daemon]
  PY030 --> PY040[PY-040 RouteChangeEvent]
  PY040 --> PY045[PY-045 daemon alerts]
  PY020 --> PY050[PY-050 IPv6 config]
```

| Sprint (proposed) | Tasks | Priority |
|-------------------|-------|----------|
| **PY-S1** | PY-010…PY-016, PY-060…PY-064 (partial) | P0 |
| **PY-S2** | PY-020…PY-023, PY-030…PY-034 | P1 |
| **PY-S3** | PY-040…PY-045 | P0 |
| **PY-S4 (opt.)** | PY-050…PY-052 | P2 |

---

## Phase 10 — Route change alerts (`beta` → `main`, P0)

**Goal:** professional users learn about route changes without an open GUI.

**Audience:** NOC, on-call, SRE runbooks.

| ID | Task | Files | DoD |
|----|------|-------|-----|
| **P10-001** | [x] ADR: alert policy (channels, rate limit, payload) | `docs/ADR_ALERTS.md`, `docs/en/ADR_ALERTS.md` | Webhook + desktop; SNMP/email — out of scope v1 |
| **P10-010** | [ ] Model `RouteChangeEvent` (host, old_ips, new_ips, ts, profile) | `monitor/RouteChangeEvent.java`, Python `models.py` | Unit test serialize/deserialize |
| **P10-011** | [ ] `AlertDispatcher` interface + no-op default | `monitor/AlertDispatcher.java` | Monitor calls on `onRouteChanged` |
| **P10-020** | [ ] Desktop notification (Linux notify-send / Windows toast / macOS) | `ui/RouteChangeNotifier.java` | Manual smoke: route change → notification |
| **P10-021** | [ ] YAML/CLI: `alerts.desktop: true\|false` | `ProfilesConfig`, `PinguiApplication` | Default off; documented in CONFIGURATION |
| **P10-030** | [ ] Webhook POST JSON (Slack-compatible + generic) | `monitor/WebhookAlertDispatcher.java` | Contract test with mock HTTP server |
| **P10-031** | [ ] CLI `--alert-webhook URL` + profile field `alert_webhook` | `CliProfileOverrides`, YAML schema | Do not log secrets; network error → log, no crash |
| **P10-040** | [ ] Rate limit: max N alerts / host / hour | `AlertRateLimiter.java` | Unit test burst |
| **P10-050** | [ ] LIVING_SPEC + CHECKLIST § alert smoke | `docs/LIVING_SPEC.md`, `docs/CHECKLIST.md` | Manual Linux run |

**Estimate:** 1–2 sprints.

---

## Phase 11 — Persistence and timeline (Java parity with Python, P0)

**Goal:** route history across sessions; replay «when hop N changed».

**Context:** Python `beta` has `--session-db`, export, jitter/loss; Java `main` is RAM-only.

| ID | Task | Files | DoD |
|----|------|-------|-----|
| **P11-001** | [ ] SPIKE: SQLite schema for Java (routes, events, samples) | `docs/SPIKE_PERSISTENCE.md` | Parity with Python `session_db.py` |
| **P11-010** | [ ] `SessionDatabase` — open/migrate/close | `persistence/SessionDatabase.java` | Flyway or manual schema v1 |
| **P11-011** | [ ] Persist route snapshot + route_change event | `MonitorService`, `SessionDatabase` | Unit test insert/query |
| **P11-012** | [ ] CLI `--session-db PATH` | `PinguiApplication`, `java/README.md` | Optional; without PATH — RAM-only |
| **P11-020** | [ ] UI: «History» panel — route changes 24h/7d | `RouteHistoryPresenter.java` | Manual smoke |
| **P11-021** | [ ] UI: replay snapshot on graph (read-only) | `RouteGraphPresenter` | Select event → graph |
| **P11-030** | [ ] Export CSV/HTML from DB (like Python `session_report`) | `export/SessionReportExporter.java` | CLI `--export-report` |
| **P11-040** | [ ] Java parity: jitter/loss labels from history | `HopStats`, `GraphCanvas` | Parity with J-06 / B-06 |
| **P11-050** | [ ] LIVING_SPEC + DEPLOYMENT (disk, retention) | `docs/LIVING_SPEC.md`, `docs/DEPLOYMENT.md` | Retention policy documented |

**Estimate:** 2–3 sprints.

---

## Phase 12 — Headless / daemon mode (Linux, P1)

**Goal:** monitoring on NOC server without GUI; `systemd` unit.

| ID | Task | Files | DoD |
|----|------|-------|-----|
| **P12-001** | [ ] ADR: daemon lifecycle (signals, single instance, logging) | `docs/ADR_DAEMON.md` | SIGHUP reload config |
| **P12-010** | [ ] `--daemon` mode: no JavaFX, MonitorService loop only | `PinguiApplication`, `DaemonRunner.java` | `./pingui-java.sh --daemon` stays running |
| **P12-011** | [ ] PID file + `--stop` / `--status` | `DaemonPidFile.java` | Contract test start/stop |
| **P12-020** | [ ] `systemd/pingui.service.example` | `systemd/` | `Type=simple`, `Restart=on-failure` |
| **P12-021** | [ ] DEPLOYMENT § NOC headless | `docs/DEPLOYMENT.md` | cap_net_raw, webhook, session-db |
| **P12-030** | [ ] P10 alerts integration in daemon | `DaemonRunner` | Route change → webhook without GUI |
| **P12-040** | [ ] CHECKLIST § daemon smoke | `docs/CHECKLIST.md` | start → route change → webhook log |

**Estimate:** 1–2 sprints. **Out of scope:** Windows service (separate ticket).

---

## Phase 13 — Probe efficiency (MTR, intervals, P1)

**Goal:** less load, faster reaction; especially on Windows.

| ID | Task | Files | DoD |
|----|------|-------|-----|
| **P13-001** | [ ] ADR: `probe_mode: trace \| mtr \| ping_only` | `docs/ADR_PROBE_MODES.md` | MTR = continuous per-hop, not full trace each cycle |
| **P13-010** | [ ] `MtrProbe` / per-hop poll state machine | `probe/MtrProbe.java` | Unit test state transitions |
| **P13-011** | [ ] YAML `probe_mode` per profile + host override | `ProfilesConfig`, `HostEntry` | Backward compat: default `trace` |
| **P13-020** | [ ] Smart interval: `ping_only` 1–2s, `trace` 30–300s per host | `MonitorService`, `HostPollSchedule` | Profile default + per-host override |
| **P13-021** | [ ] Burst on change: after route change — interval ×0.25 for 5 min | `BurstSchedulePolicy.java` | Unit test timer |
| **P13-030** | [ ] Parallel poll: `max_concurrent_traces` (default 3) | `MonitorService` | At most N subprocess at once |
| **P13-040** | [ ] Windows profile preset: auto `ping_only` + `interval: 60` | `config/hosts.windows.example.yaml` | CHECKLIST Windows |
| **P13-050** | [ ] LIVING_SPEC + JAVA.md known limitations | `docs/JAVA.md` | MTR vs traceroute doc |

**Estimate:** 2–3 sprints.

---

## Phase 14 — Pro GUI (`beta`, P1)

**Goal:** faster route change reading; target organization.

| ID | Task | Files | DoD |
|----|------|-------|-----|
| **P14-010** | [ ] Route diff panel: hop-by-hop «was → now», Δ RTT | `RouteDiffPresenter.java`, `GraphCanvas` | Manual smoke route change |
| **P14-020** | [ ] Target tags: `tags: [dc, vpn, customer-x]` in YAML | `HostEntry`, `ProfilesConfig` | Filter in ListView |
| **P14-021** | [ ] UI: tag filter + quick filter chips | `HostListPresenter` | Saved in YAML |
| **P14-030** | [ ] ASN + short descr in hop label (offline cache) | `geoip/AsnLookup.java` or lazy whois | Configurable; 2s timeout |
| **P14-031** | [ ] rDNS in label (async, non-blocking UI) | `DnsResolver.java`, `GraphCanvas` | Cache TTL 5 min |
| **P14-040** | [ ] Expert ping presets: MTU probe, DF, DSCP, burst | `PingExpertDialog`, `ping_presets.yaml` | 4 preset buttons |
| **P14-050** | [ ] USER_GUIDE § pro workflow | `docs/USER_GUIDE.md` | NOC scenario |

**Estimate:** 2 sprints.

---

## Phase 15 — Team integrations (P1–P2)

**Goal:** Grafana, runbook API, scheduled reports.

| ID | Task | Files | DoD |
|----|------|-------|-----|
| **P15-001** | [ ] ADR: observability boundaries (metrics vs TS backend) | `docs/ADR_OBSERVABILITY.md` | Prometheus read; Influx write optional |
| **P15-010** | [ ] Prometheus `/metrics` endpoint (daemon mode) | `observability/PrometheusExporter.java` | `pingui_rtt_ms`, `pingui_route_change_total` |
| **P15-011** | [ ] CLI `--metrics-port 9090` | `DaemonRunner` | localhost bind default |
| **P15-020** | [ ] Java parity: InfluxDB/Timescale writer (Python B-05) | `persistence/timeseries/` | Config parity with Python |
| **P15-030** | [ ] Scheduled CSV/HTML export (cron-friendly CLI) | `export/ScheduledExport.java` | `--export-schedule daily` one-shot |
| **P15-040** | [ ] REST read-only API: `GET /hosts`, `GET /routes/{host}` | `api/ReadOnlyApiServer.java` | OpenAPI stub; auth out of scope v1 |
| **P15-041** | [ ] DEPLOYMENT § reverse proxy + TLS | `docs/DEPLOYMENT.md` | nginx example |
| **P15-050** | [ ] LIVING_SPEC + API contract tests | `docs/LIVING_SPEC.md` | Mock HTTP tests |

**Estimate:** 2–3 sprints. **Out of scope:** full Zabbix/NMS replacement.

**Related:** phase 16 — unified network metrics collection and LOG-server export; Prometheus (P15-010) and Influx (P15-020) are remote sinks of this layer.

---

## Phase 16 — Telemetry: network metrics + LOG-server (`beta` → `main`, P0–P1)

**Goal:** collect RTT/loss/jitter/route-change with **local storage** and/or **LOG-server export**; single contract for GUI, daemon, and Python/Java.

**Prerequisites:** P11 (SQLite), P12 (daemon) recommended; P10 (alerts) — separate channel, events also emitted to telemetry.

**Principle:** *events* → LOG-server (syslog/GELF); *time-series samples* → SQLite / Influx / Prometheus; do not send per-second hop RTT to syslog.

### 16.0 — Design gate

| ID | Task | Files | DoD |
|----|------|-------|-----|
| **P16-001** | [ ] ADR: telemetry (events vs samples vs aggregates, sinks) | `docs/ADR_TELEMETRY.md` | Bus → local/remote diagram; boundaries with P10/P15 |
| **P16-002** | [ ] SPIKE: LOG-server protocol comparison (syslog, GELF, Loki) | `docs/SPIKE_LOG_SINKS.md` | v1 recommendation: syslog TCP + GELF |

### 16.1 — Model and bus (P0)

| ID | Task | Files | DoD |
|----|------|-------|-----|
| **P16-010** | [ ] `MetricSample` + `TelemetryEvent` (host, hop, labels) | `telemetry/MetricSample.java`, `models.py` | Unit test serialize |
| **P16-011** | [ ] `TelemetrySink` interface + `SinkRegistry` | `telemetry/TelemetrySink.java` | register/unregister; no-op default |
| **P16-012** | [ ] `TelemetryBus` — async queue, batch flush, backpressure | `telemetry/TelemetryBus.java` | Queue max size; drop policy documented |
| **P16-013** | [ ] Wire MonitorService → bus (RTT, loss, route_change, probe_error) | `MonitorService`, `worker.py` | Does not block poll loop |
| **P16-014** | [ ] Metrics: `trace_duration_ms`, `target_reachable` | `telemetry/MetricNames.java` | Labels: profile, probe_mode, edition |

### 16.2 — Local storage (P0)

| ID | Task | Files | DoD |
|----|------|-------|-----|
| **P16-020** | [ ] `SqliteTelemetrySink` — samples + events (P11 schema extension) | `persistence/SqliteTelemetrySink.java` | Migration v2; unit insert/query |
| **P16-021** | [ ] `JsonlRotateSink` — JSONL with size/day rotation | `telemetry/JsonlRotateSink.java` | `telemetry.jsonl.%Y-%m-%d` |
| **P16-022** | [ ] `retention_days` — purge old samples/events | `TelemetryRetentionJob.java` | CLI `--telemetry-retention 30` |
| **P16-023** | [ ] Export from local store: `--telemetry-dump` (CSV/JSON) | `export/TelemetryDump.java` | Cron-friendly one-shot |

### 16.3 — LOG-server export (P1)

| ID | Task | Files | DoD |
|----|------|-------|-----|
| **P16-030** | [ ] `SyslogSink` — RFC 5424 TCP/TLS, structured data | `telemetry/SyslogSink.java` | Contract test with mock server |
| **P16-031** | [ ] `GelfSink` — Graylog UDP/TCP | `telemetry/GelfSink.java` | route_change + probe_error events |
| **P16-032** | [ ] `LokiPushSink` — HTTP push (optional P2) | `telemetry/LokiPushSink.java` | labels: job=pingui, site |
| **P16-033** | [ ] `events_only` mode for LOG sinks (no high-freq RTT) | `SinkConfig.java` | Default true for syslog |
| **P16-034** | [ ] 5m aggregates (avg/max RTT per hop) → LOG optional | `AggregateTelemetryJob.java` | YAML `log_aggregates: true` |

### 16.4 — Configuration (P0)

| ID | Task | Files | DoD |
|----|------|-------|-----|
| **P16-040** | [ ] YAML section `telemetry:` (local + remote sinks) | `ProfilesConfig`, `config.py` | Example in `hosts.example.yaml` |
| **P16-041** | [ ] CLI: `--telemetry-syslog HOST:PORT`, `--telemetry-jsonl DIR` | `PinguiApplication`, `__main__.py` | Override profile |
| **P16-042** | [ ] Secrets (URL, token) — no logging; mask in debug | `TelemetryConfig.java` | Unit test redaction |
| **P16-043** | [ ] Windows preset: `events_only` + no high-freq jsonl | `config/hosts.windows.example.yaml` | CHECKLIST |

### 16.5 — Integration with P10/P15 (P1)

| ID | Task | Files | DoD |
|----|------|-------|-----|
| **P16-050** | [ ] P10 webhook — implement as `TelemetrySink` (no duplicate code) | `WebhookAlertDispatcher` → `telemetry/` | Single emit path |
| **P16-051** | [ ] P15 Prometheus — `PrometheusTelemetrySink` | `observability/PrometheusExporter.java` | Scrape from daemon |
| **P16-052** | [ ] Python B-05 Influx — `InfluxTelemetrySink` wrapper | `persistence/timeseries/influx.py` | Config parity |

### 16.6 — Docs / QA (P0)

| ID | Task | Files | DoD |
|----|------|-------|-----|
| **P16-060** | [ ] CONFIGURATION § telemetry | `docs/CONFIGURATION.md`, `docs/en/CONFIGURATION.md` | Full field table |
| **P16-061** | [ ] DEPLOYMENT § LOG-server, rsyslog, Graylog, retention | `docs/DEPLOYMENT.md` | nginx/TLS optional |
| **P16-070** | [ ] LIVING_SPEC: telemetry bus + sinks | `docs/LIVING_SPEC.md` | Module → test matrix |
| **P16-071** | [ ] CHECKLIST § telemetry smoke | `docs/CHECKLIST.md` | local sqlite + syslog event |
| **P16-072** | [ ] Contract tests: mock syslog/gelf | `src/test/java/.../SyslogSinkTest.java` | CI green |

**Estimate:** 2–3 sprints. **Out of scope v1:** OpenTelemetry OTLP export (P2 ticket P16-080).

| ID | Task | Priority |
|----|------|----------|
| **P16-080** | [ ] OTLP logs/metrics export | P2 |

---

## Out of scope (not planned)

| ID | Idea | Why not |
|----|------|---------|
| **X-001** | BGP looking glass | Different product class |
| **X-002** | >10 targets without worker redesign | Conscious MVP limit |
| **X-003** | Full NMS/alert manager | PINGUI is route-focused utility |

---

## Recommended order (2026 Q3–Q4)

```mermaid
flowchart TD
  V6070[V6-070 IPv6 QA gate] --> PYS1[PY-S1 launcher docs]
  PYS1 --> PYS2[PY-S2 MonitorLoop daemon]
  PYS1 --> P10010[P10 alerts]
  P10010 --> PYS3[PY-S3 Python alerts]
  P10010 --> P11010[P11 SQLite Java]
  P11010 --> PYS2
  PYS2 --> P12010[P12 daemon Java]
  P12010 --> P13010[P13 MTR / smart interval]
  P13010 --> P14010[P14 route diff / tags]
  P14010 --> P15010[P15 Prometheus / API]
  P15010 --> P16010[P16 telemetry / LOG-server]
  V6070 --> PY50[PY-S4 IPv6 Python opt]
```

| Priority | Sprint (prop.) | Phase | Tasks |
|----------|----------------|-------|-------|
| **P0** | PY-S1 | PY.0 + PY.5 | PY-010…PY-016, PY-060…PY-064 |
| **P1** | PY-S2 | PY.1 + PY.2 | PY-020…PY-034 |
| **P0** | PY-S3 | PY.3 | PY-040…PY-045 |
| **P2** | PY-S4 (opt.) | PY.4 | PY-050…PY-052 |
| **P0** | Pro-S1 | 9 (close) | V6-035…V6-037, V6-060…V6-074 |
| **P0** | Pro-S2 | 10 | P10-001…P10-050 (+ PY-S3 Python) |
| **P0** | Pro-S3–S4 | 11 | P11-001…P11-050 |
| **P1** | Pro-S5 | 12 | P12-001…P12-040 |
| **P1** | Pro-S6–S7 | 13 | P13-001…P13-050 |
| **P1** | Pro-S8 | 14 | P14-010…P14-050 |
| **P1–P2** | Pro-S9–S10 | 15 | P15-001…P15-050 |
| **P0–P1** | Pro-S11–S12 | 16 | P16-001…P16-072 |
| **P2** | opt. | 9.4 | V6-040…V6-045 raw ICMP v6 |
| **P2** | opt. | 16.7 | P16-080 OTLP |

---

## Phase 8 — Production polish (`beta`, P2)

| ID | Task | DoD |
|----|------|-----|
| **B-060** | [x] Version in About with CI build number / git sha | `AppInfo`, Gradle `generateBuildProperties` | About shows `versionDetail()` |
| **B-061** | [x] jpackage smoke in CHECKLIST after each release | `docs/CHECKLIST.md` § Release |
| **B-062** | [x] Weekly doc smoke (README ↔ actual CLI) | `docs/CHECKLIST.md` § Docs smoke |
| **B-063** | [x] Import graph / cycle detection (Gradle plugin or script) | `java/scripts/check-layer-deps.sh`, `layerCheck` | `./gradlew check` includes layerCheck |

---

## Recommended execution order

```mermaid
flowchart LR
  M001[M-001 hygiene] --> M010[M-010 CLI Optional]
  M010 --> M012[M-012 merge profile]
  M002[M-002 IPv4 docs]
  M020[M-020 Spotless] --> M021[M-021 apply]
  M021 --> B001[B-001 JUnit]
  B001 --> B004[B-004 parse tests]
  B004 --> B020[B-020 CI]
  B020 --> B030[B-030 split UI]
  B004 --> B040[B-040 command builders]
```

**Sprint 1 (`main`):** M-001, M-002, M-010…M-014  
**Sprint 2 (`main`→`beta` merge):** M-020…M-023, B-001…B-010  
**Sprint 3 (`beta`):** B-020…B-023, B-030…B-035  
**Backlog:** M/B roadmap closed; B-064 coverage ongoing; **IPv6 — Phase 9 (V6-*)**; **Python NOC — Phase PY (PY-*)**; **Pro — Phases 10–16 (P10–P16)**.

Full plan: this file. Short phase index: [../../ROADMAP.md](../../ROADMAP.md).

---

## Anti-stub checklist (each MR)

- [ ] No `pass` / `return null` / `Mock` without TODO with ticket ID  
- [ ] Changed module — test or updated row in `LIVING_SPEC.md`  
- [ ] `./gradlew check` (or `compileJava` on `main`) green in venv/CI  
- [ ] README / `java/README` / CHANGELOG — if behavior changed  
- [ ] Review: recursion, unused fields, stubs  

---

## Branch relationship

| After task | Action |
|------------|--------|
| `main` only | cherry-pick or merge `main` → `beta` |
| `beta` only | periodically merge `beta` Java layer → `main` (without Python/tests in `main` tree) |

**Sprint 10 (2025-06-26):** `origin/main` merged into `beta`; Python tree preserved; `./gradlew check` + JaCoCo ≥80% + Python pytest green.

**Sprint 11 (2025-06-26):** Java test suite + JaCoCo gate from `beta` → `main`; Python tree not added to `main`.

**Sprint 12 (2025-06-26):** `origin/main` merged into `beta` — branch parity after Sprint 11.

**Sprint 13 (2025-06-26):** B-064 — extended `ProfilesConfigTest`/`HopStatsTest`; removed 6 JaCoCo exclusions (RoutePoller, HopStats, HostEntry, ProfileDocument, HostTargetStats, GeoCountry lookup).

**Sprint 13b (2025-06-30):** B-064 — `MonitorServiceTest`/`SessionStoreTest`/`IcmpPacketTest`; removed exclusion `IcmpPacket`.

**Sprint 14 (2025-06-26):** `origin/main` (B-064 push) merged into `beta`; `origin/beta` synchronized.

**Sprint 13b (2025-06-30):** B-064 — `MonitorServiceTest`/`SessionStoreTest`/`IcmpPacketTest`; removed exclusion `IcmpPacket`.

**Sprint 15 (2025-06-30):** B-064b from `beta` → `main` (cherry-pick).

**Sprint 16 (2025-06-30):** `MonitorService` — wire `PingOnlyResolver` in `pollHostOnce` (live ping-only from `SessionStore`).

**Sprint 17 (2025-06-30):** B-064c — extended config/geoip unit tests (`HostsConfig`, `ProfileDocument`, `GeoCountry`).

**Sprint 18 (2025-06-26):** B-064d — `GeoCountryTest`/`ProfilesConfigTest` (longest-prefix, LAN edge cases, host entry flags); JaCoCo bundle ≥80%.

**Sprint 19 (2025-06-26):** B-064e — `HostEntryTest`; GeoIP YAML validation + ProfilesConfig type/save guards.

**Sprint 20 (2025-06-26):** B-064f — `PingExpertValidator` + `ExpertPingEnricher` stub tests; −exclusion `ExpertPingEnricher`.

**IPv6-S1 (2026-06-26):** V6-001…V6-015 — `HostAddressParser`, RFC 5952, mixed v4/v6 YAML.

**Roadmap Pro (2026-07-09):** added phases 10–15 (alerts, persistence, daemon, MTR, pro GUI, integrations) — official NOC/SRE plan.

**Roadmap Telemetry (2026-07-09):** phase 16 — network metrics collection, local storage (SQLite/JSONL), LOG-server export (syslog/GELF/Loki).

**Roadmap Python (2026-07-09):** phase PY — atomic tickets PY-010…PY-064: launcher, docs, MonitorLoop, daemon, alerts, IPv6, CI/packaging (`beta` only).

**PY-S4 (2026-07-09):** PY-050…PY-052 — Python IPv6 dual-stack on `beta`.

**IPv6-S3 (2026-07-09):** V6-035…V6-037 — `GeoCountry` IPv6 longest-prefix + `prefixes_v6` YAML.

**IPv6-S4 (2026-07-09):** V6-060…V6-063 — dual-stack Help/About, IPv6 graph labels, host input validation.

**IPv6-S4 QA (2026-07-09):** V6-070…V6-074 — CHECKLIST smoke, v4 fixture regression test, JaCoCo notes, IPv6 beta release note.

**IPv6 expert UI (2026-07-09):** V6-053 — `-F` flow label gated by IPv6 target / `-6` AF.

**IPv6 deployment docs (2026-07-09):** V6-045 — `cap_net_raw` matrix for dual-stack (v4 raw vs v6 process trace).

**Raw ICMPv6 Linux (2026-07-09):** V6-040…V6-043 — JNA `sockaddr_in6`, `IcmpV6Packet`, dual transport, v6 hop-limit trace (`probe: raw`).

**IPv6 release 0.2.0 (2026-07-09):** semver bump, CHANGELOG `[0.2.0]`, SPIKE/CHECKLIST/DEPLOYMENT parity; phase 9 code-complete on `beta`.

**Alerts ADR (2026-07-09):** P10-001 — `ADR_ALERTS.md` (webhook + desktop v1, payload, rate limit); Java P10-010+.

Update this file when closing a task: `[x] M-001` + date in CHANGELOG.
