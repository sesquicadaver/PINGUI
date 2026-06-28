# PINGUI Java — архітектура та розгортання

Крос-платформова реалізація в каталозі [`java/`](../java/).

## Мета

Дозволити моніторинг маршрутів **незалежно від ОС** без Python/PyQt6 і без Linux-only `cap_net_raw`.

## Стек

- Java 21 (LTS)
- JavaFX 21 (GUI)
- Gradle 8.10 (Kotlin DSL)
- SnakeYAML (конфіг)
- JUnit 5 (тести)

## Probe-шар

Python використовує scapy + raw ICMP. Java підтримує два backend:

| Backend | Клас | ОС | Вимоги |
|---------|------|-----|--------|
| **process** (за замовч.) | `ProcessRouteProbe` | Linux, macOS, Windows | `traceroute` / `tracert` у PATH |
| **raw-icmp** (Linux) | `RawIcmpRouteProbe` | Linux | JNA + `CAP_NET_RAW` або root |
| **auto** | `RouteProbeFactory` | Усі | Linux + cap → raw, інакше process |

CLI: `--probe auto|process|raw` (default: `auto`).

### ProcessRouteProbe (subprocess)

| ОС | Команда |
|----|---------|
| Linux / macOS | `traceroute -n -w SEC -m N -q 1 HOST` |
| Windows | `tracert -h N -w MS HOST` |

### RawIcmpRouteProbe (JNA, Linux)

Інкрементальний TTL 1..N через raw ICMP socket (parity з Python `trace_route`).

Потрібно: `sudo setcap cap_net_raw+ep` на JDK binary або запуск від root (аналог `./pingui.sh --deploy` для Python).

## Monitor-шар

`MonitorService` — один daemon thread (`ScheduledExecutorService`):

1. Збирає enabled хости.
2. `RoutePoller.pollHostRoute()` → `RouteProbe.trace()`.
3. Callbacks: `onDataReceived`, `onRouteChanged`, `onProbeError`.

Логіка store/history/change detection — порт з Python (`SessionStore`, `RouteHistory`, `RouteChangeDetector`).

## UI-шар

`MainController` (JavaFX):

- `ListView<HostItem>` + CheckBox у комірці
- **GraphCanvas** — вертикальний граф, inactive/active колонки
- Log `TextArea`

Оновлення з worker — через `Platform.runLater()`.

## Конфігурація

Той самий YAML, що й Python:

```yaml
hosts:
  - "8.8.8.8"
  - "google.com"
```

Файл за замовч.: `java/config/hosts.example.yaml` (робоча директорія — `java/`).

## Збірка та CI

```bash
cd java && ./gradlew test build          # тести + JaCoCo gate (≥80%)
cd java && ./gradlew jpackageDeb         # Linux .deb
cd java && ./pingui-java.sh --package    # те саме через launcher
```

GitHub Actions: `.github/workflows/java-ci.yml` (JDK 21, `./gradlew test jacocoTestReport jacocoTestCoverageVerification`).

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

- jpackage .msi / .dmg у CI matrix (Linux .deb — ✅)
- CI matrix Windows/macOS
