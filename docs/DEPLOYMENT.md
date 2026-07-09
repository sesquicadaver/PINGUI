> **Мова:** Українська · [English](en/DEPLOYMENT.md)

# Розгортання PINGUI (Java)

## Платформи

| ОС | Підтримка |
|----|-----------|
| Linux (Ubuntu 22.04+) | ✅ |
| Windows 11+ | ✅ |
| macOS 12+ | ✅ |

Покроковий checklist: **[CHECKLIST.md](CHECKLIST.md)**.

Expert ping (режим «Експерт») — **лише Linux** (iputils `ping`).

**Dual-stack (phase 9, `beta` / **0.2.0**):** YAML і session приймають IPv6 literal (RFC 5952). IPv6 literal → subprocess `traceroute -6` у `probe: auto` (**Linux/macOS**); `probe: raw` + `cap_net_raw` — raw ICMPv6 на Linux. Windows Python trace для v6 literal поки не підтримується.

## Рекомендація щодо ОС

| Платформа | Для щоденної роботи | Коментар |
|-----------|---------------------|----------|
| **Linux** | ✅ **Рекомендовано** | `traceroute -q 1` — швидко; Expert ping; raw ICMP |
| **macOS** | ✅ Добре | Швидкий trace; Expert ping недоступний |
| **Windows** | ⚠ Обмежено | **Повільний `tracert`**: 3 probe на hop, ~4 с очікування на probe → повний trace 1–4+ хв на ціль при 20 hop. Expert ping недоступний |

**Windows — не найкращий вибір** для постійного моніторингу маршруту з коротким `interval` (напр. 1 с): наступний цикл часто стартує до завершення попереднього trace.

**Як пом’якшити на Windows:**

- У GUI: чекбокс **Ping only** на хості (лише RTT до цілі, без hop-ів).
- У YAML: `ping_only: true` або `interval: 30` (і більше) для режиму з trace.
- Очікуйте затримку 1–4 хвилини на перший повний trace до віддаленої цілі.

## Вимоги

| Компонент | Версія |
|-----------|--------|
| JDK | **21** (не Java 25 як launcher Gradle) |
| traceroute | Linux/macOS |
| tracert | Windows (вбудований) |
| Дисплей | X11 / Wayland / Windows Desktop |

## Запуск

**Linux / macOS**

```bash
cd java
chmod +x pingui-java.sh gradlew
export PINGUI_JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64   # за потреби
./pingui-java.sh --build
./pingui-java.sh
```

**Windows**

```bat
cd java
pingui-java.bat --build
pingui-java.bat
```

## Збірка та пакування

```bash
cd java
./gradlew build          # compile + jar
./pingui-java.sh --package   # jpackage: .deb / .dmg / .msi
```

## Raw ICMP (Linux, опційно)

За замовч. використовується `traceroute`/`tracert`. Raw ICMP (`probe: auto|raw`) на **Linux** відкриває сирцевий сокет `AF_INET` (ICMPv4) — потрібні права:

```bash
sudo setcap cap_net_raw+ep "$(readlink -f "$(which java)")"
```

Після `setcap` перезапустіть PINGUI. Той самий capability потрібен для Python raw ICMP (scapy) на JDK/venv не стосується — лише для процесу, що відкриває raw socket.

### IPv6 і `cap_net_raw` (dual-stack, `beta`)

| Шлях trace/ping | Ціль | `cap_net_raw`? | Примітка |
|-----------------|------|----------------|----------|
| `probe: auto` / `raw` | IPv4 literal, hostname (A) | **Так** (raw) | Без cap → fallback на `traceroute` |
| `probe: auto` | IPv6 literal | **Ні** | Завжди subprocess `traceroute -6` (Java і Python) |
| Expert ping `-6` | IPv6 literal / `-6` | **Ні** | iputils `ping`, не raw socket |
| Raw ICMPv6 (`probe: raw`) | IPv6 literal | **Так** | Java `beta`: `LinuxJnaIcmpTransport` + `IcmpV6Packet`; `auto` лишає process trace |

**Поточна поведінка:** навіть із `cap_net_raw` на JDK, **IPv6 literal ніколи не йде через raw ICMP** — лише процесний trace. Capability впливає на v4/hostname у режимі `auto|raw`.

**Перевірка cap (Linux):**

```bash
getcap "$(readlink -f "$(which java)")"
# очікується: cap_net_raw=ep
```

Якщо raw v4 не відкривається, PINGUI логує помилку сокета і (у `probe: auto`) перемикається на `traceroute`.

## Конфігурація

Приклад: `java/config/hosts.example.yaml`. Формат v2:

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
```

## Python NOC (headless, гілка `beta`)

Без Qt — фоновий моніторинг для серверів / NOC:

```bash
cd /path/to/PINGUI
./pingui.sh --deploy   # venv + parity (перший раз)
.venv/bin/python -m pingui monitor --config config/hosts.example.yaml --session-db data/ping.db
```

Daemon з PID-файлом (PY-030…032):

```bash
.venv/bin/python -m pingui daemon --session-db data/ping.db --pid-file /tmp/pingui.pid
.venv/bin/python -m pingui daemon --alert-webhook https://hooks.example.com/pingui --session-db data/ping.db
.venv/bin/python -m pingui status --pid-file /tmp/pingui.pid
.venv/bin/python -m pingui stop --pid-file /tmp/pingui.pid
```

Приклад systemd: `systemd/pingui.service.example` (Type=simple, `ExecStart=... daemon`).

## SQLite session persistence (Java / Python)

| Що | Де |
|----|-----|
| Файл БД | `--session-db PATH`, YAML `persistence.session_db`, або GUI **Налаштування → База даних…** |
| Метрики маршруту | Таблиця `host_session` (JSON: routes, `ping_history`, `hop_stats`) |
| Події | Таблиця `persistence_event` (`route_change`, `probe_error`) |

**Запис у БД:** після підключення SQLite увімкніть чекбокс цілі в списку хостів — legacy YAML за замовчуванням має `enabled: false`, без активного моніторингу маршрут і `hop_stats` не оновлюються. Рядки `host_session` з’являються одразу при connect; `current_route_json` — після першого trace; `ping_history_json` — після першого poll (авто-`ping` цілі, якщо Expert ping вимкнено). **Історія змін:** перший trace дає рядок «Початковий маршрут»; наступні — лише при зміні IP-ланцюга.

**Диск і retention (P11-050):**

- Автоматичного TTL / ротації для `host_session` **немає** — файл росте з кількістю хостів і накопиченими `ping_history` / `hop_stats` (обмеження в RAM: до 50 RTT на hop у `hop_stats`, історія ping по IP).
- Події `persistence_event` видаляються лише вручну: GUI **База даних…** → вимкнути тип події → **Видалити** (purge confirm).
- Повне скидання: видалити файл `.db` або рядок хоста через видалення цілі в UI.
- Звіт без GUI: `./pingui-java.sh -- --session-db data/ping.db --export-report report.csv`
- Орієнтовний розмір: кілька–десятки KB на хост при типовому NOC-профілі; моніторити `du -h data/ping.db` на довгоживучих daemon.

Деталі схеми: [SPIKE_PERSISTENCE.md](SPIKE_PERSISTENCE.md).

## Troubleshooting

| Симптом | Рішення |
|---------|---------|
| SQLite порожня / немає маршруту | Підключити БД (CLI/YAML/GUI), **увімкнути чекбокс хоста**, дочекатись poll; перевірка: `sqlite3 data/ping.db "SELECT host, enabled, length(current_route_json) FROM host_session;"` |
| Trace на Windows «зависає» / дуже довгий | Нормально для `tracert`; увімкніть **Ping only** або збільште `interval` |
| Gradle «What went wrong: 25.0.3» | JDK 21: `export PINGUI_JAVA_HOME=.../java-21-openjdk-*` |
| «No hops parsed» | Встановити `traceroute`; на macOS — `/usr/sbin/traceroute` |
| JavaFX runtime missing | `./gradlew run` або jpackage installer |
| Expert ping без RTT | Linux + `iputils-ping` |
| `dbind-WARNING` / `accessibility bus` | Нормально без a11y; не запускайте через `sudo`; або `NO_AT_BRIDGE=1` (у `pingui-java.sh` за замовч.) |
| IPv6 trace «немає hop-ів» | `traceroute -6` у PATH; raw cap **не** потрібен для v6 literal |
| Raw ICMP v4 «permission denied» | `setcap cap_net_raw+ep` на JDK binary (див. § Raw ICMP) |

## Розробка

Тести, CI, Python-редакція — гілка **`beta`**.
