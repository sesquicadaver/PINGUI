> **Мова:** Українська · [English](en/CHECKLIST.md)

# Checklist розгортання PINGUI (Java)

Checklist для Java-редакції на **Linux / Windows / macOS**.

**Expert ping** (режим «Експерт», кнопка **Exten.**) — **лише Linux + iputils-ping**.

Python-редакція та тести — у дереві **обох** гілок; нові зміни ROADMAP — на **`beta`**.

Деталі: [JAVA.md](JAVA.md), [DEPLOYMENT.md](DEPLOYMENT.md).

### Java daemon smoke (P12)

- [ ] `./pingui-java.sh -- --daemon --config config/hosts.example.yaml --session-db data/ping.db --pid-file /tmp/pingui-java.pid` (hosts `enabled: true` у YAML)
- [ ] `./pingui-java.sh -- --status --pid-file /tmp/pingui-java.pid` → `running pid=…`
- [ ] `sqlite3 data/ping.db "SELECT host FROM host_session;"` — рядки після poll
- [ ] `./pingui-java.sh -- --daemon --alert-webhook URL …` — route change → POST (лог webhook)
- [ ] `--api-port 8080` / `--metrics-port 9090`: `curl http://127.0.0.1:8080/hosts` і `curl http://127.0.0.1:9090/metrics`; TLS — за [DEPLOYMENT § reverse proxy](DEPLOYMENT.md#reverse-proxy--tls-p15-041)
- [ ] `./pingui-java.sh -- --stop --pid-file /tmp/pingui-java.pid`

### Python daemon smoke

- [ ] `./pingui.sh --deploy` — venv + doc parity
- [ ] `.venv/bin/python -m pingui monitor --config config/hosts.example.yaml` — foreground headless (Ctrl+C)
- [ ] `.venv/bin/python -m pingui daemon --session-db data/ping.db --pid-file /tmp/pingui.pid`
- [ ] `.venv/bin/python -m pingui status --pid-file /tmp/pingui.pid` → running
- [ ] `.venv/bin/python -m pingui stop --pid-file /tmp/pingui.pid`

### Python IPv6 smoke (Linux/macOS)

- [ ] `traceroute -6` встановлено (`which traceroute`)
- [ ] YAML з `::1` або `2001:db8::1`: `load_hosts_config` без помилки
- [ ] `.venv/bin/python -c "from pingui.icmp.tracer import trace_route; print(trace_route('::1', max_hops=3))"` — ≥1 hop
- [ ] GeoIP v6: `country_code_for_ip('2001:4860:4860::8888')` → `US` (з `config/geoip_hints.yaml`)

### Java IPv6 UI smoke

- [ ] Довідка (F1) згадує IPv4/IPv6 literal
- [ ] Додати ціль `2001:db8::1` — нормалізується в лог; невалідна `not:valid:ipv6` → помилка в журналі
- [ ] Граф hop з v6 показує IP у дужках `[2001:4860:4860::8888]`

### Java IPv6 process trace smoke (Linux, V6-070)

- [ ] `traceroute -6 ::1` — ≥1 hop (потрібен `traceroute` у PATH)
- [ ] Профіль YAML з `2001:db8::1` — trace без «No hops parsed»
- [ ] `./gradlew test --tests io.pingui.probe.ProcessRouteProbeTest` — `unix_v6_*` green
- [ ] Ping-only на v6 literal — RTT оновлюється без crash
- [ ] `./gradlew test --tests io.pingui.probe.ProcessRouteProbeTest.v4FixturesRemainGreen` — v4 regression (CI)

### Java IPv6 smoke (Windows, optional — V6-071)

- [ ] `tracert -6 ::1` завершується (повільно; Ping only рекомендовано для production)
- [ ] `./gradlew test --tests io.pingui.probe.ProcessRouteProbeTest.parseWindowsIpv6*` — `win_v6_*` green

### Java IPv6 raw ICMP smoke (Linux optional, V6-040…043)

- [ ] `./gradlew test --tests io.pingui.probe.icmp.IcmpV6PacketTest` — packet build/parse (CI)
- [ ] `./gradlew test --tests io.pingui.probe.icmp.LinuxCLibraryTest` — `sockaddr_in6` layout (Linux CI)
- [ ] `./gradlew test --tests io.pingui.probe.RawIcmpRouteProbeTest.traceIpv6UsesHopLimitSequence` — hop-limit trace (CI)
- [ ] YAML `probe: raw` + `cap_net_raw` + ціль `::1` — trace без crash (ручний)

### Python alert smoke

- [ ] `python -m pingui monitor --alert-webhook http://127.0.0.1:9/hook` — старт без crash (webhook недоступний → log)
- [ ] `python -m pingui run --desktop-alerts` — GUI + notify-send при зміні маршруту (Linux)
- [ ] `python -m pingui daemon --alert-webhook URL --session-db data/ping.db` — route change → POST JSON

### Java alert smoke (Linux)

- [ ] `./gradlew test --tests io.pingui.monitor.WebhookAlertDispatcherTest` — contract POST JSON (CI)
- [ ] `./gradlew test --tests io.pingui.monitor.AlertRateLimiterTest` — burst rate limit (CI)
- [ ] `./pingui-java.sh --alert-webhook http://127.0.0.1:9/hook` — старт без crash (webhook недоступний → WARNING)
- [ ] `./pingui-java.sh --desktop-alerts` — GUI + `notify-send` при зміні маршруту (потрібен `libnotify-bin`)
- [ ] YAML `alerts.webhook` / `alert_webhook` у профілі — route change → POST без CLI override

### Java telemetry smoke (P16-071)

Поля sinks: [CONFIGURATION § Телеметрія](CONFIGURATION.md#телеметрія-p16-040052). LOG-server: [DEPLOYMENT § LOG-server](DEPLOYMENT.md#log-server-p16-061). Unit-покриття: `TelemetrySinkInstallerTest`, `TelemetryAttachmentTest`, `DaemonRunnerTest.startRegistersSqliteAndSyslogFromTelemetryConfig`, `SqliteTelemetrySinkTest`, `SyslogSinkTest`, `AppMenuDialogsTest`. GUI desktop — той самий `TelemetryAttachment` (P16-090); GUI smoke нижче (P16-094).

**Підготувати профіль** (копія `java/config/hosts.example.yaml` або тимчасовий YAML): хост `enabled: true`; у профілі:

```yaml
telemetry:
  events_only: true
  sqlite: data/telemetry.db
  syslog:
    host: 127.0.0.1
    port: 1514
    tls: false
```

- [ ] CI: `cd java && ./gradlew test --tests io.pingui.TelemetrySinkInstallerTest --tests io.pingui.daemon.DaemonRunnerTest.startRegistersSqliteAndSyslogFromTelemetryConfig` — green
- [ ] Термінал A (mock syslog TCP): `nc -l 1514 | tee /tmp/pingui-syslog.log` (або rsyslog з DEPLOYMENT)
- [ ] Термінал B: `./pingui-java.sh -- --daemon --config <yaml> --session-db data/ping.db --pid-file /tmp/pingui-java.pid` (або CLI override `--telemetry-syslog 127.0.0.1:1514`)
- [ ] Після першого poll (baseline route_change): `sqlite3 data/telemetry.db "SELECT event, host FROM telemetry_event LIMIT 5;"` — є `route_change` (або `probe_error`)
- [ ] У `/tmp/pingui-syslog.log` — рядок RFC 5424 з JSON `"event":"route_change"` (або `probe_error`); **немає** потоку hop-RTT samples при `events_only: true`
- [ ] `./pingui-java.sh -- --stop --pid-file /tmp/pingui-java.pid`

### Java GUI telemetry smoke (P16-094)

- [ ] CI: `cd java && ./gradlew test --tests io.pingui.ui.AppMenuDialogsTest --tests io.pingui.TelemetryAttachmentTest --tests io.pingui.ui.TelemetrySettingsDialogTest` — green
- [ ] About («Про PINGUI…») згадує SQLite session і меню Телеметрія (не «лише RAM»)
- [ ] Довідка (F1) має секцію Налаштування з `persistence.session_db` ≠ `telemetry.sqlite`
- [ ] GUI: **Налаштування → Телеметрія…** → sqlite `data/telemetry.db`, Apply → лог «Телеметрія оновлена»; **Зберегти** → YAML містить `telemetry.sqlite`
- [ ] Після poll: `sqlite3 data/telemetry.db "SELECT event FROM telemetry_event LIMIT 3;"` — є `route_change` або `probe_error`

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
- [ ] Expert ON → **MTU** (або Expert → **MTU wizard…**) → Старт → Stop/завершення → Alert з MTU → Apply → args `-M do -s …` у Expert

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

> ⚠ **Попередження:** Windows — **не рекомендовано** для інтенсивного моніторингу маршруту. `tracert` виконує 3 probe на кожен hop з тривалими таймаутами; один trace до 20 hop може займати **1–4+ хвилини**. Expert ping недоступний. Для практичної роботи: **Ping only** у GUI або стартовий пресет `config/hosts.windows.example.yaml` (`probe_mode: ping_only`, `interval: 60`; телеметрія P16-043: `events_only: true`, **без** `jsonl_dir`). Рекомендована платформа — **Linux**. [DEPLOYMENT.md#рекомендація-щодо-ос](DEPLOYMENT.md#рекомендація-щодо-ос)

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
pingui-java.bat --config config/hosts.windows.example.yaml
```

- [ ] `java -version` → **21**
- [ ] `./gradlew build` — SUCCESS
- [ ] GUI (JavaFX) відкривається з `hosts.windows.example.yaml` (ping_only, interval 60)

### Smoke-test

- [ ] Старт з `config/hosts.windows.example.yaml` (P13-040 + P16-043 preset)
- [ ] Ціль `8.8.8.8`, чекбокс увімкнено
- [ ] **Ping only** ON (preset) → RTT за кілька секунд (без очікування повного trace)
- [ ] Телеметрія: у YAML `events_only: true`, немає `jsonl_dir` / high-freq sqlite (не пише hop-RTT JSONL)
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
7. **Linux only:** Expert → **MTU** → wizard Start/Apply (не плутати з пресетом «MTU probe»)

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

Unit-тести: `cd java && ./gradlew check` (JUnit 5); Python — `.venv/bin/pytest` (обидві гілки; розробка на **`beta`**).
