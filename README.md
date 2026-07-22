# PINGUI

> **Мова:** Українська · [English](README.en.md)

![Java CI](https://github.com/sesquicadaver/PINGUI/actions/workflows/java.yml/badge.svg)
![Python CI](https://github.com/sesquicadaver/PINGUI/actions/workflows/ci.yml/badge.svg)

Крос-платформний монітор маршрутів і RTT до 10 цілей одночасно (Java 21 + JavaFX).
За замовчуванням дані сесії — **в RAM**; опційно **SQLite** (`--session-db`, GUI «База даних…»), алерти, історія маршруту, headless **daemon**, dual-stack **IPv6** і Python-редакція — у дереві **обох** гілок після merge з `beta`.

> **Рекомендація щодо ОС:** для щоденної роботи обирайте **Linux** — найшвидше трасування та повний набір функцій (Expert ping, raw ICMP). **Windows** підтримується, але через повільний `tracert` (3 probe на hop, секунди очікування на кожен) один повний trace до 20 hop може тривати **хвилини**; для Windows краще режим **Ping only** або `interval` ≥ 30 с. Деталі: [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md#рекомендація-щодо-ос).

## Гілки `main` і `beta`

| | **`main`** | **`beta`** |
|---|------------|------------|
| **Роль** | Стабільний зріз після merge з `beta` (production) | Активна розробка (`/autopilot` — лише якщо NEXT ≠ DONE) |
| **Java desktop** | ✅ GUI + Pro (IPv6, SQLite, alerts, history, daemon, export, telemetry, Expert MTU) — як у останньому merge | ✅ Те саме **+** будь-які зміни до merge; лінійна черга ROADMAP **P20-008** (фаза 20) |
| **Сесія** | RAM за замовчуванням; опційно **SQLite** | Те саме |
| **Оповіщення / історія / daemon / IPv6** | ✅ (після merge фаз 9–12+) | ✅ + новіші зміни до merge |
| **Python PyQt6** | ✅ `src/pingui/`, `./pingui.sh`, pytest (може трохи відставати від `beta`) | ✅ Найновіший Python-шар |
| **CI** | Java `gradlew check` + Python pytest | Те саме |
| **Документація** | Синхронізується при merge | Living docs попереду `main` до merge |

**Що обрати:** **`main`** — останній стабільний merge для production; **`beta`** — щоденна розробка і найновіші фічі (поки їх не злито в `main`). Різниця — **наскільки `beta` попереду**, а не «на `main` немає SQLite/алертів/Python». План: [docs/ROADMAP.md](docs/ROADMAP.md).

```bash
git clone https://github.com/sesquicadaver/PINGUI.git
cd PINGUI
git checkout beta    # або main
```

## Швидкий старт

**Linux / macOS**

```bash
git clone https://github.com/sesquicadaver/PINGUI.git
cd PINGUI/java
chmod +x pingui-java.sh gradlew
./pingui-java.sh --build
./pingui-java.sh
```

**Windows**

> ⚠ **Не найкращий вибір для інтенсивного моніторингу.** `tracert` значно повільніший за Linux `traceroute` (3 probe/hop, довгі таймаути). Перший trace може тривати 1–4 хв на ціль; Expert ping недоступний. Для Windows: **Ping only** на хості або `interval: 30`+ у профілі. Див. [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md#рекомендація-щодо-ос).

Потрібен **JDK 21**: [Eclipse Temurin 21 (Windows x64)](https://adoptium.net/temurin/releases/?version=21) — під час інсталяції увімкніть **Add to PATH** та **Set JAVA_HOME**.

```bat
cd PINGUI\java
pingui-java.bat --build
pingui-java.bat
```

Потрібні: **JDK 21** ([Eclipse Temurin](https://adoptium.net/temurin/releases/?version=21)), `traceroute` (Linux/macOS) або `tracert` (Windows).

## Можливості

- До **10 цілей**, чекбокс = активне трасування; **Ping only** = RTT до цілі без trace
- **Профілі трасування** в одному YAML (`active_profile` + `profiles`)
- **Простий** / **Розширений** режим UI; loss %, min/avg/max RTT; граф маршруту
- **Expert ping** (Linux, iputils) — **Exten.**: пресети, **MTU wizard**, **Self-check** DF/DSCP/Burst
- **Телеметрія** — sinks (sqlite/jsonl/syslog/GELF/Loki/OTLP), меню **Налаштування → Телеметрія…**
- Трасування через `traceroute` / `tracert`; опційно raw ICMP на Linux (`probe: auto|raw`)
- Dual-stack IPv6, алерти зміни маршруту, SQLite + «Історія змін», headless daemon, CSV/HTML export — див. [java/README.md](java/README.md)

## CLI

```bash
cd java
./pingui-java.sh -- --config config/hosts.example.yaml --interval 2 --max-hops 15
```

| Параметр | Опис |
|----------|------|
| `--config` | YAML з 0–10 цілями (v2 `profiles:` або legacy `hosts:`) |
| `--interval` | Перезаписати `interval` активного профілю **лише якщо передано** |
| `--max-hops` | Перезаписати `max_hops` активного профілю **лише якщо передано** |
| `--timeout` | Перезаписати `timeout` активного профілю **лише якщо передано** |
| `--probe` | Перезаписати `probe` активного профілю **лише якщо передано** |
| `--session-db` | SQLite метрики + події сесії |
| `--export-report` | CSV/HTML з `--session-db` без GUI |
| `--daemon` / `--stop` / `--status` | Headless монітор (NOC) |
| `--alert-webhook` / `--desktop-alerts` | Оповіщення про зміну маршруту |
| `--geoip-hints` | Offline CIDR→країна |
| `--no-geoip` | Вимкнути країну в підписах |
| `--verbose` | Debug-лог |

Без `--interval` / `--max-hops` / `--timeout` / `--probe` значення беруться з YAML профілю. Повний список CLI (включно з persistence): [java/README.md](java/README.md#cli).

## Структура репозиторію

```
PINGUI/
├── java/                 # Java edition (JavaFX + daemon / SQLite / alerts)
├── src/pingui/           # Python edition
├── tests/                # pytest
├── config/               # Приклади YAML, GeoIP hints
├── systemd/              # pingui-java.service.example
├── docs/
│   ├── en/               # English documentation
│   └── *.md              # Ukrainian documentation
└── CHANGELOG.md
```

## Документація

| Файл | Призначення |
|------|-------------|
| [docs/README.md](docs/README.md) | Індекс документації (UK) |
| [docs/en/README.md](docs/en/README.md) | Documentation index (EN) |
| [java/README.md](java/README.md) | Запуск, Gradle, GUI, YAML (UK) |
| [java/README.en.md](java/README.en.md) | Launch, Gradle, GUI, YAML (EN) |
| [docs/CHECKLIST.md](docs/CHECKLIST.md) | Checklist Linux / Windows / macOS |
| [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) | Розгортання |
| [docs/JAVA.md](docs/JAVA.md) | Архітектура Java-редакції |
| [docs/ROADMAP.md](docs/ROADMAP.md) | План розвитку (`main` / `beta`) |
| [CHANGELOG.md](CHANGELOG.md) | Історія змін |

Python: `./pingui.sh` (venv; розробка на **`beta`**). Java: `cd java && ./gradlew check`. NOC без GUI: `./pingui-java.sh -- --daemon --config …`.

## Support the project

If this project is useful to you, you may support its development with a voluntary donation in USDT.

Donations are optional and do not provide ownership, equity, tokens, governance rights, paid support, priority service, or any investment return.

### USDT donations

| Network | Address |
|---|---|
| USDT ERC-20 / Ethereum | 0xfa9821efd142228d53e1418fe335bb1cd8ff3c39 |
| USDT TRC-20 / Tron | TNnhueeGqujf6AAUhcgissoEkL7tdzmqQv |

### Important

Please make sure that the selected network matches the address type.

- Send **USDT ERC-20** only to the Ethereum address.
- Send **USDT TRC-20** only to the Tron address.

Transactions sent to the wrong network may be permanently lost.

Thank you for supporting the project.
