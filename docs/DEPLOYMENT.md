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

**Dual-stack (phase 9 / **0.2.0**):** YAML і session приймають IPv6 literal (RFC 5952). IPv6 literal → subprocess `traceroute -6` у `probe: auto` (**Linux/macOS**); `probe: raw` + `cap_net_raw` — raw ICMPv6 на Linux. Windows Python trace для v6 literal поки не підтримується.

## Рекомендація щодо ОС

| Платформа | Для щоденної роботи | Коментар |
|-----------|---------------------|----------|
| **Linux** | ✅ **Рекомендовано** | `traceroute -q 1` — швидко; Expert ping; raw ICMP |
| **macOS** | ✅ Добре | Швидкий trace; Expert ping недоступний |
| **Windows** | ⚠ Обмежено | **Повільний `tracert`**: 3 probe на hop, ~4 с очікування на probe → повний trace 1–4+ хв на ціль при 20 hop. Expert ping недоступний |

**Windows — не найкращий вибір** для постійного моніторингу маршруту з коротким `interval` (напр. 1 с): наступний цикл часто стартує до завершення попереднього trace.

**Як пом’якшити на Windows:**

- Стартовий пресет: `java/config/hosts.windows.example.yaml` — `probe_mode: ping_only`, `interval: 60` (P13-040).
- У GUI: чекбокс **Ping only** на хості (лише RTT до цілі, без hop-ів).
- У YAML: `probe_mode: ping_only` або `interval: 60` (і більше) для режиму з trace.
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
pingui-java.bat --config config/hosts.windows.example.yaml
```

## Збірка та пакування

Версія застосунку — поле `version` у `java/build.gradle.kts` (єдине джерело). Gradle `generateBuildProperties` записує її в `pingui/build.properties` і JAR manifest; About (`AppInfo`) показує ту саму версію. `jpackage --app-version` — semver без `-SNAPSHOT` (напр. `0.2.0` з `0.2.0-SNAPSHOT`).

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

### IPv6 і `cap_net_raw` (dual-stack)

| Шлях trace/ping | Ціль | `cap_net_raw`? | Примітка |
|-----------------|------|----------------|----------|
| `probe: auto` / `raw` | IPv4 literal, hostname (A) | **Так** (raw) | Без cap → fallback на `traceroute` |
| `probe: auto` | IPv6 literal | **Ні** | Завжди subprocess `traceroute -6` (Java і Python) |
| Expert ping `-6` | IPv6 literal / `-6` | **Ні** | iputils `ping`, не raw socket |
| Raw ICMPv6 (`probe: raw`) | IPv6 literal | **Так** | Java: `LinuxJnaIcmpTransport` + `IcmpV6Packet`; `auto` лишає process trace |

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

## Python NOC (headless)

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

## Java NOC (headless daemon, P12)

Без JavaFX — той самий `MonitorService`, що й GUI:

```bash
cd /path/to/PINGUI/java
./pingui-java.sh -- --daemon \
  --config config/hosts.example.yaml \
  --session-db data/ping.db \
  --pid-file /tmp/pingui-java.pid \
  --alert-webhook https://hooks.example.com/pingui

./pingui-java.sh -- --status --pid-file /tmp/pingui-java.pid
./pingui-java.sh -- --stop --pid-file /tmp/pingui-java.pid
```

У YAML увімкніть `enabled: true` для цілей (daemon не має чекбоксів UI).

Приклад systemd: `systemd/pingui-java.service.example`. ADR: [ADR_DAEMON.md](ADR_DAEMON.md).

## Reverse proxy + TLS (P15-041)

Java daemon слухає **лише `127.0.0.1`** для Prometheus (`--metrics-port`) і read-only REST (`--api-port`). TLS і автентифікація **в застосунку відсутні** (v1) — виставляйте їх на reverse proxy.

**Приклад запуску daemon (localhost):**

```bash
cd /path/to/PINGUI/java
./pingui-java.sh -- --daemon \
  --config config/hosts.example.yaml \
  --session-db data/ping.db \
  --pid-file /tmp/pingui-java.pid \
  --api-port 8080 \
  --metrics-port 9090
```

| Внутрішній URL | Призначення |
|----------------|-------------|
| `http://127.0.0.1:8080/hosts` | Список цілей (JSON) |
| `http://127.0.0.1:8080/routes/{host}` | Поточний маршрут |
| `http://127.0.0.1:8080/openapi.json` | OpenAPI stub |
| `http://127.0.0.1:9090/metrics` | Prometheus scrape |

**nginx (TLS termination + optional Basic Auth):**

```nginx
# /etc/nginx/sites-available/pingui.conf
upstream pingui_api {
    server 127.0.0.1:8080;
}
upstream pingui_metrics {
    server 127.0.0.1:9090;
}

server {
    listen 443 ssl http2;
    server_name pingui.example.com;

    ssl_certificate     /etc/letsencrypt/live/pingui.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/pingui.example.com/privkey.pem;

    # Optional Basic Auth (uncomment after: htpasswd -c /etc/nginx/htpasswd/pingui operator)
    # auth_basic           "PINGUI";
    # auth_basic_user_file /etc/nginx/htpasswd/pingui;

    location /metrics {
        proxy_pass http://pingui_metrics;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location / {
        proxy_pass http://pingui_api;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}

server {
    listen 80;
    server_name pingui.example.com;
    return 301 https://$host$request_uri;
}
```

Перевірка після `nginx -t && systemctl reload nginx`:

```bash
curl -fsS https://pingui.example.com/hosts
curl -fsS https://pingui.example.com/metrics | head
# With Basic Auth enabled:
# curl -fsS -u operator:SECRET https://pingui.example.com/hosts
```

**Сертифікати (Let’s Encrypt):**

```bash
sudo certbot --nginx -d pingui.example.com
```

**Безпека:**

- Не біндіть `--api-port` / `--metrics-port` на `0.0.0.0` — у коді лише loopback.
- Для Prometheus у тій самій мережі часто достатньо scrape `127.0.0.1:9090` без публічного `/metrics`.
- Basic Auth — мінімум; для NOC краще mTLS / SSO на edge (поза scope PINGUI v1).
- ADR: [ADR_OBSERVABILITY.md](ADR_OBSERVABILITY.md).

## LOG-server (P16-061)

Telemetry **events** (`route_change`, `probe_error`, optional `rtt_aggregate`) можна надсилати на зовнішній LOG-stack. High-freq hop-RTT **не** йде в syslog/GELF/Loki при `events_only: true` (default). Повна таблиця полів: [CONFIGURATION.md § Телеметрія](CONFIGURATION.md#телеметрія-p16-040052). SPIKE: [SPIKE_LOG_SINKS.md](SPIKE_LOG_SINKS.md). ADR: [ADR_TELEMETRY.md](ADR_TELEMETRY.md).

**Принцип:** *events* → LOG-server; *samples* → SQLite/JSONL / Influx / Prometheus scrape.

### Daemon → remote sinks (приклад)

```yaml
# у профілі v2 (фрагмент)
telemetry:
  events_only: true
  log_aggregates: false
  syslog:
    host: 127.0.0.1
    port: 1514
    tls: false          # опційно true (syslog TCP+TLS)
  gelf:
    host: 127.0.0.1
    port: 12201
    transport: tcp      # tcp prod; udp лише lab
  # loki:               # опційно (P16-032)
  #   url: http://127.0.0.1:3100
  #   site: noc
```

```bash
cd /path/to/PINGUI/java
./pingui-java.sh -- --daemon \
  --config config/hosts.example.yaml \
  --session-db data/ping.db \
  --pid-file /tmp/pingui-java.pid \
  --telemetry-syslog 127.0.0.1:1514
```

CLI `--telemetry-syslog` / `--telemetry-jsonl` має пріоритет над YAML. Windows: не вмикайте `jsonl_dir` для hop-RTT — див. `hosts.windows.example.yaml` (P16-043).

### rsyslog (TCP RFC 5424)

PINGUI `SyslogSink`: TCP, framing trailing NL, MSG = one-line JSON event.

```bash
# /etc/rsyslog.d/30-pingui.conf
module(load="imtcp")
input(type="imtcp" port="1514")

template(name="PinguiJson" type="string" string="%msg%\n")
if $syslogfacility-text == 'local0' then {
  action(type="omfile" file="/var/log/pingui/events.log" template="PinguiJson")
  stop
}
```

```bash
sudo mkdir -p /var/log/pingui
sudo systemctl restart rsyslog
# перевірка: logger не потрібен — дивіться /var/log/pingui після route_change у daemon
```

TLS (опційно): terminate на rsyslog (`imtcp` + `StreamDriver`) або stunnel/nginx stream перед `syslog.host`; у YAML — `telemetry.syslog.tls: true` коли sink говорить TLS напряму.

### Graylog (GELF TCP)

`GelfSink`: GELF 1.1 JSON, TCP framing `\0` (prod). UDP — лише lab.

1. Graylog → System → Inputs → **GELF TCP** (port `12201` або інший).
2. У YAML: `telemetry.gelf.host` / `port` / `transport: tcp`.
3. Index/stream: фільтр за `short_message` ∈ {`route_change`, `probe_error`} або `_event`.

Не використовуйте GELF UDP у production (втрати без chunking у v1).

### Grafana Loki (опційно)

`LokiPushSink` — HTTP `POST /loki/api/v1/push`; labels `job=pingui`, `site`, `host`. Тримати `events_only: true`. URL secrets redact у логах (P16-042).

### Локальна телеметрія + retention

| Шлях | Конфіг | Retention |
|------|--------|-------------|
| SQLite telemetry tables | `telemetry.sqlite` + `--session-db` | `--telemetry-retention N` |
| JSONL files | `telemetry.jsonl_dir` / `--telemetry-jsonl` | `--telemetry-retention N --telemetry-jsonl-dir DIR` |
| Dump | — | `--telemetry-dump out.csv` (потрібен `--session-db`) |

```bash
# cron (щодня): purge samples/events старші за 30 днів
cd /path/to/PINGUI/java
./pingui-java.sh -- --session-db /var/lib/pingui/ping.db \
  --telemetry-retention 30 \
  --telemetry-jsonl-dir /var/lib/pingui/telemetry
```

Session persistence (`host_session` / `persistence_event`) — окремий шар; див. § SQLite нижче (P11-050).

### Безпека LOG

- За замовч. **не** слати hop-RTT у LOG (`events_only: true`).
- Не логувати plaintext token/query в webhook/Loki URL.
- Remote LOG — **outbound** з хоста PINGUI; inbound TLS для Prometheus/API — § Reverse proxy (P15-041), не плутати канали.
- Firewall: дозволити лише TCP до rsyslog/Graylog портів з NOC-хоста.

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
- Звіт без GUI: `cd java && ./pingui-java.sh -- --session-db data/ping.db --export-report report.csv`
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
| Немає events у rsyslog/Graylog | Увімкнути `telemetry.syslog`/`gelf` або `--telemetry-syslog`; `enabled: true` на хості; перевірити TCP порт / `events_only` |
| LOG засипаний RTT | Повернути `events_only: true`; не вмикати `jsonl_dir` на Windows для high-freq |

## Розробка

Нові зміни ROADMAP — на гілці **`beta`**; `main` — останній стабільний merge. Тести Java + Python доступні на обох гілках.
