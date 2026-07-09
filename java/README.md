> **Мова:** Українська · [English](README.en.md)

# PINGUI Java

> **Мова:** Українська · [English](README.en.md)

Крос-платформова версія PINGUI на **Java 21 + JavaFX**.

Працює на **Linux, macOS та Windows**: трасування через системні
`traceroute` / `tracert`. Дані сесії — в RAM; SQLite через `SessionDatabase` (P11-010, wire у P11-011…012).

> **Рекомендація:** **Linux** — оптимальна платформа (швидкий `traceroute -q 1`, Expert ping, raw ICMP). **Windows** — для періодичних перевірок: повний trace повільний через `tracert`; у GUI використовуйте **Ping only** або збільште `interval` у YAML. [docs/DEPLOYMENT.md](../docs/DEPLOYMENT.md#рекомендація-щодо-ос)

## Вимоги

| Компонент | Версія |
|-----------|--------|
| JDK | **21** (Java 25 як launcher Gradle не підтримується) |
| traceroute | Linux/macOS |
| tracert | Windows (вбудований) |

## Швидкий старт

**Linux / macOS**

```bash
cd java
chmod +x pingui-java.sh gradlew
./pingui-java.sh              # GUI
./pingui-java.sh --build      # збірка
./pingui-java.sh --package    # jpackage (.deb / .dmg / .msi)
./pingui-java.sh --help
```

**Windows**

> ⚠ Повільне трасування — див. [DEPLOYMENT.md](../docs/DEPLOYMENT.md#рекомендація-щодо-ос). Launcher: `pingui-java.bat` або `gradlew.bat run`.

Потрібен **JDK 21** ([Eclipse Temurin](https://adoptium.net/temurin/releases/?version=21); Add to PATH + JAVA_HOME).

`pingui-java.bat` — обгортка над `gradlew.bat`. Якщо `gradlew.bat build` успішний — launcher працює так само.

```bat
cd java
gradlew.bat build
gradlew.bat run
rem або
pingui-java.bat --build
pingui-java.bat
```

Якщо `java` не в PATH:

```bat
set "PINGUI_JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.x-hotspot"
pingui-java.bat --build
```

Gradle напряму:

```bash
cd java
./gradlew run          # Linux / macOS
gradlew.bat run        # Windows
```

## CLI

```bash
./pingui-java.sh -- --config config/hosts.example.yaml --interval 2 --max-hops 15
```

| Опція | За замовч. | Опис |
|-------|------------|------|
| `--config` | `config/hosts.example.yaml` | YAML з 0–10 цілями |
| `--interval` | *(з YAML)* | Перезапис poll interval (с), **лише якщо передано** |
| `--max-hops` | *(з YAML)* | Перезапис max hop, **лише якщо передано** |
| `--timeout` | *(з YAML)* | Перезапис probe timeout (с), **лише якщо передано** |
| `--probe` | *(з YAML)* | Перезапис `auto`/`process`/`raw`, **лише якщо передано** |
| `--alert-webhook` | off | POST JSON `RouteChangeEvent` при зміні маршруту |
| `--desktop-alerts` | off | Linux `notify-send` при зміні маршруту |
| `--alert-rate-limit` | `10` | Макс. алертів на host / годину |
| `--geoip-hints` | `config/geoip_hints.yaml` | Offline CIDR→країна |
| `--no-geoip` | off | Вимкнути країну в підписах |
| `--verbose` | off | Debug-лог |

CLI **не затирає** поля профілю defaults (1.0 / 20 / 0.5 / auto), якщо відповідний прапор не передано.

## GUI

- **Про** / **Довідка** — меню з діалогами «Про PINGUI…» та «Довідка…» (F1); dual-stack IPv4/IPv6 literal
- **Профілі трасування**: кілька named-профілів у YAML, перемикання в UI
- Список до **10 цілей**, чекбокс = активне трасування; **Ping only** = лише ping без trace
- **Додати / Змінити / Видалити / Зберегти** → YAML
- **Експерт** (Linux): **Exten.** → параметри `ping(8)` iputils; один AF (`-4` або `-6`, default IPv4); на Win/mac disabled
- **Простий** / **Розширений**: метрики RTT, loss %, граф маршруту, лог змін

## Архітектура

```
io.pingui
├── config/          ProfilesConfig, PingExpertEntry
├── model/           HopNode, RouteSnapshot
├── probe/           RouteProbeFactory, ProcessRouteProbe, TraceCommandBuilder,
                       UnixTraceOutputParser, WindowsTraceOutputParser, ProcessExpertPing
├── monitor/         SessionStore, MonitorService, AlertDispatchers, RouteChangeEvent
└── ui/              MainController (wiring), ProfileUiCoordinator, HostListPresenter,
                       MonitorLifecycle, ViewModeController, RouteGraphPresenter, GraphCanvas
```

Деталі: [docs/JAVA.md](../docs/JAVA.md).

### Формат профілів (v2)

```yaml
active_profile: office
profiles:
  office:
    interval: 1.0
    max_hops: 20
    timeout: 0.5
    probe: auto
    hosts:
      - address: "8.8.8.8"
        enabled: true
        ping_only: false
        ping_expert:
          chain: false
          args: ["-4", "-s", "128"]
      - "2001:db8::1"
      - address: "2001:4860:4860::8888"
        enabled: true
```

## Збірка

```bash
cd java
./gradlew check          # compile + Spotless + Checkstyle + layerCheck + JaCoCo + tests
./gradlew test           # JUnit 5 only
./gradlew spotlessApply  # автоформат Java / Gradle Kotlin DSL
./gradlew build
./gradlew run
./gradlew jpackageDeb   # Linux .deb → build/dist/
```

Unit-тести (21) — `src/test/java`; матриця: [docs/LIVING_SPEC.md](../docs/LIVING_SPEC.md). CI: ![Java CI](https://github.com/sesquicadaver/PINGUI/actions/workflows/java.yml/badge.svg)

## Пакування (jpackage)

```bash
./pingui-java.sh --package    # Linux / macOS
pingui-java.bat --package     # Windows
ls build/dist/
```
