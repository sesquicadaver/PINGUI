> **Мова:** Українська · [English](en/ADR_OBSERVABILITY.md)

# ADR: Межі observability — Prometheus vs time-series backend (P15-001)

**Статус:** accepted (P15-001)  
**Дата:** 2026-07-10  
**Гілка (історично):** прийнято на `beta`; після merge — на `main` і `beta`.

## Контекст

NOC/SRE очікують дві різні інтеграції з PINGUI:

1. **Pull metrics** — Prometheus/Grafana scrape gauges і counters з daemon.
2. **Push time-series** — запис RTT/route samples у InfluxDB або Timescale (уже є в Python B-05).

Паралельно вже існують інші канали даних, які **не** є metrics backends:

| Канал | Фаза | Призначення |
|-------|------|-------------|
| SQLite session (`--session-db`) | P11 | Стан сесії + дискретні події (`route_change`, `probe_error`) для GUI/history |
| Alerts (webhook / desktop) | P10 | Оповіщення оператора; не time-series store |
| REST read-only API | P15-040 | Операційний знімок хостів/маршрутів; не scrape metrics |
| Telemetry bus + LOG-server | P16 | Уніфікований emit → sinks (майбутнє) |

Без ADR легко змішати scrape з push, або намагатися годувати Grafana з SQLite session DB.

## Рішення

### 1. Два ортогональні шляхи (P15)

| Шлях | Напрям | Протокол | Коли | Ticket |
|------|--------|----------|------|--------|
| **Prometheus** | **read / pull** | HTTP `GET /metrics` (text exposition) | Daemon mode; scrape ззовні | P15-010, P15-011 |
| **TS backend** | **write / push** | Influx line protocol / SQL insert | Опційно, якщо сконфігуровано | P15-020 ✅ (Java); Python B-05 ✅ |

- Prometheus **не** є write-store для PINGUI у v1 (немає `remote_write` клієнта).
- Influx/Timescale **не** замінюють `/metrics`: Grafana може використовувати **обидва** джерела незалежно.
- Обидва шляхи **вимкнені за замовчуванням** (zero network listeners / zero remote writes без явного CLI/YAML).

### 2. Межі з іншими шарами

```
MonitorService / poll loop
        │
        ├─► SessionStore + SQLite (P11)     — session / discrete events
        ├─► AlertDispatcher (P10)          — operator notify
        ├─► PrometheusExporter (P15)       — in-process gauges → scrape
        ├─► TimeSeriesBackend (P15/B-05)   — push samples (опційно)
        └─► TelemetryBus (P16, later)      — unified async emit → sinks
```

| Не робити в P15 | Чому |
|-----------------|------|
| Писати hop-RTT у SQLite як «Prometheus replacement» | Session DB ≠ TS; обсяг і query model інші |
| Дублювати alert webhook як metrics push | P10 лишається notify; metrics — окремий контракт |
| OTLP / OpenTelemetry export | Відкладено (P16-080) |
| Prometheus `remote_write` | Out of scope v1; scrape достатньо для daemon |
| High-freq RTT у syslog | Це P16 LOG-server (`events_only`) |

### 3. Контракт Prometheus (v1, P15-010)

Мінімальний набір імен (префікс `pingui_`):

| Metric | Тип | Labels (v1) | Опис |
|--------|-----|-------------|------|
| `pingui_rtt_ms` | gauge | `host`, `hop` | Останній відомий RTT (мс) |
| `pingui_route_change_total` | counter | `host` | Кількість виявлених змін маршруту |
| `pingui_target_reachable` | gauge | `host` | `1` / `0` — ціль досяжна в останньому poll (hop IP == `targetIp`) |
| `pingui_trace_duration_ms` | gauge | `host`, `probe_mode` | Тривалість останнього trace/mtr/ping |

- Bind default: **localhost** (`--metrics-port`, P15-011).
- Лише **daemon** (і опційно headless monitor); GUI-only без listener — за замовчуванням.
- Auth / TLS на `/metrics` — **out of scope v1**; reverse proxy — P15-041 ✅ (`docs/DEPLOYMENT.md`).

### 4. Контракт TS backend (write)

- Backends: **`influx`** | **`timescale`** (як Python `create_timeseries_backend`).
- Дані: `PingSample` (RTT per hop) + `RouteEvent` (snapshot / change marker).
- Конфіг: CLI / env (`INFLUXDB_*`, `PINGUI_TIMESCALE_DSN`) — пріоритет як у alerts ADR §6.
- Помилка write → `WARNING`, **без** зупинки poll loop.
- Секрети (token, DSN) — **не** логувати plaintext.

Java P15-020 має дзеркалити Python API (`TimeSeriesBackend`), не вигадувати третю модель.

### 5. Звʼязок з фазою 16

P15 адаптери — **тимчасові прямі** виклики з monitor (**dual-emit debt** до P16-051/052): MonitorService може одночасно оновлювати Prometheus gauges і push у TS backend. Це прийнятно для v1, але **не** цільова топологія. P16-013 лише підключає poll → TelemetryBus; міграція sinks — пізніше.

Після P16-011…013:

- `PrometheusExporter` → `PrometheusTelemetrySink` (P16-051) — **in-process state-holder для scrape**, не Prometheus `remote_write` / push-клієнт
- Influx/Timescale → `InfluxTelemetrySink` / wrapper (P16-052)
- Один emit шлях через `TelemetryBus` (без подвійного HTTP/SQL з MonitorService)

**SQLite samples (P16-020):** `SqliteTelemetrySink` зберігає samples/events у schema v4 (default off / retention P16-022). Це **локальний архів**, не заміна Prometheus scrape і не Grafana TS datasource за замовчуванням. Межа з §2 («не писати hop-RTT у SQLite як Prometheus replacement») лишається в силі для P15 і для операторських dashboards.

ADR P16-001 ✅ деталізує events vs samples vs aggregates і mapping імен метрик (узгодження з `pingui_*` / P16-014); цей ADR фіксує межі P15.

### 6. Конфігурація (пріоритет)

1. CLI (найвищий): `--metrics-port`, TS flags / env  
2. YAML активного профілю (майбутні секції `metrics:` / `timeseries:` — у ticket імплементації)  
3. Default: **обидва шляхи off**

## Наслідки

- **Документація:** цей ADR — gate перед P15-010/P15-020; індекс у `docs/README.md`.
- **Імплементація:** P15-010 ✅ (`PrometheusExporter` / `MetricsHttpServer`); P15-011 ✅ (`--metrics-port`); P15-020 ✅ (`persistence/timeseries/` + CLI `--ts-backend`); P15-040 ✅ (read-only API); P15-041 ✅ (nginx/TLS у DEPLOYMENT).
- **Оператори:** Grafana = scrape Prometheus **і/або** Influx/Timescale datasource; SQLite session — для GUI history, не для dashboards.
- **Не робити:** єдиний «observability blob», що змішує alerts + SQLite + Prometheus remote_write.

## Посилання

- [ROADMAP.md](ROADMAP.md) — фаза 15 (P15-*), фаза 16 (P16-051…052)  
- [ADR_ALERTS.md](ADR_ALERTS.md) — окремий notify-канал  
- [ADR_DAEMON.md](ADR_DAEMON.md) — headless process, де живе `/metrics`  
- [SPIKE_PERSISTENCE.md](SPIKE_PERSISTENCE.md) — SQLite ≠ TS  
- Python: `src/pingui/persistence/timeseries/`  
- Java: `observability/PrometheusExporter.java`, `persistence/timeseries/` (P15-020 ✅)
