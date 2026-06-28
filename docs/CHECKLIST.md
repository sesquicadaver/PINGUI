# Checklist розгортання PINGUI

Операційні checklist для **Python** (Linux) та **Java** (Linux / Windows / macOS).

| Редакція | Платформи | Запуск |
|----------|-----------|--------|
| Python | Linux | `./pingui.sh` |
| Java | Linux, Windows, macOS | `java/pingui-java.sh` / `java/pingui-java.bat` |

**Expert ping** (режим «Експерт», кнопка **Exten.**) — **лише Linux + iputils-ping**.

Деталі архітектури: [JAVA.md](JAVA.md), [DEPLOYMENT.md](DEPLOYMENT.md).

---

## Linux (Ubuntu 22.04 / 24.04 / 26.04)

### Preflight

- [ ] x86_64 або arm64
- [ ] GUI: X11 або Wayland
- [ ] Мережа до цілей моніторингу
- [ ] **Python:** Python **≥ 3.11** (на 22.04 часто потрібен `python3.11` окремо)
- [ ] **Java:** JDK **21** (не Java 25 як launcher Gradle)

### Системні пакети

```bash
sudo apt update
sudo apt install -y openjdk-21-jdk traceroute iputils-ping libcap2-bin
# Python GUI:
sudo apt install -y python3.11-venv libegl1 libgl1 libxkbcommon0 libxcb-cursor0
```

- [ ] `java -version` → major **21**
- [ ] `traceroute --version` — встановлено
- [ ] `ping -V` → **iputils** (для Expert ping)
- [ ] `python3 --version` → **≥ 3.11** (Python-редакція)

### Встановлення Java

```bash
cd /path/to/PINGUI/java
chmod +x pingui-java.sh gradlew
export PINGUI_JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64   # за потреби
./pingui-java.sh --build
./pingui-java.sh
```

- [ ] `./pingui-java.sh --build` — без «What went wrong: 25.0.3»
- [ ] `./gradlew test` — SUCCESS
- [ ] GUI відкривається

### Встановлення Python

```bash
cd /path/to/PINGUI
chmod +x pingui.sh
./pingui.sh --deploy
./pingui.sh
```

- [ ] venv `--copies` (не symlink)
- [ ] `getcap .venv/bin/python*` → `cap_net_raw+ep`
- [ ] GUI без помилки raw ICMP

### Smoke-test (Java)

- [ ] Додати `8.8.8.8`, увімкнути чекбокс
- [ ] Simple: loss %, min/avg/max RTT
- [ ] Extended: граф + лог змін
- [ ] Зберегти YAML → перезапуск → ціль на місці
- [ ] Expert ON → **Exten.** → `-4 -s 128` → RTT оновлюється

### jpackage (.deb)

```bash
cd java && ./pingui-java.sh --package
ls build/dist/*.deb
```

### Troubleshooting

| Симптом | Дія |
|---------|-----|
| Gradle «25.0.3» | `export PINGUI_JAVA_HOME=.../java-21-openjdk-*` |
| «No hops parsed» | `sudo apt install traceroute` |
| Expert ping без RTT | перевірити `ping -V` (iputils) |
| Qt plugin (Python) | `libegl1 libgl1 libxkbcommon0 libxcb-cursor0` |
| Raw ICMP denied | `./pingui.sh --deploy` |

---

## Windows 11+

### Preflight

- [ ] Windows 11 x64 (arm64 — перевірити JDK/JavaFX окремо)
- [ ] JDK **21** у PATH / `JAVA_HOME`
- [ ] `tracert` і `ping` — вбудовані
- [ ] Firewall не блокує ICMP для `tracert`

### Встановлення

```bat
cd C:\path\to\PINGUI\java
pingui-java.bat --build
pingui-java.bat
```

- [ ] `java -version` → **21** (launcher відмовить інакше)
- [ ] `gradlew.bat test` — SUCCESS
- [ ] GUI (JavaFX) відкривається

### Smoke-test

- [ ] Ціль `8.8.8.8`, чекбокс увімкнено
- [ ] Simple / Extended — метрики та граф (`tracert`)
- [ ] YAML save/load
- [ ] Checkbox «Експерт» **disabled** (лише Linux)

### jpackage (.msi)

- [ ] WiX Toolset v3+ у PATH
- [ ] `pingui-java.bat --package` → `build\dist\*.msi`

### Troubleshooting

| Симптом | Дія |
|---------|-----|
| Gradle fails / «25.0.3» | JDK 21 + `JAVA_HOME` |
| Немає hop-ів | `tracert 8.8.8.8` вручну; firewall |
| JavaFX missing | `./gradlew run`, не старий JAR без JavaFX |

### Обмеження

- Python `./pingui.sh` — **не підтримується**
- Expert ping — **не підтримується**
- Raw ICMP probe — **лише Linux**

---

## macOS (Intel / Apple Silicon)

### Preflight

- [ ] macOS 12+
- [ ] JDK **21** (`/usr/libexec/java_home -v 21`)
- [ ] `traceroute` (зазвичай `/usr/sbin/traceroute`; Java-редакція резолвить автоматично)
- [ ] Xcode Command Line Tools (збірка / jpackage)

### Встановлення

```bash
brew install openjdk@21    # або Temurin .pkg
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
export PATH="$JAVA_HOME/bin:$PATH"
cd /path/to/PINGUI/java
chmod +x pingui-java.sh gradlew
./pingui-java.sh --build
./pingui-java.sh
```

- [ ] `java -version` → **21**
- [ ] `./gradlew test` — SUCCESS
- [ ] GUI відкривається

### Smoke-test

- [ ] `traceroute -n 8.8.8.8` з терміналу (або `/usr/sbin/traceroute`)
- [ ] Trace у GUI — ≥ 1 hop
- [ ] Simple / Extended, YAML save/load
- [ ] «Експерт» **disabled**

### jpackage (.dmg)

- [ ] `xcode-select --install`
- [ ] `./pingui-java.sh --package` → `build/dist/*.dmg`

### Apple Silicon

- [ ] JDK 21 **aarch64** (не x86_64 через Rosetta для Gradle)

### Troubleshooting

| Симптом | Дія |
|---------|-----|
| «No hops parsed» | встановити Xcode CLT; перевірити `/usr/sbin/traceroute` |
| JDK не знайдено | `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` |
| GUI з .app без hop-ів | PATH у .app ≠ термінал; використати jpackage або wrapper |

---

## Швидкий вибір редакції

| Потреба | Linux | Windows | macOS |
|---------|-------|---------|-------|
| Folium «Карта» | Python ✅ | — | — |
| Cross-platform trace | Java ✅ | Java ✅ | Java ✅ |
| Expert ping (iputils) | Java ✅ | ❌ | ❌ |
| Raw ICMP | Python / Java ✅ | ❌ | ❌ |

## Універсальний smoke-test (Java)

1. `[launcher] --build`
2. Запустити GUI
3. `8.8.8.8` + чекбокс → зачекати 10–30 с
4. Simple: RTT/loss; Extended: граф
5. Зберегти конфіг → перезапуск
6. **Linux only:** Expert → Exten. → `-4 -s 128`
