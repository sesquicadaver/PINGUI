> **Мова:** Українська · [English](en/JAVA.md)

# PINGUI Java — архітектура та розгортання

Крос-платформова реалізація в каталозі [`java/`](../java/).

## Мета

Дозволити моніторинг маршрутів **незалежно від ОС** без Python/PyQt6 і без Linux-only `cap_net_raw`.

## Стек

- Java 21 (LTS)
- JavaFX 21 (GUI)
- Gradle 8.10 (Kotlin DSL)
- Spotless + Palantir Java Format (`./gradlew spotlessCheck`)
- SnakeYAML (конфіг)
- JUnit 5 (unit-тести в `src/test/java`, `./gradlew test`)

## Обмеження

- **Raw ICMP** — Linux only (`AF_INET` / `AF_INET6`); `probe: auto` лишає IPv6 literal на subprocess trace (V6-044); `probe: raw` — raw v6 з `cap_net_raw`.
- **Hostname AAAA** — trace: резолв ОС; expert ping `-6`: AAAA через `HostAddressResolver` (V6-055).

## CLI vs YAML профіль

| Поле профілю | Джерело за замовч. | CLI override |
|--------------|-------------------|--------------|
| `interval` | YAML активного профілю | `--interval SEC` (лише якщо передано) |
| `max_hops` | YAML | `--max-hops N` |
| `timeout` | YAML | `--timeout SEC` |
| `probe` | YAML | `--probe MODE` |

Реалізація: `CliProfileOverrides` + `PinguiApplication.parseOptions()`; merge у `MainController.applyCliOverridesToActiveProfile()`.

## Probe-шар

Python використовує scapy + raw ICMP. Java підтримує два backend:

| Backend | Клас | ОС | Вимоги |
|---------|------|-----|--------|
| **process** (за замовч.) | `ProcessRouteProbe` | Linux, macOS, Windows | `traceroute` / `tracert` у PATH |
| **raw-icmp** (Linux) | `RawIcmpRouteProbe` | Linux | JNA + `CAP_NET_RAW`; v4 і v6 literal при `probe: raw` |
| **auto** | `RouteProbeFactory` | Linux + cap → raw для v4/hostname, process для v6 literal |

CLI: `--probe auto|process|raw` (default: `auto`).

### Режими poll (`probe_mode`, P13-001…050)

Ортогонально транспорту `probe:` — це **стратегія моніторингу**, не backend trace.

| `probe_mode` | Виклик | За один poll | Per-hop RTT/loss | Зміна маршруту |
|--------------|--------|--------------|------------------|----------------|
| **`trace`** | `RoutePoller.pollHostRoute` → `RouteProbe.trace()` | Повний trace усіх hop-ів | Так (expert/default ping) | Порівняння повного шляху |
| **`mtr`** | `RoutePoller.pollHostMtr` → `MtrProbe` | **Один hop** (TTL probe) | Так, інкрементально | DISCOVERING росте по prefix; burst лише на reroute |
| **`ping_only`** | `RoutePoller.pollHostPingOnly` | Ping до цілі | Лише ціль | Hop-ів немає |

YAML (профіль + override на хост):

```yaml
profiles:
  default:
    probe_mode: trace          # trace | mtr | ping_only
    interval: 30.0
    max_concurrent_traces: 10  # default = MAX_HOSTS; all enabled TRACE hosts may run in parallel
    hosts:
      - address: "8.8.8.8"
        probe_mode: ping_only  # optional override
```

Legacy `ping_only: true` на хості → `probe_mode: ping_only` (deprecated flag; збережено для backward compat).

Див. [ADR_PROBE_MODES.md](ADR_PROBE_MODES.md).

#### MTR vs повний trace — known limitations (P13-050)

| Тема | `trace` | `mtr` |
|------|---------|-------|
| Навантаження за цикл | Усі hop-и (subprocess або raw ICMP) | Один hop за виклик `MtrProbe.poll()` |
| Транспорт | Залежить від `probe:` (`auto`/`process`/`raw`) | `IcmpMtrHopProber` / platform ping — **не** зовнішній `mtr` subprocess |
| Discovery маршруту | Повний шлях одразу | Фаза **DISCOVERING**: hop 1→N поки не досягнуто цілі |
| Windows | Повільний `tracert` (3 probe/hop) | Легші per-hop ping; рекомендовано `ping_only` на Windows |
| `probe: raw` | Raw ICMP trace (Linux) | MTR **не** використовує `RawIcmpRouteProbe` |
| Expert ping | Після trace (chain/target) | Після MTR snapshot, якщо expert налаштовано |
| Паралелізм | Ліміт `max_concurrent_traces` | Без ліміту (легкі probe) |
| Інтервал (default) | `interval` профілю (30–300 с) | 10 с; `ping_only` — 1.5 с (`HostPollSchedule`) |
| Burst після reroute | Так (`BurstSchedulePolicy`) | Ні на prefix growth (лише справжній reroute) |

**Не є MTR:** парсинг виводу зовнішньої утиліти `mtr`/`mtr.exe` — поза scope (див. parser table нижче). In-process state machine — див. `MtrProbe`, `MtrProbeState`.

> **Продуктивність:** на **Windows** subprocess trace через `tracert` на порядки повільніший за Linux `traceroute` (`-q 1`). Стартовий пресет `config/hosts.windows.example.yaml` (`probe_mode: ping_only`, `interval: 60`). Див. [DEPLOYMENT.md](DEPLOYMENT.md#рекомендація-щодо-ос).

### ProcessRouteProbe (subprocess)

| ОС | Builder | Команда |
|----|---------|---------|
| Linux | `LinuxTracerouteCommand` | `traceroute -n -w SEC -m N -q 1 HOST` (GNU inetutils: без `-n`) |
| macOS | `MacTracerouteCommand` | те саме; бінарник `/usr/sbin/traceroute` якщо є |
| Windows | `WindowsTracertCommand` | `tracert -d -h N -w MS HOST` (3 probe/hop, MS ≥ 4000) — **повільно** |

Парсери: `UnixTraceOutputParser`, `WindowsTraceOutputParser`. Фабрика: `TraceCommandFactory`.

#### Обмеження парсера (known limitations)

| Область | Поведінка |
|---------|-----------|
| **IPv6 trace output** | Literal v6: `-6` у traceroute/tracert + парсери v6 (фікстури `unix_v6_*`, `win_v6_*`). Hostname AAAA — резолв ОС, не PINGUI |
| **ASN / IGP labels** | Не парсяться; hop IP береться з першого IPv4-токена або `[IP]` |
| **Unix hostname hops** | Token після номера hop зберігається як «IP» (може бути hostname) |
| **Windows локалізація** | Timeout: `timed out`, `timeout`, `перевищ…`; RTT: `ms` / `мс`, `<1 ms` → 0.5 |
| **GNU inetutils** | Flavor без `-n`; `-n` дає exit 64 — детекція через `traceroute --version` |
| **Mixed trace formats** | Лише класичний vertical traceroute/tracert; вивід зовнішнього **mtr** / JSON — поза scope (`probe_mode: mtr` ≠ subprocess mtr) |

### RawIcmpRouteProbe (JNA, Linux)

Інкрементальний TTL/hop limit 1..N через raw ICMP socket (IPv4 `IP_TTL`, IPv6 `IPV6_UNICAST_HOPS`).

Потрібно: `sudo setcap cap_net_raw+ep` на JDK binary або запуск від root. IPv6 literal у режимі `probe: raw`; у `probe: auto` — subprocess `traceroute -6`.

## Monitor-шар (P13-020…030)

`MonitorService` — scheduler tick **0.25 с** + пул probe-потоків:

1. `cycle()` — лише **due** хости (`HostPollSchedule`, per-host `lastPollAt`).
2. `dispatchDueHost()` — `TraceConcurrencyLimiter` для `probe_mode: trace`.
3. `pollHostOnce()` гілкується за `resolveProbeMode(host)`:
   - **trace** → `pollHostRoute`
   - **mtr** → `pollHostMtr`
   - **ping_only** → `pollHostPingOnly`
4. `BurstSchedulePolicy` — ×0.25 інтервал на 5 хв після reroute (не MTR prefix growth).
5. Expert/default ping enrich для trace/mtr; callbacks + persistence + alerts.

Store/history/change detection — `SessionStore`, `RouteHistory`, `RouteChangeDetector`.

## UI-шар

`MainController` (JavaFX):

- Меню **Про** / **Довідка** (F1) — `AppMenuDialogs`
- Вибір **профілю трасування** (ComboBox + новий/видалити); усі профілі в одному YAML
- Чекбокс **«Експерт»** → **Exten.** / **MTU** на рядку хоста → `PingExpertDialog` (каталог з `pingMan.txt`, без `-c/-w/-W/-i` тощо); 4 quick presets з `ping_presets.yaml` (MTU probe, DF, DSCP, Burst); **MTU wizard…** (`MtuDiscoveryDialog`); **Self-check** (`PresetSelfCheckUi`)
- Меню **Налаштування → Телеметрія…** — `TelemetrySettingsDialog` + bus via `TelemetryAttachment`
- `ListView<HostItem>` + CheckBox у комірці
- **GraphCanvas** — вертикальний граф, inactive/active колонки
- Log `TextArea`

Оновлення з worker — через `Platform.runLater()`.

## Конфігурація

**v2 (Java, рекомендовано)** — кілька профілів + expert ping:

```yaml
active_profile: default
profiles:
  default:
    interval: 1.0
    max_hops: 20
    timeout: 0.5
    probe: auto
    probe_mode: trace
    max_concurrent_traces: 10  # default = MAX_HOSTS; all enabled TRACE hosts may run in parallel
    hosts:
      - address: "8.8.8.8"
        enabled: true
        ping_expert:
          chain: false
          args: ["-4", "-s", "128"]
```

**Legacy** (Python + Java load):

```yaml
hosts:
  - "8.8.8.8"
  - "google.com"
```

Legacy автоматично мігрується в профіль `default`. Збереження з Java UI пише v2.

Файл за замовч.: `java/config/hosts.example.yaml` (робоча директорія — `java/`).

## Збірка

**Linux / macOS**

```bash
cd java && ./gradlew build
cd java && ./pingui-java.sh --package    # jpackage (.deb / .dmg)
```

**Windows**

```bat
cd java
gradlew.bat build
pingui-java.bat --package    REM .msi
```

Тести та CI — на **`main`** і **`beta`** (розробка ROADMAP — на `beta`).

## Матриця parity з MVP

| ID | Вимога | Java статус |
|----|--------|-------------|
| F-01 | YAML 0–10 | ✅ |
| F-02 | GUI CRUD | ✅ |
| F-03 | Trace enabled only | ✅ |
| F-04 | Max 10 active | ✅ |
| F-05 | Vertical graph | ✅ JavaFX GraphCanvas |
| F-06 | Inactive column | ✅ дві колонки |
| F-07 | Last known IP | ✅ |
| F-08 | Route change log | ✅ |
| F-09 | ICMP | ✅ raw (Linux) або traceroute |
| F-10 | CLI options | ✅ |

## Майбутнє

Лінійна черга ROADMAP: **NEXT = DONE** (черга вичерпана). Деталі — [docs/ROADMAP.md](ROADMAP.md) § NEXT.
