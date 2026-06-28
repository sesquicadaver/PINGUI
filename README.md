# PINGUI

Крос-платформний монітор маршрутів і RTT до 10 цілей одночасно (Java 21 + JavaFX).
Дані зберігаються **лише в RAM** протягом сесії.

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

## CLI

```bash
cd java
./pingui-java.sh -- --config config/hosts.example.yaml --interval 2 --max-hops 15
```

| Параметр | Опис |
|----------|------|
| `--config` | YAML з 0–10 цілями (v2 `profiles:` або legacy `hosts:`) |
| `--interval` | Інтервал опитування (с) |
| `--max-hops` | Максимум hop |
| `--timeout` | Таймаут probe (с) |
| `--probe` | `auto`, `process`, `raw` (Linux) |
| `--geoip-hints` | Offline CIDR→країна |
| `--no-geoip` | Вимкнути країну в підписах |
| `--verbose` | Debug-лог |

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
| [CHANGELOG.md](CHANGELOG.md) | Історія змін |

Розробка, тести та Python-редакція — гілка **`beta`**.
