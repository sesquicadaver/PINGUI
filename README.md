# PINGUI

Крос-платформний монітор маршрутів і RTT до 10 цілей одночасно (Java 21 + JavaFX).
Дані зберігаються **лише в RAM** протягом сесії.

> **Рекомендація щодо ОС:** для щоденної роботи обирайте **Linux** — найшвидше трасування та повний набір функцій (Expert ping, raw ICMP). **Windows** підтримується, але через повільний `tracert` (3 probe на hop, секунди очікування на кожен) один повний trace до 20 hop може тривати **хвилини**; для Windows краще режим **Ping only** або `interval` ≥ 30 с. Деталі: [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md#рекомендація-щодо-ос).

| Гілка | Зміст |
|-------|--------|
| **`main`** | Java-редакція + документація для запуску |
| **`beta`** | Python-редакція, тести, CI, специфікації, roadmap |

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

> ⚠ **Не найкращий вибір для інтенсивного моніторингу.** `tracert` значно повільніший за Linux `traceroute` (3 probe/hop, довгі таймаути). Перший trace може тривати 1–4 хв на ціль; Expert ping недоступний. Для Windows: **Ping only** на хості або `interval: 30`+ у профілі. Див. [docs/DEPLOYMENT.md](../docs/DEPLOYMENT.md#рекомендація-щодо-ос).

Потрібен **JDK 21**: [Eclipse Temurin 21 (Windows x64)](https://adoptium.net/temurin/releases/?version=21) — під час інсталяції увімкніть **Add to PATH** та **Set JAVA_HOME**.

```bat
cd PINGUI\java
pingui-java.bat --build
pingui-java.bat
```

Потрібні: **JDK 21** ([Eclipse Temurin](https://adoptium.net/temurin/releases/?version=21)), `traceroute` (Linux/macOS) або `tracert` (Windows).

## Можливості

- До **10 цілей**, чекбокс = активне трасування
- **Профілі трасування** в одному YAML (`active_profile` + `profiles`)
- **Простий** / **Розширений** режим UI; loss %, min/avg/max RTT
- **Expert ping** (Linux, iputils) — діалог **Exten.** на хост
- Трасування через `traceroute` / `tracert` (без `CAP_NET_RAW` за замовч.)
- Опційно на Linux: raw ICMP (`probe: auto|raw` + `cap_net_raw`)
- **IPv4-only:** IPv6-літерали та IPv6 trace не підтримуються (див. [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md))

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
| `--geoip-hints` | Offline CIDR→країна |
| `--no-geoip` | Вимкнути країну в підписах |
| `--verbose` | Debug-лог |

Без `--interval` / `--max-hops` / `--timeout` / `--probe` значення беруться з YAML профілю.

## Структура репозиторію (`main`)

```
PINGUI/
├── java/                 # Java edition (JavaFX)
│   ├── pingui-java.sh    # Linux / macOS launcher
│   ├── pingui-java.bat   # Windows launcher
│   ├── build.gradle.kts
│   └── src/main/java/io/pingui/
├── docs/                 # Документація
├── CHANGELOG.md
└── ISSUES.txt
```

## Документація

| Файл | Призначення |
|------|-------------|
| [java/README.md](java/README.md) | Запуск, Gradle, GUI, YAML |
| [docs/CHECKLIST.md](docs/CHECKLIST.md) | Checklist Linux / Windows / macOS |
| [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) | Розгортання |
| [docs/JAVA.md](docs/JAVA.md) | Архітектура Java-редакції |
| [docs/README.md](docs/README.md) | Індекс документації |
| [docs/ROADMAP.md](docs/ROADMAP.md) | План виправлень |
| [CHANGELOG.md](CHANGELOG.md) | Історія змін |

Розробка, тести та Python-редакція — гілка **`beta`**.
