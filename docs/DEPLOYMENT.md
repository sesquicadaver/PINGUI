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

**IPv4 trace/raw ICMP; dual-stack config (V6-S1):** YAML і session приймають IPv6 literal (RFC 5952, напр. `2001:db8::1` або `[::1]`). Subprocess trace і raw ICMP для v6 — фаза 9.2+ ([ROADMAP.md](ROADMAP.md)); зараз probe лишається IPv4-oriented.

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

За замовч. використовується `traceroute`/`tracert`. Raw ICMP (`probe: auto|raw`):

```bash
sudo setcap cap_net_raw+ep "$(readlink -f "$(which java)")"
```

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
.venv/bin/python -m pingui status --pid-file /tmp/pingui.pid
.venv/bin/python -m pingui stop --pid-file /tmp/pingui.pid
```

Приклад systemd: `systemd/pingui.service.example` (Type=simple, `ExecStart=... daemon`).

## Troubleshooting

| Симптом | Рішення |
|---------|---------|
| Trace на Windows «зависає» / дуже довгий | Нормально для `tracert`; увімкніть **Ping only** або збільште `interval` |
| Gradle «What went wrong: 25.0.3» | JDK 21: `export PINGUI_JAVA_HOME=.../java-21-openjdk-*` |
| «No hops parsed» | Встановити `traceroute`; на macOS — `/usr/sbin/traceroute` |
| JavaFX runtime missing | `./gradlew run` або jpackage installer |
| Expert ping без RTT | Linux + `iputils-ping` |

## Розробка

Тести, CI, Python-редакція — гілка **`beta`**.
