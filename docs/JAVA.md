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

- **IPv4-only** для цілей і raw ICMP (`HostsConfig` validator, `RawIcmpRouteProbe` / `AF_INET`).
- IPv6 — поза scope `main`; див. [ROADMAP.md](ROADMAP.md) фаза 7.

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
| **raw-icmp** (Linux) | `RawIcmpRouteProbe` | Linux | JNA + `CAP_NET_RAW` або root |
| **auto** | `RouteProbeFactory` | Усі | Linux + cap → raw, інакше process |

CLI: `--probe auto|process|raw` (default: `auto`).

> **Продуктивність:** на **Windows** subprocess trace через `tracert` на порядки повільніший за Linux `traceroute` (`-q 1`). Для production-моніторингу рекомендується **Linux**; на Windows — **Ping only** або великий `interval`. Див. [DEPLOYMENT.md](DEPLOYMENT.md#рекомендація-щодо-ос).

### ProcessRouteProbe (subprocess)

| ОС | Команда |
|----|---------|
| Linux / macOS | `traceroute -n -w SEC -m N -q 1 HOST` (1 probe/hop) |
| Windows | `tracert -d -h N -w MS HOST` (3 probe/hop, MS ≥ 4000) — **повільно** |

### RawIcmpRouteProbe (JNA, Linux)

Інкрементальний TTL 1..N через raw ICMP socket (parity з Python `trace_route`).

Потрібно: `sudo setcap cap_net_raw+ep` на JDK binary або запуск від root.

## Monitor-шар

`MonitorService` — один daemon thread (`ScheduledExecutorService`):

1. Збирає enabled хости.
2. `RoutePoller.pollHostRoute()` → `RouteProbe.trace()`.
3. Якщо для хоста налаштовано expert ping — `ExpertPingEnricher` виконує `ping -c 1` з додатковими flags.
4. Callbacks: `onDataReceived`, `onRouteChanged`, `onProbeError`.

Логіка store/history/change detection — порт з Python (`SessionStore`, `RouteHistory`, `RouteChangeDetector`).

## UI-шар

`MainController` (JavaFX):

- Меню **Про** / **Довідка** (F1) — `AppMenuDialogs`
- Вибір **профілю трасування** (ComboBox + новий/видалити); усі профілі в одному YAML
- Чекбокс **«Експерт»** → кнопка **Exten.** на рядку хоста → `PingExpertDialog` (каталог з `pingMan.txt`, без `-c/-w/-W/-i` тощо)
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

Тести та CI — гілка **`beta`**.

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

Див. backlog у гілці **`beta`**.
