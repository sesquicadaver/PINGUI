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
| Entry format | IPv4 or hostname (Latin letters, digits, `-`, `.`) |
| Duplicates | Forbidden (case-insensitive) |
| File encoding | UTF-8 |

Empty list `hosts: []` is valid; targets are added in the GUI.

### Save from GUI

**Save** button calls `save_hosts_config(config_path, hosts)`.
Config path is passed at startup (`MainWindow.config_path`, usually the same file as `--config`).

## CLI

```bash
.venv/bin/python -m pingui [OPTIONS]
# or
./pingui.sh                              # GUI, config/hosts.example.yaml
./pingui.sh --export-csv report.csv      # headless export
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

### GeoIP and map

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `--geoip-hints` | Path | `config/geoip_hints.yaml` | CIDR→country for hop labels |
| `--no-geoip` | flag | off | Disable country hints |
| `--no-geo-map` | flag | off | Disable folium geo-map tab |

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
- invalid hostname;
- duplicate or limit of 10 exceeded;
- DNS error (`resolve_host_ipv4`).

In the GUI errors are appended to the text log with a timestamp.
