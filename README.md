# PINGUI

> **Мова:** Українська · [English](README.en.md)

![Java CI](https://github.com/sesquicadaver/PINGUI/actions/workflows/java.yml/badge.svg)
![Python CI](https://github.com/sesquicadaver/PINGUI/actions/workflows/ci.yml/badge.svg)

Крос-платформний монітор маршрутів і RTT до 10 цілей одночасно (Java 21 + JavaFX).
За замовчуванням дані сесії — **в RAM**; на гілці **`beta`** Java також підтримує опційний **SQLite** (`--session-db`, GUI «База даних…») і headless **daemon**.

> **Рекомендація щодо ОС:** для щоденної роботи обирайте **Linux** — найшвидше трасування та повний набір функцій (Expert ping, raw ICMP). **Windows** підтримується, але через повільний `tracert` (3 probe на hop, секунди очікування на кожен) один повний trace до 20 hop може тривати **хвилини**; для Windows краще режим **Ping only** або `interval` ≥ 30 с. Деталі: [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md#рекомендація-щодо-ос).

## Гілки `main` і `beta`

| | **`main`** | **`beta`** |
|---|------------|------------|
| **Роль** | Стабільна лінія для щоденного Java GUI | Розробка: нові фази ROADMAP, Python-редакція |
| **Java desktop** | ✅ GUI, профілі, trace/tracert, Expert ping (Linux) | ✅ Усе з `main` **+** фази 9–12 (див. нижче) |
| **Сесія** | Лише RAM | RAM або **SQLite** (метрики + `route_change` / `probe_error`) |
| **Оповіщення** | — | Webhook + desktop alerts при зміні маршруту (P10) |
| **Історія маршруту** | — | Панель «Історія змін» + replay на графі (P11) |
| **Headless NOC** | — | `--daemon`, `--stop`, `--status`, systemd example (P12) |
| **Dual-stack IPv6** | Обмежено / без фази 9 | ✅ Java + Python (фаза 9) |
| **Python PyQt6** | Код у репо, **без** повного parity з `beta` | ✅ `./pingui.sh`, pytest, timeseries/export |
| **CI** | Java `gradlew check` | Java + Python pytest |
| **Документація** | UK + EN індекс | Повний пакет + ADR/SPIKE (P10–P13) |

**Що обрати:** для production GUI на стабільній базі — `main`; для SQLite, алертів, історії, daemon, IPv6 і Python — **`beta`**. Детальний план: [docs/ROADMAP.md](docs/ROADMAP.md).

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
- **Expert ping** (Linux, iputils) — діалог **Exten.** на хост
- Трасування через `traceroute` / `tracert`; опційно raw ICMP на Linux (`probe: auto|raw`)
- **`beta`:** dual-stack IPv6, алерти зміни маршруту, SQLite + «Історія змін», headless daemon, CSV/HTML export — див. [java/README.md](java/README.md)

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
| `--session-db` | *(beta)* SQLite метрики + події сесії |
| `--export-report` | *(beta)* CSV/HTML з `--session-db` без GUI |
| `--daemon` / `--stop` / `--status` | *(beta)* headless монітор (NOC) |
| `--alert-webhook` / `--desktop-alerts` | *(beta)* оповіщення про зміну маршруту |
| `--geoip-hints` | Offline CIDR→країна |
| `--no-geoip` | Вимкнути країну в підписах |
| `--verbose` | Debug-лог |

Без `--interval` / `--max-hops` / `--timeout` / `--probe` значення беруться з YAML профілю. Повний список CLI (включно з persistence): [java/README.md](java/README.md#cli).

## Структура репозиторію

```
PINGUI/
├── java/                 # Java edition (JavaFX + опційно daemon на beta)
├── src/pingui/           # Python edition (повний цикл на beta)
├── tests/                # pytest (beta)
├── config/               # Приклади YAML, GeoIP hints
├── systemd/              # pingui-java.service.example (beta)
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

Python: `./pingui.sh` на гілці **`beta`** (venv). Java: `cd java && ./gradlew check`. Для NOC без GUI на **`beta`**: `./pingui-java.sh -- --daemon --config …`.

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
