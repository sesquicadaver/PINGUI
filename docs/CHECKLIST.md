> **Мова:** Українська · [English](en/CHECKLIST.md)

# Checklist розгортання PINGUI (Java)

Checklist для Java-редакції на **Linux / Windows / macOS**.

**Expert ping** (режим «Експерт», кнопка **Exten.**) — **лише Linux + iputils-ping**.

Python-редакція та тести — гілка **`beta`**.

Деталі: [JAVA.md](JAVA.md), [DEPLOYMENT.md](DEPLOYMENT.md).

### Python daemon smoke (beta)

- [ ] `./pingui.sh --deploy` — venv + doc parity
- [ ] `.venv/bin/python -m pingui monitor --config config/hosts.example.yaml` — foreground headless (Ctrl+C)
- [ ] `.venv/bin/python -m pingui daemon --session-db data/ping.db --pid-file /tmp/pingui.pid`
- [ ] `.venv/bin/python -m pingui status --pid-file /tmp/pingui.pid` → running
- [ ] `.venv/bin/python -m pingui stop --pid-file /tmp/pingui.pid`

---

## Linux (Ubuntu 22.04 / 24.04 / 26.04)

### Preflight

- [ ] x86_64 або arm64
- [ ] GUI: X11 або Wayland
- [ ] Мережа до цілей моніторингу
- [ ] JDK **21** (не Java 25 як launcher Gradle)

### Системні пакети

```bash
sudo apt update
sudo apt install -y openjdk-21-jdk traceroute iputils-ping
```

- [ ] `java -version` → major **21**
- [ ] `traceroute --version` — встановлено
- [ ] `ping -V` → **iputils** (для Expert ping)

### Встановлення

```bash
cd /path/to/PINGUI/java
chmod +x pingui-java.sh gradlew
export PINGUI_JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64   # за потреби
./pingui-java.sh --build
./pingui-java.sh
```

- [ ] `./pingui-java.sh --build` — без «What went wrong: 25.0.3»
- [ ] `./gradlew build` — SUCCESS
- [ ] GUI відкривається

### Smoke-test

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
| Raw ICMP denied | `sudo setcap cap_net_raw+ep` на JDK binary |

---

## Windows 11+

> ⚠ **Попередження:** Windows — **не рекомендовано** для інтенсивного моніторингу маршруту. `tracert` виконує 3 probe на кожен hop з тривалими таймаутами; один trace до 20 hop може займати **1–4+ хвилини**. Expert ping недоступний. Для практичної роботи: **Ping only** у GUI або `ping_only: true` / `interval: 30` у YAML. Рекомендована платформа — **Linux**. [DEPLOYMENT.md#рекомендація-щодо-ос](DEPLOYMENT.md#рекомендація-щодо-ос)

### Preflight

- [ ] Windows 11 x64
- [ ] **JDK 21 встановлено** (Eclipse Temurin / Microsoft Build of OpenJDK)
- [ ] Під час інсталяції: **Add to PATH** + **Set JAVA_HOME**
- [ ] `tracert` і `ping` — вбудовані

### Встановлення JDK 21 (якщо `java` не знайдено)

1. Завантажити [Eclipse Temurin 21 (Windows x64)](https://adoptium.net/temurin/releases/?version=21)
2. Інсталятор → увімкнути **Add to PATH**, **Set JAVA_HOME**
3. Нове вікно cmd:

```bat
java -version
```

Має бути `openjdk version "21.x.x"`.

Якщо PATH не оновився:

```bat
set "PINGUI_JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot"
cd C:\path\to\PINGUI\java
pingui-java.bat --build
```

`pingui-java.bat` лише передає `JAVA_HOME`/`PINGUI_JAVA_HOME` у `gradlew.bat` (без пошуку JDK у cmd — старий пошук ламався на шляхах з пробілами).

### Збірка PINGUI

```bat
cd C:\path\to\PINGUI\java
pingui-java.bat --build
pingui-java.bat
```

- [ ] `java -version` → **21**
- [ ] `./gradlew build` — SUCCESS
- [ ] GUI (JavaFX) відкривається

### Smoke-test

- [ ] Ціль `8.8.8.8`, чекбокс увімкнено
- [ ] **Ping only** ON → RTT за кілька секунд (без очікування повного trace)
- [ ] Або trace OFF + ping only OFF: перший trace — **до 4 хв** (це нормально для `tracert`)
- [ ] Simple / Extended — метрики та граф
- [ ] YAML save/load
- [ ] «Експерт» **disabled**

### jpackage (.msi)

- [ ] WiX Toolset v3+ у PATH
- [ ] `pingui-java.bat --package` → `build\dist\*.msi`

---

## macOS (Intel / Apple Silicon)

### Preflight

- [ ] macOS 12+
- [ ] JDK **21** (`/usr/libexec/java_home -v 21`)
- [ ] `traceroute` (Java резолвить `/usr/sbin/traceroute` автоматично)
- [ ] Xcode Command Line Tools (jpackage)

### Встановлення

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
cd /path/to/PINGUI/java
chmod +x pingui-java.sh gradlew
./pingui-java.sh --build
./pingui-java.sh
```

- [ ] `java -version` → **21**
- [ ] `./gradlew build` — SUCCESS
- [ ] GUI відкривається

### Smoke-test

- [ ] Trace у GUI — ≥ 1 hop
- [ ] Simple / Extended, YAML save/load
- [ ] «Експерт» **disabled**

---

## Універсальний smoke-test

1. `[launcher] --build`
2. Запустити GUI
3. `8.8.8.8` + чекбокс → 10–30 с
4. Simple: RTT/loss; Extended: граф
5. Зберегти конфіг → перезапуск
6. **Linux only:** Expert → Exten. → `-4 -s 128`

---

## § GUI smoke (B-035, після UI-split)

Виконати на **Linux** (регресія «чорного фрейму» після profile CRUD):

- [ ] **Про** (меню) — діалог версії відкривається без зависання
- [ ] **F1 / Довідка** — діалог довідки відкривається
- [ ] **Новий профіль** → ім'я `test` → список хостів порожній, вікно без чорних смуг
- [ ] **Видалити профіль** (повернення до default) → Simple mode, вікно зменшується коректно (не лишається oversized frame)
- [ ] **Розширений** → граф + лог; **Простий** → знову compact layout
- [ ] Додати `8.8.8.8` → **Зберегти** → перезапуск `./pingui-java.sh` → ціль і профіль на місці
- [ ] Перемикання профілів у ComboBox — хости оновлюються

---

## § CLI interval (M-014)

Перевірка, що YAML `interval` не затирається без CLI:

1. У активному профілі YAML: `interval: 30.0`
2. Запуск **без** `--interval`: `./pingui-java.sh --config /path/to/hosts.yaml`
3. Очікування: опитування ~30 с між циклами (не ~1 с)

Автоматичний контракт: `PinguiApplicationTest.m014_yamlInterval30_noCliOverride_preservesInterval`.

---

## § Release / jpackage smoke (B-061)

Після bump версії або перед тегом release:

- [ ] `cd java && ./gradlew check` — green (включно з `layerCheck`)
- [ ] `./pingui-java.sh --build` — SUCCESS
- [ ] `./pingui-java.sh --package` (Linux: `.deb`) — артефакт у `build/dist/`
- [ ] Інсталятор запускається; GUI відкривається
- [ ] **Про** — версія містить git sha (`AppInfo.versionDetail()`)
- [ ] Smoke з § GUI smoke (B-035) на цільовій ОС

---

## § Docs smoke (B-062, щотижня / перед release)

Перевірка відповідності README ↔ фактичний CLI:

- [ ] `java/README.md` — прапорці `--config`, `--interval`, `--probe`, `--geoip-hints` збігаються з `./pingui-java.sh --help`
- [ ] `java/README.en.md` — той самий CLI parity (EN)
- [ ] `docs/JAVA.md` — таблиця CLI vs YAML актуальна
- [ ] `docs/en/JAVA.md` — EN parity з UK
- [ ] `docs/DEPLOYMENT.md` — JDK 21, Windows warning, dual-stack
- [ ] `docs/en/` — перемикачі мов і відповідність з `docs/` (bilingual smoke)
- [ ] `python3 scripts/check_doc_parity.py` — green (CI gate)
- [ ] `docs/ROADMAP.md` — закриті задачі позначені `[x]`
- [ ] Badge CI у `README.md` — відображає останній push

---

## Можливості за платформою

| Функція | Linux | Windows | macOS |
|---------|-------|---------|-------|
| Trace (tracert/traceroute) | ✅ | ✅ | ✅ |
| Expert ping | ✅ | ❌ | ❌ |
| Raw ICMP (`probe: raw`) | ✅ | ❌ | ❌ |

Unit-тести: `cd java && ./gradlew check` (JUnit 5 на `main`; Python-тести — гілка **`beta`**).
