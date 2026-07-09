> **Language:** English · [Українська](../MODULES.md)

# PINGUI Module Reference

Public APIs of the `pingui` package (version 0.1.0).

---

## pingui.models

### `HopNode` (frozen dataclass)

| Field | Type | Description |
|-------|------|-------------|
| `hop` | int | Hop number (TTL) |
| `ip` | str | IPv4/IPv6 or `"*"` on timeout |
| `ping_ms` | float \| None | RTT in milliseconds |
| `is_timeout` | bool | True if probe did not respond |

**Class method:** `HopNode.timeout(hop: int) -> HopNode`

### `RouteSnapshot`

| Field | Type |
|-------|------|
| `target` | str — hostname/IP as in config |
| `target_ip` | str — resolved IPv4 or canonical IPv6 |
| `nodes` | list[HopNode] |
| `timestamp` | datetime (UTC) |

**Method:** `route_ips() -> list[str]` — IPs excluding timeouts.

### `HostSessionData`

In-memory state for a single target: `current_route`, `previous_route`, `last_known_by_hop`, `ping_history`, `enabled`.

### Constant

`TIMEOUT_IP = "*"`

---

## pingui.config

### Exceptions

`ConfigError(ValueError)`

### Constants

`MIN_HOSTS = 0`, `MAX_HOSTS = 10`

### Functions

| Function | Description |
|----------|-------------|
| `normalize_host_entry(entry: str) -> str` | Trim + validate (IPv4/IPv6 RFC 5952/hostname) |
| `host_address_kind(normalized) -> HostAddressKind` | IPV4 / IPV6 / HOSTNAME |
| `resolve_trace_target(host) -> str` | IPv4 A-record or canonical IPv6 |
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

### Functions

| Function | Description |
|----------|-------------|
| `check_raw_icmp_permission() -> None` | Raises `RawIcmpPermissionError` |
| `resolve_target(host) -> str` | `resolve_trace_target` (v4/hostname→A, v6 literal) |
| `send_probe(..., transport=None) -> ProbeResult \| None` | Default: ScapyProbeTransport |

### Class

`ScapyProbeTransport` — production ICMP via scapy `sr1`.

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

First observation (`previous_ips` empty) — `changed=False`.

---

## pingui.monitor.route_history

| Function | Description |
|----------|-------------|
| `record_last_known(last_known, nodes)` | Update dict hop→HopNode |
| `route_with_last_known_ips(route, last_known)` | Replace `*` with last known |

---

## pingui.persistence.session_db

### `SessionDatabase`

SQLite persistence for `SessionStore` (optional `--session-db`).

| Method | Description |
|--------|-------------|
| `load(host) -> HostSessionData \| None` | Restore host state |
| `save(host, data)` | Write snapshot + ping history |
| `close()` | Flush and close connection |

Schema v2: JSON for hops, routes, `hop_stats`.

---

## pingui.export.session_report

| Function | Description |
|----------|-------------|
| `export_session_csv(store, path)` | Flat CSV (`ROUTE_CSV_FIELDS`) |
| `export_session_html(store, path)` | Standalone HTML with per-host tables |
| `build_route_rows(store)` | current / inactive / previous routes + stats |

---

## pingui.geoip

| Module | Description |
|--------|-------------|
| `country.configure(enabled, hints_path)` | Offline CIDR→country from YAML (`prefixes`, `prefixes_v6`) |
| `country.lookup_country(ip) -> str \| None` | Longest-prefix match |
| `coordinates.centroid_for_ip(ip)` | Lat/lon for folium |
| `map_builder.build_geo_map(hosts, store)` | HTML for WebEngine tab |

---

## pingui.persistence.timeseries

| Module | Description |
|--------|-------------|
| `factory.create_timeseries_backend(...)` | `influx` / `timescale` / `None` |
| `base.TimeSeriesBackend` | Protocol: `record_rtt`, `record_route` |
| `memory.MemoryTimeSeriesBackend` | In-memory (tests) |

Optional deps: `pip install -e ".[timeseries]"`.

---

## pingui.monitor.hop_stats

| Function | Description |
|----------|-------------|
| `hop_stats_summary(samples) -> HopStatsSummary` | avg RTT, jitter, loss % |

---

## pingui.monitor.session_store

### `SessionStore`

| Method | Description |
|--------|-------------|
| `hosts() -> list[str]` | Keys |
| `can_add_host() -> bool` | < MAX_HOSTS |
| `add_host(host, *, enabled=False) -> str` | |
| `remove_host(host) -> None` | |
| `rename_host(old, new) -> str` | |
| `set_enabled(host, enabled)` | |
| `get(host) -> HostSessionData` | |
| `update_route(host, snapshot)` | Saves previous on change |
| `inactive_route(host) -> list[HopNode]` | Previous + last known |
| `append_ping_samples(host, snapshot)` | Trim to 50/IP |
| `avg_ping(host, ip) -> float \| None` | |
| `extract_route_ips(snapshot)` | static |

`MAX_PING_SAMPLES = 50`

---

## pingui.monitor.monitor_loop

### `MonitorLoop`

Headless monitoring loop on `threading` (no Qt). Callbacks instead of signals.

| Method | Description |
|--------|-------------|
| `hosts()`, `enabled_hosts()` | From `SessionStore` or internal list |
| `add_host()`, `remove_host()`, `rename_host()`, `set_host_enabled()` | Delegates to store when set |
| `start()`, `stop()`, `join()`, `is_running()` | Thread lifecycle |

`MonitorCallbacks` — `on_data_received`, `on_route_changed`, `on_probe_error`.

---

## pingui.monitor.daemon_runner

### `run_headless_monitor(...) -> int`

Foreground/daemon monitoring until SIGINT/SIGTERM; optional PID file.

### `PidFile`

| Method | Description |
|--------|-------------|
| `acquire()` / `release()` | Write/remove PID |
| `stop(path)` / `status(path)` | CLI `stop` / `status` |

With `alert_dispatcher`, route changes trigger webhook/desktop alerts (PY-045).

---

## pingui.monitor.alert_dispatcher

### `build_alert_dispatcher(...)`

Builds webhook + desktop channels with rate limiting.

### `WebhookAlertDispatcher`

POST JSON `RouteChangeEvent`; URLs logged without secrets.

### `AlertRateLimiter`

Max N alerts per host / hour (PY-044).

---

## pingui.monitor.desktop_notifier

`notify_route_change(event)` — Linux `notify-send` (PY-043).

---

## pingui.monitor.worker

### `LightweightMonitorWorker(QThread)`

Thin Qt wrapper over `MonitorLoop`; delegates host CRUD and enabled state.

**Signals:**

- `data_received(str, object)` — RouteSnapshot
- `route_changed(str, list, list)` — host, old_ips, new_ips
- `probe_error(str, str)` — host, message

**Methods:**

| Method | Description |
|--------|-------------|
| `hosts()`, `enabled_hosts()` | Thread-safe lists |
| `can_add_host()`, `add_host()`, `remove_host()`, `rename_host()` | |
| `set_host_enabled(host, enabled)` | Max 10 enabled |
| `stop()` | Stop loop |
| `run()` | Background loop |

---

## pingui.ui

### `app.run_app(hosts, config_path, interval_seconds, max_hops, timeout, *, quiet=True) -> int`

Qt event loop; returns exit code.

### `MainWindow(QMainWindow)`

Constructor: `hosts`, `config_path`, `interval_seconds`, `max_hops`, `timeout`.

### `graph_canvas.GraphCanvas`

`render_route(current_route, avg_ping_fn, previous_route=None)`

### `graph_canvas.ping_color(avg_ms, is_timeout) -> str`

---

## pingui.__main__

```python
def main(argv: list[str] | None = None) -> int
```

Entry point for `python -m pingui` and the console script `pingui`.

---

## pingui.logging_setup

```python
def setup_logging(*, verbose: bool = False) -> None
```

Root logger: ERROR (GUI) or DEBUG (`--verbose`).

---

## Scripts (not importable)

| Script | Purpose |
|--------|---------|
| `pingui.sh` | Deploy / GUI / destroy; forwards CLI flags |
| `scripts/ci_venv.sh` | CI pipeline |
| `scripts/check_caps.sh` | ICMP permission smoke |
| `scripts/setup_caps.sh` | Manual setcap |
| `scripts/check_imports.py` | Cycle detection |
| `scripts/check_doc_parity.py` | UK/EN docs parity |
