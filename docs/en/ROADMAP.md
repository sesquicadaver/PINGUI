> **Language:** English · [Українська](../ROADMAP.md)

# ROADMAP — PINGUI Java (`main` / `beta`)

Remediation plan after `main` audit (MVP desktop utility, production readiness: low–medium).

**Legend**

| Field | Values |
|-------|--------|
| **Branch** | `main` — Java + docs; `beta` — + Python, tests, CI |
| **Priority** | P0 critical · P1 important · P2 nice-to-have |
| **DoD** | Definition of Done — task closure criteria |

Tasks are **atomic**: one task ≈ one MR/commit, ≤ 1 day of work.

---

## Phase 0 — Quick fixes (`main`, P0)

| ID | Task | Files | DoD |
|----|------|-------|-----|
| **M-001** | [x] Remove duplicate `import java.io.IOException` | `probe/RawIcmpRouteProbe.java` | `./gradlew compileJava` OK; single import |
| **M-002** | [x] Document **IPv4-only** (validator + raw ICMP) | `README.md`, `docs/JAVA.md`, `docs/DEPLOYMENT.md`, `AppMenuDialogs` help | Explicit note «IPv6 not supported»; examples IPv4/hostname only |
| **M-003** | [x] CHANGELOG: entry for roadmap and IPv4-only | `CHANGELOG.md` | `[Unreleased]` section updated |

---

## Phase 1 — CLI override (`main`, P0)

**Problem:** `applyCliOverridesToActiveProfile()` always applies `AppOptions.defaults()`, overwriting YAML on startup without CLI.

| ID | Task | Files | DoD |
|----|------|-------|-----|
| **M-010** | [x] Introduce `CliOverrides` (record with `Optional` fields: interval, maxHops, timeout, probe) | `CliProfileOverrides.java`, `AppOptions.java`, `PinguiApplication.java` | CLI parser sets `Optional.empty()` for flags not passed |
| **M-011** | [x] `parseOptions`: distinguish «not passed» vs «default» | `PinguiApplication.java` | `--interval 2` → override; without `--interval` → empty |
| **M-012** | [x] `applyCliOverridesToActiveProfile`: merge present fields only | `MainController.java` | Startup without CLI preserves YAML `interval`/`max_hops`/`timeout`/`probe` |
| **M-013** | [x] Document CLI vs YAML behavior | `java/README.md`, `docs/JAVA.md` | Table «CLI overwrites profile field only if passed» |
| **M-014** | [x] Manual smoke: profile `interval: 30` + `./pingui-java.sh` | — | Unit test M-014 + CHECKLIST § CLI interval |

---

## Phase 2 — Hygiene / static checks (`main` → `beta`, P1)

| ID | Task | Files | DoD |
|----|------|-------|-----|
| **M-020** | [x] Enable Spotless (Google Java Format or Palantir) | `java/build.gradle.kts`, `settings.gradle.kts` | `./gradlew spotlessCheck` passes |
| **M-021** | [x] `./gradlew spotlessApply` + format existing `.java` | `java/src/main/**` | `spotlessCheck` green; diff formatting only |
| **M-022** | [x] Gradle task `check` = `compileJava` + `spotlessCheck` | `java/build.gradle.kts` | `./gradlew check` on `main` |
| **M-023** | [x] Checkstyle — minimal ruleset | `java/build.gradle.kts`, `config/checkstyle/checkstyle.xml` | RedundantImport, UnusedImports; `./gradlew check` |

---

## Phase 3 — Test layer (`beta`, P0)

| ID | Task | Files | DoD |
|----|------|-------|-----|
| **B-001** | [x] JUnit 5 + test deps in `java/build.gradle.kts` | `build.gradle.kts` | `./gradlew test` runs |
| **B-002** | [x] Fixtures: sample `traceroute` output (Linux/macOS) | `src/test/resources/trace/unix_*.txt` | ≥ 3 files (ok, timeout, hostname) |
| **B-003** | [x] Fixtures: sample `tracert` (Windows) | `src/test/resources/trace/win_*.txt` | ≥ 3 files (`<1 ms`, `host [IP]`, timeout) |
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
| **B-020** | [x] GitHub Actions: JDK 21, venv not required for Java job | `.github/workflows/java.yml` | `compileJava` + `test` + `spotlessCheck` on push/PR |
| **B-021** | [x] CI matrix: `ubuntu-latest` (required); Windows optional | workflow | Linux green; Windows job `continue-on-error` |
| **B-022** | [x] Badge / status in README | `README.md` | CI badge visible |
| **B-023** | [x] Living spec: «spec → module → test» matrix | `docs/LIVING_SPEC.md` | Rows for probe, config, CLI override, CI |

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

**Out of phase 9 scope (separate tickets):** Python layer on `beta`; full Windows expert-ping parity (see backlog after V6-059).

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
| **V6-035** | [ ] `GeoCountry`: `Inet6Address` — loopback/link-local/ULA → `LAN` | `GeoCountry.java` | `GeoCountryTest` |
| **V6-036** | [ ] `GeoCountry`: longest-prefix for IPv6 CIDR | `GeoCountry.java`, `geoip_hints.yaml` | Test: `2001:db8::/32` |
| **V6-037** | [ ] YAML schema: optional `prefixes_v6` (or unified map) | `GeoCountry.java`, docs | Backward compat v4 hints |

### 9.4 — Raw ICMP v6 (Linux only, P2)

| ID | Task | Files | DoD |
|----|------|-------|-----|
| **V6-040** | [ ] JNA: `AF_INET6`, `sockaddr_in6` | `LinuxSocketConstants`, `LinuxCLibrary` | Compile + struct layout test |
| **V6-041** | [ ] ICMPv6 echo request/reply parse | `IcmpPacket.java` or `IcmpV6Packet.java` | Unit test without cap (build packet) |
| **V6-042** | [ ] `LinuxJnaIcmpTransport` dual: v4/v6 socket | `LinuxJnaIcmpTransport.java` | Integration test optional; mock-friendly unit |
| **V6-043** | [ ] `RawIcmpRouteProbe`: hop limit for v6 | `RawIcmpRouteProbe.java` | v6 target → trace hops |
| **V6-044** | [x] `RouteProbeFactory`: v6 literal → process fallback on AUTO+raw | `DualStackRouteProbe.java` | Test: v6 → process, v4 → raw |
| **V6-045** | [ ] DEPLOYMENT: cap note for ICMPv6 | `docs/DEPLOYMENT.md` | Linux-only raw v6 documented |

### 9.5 — Expert ping v6 (P1)

| ID | Task | Files | DoD |
|----|------|-------|-----|
| **V6-050** | [x] Auto `-6` in `ProcessExpertPing.buildCommand` for v6 target | `ExpertPingArgs.java` | Test: v6 target → `-6` in argv |
| **V6-051** | [x] `ProcessHostPing`: expert args + auto v6 on Linux/macOS | `ProcessHostPing.java` | Test: args appended |
| **V6-052** | [x] Validator: `-4` + v6 target → `ConfigError` | `ExpertPingArgs.java` | Unit test |
| **V6-053** | [ ] `-F` flow label — only with v6 target (UI hint) | `PingExpertDialog.java` | Tooltip / disable when target v4 |

### 9.6 — UI / docs (P1)

| ID | Task | Files | DoD |
|----|------|-------|-----|
| **V6-060** | [ ] Help/About: dual-stack instead of «IPv4-only» | `AppMenuDialogs.java`, `README.md` | Text updated |
| **V6-061** | [ ] `GraphCanvas` / labels: bracket display for long v6 | `GraphCanvas.java`, `RouteGraphLayout` | Manual smoke note in CHECKLIST |
| **V6-062** | [ ] Input validation in Add Host dialog for v6 | `HostListPresenter` / dialog | Invalid v6 → log error |
| **V6-063** | [ ] CHANGELOG + ROADMAP `[x]` on subphase closure | `CHANGELOG.md` | Per-sprint notes |

### 9.7 — QA / release gate (P0)

| ID | Task | Files | DoD |
|----|------|-------|-----|
| **V6-070** | [ ] CHECKLIST § IPv6 smoke (Linux process trace) | `docs/CHECKLIST.md` | literal v6 + ping-only |
| **V6-071** | [ ] CHECKLIST § IPv6 smoke (Windows tracert -6) | `docs/CHECKLIST.md` | optional OS job |
| **V6-072** | [ ] Regression: all v4 fixtures remain green | CI | `./gradlew check` |
| **V6-073** | [ ] JaCoCo: new modules in bundle or documented exclusion | `build.gradle.kts` | Gate ≥80% |
| **V6-074** | [ ] Release note: «IPv6 beta» / feature flag if needed | `CHANGELOG.md` | Semver minor bump note |

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

**Estimate:** 3–5 sprints × 3–5 tasks; raw ICMP (V6-040…) can be deferred after process+GeoIP MVP.

| Sprint (suggested) | Tasks |
|--------------------|-------|
| IPv6-S1 | V6-001…V6-015 (config) |
| IPv6-S2 | V6-020…V6-030 (process trace) |
| IPv6-S3 | V6-035…V6-037, V6-050…V6-053 (GeoIP + expert) |
| IPv6-S4 | V6-060…V6-063, V6-070…V6-074 (UI + QA) |
| IPv6-S5 (opt.) | V6-040…V6-045 (raw ICMP v6 Linux) |

---

## Phase 8 — Production polish (`beta`, P2)

| ID | Task | DoD |
|----|------|-----|
| **B-060** | [x] Version in About from CI build number / git sha | `AppInfo`, Gradle `generateBuildProperties` | About shows `versionDetail()` |
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
**Backlog:** M/B roadmap closed; B-064 coverage ongoing; **IPv6 — Phase 9 (V6-*)**.

---

## Anti-stub checklist (every MR)

- [ ] No `pass` / `return null` / `Mock` without TODO with ticket ID  
- [ ] Changed module — test or updated row in [LIVING_SPEC.md](LIVING_SPEC.md)  
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

**Sprint 13 (2025-06-26):** B-064 — expanded `ProfilesConfigTest`/`HopStatsTest`; removed 6 JaCoCo exclusions (RoutePoller, HopStats, HostEntry, ProfileDocument, HostTargetStats, GeoCountry lookup).

**Sprint 13b (2025-06-30):** B-064 — `MonitorServiceTest`/`SessionStoreTest`/`IcmpPacketTest`; removed exclusion `IcmpPacket`.

**Sprint 14 (2025-06-26):** `origin/main` (B-064 push) merged into `beta`; `origin/beta` synchronized.

**Sprint 13b (2025-06-30):** B-064 — `MonitorServiceTest`/`SessionStoreTest`/`IcmpPacketTest`; removed exclusion `IcmpPacket`.

**Sprint 15 (2025-06-30):** B-064b from `beta` → `main` (cherry-pick).

**Sprint 16 (2025-06-30):** `MonitorService` — wire `PingOnlyResolver` in `pollHostOnce` (live ping-only from `SessionStore`).

**Sprint 17 (2025-06-30):** B-064c — expanded config/geoip unit tests (`HostsConfig`, `ProfileDocument`, `GeoCountry`).

**Sprint 18 (2025-06-26):** B-064d — `GeoCountryTest`/`ProfilesConfigTest` (longest-prefix, LAN edge cases, host entry flags); JaCoCo bundle ≥80%.

**Sprint 19 (2025-06-26):** B-064e — `HostEntryTest`; GeoIP YAML validation + ProfilesConfig type/save guards.

**Sprint 20 (2025-06-26):** B-064f — `PingExpertValidator` + `ExpertPingEnricher` stub tests; −exclusion `ExpertPingEnricher`.

**IPv6-S1 (2026-06-26):** V6-001…V6-015 — `HostAddressParser`, RFC 5952, mixed v4/v6 YAML.

Update this file when closing a task: `[x] M-001` + date in CHANGELOG.
