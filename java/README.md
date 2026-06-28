# PINGUI Java

Крос-платформова версія PINGUI на **Java 21 + JavaFX**.

Працює на **Linux, macOS та Windows** без `CAP_NET_RAW`: трасування через системні
`traceroute` / `tracert`. Дані сесії — лише в RAM, як у Python-редакції.

## Вимоги

| Компонент | Версія |
|-----------|--------|
| JDK | 21+ |
| traceroute | Linux/macOS (`traceroute` у PATH) |
| tracert | Windows (вбудований) |

## Швидкий старт

**Linux / macOS**

```bash
cd java
chmod +x pingui-java.sh
./pingui-java.sh              # GUI
./pingui-java.sh --test       # unit-тести
./pingui-java.sh --build      # збірка + тести + JaCoCo gate
./pingui-java.sh --package    # jpackage (.deb / .dmg / .msi)
./pingui-java.sh --help
```

**Windows (cmd / PowerShell)**

```bat
cd java
pingui-java.bat              REM GUI
pingui-java.bat --test       REM unit-тести
pingui-java.bat --build      REM збірка + тести + JaCoCo gate
pingui-java.bat --package    REM jpackage (.msi)
pingui-java.bat --help
```

Або напряму Gradle (`gradlew` / `gradlew.bat`):

```bash
cd java
./gradlew run          # Linux / macOS
gradlew.bat run        # Windows
./gradlew test
```

## CLI

```bash
./pingui-java.sh -- --config config/hosts.example.yaml --interval 2 --max-hops 15
```

| Опція | За замовч. | Опис |
|-------|------------|------|
| `--config` | `config/hosts.example.yaml` | YAML з 0–10 цілями |
| `--interval` | `1.0` | Інтервал опитування (с) |
| `--max-hops` | `20` | Максимум hop |
| `--timeout` | `0.5` | Таймаут probe (с) |
| `--probe` | `auto` | `auto`, `process`, `raw` (Linux cap) |
| `--geoip-hints` | `config/geoip_hints.yaml` | Offline CIDR→країна для підписів hop |
| `--no-geoip` | off | Вимкнути країну в підписах |
| `--verbose` | off | Debug-лог |

## GUI

Функціональність вирівняна з Python MVP:

- Список до **10 цілей**, чекбокс = активне трасування
- **Додати / Змінити / Видалити / Зберегти** → YAML
- **Режим «Простий»** (за замовчуванням): компактне вікно під список цілей; **loss %**, **min/avg/max RTT**; кольоровий фон рядка
- **Режим «Розширений»**: + граф маршруту + лог змін маршруту
- Неактивні (без чекбокса) цілі — лише ім’я, без метрик

> Топологічний граф вирівняний з Python `GraphCanvas` (Canvas замість Matplotlib).

## Архітектура

```
io.pingui
├── config/          HostsConfig (YAML)
├── model/           HopNode, RouteSnapshot, HostSessionData
├── probe/           RouteProbeFactory → ProcessRouteProbe / RawIcmpRouteProbe (JNA)
├── monitor/         SessionStore, RoutePoller, MonitorService
└── ui/              MainController (JavaFX)
```

Деталі: [docs/JAVA.md](../docs/JAVA.md).

## Відмінності від Python-версії

| Аспект | Python | Java |
|--------|--------|------|
| Платформа | Linux | Linux, macOS, Windows |
| ICMP | scapy raw socket + cap_net_raw | traceroute/tracert subprocess |
| GUI | PyQt6 + Matplotlib graph | JavaFX + GraphCanvas |
| Worker | QThread | ScheduledExecutorService |
| Запуск | `./pingui.sh` (Linux) | `java/pingui-java.sh` або `java/pingui-java.bat` (Windows) |

Спільний формат конфігу YAML (`hosts:`) сумісний між редакціями.

## Розробка

```bash
cd java
./gradlew test
./gradlew run
./gradlew jpackageDeb   # Linux .deb → build/dist/
```

JUnit 5 (27+ тестів: config, probe, monitor, ui layout, CLI options).

## Пакування (jpackage)

На Linux / Windows / macOS з JDK 21+ (містить `jpackage`):

```bash
./pingui-java.sh --package    # Linux / macOS
pingui-java.bat --package     # Windows
# або
./gradlew installDist jpackage
ls build/dist/    # pingui_0.1.0_amd64.deb | .msi | .dmg залежно від ОС
```

Інсталятор включає JavaFX та залежності з `installDist`.

## Backlog (Java)

- Спільний CI matrix з Python
