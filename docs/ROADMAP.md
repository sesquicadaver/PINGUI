# ROADMAP — PINGUI Java (`main` / `beta`)

План виправлень після аудиту `main` (MVP desktop utility, production readiness: низька–середня).

**Легенда**

| Поле | Значення |
|------|----------|
| **Гілка** | `main` — Java + docs; `beta` — + Python, тести, CI |
| **Пріоритет** | P0 критично · P1 важливо · P2 бажано |
| **DoD** | Definition of Done — умова закриття задачі |

Задачі **атомарні**: одна задача ≈ один MR/коміт, ≤ 1 день роботи.

---

## Фаза 0 — Швидкі виправлення (`main`, P0)

| ID | Задача | Файли | DoD |
|----|--------|-------|-----|
| **M-001** | [x] Видалити дубльований `import java.io.IOException` | `probe/RawIcmpRouteProbe.java` | `./gradlew compileJava` OK; один import |
| **M-002** | [x] Задокументувати **IPv4-only** (validator + raw ICMP) | `README.md`, `docs/JAVA.md`, `docs/DEPLOYMENT.md`, `AppMenuDialogs` help | Явна примітка «IPv6 не підтримується»; приклади лише IPv4/hostname |
| **M-003** | [x] CHANGELOG: запис про roadmap і IPv4-only | `CHANGELOG.md` | Секція `[Unreleased]` оновлена |

---

## Фаза 1 — CLI override (`main`, P0)

**Проблема:** `applyCliOverridesToActiveProfile()` завжди підставляє `AppOptions.defaults()`, затираючи YAML при старті без CLI.

| ID | Задача | Файли | DoD |
|----|--------|-------|-----|
| **M-010** | [x] Ввести `CliOverrides` (record з `Optional` полями: interval, maxHops, timeout, probe) | `CliProfileOverrides.java`, `AppOptions.java`, `PinguiApplication.java` | Парсер CLI заповнює `Optional.empty()` для непереданих прапорців |
| **M-011** | [x] `parseOptions`: розрізняти «не передано» vs «default» | `PinguiApplication.java` | `--interval 2` → override; без `--interval` → empty |
| **M-012** | [x] `applyCliOverridesToActiveProfile`: merge лише present-полів | `MainController.java` | Старт без CLI зберігає YAML `interval`/`max_hops`/`timeout`/`probe` |
| **M-013** | [x] Документувати поведінку CLI vs YAML | `java/README.md`, `docs/JAVA.md` | Таблиця «CLI перезаписує поле профілю лише якщо передано» |
| **M-014** | [x] Ручний smoke: профіль `interval: 30` + `./pingui-java.sh` | — | Unit-тест M-014 + CHECKLIST § CLI interval |

---

## Фаза 2 — Hygiene / static checks (`main` → `beta`, P1)

| ID | Задача | Файли | DoD |
|----|--------|-------|-----|
| **M-020** | [x] Підключити Spotless (Google Java Format або Palantir) | `java/build.gradle.kts`, `settings.gradle.kts` | `./gradlew spotlessCheck` проходить |
| **M-021** | [x] `./gradlew spotlessApply` + форматування існуючих `.java` | `java/src/main/**` | `spotlessCheck` green; diff лише formatting |
| **M-022** | [x] Gradle task `check` = `compileJava` + `spotlessCheck` | `java/build.gradle.kts` | `./gradlew check` на `main` |
| **M-023** | [x] Checkstyle — мінімальний ruleset | `java/build.gradle.kts`, `config/checkstyle/checkstyle.xml` | RedundantImport, UnusedImports; `./gradlew check` |

---

## Фаза 3 — Тестовий шар (`beta`, P0)

| ID | Задача | Файли | DoD |
|----|--------|-------|-----|
| **B-001** | [x] JUnit 5 + test deps у `java/build.gradle.kts` | `build.gradle.kts` | `./gradlew test` запускається |
| **B-002** | [x] Фікстури: зразки виводу `traceroute` (Linux/macOS) | `src/test/resources/trace/unix_*.txt` | ≥ 3 файли (ok, timeout, hostname) |
| **B-003** | [x] Фікстури: зразки `tracert` (Windows) | `src/test/resources/trace/win_*.txt` | ≥ 3 файли (`<1 ms`, `host [IP]`, timeout) |
| **B-004** | [x] Unit: `ProcessRouteProbe.parseUnix` | `ProcessRouteProbeTest.java` | Hop count, IP, RTT для кожної фікстури |
| **B-005** | [x] Unit: `ProcessRouteProbe.parseWindows` | той самий test class | Парсинг Windows-рядків без `No hops parsed` |
| **B-006** | [x] Unit: `windowsTracertWaitMs` / `-w` ≥ 4000 | `ProcessRouteProbeTest.java` | Assert на мінімальний wait |
| **B-007** | [x] Unit: `HostsConfig.validateSessionHost` | `HostsConfigTest.java` | duplicate, max 10, invalid chars, IPv4 ok |
| **B-008** | [x] Unit: `ProfilesConfig` v2 + legacy migration | `ProfilesConfigTest.java` | load/save round-trip; `active_profile` |
| **B-009** | [x] Unit: `PingExpertValidator` | `PingExpertValidatorTest.java` | invalid flags → `ConfigError` |
| **B-010** | [x] Unit: CLI override merge | `PinguiApplicationTest.java` | optional fields не затирають profile |

---

## Фаза 4 — CI (`beta`, P0)

| ID | Задача | Файли | DoD |
|----|--------|-------|-----|
| **B-020** | [x] GitHub Actions: JDK 21, venv не потрібен для Java job | `.github/workflows/java.yml` | `compileJava` + `test` + `spotlessCheck` на push/PR |
| **B-021** | [x] CI matrix: `ubuntu-latest` (обовʼязково); Windows optional | workflow | Linux green; Windows job `continue-on-error` |
| **B-022** | [x] Badge / статус у README | `README.md` | Badge CI видимий |
| **B-023** | [x] Living spec: матриця «ТЗ → модуль → тест» | `docs/LIVING_SPEC.md` | Рядки для probe, config, CLI override, CI |

---

## Фаза 5 — Розділення UI (`beta`, P1)

**Мета:** `MainController` ≤ ~300 рядків; SRP.

| ID | Задача | Виділити з | DoD |
|----|--------|------------|-----|
| **B-030** | [x] `ProfileUiActions` — new/delete/select profile, combo sync | `MainController` | Profile CRUD винесено; controller делегує |
| **B-031** | [x] `HostListPresenter` — add/edit/remove, toggles, list height | `MainController` | Host ops + `HostListCell` callbacks |
| **B-032** | [x] `MonitorLifecycle` — create/close monitor, reload profile | `MainController` | `reloadActiveProfile` + `createMonitor` |
| **B-033** | [x] `ViewModeController` — Simple/Extended, `fitWindowToContent` | `MainController` | Easter egg лишається або → `HostViewRules` helper |
| **B-034** | [x] `RouteGraphPresenter` — `redrawRouteIfExtended`, graph panel | `MainController` | Extended mode graph + status label |
| **B-035** | [x] Smoke GUI: профіль, host, save, F1/About | `docs/CHECKLIST.md` § GUI smoke | Checklist B-035; ручний прогін на Linux |

---

## Фаза 6 — Probe / OS strategy (`beta`, P1)

| ID | Задача | Файли | DoD |
|----|--------|-------|-----|
| **B-040** | [x] Інтерфейс `TraceCommandBuilder` (OS → argv[]) | `probe/` | Linux/macOS/Windows реалізації |
| **B-041** | [x] Перенести команди з `ProcessRouteProbe` | `LinuxTracerouteCommand`, `MacTracerouteCommand`, `WindowsTracertCommand` | Паритет з поточною поведінкою; тести B-004/B-005 green |
| **B-042** | [x] Парсер Unix: окремий `UnixTraceOutputParser` | `probe/` | Unit-тести на фікстурах |
| **B-043** | [x] Парсер Windows: `WindowsTraceOutputParser` | `probe/` | Локалізовані timeout-рядки в фікстурах |
| **B-044** | [x] Документувати обмеження парсера (IPv6 trace output, ASN) | `docs/JAVA.md` | Known limitations |

---

## Фаза 7 — IPv6 (окремий scope, P2)

| ID | Задача | DoD |
|----|--------|-----|
| **B-050** | [x] SPIKE: IPv6 trace + ping — обсяг робіт | `docs/SPIKE_IPV6.md` | Рішення: wontfix |
| **B-051** | — (cancelled) `HostsConfig` — IPv6 literal | — | Out of scope per B-050 |
| **B-052** | — (cancelled) Raw ICMP IPv6 | — | Out of scope per B-050 |
| **B-053** | [x] Закрити B-050 статусом «IPv4-only by design» | `HostsConfig`, `SPIKE_IPV6.md` | Явна помилка для IPv6 literal |

---

## Фаза 8 — Production polish (`beta`, P2)

| ID | Задача | DoD |
|----|--------|-----|
| **B-060** | [x] Версія в About з CI build number / git sha | `AppInfo`, Gradle `generateBuildProperties` | About показує `versionDetail()` |
| **B-061** | [x] jpackage smoke у CHECKLIST після кожного release | `docs/CHECKLIST.md` § Release |
| **B-062** | [x] Weekly doc smoke (README ↔ фактичний CLI) | `docs/CHECKLIST.md` § Docs smoke |
| **B-063** | [x] Import graph / cycle detection (Gradle plugin або script) | `java/scripts/check-layer-deps.sh`, `layerCheck` | `./gradlew check` включає layerCheck |

---

## Рекомендований порядок виконання

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
**Backlog:** усі заплановані M/B задачі закриті; подальші зміни — поза цим ROADMAP (нові ticket / release).

---

## Anti-stub checklist (кожен MR)

- [ ] Немає `pass` / `return null` / `Mock` без TODO з ticket ID  
- [ ] Змінений модуль — тест або оновлений рядок у `LIVING_SPEC.md`  
- [ ] `./gradlew check` (або `compileJava` на `main`) green у venv/CI  
- [ ] README / `java/README` / CHANGELOG — якщо змінилась поведінка  
- [ ] Ревʼю: рекурсія, невикористані поля, заглушки  

---

## Звʼязок з гілками

| Після задачі | Дія |
|--------------|-----|
| `main` only | cherry-pick або merge `main` → `beta` |
| `beta` only | періодично merge `beta` Java-шар → `main` (без Python/tests у tree `main`) |

**Sprint 11 (2025-06-26):** Java test suite + JaCoCo gate з `beta` → `main`; Python-дерево на `main` не додається.

**Sprint 13 (2025-06-26):** B-064 — розширено `ProfilesConfigTest`/`HopStatsTest`; прибрано 6 JaCoCo exclusions.

**Sprint 13b (2025-06-30):** B-064 — `MonitorServiceTest`/`SessionStoreTest`/`IcmpPacketTest`; прибрано exclusion `IcmpPacket`.

**Sprint 15 (2025-06-30):** B-064b з `beta` → `main` (cherry-pick).

**Sprint 16 (2025-06-30):** `MonitorService` — wire `PingOnlyResolver` у `pollHostOnce` (live ping-only з `SessionStore`).

**Sprint 17 (2025-06-30):** B-064c — розширено config/geoip unit-тести (`HostsConfig`, `ProfileDocument`, `GeoCountry`).

**Sprint 18 (2025-06-26):** B-064d — `GeoCountryTest`/`ProfilesConfigTest` (longest-prefix, LAN edge cases, host entry flags); JaCoCo bundle ≥80%.

**Sprint 19 (2025-06-26):** B-064e — `HostEntryTest`; GeoIP YAML validation + ProfilesConfig type/save guards.

**Sprint 20 (2025-06-26):** B-064f — `PingExpertValidator` + `ExpertPingEnricher` stub tests; −exclusion `ExpertPingEnricher`.

Оновлюй цей файл при закритті задачі: `[x] M-001` + дата в CHANGELOG.
