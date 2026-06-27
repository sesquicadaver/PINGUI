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

```bash
cd java
chmod +x pingui-java.sh
./pingui-java.sh              # GUI
./pingui-java.sh --test       # unit-тести
./pingui-java.sh --build      # збірка + тести
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
├── probe/           RouteProbe → ProcessRouteProbe (traceroute/tracert)
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
```

JUnit 5 (21+ тестів: config, probe, monitor, ui layout).

## Backlog (Java)

- JNA raw ICMP на Linux (опційно, для parity з scapy)
- jpackage — нативні інсталятори (.deb, .msi, .dmg)
- Спільний CI matrix з Python
- JaCoCo coverage gate
