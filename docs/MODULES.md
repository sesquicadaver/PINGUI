> **Мова:** Українська · [English](en/MODULES.md)

# Довідник модулів PINGUI

Публічні API пакету `pingui` (версія 0.2.0).

---

## pingui.models

### `HopNode` (frozen dataclass)

| Поле | Тип | Опис |
|------|-----|------|
| `hop` | int | Номер hop (TTL) |
| `ip` | str | IPv4/IPv6 або `"*"` при timeout |
| `ping_ms` | float \| None | RTT мілісекунди |
| `is_timeout` | bool | True якщо probe не відповів |

**Клас-метод:** `HopNode.timeout(hop: int) -> HopNode`

### `RouteSnapshot`

| Поле | Тип |
|------|-----|
| `target` | str — hostname/IP як у конфігу |
| `target_ip` | str — resolved IPv4 або canonical IPv6 |
| `nodes` | list[HopNode] |
| `timestamp` | datetime (UTC) |

**Метод:** `route_ips() -> list[str]` — IP без timeout.

### `HostSessionData`

In-memory стан однієї цілі: `current_route`, `previous_route`, `last_known_by_hop`, `ping_history`, `enabled`.

### Константа

`TIMEOUT_IP = "*"`

---

## pingui.config

### Винятки

`ConfigError(ValueError)`

### Константи

`MIN_HOSTS = 0`, `MAX_HOSTS = 10`

### Функції

| Функція | Опис |
|---------|------|
| `normalize_host_entry(entry: str) -> str` | Trim + validate (IPv4/IPv6 RFC 5952/hostname) |
| `host_address_kind(normalized) -> HostAddressKind` | IPV4 / IPV6 / HOSTNAME |
| `resolve_trace_target(host) -> str` | IPv4 A-record або canonical IPv6 |
| `validate_session_host(host, existing) -> str` | Dedup + limit |
| `load_hosts_config(path) -> list[str]` | Read YAML |
| `save_hosts_config(path, hosts) -> None` | Write YAML |
| `resolve_host_ipv4(host) -> str` | DNS A-record |

---

## pingui.icmp.raw_socket

### `ProbeResult` (frozen)

`source_ip`, `rtt_ms`, `is_target`

### `ProbeTransport` (Protocol)

```python
def send_probe(target_ip: str, ttl: int, timeout: float) -> ProbeResult | None
```

### Функції

| Функція | Опис |
|---------|------|
| `check_raw_icmp_permission() -> None` | Raises `RawIcmpPermissionError` |
| `resolve_target(host) -> str` | `resolve_trace_target` (v4/hostname→A, v6 literal) |
| `send_probe(..., transport=None) -> ProbeResult \| None` | Default: ScapyProbeTransport |

### Клас

`ScapyProbeTransport` — production ICMP через scapy `sr1`.

---

## pingui.icmp.tracer

```python
def trace_route(
    target_host: str,
    max_hops: int = 20,
    timeout: float = 0.5,
    transport: ProbeTransport | None = None,
) -> RouteSnapshot
```

TTL 1..max_hops (raw ICMP, v4/hostname); IPv6 literal → subprocess `traceroute -6` (`icmp/process_tracer.py`).

---

## pingui.monitor.polling

### `HostPollOutcome` (frozen dataclass)

` snapshot`, `error`, `route_changed`, `old_ips`, `new_ips`, `current_ips`

```python
def poll_host_route(
    host: str,
    previous_ips: list[str],
    *,
    max_hops: int = 20,
    timeout: float = 0.5,
    transport: ProbeTransport | None = None,
) -> HostPollOutcome
```

---

## pingui.monitor.route_change

```python
def detect_route_change(
    previous_ips: list[str],
    current_ips: list[str],
) -> tuple[bool, list[str], list[str]]
```

Перше спостереження (`previous_ips` порожній) — `changed=False`.

---

## pingui.monitor.route_history

| Функція | Опис |
|---------|------|
| `record_last_known(last_known, nodes)` | Оновити dict hop→HopNode |
| `route_with_last_known_ips(route, last_known)` | Замінити `*` на last known |

---

## pingui.persistence.session_db

### `SessionDatabase`

SQLite persistence для `SessionStore` (опційно `--session-db`).

| Метод | Опис |
|-------|------|
| `load(host) -> HostSessionData \| None` | Відновити стан цілі |
| `save(host, data)` | Запис snapshot + ping history |
| `close()` | Flush і закрити з'єднання |

Schema v2: JSON для hops, routes, `hop_stats`.

---

## pingui.export.session_report

| Функція | Опис |
|---------|------|
| `export_session_csv(store, path)` | Flat CSV (`ROUTE_CSV_FIELDS`) |
| `export_session_html(store, path)` | Standalone HTML з таблицями per host |
| `build_route_rows(store)` | current / inactive / previous routes + stats |

---

## pingui.geoip

| Модуль | Опис |
|--------|------|
| `country.configure(enabled, hints_path)` | Offline CIDR→country з YAML (`prefixes`, `prefixes_v6`) |
| `country.lookup_country(ip) -> str \| None` | Longest-prefix match |
| `coordinates.centroid_for_ip(ip)` | Lat/lon для folium |
| `map_builder.build_geo_map(hosts, store)` | HTML для WebEngine tab |

---

## pingui.persistence.timeseries

| Модуль | Опис |
|--------|------|
| `factory.create_timeseries_backend(...)` | `influx` / `timescale` / `None` |
| `base.TimeSeriesBackend` | Protocol: `record_rtt`, `record_route` |
| `memory.MemoryTimeSeriesBackend` | In-memory (tests) |

Optional deps: `pip install -e ".[timeseries]"`.

---

## pingui.monitor.hop_stats

| Функція | Опис |
|---------|------|
| `hop_stats_summary(samples) -> HopStatsSummary` | avg RTT, jitter, loss % |

---

## pingui.monitor.session_store

### `SessionStore`

| Метод | Опис |
|-------|------|
| `hosts() -> list[str]` | Ключі |
| `can_add_host() -> bool` | < MAX_HOSTS |
| `add_host(host, *, enabled=False) -> str` | |
| `remove_host(host) -> None` | |
| `rename_host(old, new) -> str` | |
| `set_enabled(host, enabled)` | |
| `get(host) -> HostSessionData` | |
| `update_route(host, snapshot)` | Зберігає previous при зміні |
| `inactive_route(host) -> list[HopNode]` | Previous + last known |
| `append_ping_samples(host, snapshot)` | Trim до 50/IP |
| `avg_ping(host, ip) -> float \| None` | |
| `extract_route_ips(snapshot)` | static |

`MAX_PING_SAMPLES = 50`

---

## pingui.monitor.monitor_loop

### `MonitorLoop`

Headless цикл моніторингу на `threading` (без Qt). Callbacks замість signals.

| Метод | Опис |
|-------|------|
| `hosts()`, `enabled_hosts()` | З `SessionStore` або внутрішній список |
| `add_host()`, `remove_host()`, `rename_host()`, `set_host_enabled()` | Делегує в store, якщо задано |
| `start()`, `stop()`, `join()`, `is_running()` | Життєвий цикл потоку |

`MonitorCallbacks` — `on_data_received`, `on_route_changed`, `on_probe_error`.

---

## pingui.monitor.daemon_runner

### `run_headless_monitor(...) -> int`

Foreground/daemon моніторинг до SIGINT/SIGTERM; опційно PID file.

### `PidFile`

| Метод | Опис |
|-------|------|
| `acquire()` / `release()` | Запис/видалення PID |
| `stop(path)` / `status(path)` | CLI `stop` / `status` |

При `alert_dispatcher` route change → webhook/desktop (PY-045).

---

## pingui.monitor.alert_dispatcher

### `build_alert_dispatcher(...)`

Збирає webhook + desktop канали з rate limit.

### `WebhookAlertDispatcher`

POST JSON `RouteChangeEvent`; URL у логах без секретів.

### `AlertRateLimiter`

Макс. N алертів на host / годину (PY-044).

---

## pingui.monitor.desktop_notifier

`notify_route_change(event)` — Linux `notify-send` (PY-043).

---

## pingui.monitor.worker

### `LightweightMonitorWorker(QThread)`

Тонка Qt-обгортка над `MonitorLoop`; делегує host CRUD і enabled state.

**Сигнали:**

- `data_received(str, object)` — RouteSnapshot
- `route_changed(str, list, list)` — host, old_ips, new_ips
- `probe_error(str, str)` — host, message

**Методи:**

| Метод | Опис |
|-------|------|
| `hosts()`, `enabled_hosts()` | Thread-safe lists |
| `can_add_host()`, `add_host()`, `remove_host()`, `rename_host()` | |
| `set_host_enabled(host, enabled)` | Max 10 enabled |
| `stop()` | Завершити цикл |
| `run()` | Background loop |

---

## pingui.ui

### `app.run_app(hosts, config_path, interval_seconds, max_hops, timeout, *, quiet=True) -> int`

Qt event loop; повертає exit code.

### `MainWindow(QMainWindow)`

Конструктор: `hosts`, `config_path`, `interval_seconds`, `max_hops`, `timeout`.

### `graph_canvas.GraphCanvas`

`render_route(current_route, avg_ping_fn, previous_route=None)`

### `graph_canvas.ping_color(avg_ms, is_timeout) -> str`

---

## pingui.__main__

```python
def main(argv: list[str] | None = None) -> int
```

Entry point для `python -m pingui` та console script `pingui`.

---

## pingui.logging_setup

```python
def setup_logging(*, verbose: bool = False) -> None
```

Root logger: ERROR (GUI) або DEBUG (`--verbose`).

---

## Java (`io.pingui.persistence`) — P11-010

### `SessionDatabase`

SQLite JDBC для Java GUI (parity з Python `session_db.py`).

| Метод | Опис |
|-------|------|
| `load(host) -> HostSessionData \| null` | Відновити метрики цілі |
| `save(host, data)` | Upsert `host_session` |
| `delete(host)`, `rename(old, new)` | CRUD хоста |
| `insertEvent(type, host, profile, payload, at)` | Append `persistence_event` (P11-011+) |
| `deleteEventsByType(type) -> int` | Purge для P11-014 |
| `close()` | Закрити JDBC |

Schema v3: `host_session` (Python v2 parity) + `persistence_event`. Залежність: `sqlite-jdbc`. Wire у `SessionStore` + `PersistenceEventWriter` (P11-011); CLI `--session-db` (P11-012).

### `PersistenceEventsConfig` (P11-015)

YAML `persistence.events` + CLI `--no-persist-route-change` / `--no-persist-probe-error`. Пріоритет: CLI > YAML > GUI session > default.

### `RouteHistoryPresenter` (P11-020…021)

Панель «Історія змін» у розширеному режимі: `listEvents(ROUTE_CHANGE, host, since)`; перемикач 24h/7d; вибір рядка → read-only replay на `GraphCanvas`.

Меню **Налаштування → База даних…** — чекбокси подій, confirm purge при вимкненні (`PersistencePolicySupport`).

`PersistencePolicy` — які події писати (`route_change`, `probe_error`; default on). `PersistencePolicyHolder` — `active` vs `pending`; `MonitorService` викликає `applyPendingAfterCycle()` після кожного poll-циклу.

### `PersistenceEventWriter`

Запис дискретних подій у `persistence_event` (P11-011). `writeRouteChange`, `writeProbeError`; перевіряє `PersistencePolicyHolder.active()` (P11-013); YAML/GUI — P11-014…015.

### `SessionStore` (persistence)

Опційний `SessionDatabase`: `load`/`save`/`delete` на зміну маршруту, ping history, enabled; `close()` flush + close JDBC.

---

## Скрипти (не importable)

| Скрипт | Призначення |
|--------|-------------|
| `pingui.sh` | Deploy / GUI / destroy; прокидання CLI-прапорців |
| `scripts/ci_venv.sh` | CI pipeline |
| `scripts/check_caps.sh` | ICMP permission smoke |
| `scripts/setup_caps.sh` | Manual setcap |
| `scripts/check_imports.py` | Cycle detection |
| `scripts/check_doc_parity.py` | UK/EN docs parity |
