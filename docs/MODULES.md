> **Мова:** Українська · [English](en/MODULES.md)

# Довідник модулів PINGUI

Публічні API пакета `pingui` (версія 0.1.0).

---

## pingui.models

### `HopNode` (frozen dataclass)

| Поле | Тип | Опис |
|------|-----|------|
| `hop` | int | Номер hop (TTL) |
| `ip` | str | IPv4 або `"*"` при timeout |
| `ping_ms` | float \| None | RTT у мілісекундах |
| `is_timeout` | bool | True якщо probe не відповів |

**Class method:** `HopNode.timeout(hop: int) -> HopNode`

### `RouteSnapshot`

| Поле | Тип |
|------|-----|
| `target` | str — hostname/IP як у конфігу |
| `target_ip` | str — resolved IPv4 |
| `nodes` | list[HopNode] |
| `timestamp` | datetime (UTC) |

**Method:** `route_ips() -> list[str]` — IP без timeout.

### `HostSessionData`

In-memory стан для однієї цілі: `current_route`, `previous_route`, `last_known_by_hop`, `ping_history`, `enabled`.

### Константа

`TIMEOUT_IP = "*"`

---

## pingui.config

### Exceptions

`ConfigError(ValueError)`

### Константи

`MIN_HOSTS = 0`, `MAX_HOSTS = 10`

### Functions

| Function | Опис |
|----------|------|
| `normalize_host_entry(entry: str) -> str` | Trim + validate |
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

| Function | Опис |
|----------|------|
| `check_raw_icmp_permission() -> None` | Raises `RawIcmpPermissionError` |
| `resolve_target(host) -> str` | Alias resolve_host_ipv4 |
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

TTL 1..max_hops; stop on `is_target` or max_hops.

---

## pingui.monitor.polling

### `HostPollOutcome` (frozen dataclass)

`snapshot`, `error`, `route_changed`, `old_ips`, `new_ips`, `current_ips`

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

Перше спостереження (`previous_ips` empty) — `changed=False`.

---

## pingui.monitor.route_history

| Function | Опис |
|----------|------|
| `record_last_known(last_known, nodes)` | Update dict hop→HopNode |
| `route_with_last_known_ips(route, last_known)` | Replace `*` with last known |

---

## pingui.monitor.session_store

### `SessionStore`

| Method | Опис |
|--------|------|
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

## pingui.monitor.worker

### `LightweightMonitorWorker(QThread)`

**Signals:**

- `data_received(str, object)` — RouteSnapshot
- `route_changed(str, list, list)` — host, old_ips, new_ips
- `probe_error(str, str)` — host, message

**Methods:**

| Method | Опис |
|--------|------|
| `hosts()`, `enabled_hosts()` | Thread-safe lists |
| `can_add_host()`, `add_host()`, `remove_host()`, `rename_host()` | |
| `set_host_enabled(host, enabled)` | Max 10 enabled |
| `stop()` | End loop |
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

Entry point for `python -m pingui` and console script `pingui`.

---

## pingui.logging_setup

```python
def setup_logging(*, verbose: bool = False) -> None
```

Root logger: ERROR (GUI) or DEBUG (`--verbose`).

---

## Scripts (not importable)

| Script | Призначення |
|--------|-------------|
| `pingui.sh` | Deploy / GUI / destroy |
| `scripts/ci_venv.sh` | CI pipeline |
| `scripts/check_caps.sh` | ICMP permission smoke |
| `scripts/setup_caps.sh` | Manual setcap |
| `scripts/check_imports.py` | Cycle detection |
