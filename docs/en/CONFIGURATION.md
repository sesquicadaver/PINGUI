> **Language:** English · [Українська](../CONFIGURATION.md)

# PINGUI Configuration

## YAML — target list

Default file: `config/hosts.example.yaml`.

```yaml
hosts:
  - "8.8.8.8"
  - "google.com"
```

### Rules

| Parameter | Value |
|-----------|-------|
| Entry count | 0–10 |
| Entry format | IPv4, IPv6 literal (RFC 5952, e.g. `2001:db8::1` or `[::1]`) or hostname |
| Duplicates | Forbidden (case-insensitive) |
| File encoding | UTF-8 |

Empty list `hosts: []` is valid; targets are added in the GUI.

### Save from GUI

**Save** button calls `save_hosts_config(config_path, hosts)`.
Config path is passed at startup (`MainWindow.config_path`, usually the same file as `--config`).

## CLI

### Subcommands (PY-023)

```bash
.venv/bin/python -m pingui run              # GUI (default)
.venv/bin/python -m pingui export --csv out.csv
.venv/bin/python -m pingui monitor --config config/hosts.example.yaml
.venv/bin/python -m pingui daemon --session-db data/ping.db --pid-file /tmp/pingui.pid
.venv/bin/python -m pingui stop --pid-file /tmp/pingui.pid
.venv/bin/python -m pingui status --pid-file /tmp/pingui.pid
```

Flat legacy CLI (`--export-csv`, `--session-db` without subcommand) remains for backward compatibility.

### Launcher

```bash
./pingui.sh                              # GUI, config/hosts.example.yaml
./pingui.sh --export-csv report.csv      # headless export
./pingui.sh monitor --config config/hosts.example.yaml
./pingui.sh -- --session-db data/ping.db # explicit launcher/CLI separator
```

### Core options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `--config` | Path | `config/hosts.example.yaml` | YAML with targets |
| `--interval` | float | `1.0` | Seconds between full worker cycles |
| `--max-hops` | int | `20` | Maximum TTL |
| `--timeout` | float | `0.5` | Timeout per ICMP probe (s) |
| `--verbose` | flag | off | DEBUG log to stderr |

### Persistence and export

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `--session-db` | Path | — | SQLite: routes/ping across sessions |
| `--export-csv` | Path | — | Export CSV and exit (no GUI/ICMP) |
| `--export-html` | Path | — | Export HTML and exit (no GUI/ICMP) |

### Alerts (PY-042…044)

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `--alert-webhook` | URL | — | POST JSON `RouteChangeEvent` on route change |
| `--desktop-alerts` | flag | off | Linux `notify-send` on route change |
| `--alert-rate-limit` | int | `10` | Max alerts per host / hour |

URL secrets are not logged; webhook failures are WARNING only (process continues).

### Java edition (`./pingui-java.sh`)

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `--alert-webhook` | URL | — | POST JSON `RouteChangeEvent` on route change |
| `--desktop-alerts` | flag | off | Linux `notify-send` on route change |
| `--alert-rate-limit` | int | `10` | Max alerts per host / hour |

CLI overrides YAML. In v2 profiles:

```yaml
profiles:
  noc:
    hosts:
      - "8.8.8.8"
    alerts:
      desktop: true
      webhook: https://hooks.example.com/ping
      rate_limit: 10
    # legacy alias:
    alert_webhook: https://hooks.example.com/ping
```

Alerts are disabled by default (`NoOp` dispatcher).

### GeoIP and map

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `--geoip-hints` | Path | `config/geoip_hints.yaml` | CIDR→country for hop labels (`prefixes` v4, `prefixes_v6` v6) |
| `--no-geoip` | flag | off | Disable country hints |
| `--asn-hints` | Path | `config/asn_hints.yaml` | CIDR→ASN+org for hop labels (`{asn, org}`) |
| `--no-asn` | flag | off | Disable ASN hints |
| `--asn-timeout-ms` | int | `2000` | Reserved for future whois fallback |
| `--no-geo-map` | flag | off | Disable folium geo-map tab |

Expert ping presets (Java GUI, P14-040): `config/ping_presets.yaml` beside the hosts config (or CWD `config/ping_presets.yaml`); otherwise the bundled resource. Exactly 4 presets (`mtu_probe`, `df`, `dscp`, `burst`); buttons in `PingExpertDialog` apply args and keep the current AF (`-4`/`-6`).

### Time-series (optional extra: `pip install -e ".[timeseries]"`)

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `--ts-backend` | `influx` \| `timescale` | — | Backend for RTT/route metrics |
| `--influx-url` | str | env `INFLUXDB_URL` | InfluxDB URL |
| `--influx-token` | str | env `INFLUXDB_TOKEN` | Token |
| `--influx-org` | str | env `INFLUXDB_ORG` | Org |
| `--influx-bucket` | str | env `INFLUXDB_BUCKET` | Bucket |
| `--timescale-dsn` | str | env `PINGUI_TIMESCALE_DSN` | PostgreSQL/Timescale DSN |

Validation: `--interval`, `--timeout` > 0; `--max-hops` ≥ 1.

Export mode (`--export-csv` / `--export-html`) does not require raw ICMP and does not start the GUI.

## Environment variables

| Variable | When | Description |
|----------|------|-------------|
| `QT_QPA_PLATFORM=offscreen` | Tests / headless | No display |
| `MPLBACKEND=Agg` | Tests | Non-interactive Matplotlib |
| `QT_LOGGING_RULES` | `./pingui.sh` GUI | Suppress Qt noise |
| `PYTHONWARNINGS=ignore` | `./pingui.sh` GUI | Fewer warnings in console |
| `INFLUXDB_URL` | `--ts-backend influx` | InfluxDB endpoint |
| `INFLUXDB_TOKEN` | `--ts-backend influx` | InfluxDB token |
| `INFLUXDB_ORG` | `--ts-backend influx` | InfluxDB org |
| `INFLUXDB_BUCKET` | `--ts-backend influx` | InfluxDB bucket |
| `PINGUI_TIMESCALE_DSN` | `--ts-backend timescale` | PostgreSQL/Timescale DSN |

## Code constants

| Constant | Module | Value |
|----------|--------|-------|
| `MIN_HOSTS` | `config.py` | 0 |
| `MAX_HOSTS` | `config.py` | 10 |
| `MAX_PING_SAMPLES` | `session_store.py` | 50 |
| `DEFAULT_MAX_HOPS` | `icmp/tracer.py` | 20 |
| `DEFAULT_TIMEOUT` | `icmp/tracer.py` | 0.5 |

## Configuration errors

`ConfigError` class (extends `ValueError`):

- file not found;
- invalid YAML structure;
- invalid hostname or IPv6 (zone ID `%iface` rejected);
- duplicate or limit of 10 exceeded;
- DNS error (`resolve_host_ipv4`).

In the GUI errors are appended to the text log with a timestamp.
