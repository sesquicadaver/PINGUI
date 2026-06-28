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

З кореня репозиторію:

```bash
./pingui.sh --java              # GUI
./pingui.sh --java --deploy     # build + тести + JaCoCo
./pingui.sh --java --test       # лише unit-тести
```

Або з каталогу `java/`:

```bash
cd java
chmod +x pingui-java.sh
./pingui-java.sh              # GUI
./pingui-java.sh --test       # unit-тести
./pingui-java.sh --build      # збірка + тести + JaCoCo gate
./pingui-java.sh --package    # Linux .deb через jpackage
./pingui-java.sh --help
```

Або напряму Gradle:

```bash
cd java
./gradlew run
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
| `--verbose` | off | Debug-лог |

## GUI

Функціональність вирівняна з Python MVP:

- Список до **10 цілей**, чекбокс = активне трасування
- **Додати / Змінити / Видалити / Зберегти** → YAML
- Лог змін маршруту та помилок
- **Граф маршруту (JavaFX)**: згори вниз, дві колонки (inactive / active), кольори RTT, avg ping у підписах

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
| Запуск | `./pingui.sh` | `./java/pingui-java.sh` |

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

На Linux з JDK 21+ (містить `jpackage`):

```bash
./pingui-java.sh --package
# або
./gradlew installDist jpackageDeb
ls build/dist/pingui_0.1.0_amd64.deb
```

Інсталятор включає JavaFX та залежності з `installDist`.

## Backlog (Java)

- jpackage для Windows (.msi) / macOS (.dmg) у CI matrix
- Спільний CI matrix з Python
