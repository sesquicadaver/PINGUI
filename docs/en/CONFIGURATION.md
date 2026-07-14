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
| `--no-persist-route-change` | flag | off | Do not write `route_change` to `persistence_event` (PY-P11) |
| `--no-persist-probe-error` | flag | off | Do not write `probe_error` to `persistence_event` (PY-P11) |
| `--export-csv` | Path | — | Export CSV and exit (no GUI/ICMP) |
| `--export-html` | Path | — | Export HTML and exit (no GUI/ICMP) |

YAML (shared with Java SPIKE):

```yaml
persistence:
  session_db: data/ping.db   # when CLI --session-db is omitted
  events:
    route_change: true
    probe_error: true
```

Priority: CLI > YAML > default (both event types on).

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

### Telemetry (P16-040…052, P16-080, P16-090…092)

Profile-level `telemetry:` (Java v2) or top-level (Python `load_telemetry_config`). Priority: **CLI > YAML > defaults**. Default: all sinks **off**; `events_only: true`; `log_aggregates: false`. ADR: [ADR_TELEMETRY.md](ADR_TELEMETRY.md). Example: `java/config/hosts.example.yaml`. Windows preset: `config/hosts.windows.example.yaml` (P16-043: `events_only`, no `jsonl_dir`).

**Java GUI (P16-090…092):** desktop sinks via `TelemetryAttachment`. Menu **Settings → Telemetry…** edits `events_only`, `log_aggregates`, local sqlite/jsonl, syslog(+TLS), GELF(+transport), Loki(URL+site), OTLP(endpoint+service); status shows `toRedactedString()`. Apply updates the active profile and re-wires the bus; disk write uses **Save**. CLI `--telemetry-syslog` / `--telemetry-jsonl` / `--telemetry-otlp` lock the matching fields.

```yaml
profiles:
  noc:
    hosts:
      - "8.8.8.8"
    telemetry:
      events_only: true
      log_aggregates: false
      sqlite: data/telemetry.db
      jsonl_dir: data/telemetry
      syslog:
        host: 127.0.0.1
        port: 514
        tls: false
      gelf:
        host: 127.0.0.1
        port: 12201
        transport: tcp   # tcp | udp
      loki:
        url: http://127.0.0.1:3100
        site: default
      otlp:                         # P16-080 OTLP/HTTP JSON (Collector :4318)
        endpoint: http://127.0.0.1:4318
        service_name: pingui
```

#### YAML `telemetry:` — fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `events_only` | bool | `true` | Remote LOG sinks (syslog/GELF/Loki/OTLP logs) accept events only; no high-freq RTT samples |
| `log_aggregates` | bool | `false` | Optional 5m avg/max RTT as `rtt_aggregate` events (P16-034) |
| `sqlite` | Path | — (off) | Local `SqliteTelemetrySink` (schema v4) |
| `jsonl_dir` | Path | — (off) | `JsonlRotateSink` directory (`telemetry.jsonl.yyyy-MM-dd`) |
| `syslog.host` | str | — | RFC 5424 TCP syslog |
| `syslog.port` | int | `514` | 1…65535 |
| `syslog.tls` | bool | `false` | TLS on syslog TCP |
| `gelf.host` | str | — | Graylog GELF |
| `gelf.port` | int | `12201` | 1…65535 |
| `gelf.transport` | `tcp` \| `udp` | `tcp` | TCP `\0` framing (prod) / UDP (lab) |
| `loki.url` | URL | — | Base or full `/loki/api/v1/push` |
| `loki.site` | str | — | Label `site` (with `job=pingui`, `host`) |
| `otlp.endpoint` | URL | — | OTLP/HTTP base (appends `/v1/logs`, `/v1/metrics`) |
| `otlp.service_name` | str | `pingui` | Resource attribute `service.name` |

Presence of a sink block (`sqlite` / `jsonl_dir` / `syslog` / `gelf` / `loki` / `otlp`) **enables** that sink in the config model. Secrets in URLs/tokens are **not** logged in plaintext (P16-042: `TelemetryConfig.redactUrl` / `redactSecret`). OTLP: events → logs; samples → metrics only when `events_only: false` (Java `OtlpHttpTelemetrySink`).

#### Telemetry CLI (Java + Python)

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `--telemetry-syslog` | `HOST:PORT` or `[IPv6]:PORT` | — | Override `telemetry.syslog` (tls=false) |
| `--telemetry-jsonl` | Path | — | Override `telemetry.jsonl_dir` (not retention) |
| `--telemetry-otlp` | URL | — | Override `telemetry.otlp.endpoint` (service_name=`pingui`) |

#### One-shot / scrape CLI (mostly Java)

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `--telemetry-retention` | int ≥ 1 | — | Cron: purge SQLite (± JSONL) older than N days and exit |
| `--telemetry-jsonl-dir` | Path | — | JSONL dir only for `--telemetry-retention` |
| `--telemetry-dump` | Path `.csv`/`.json` | — | Dump telemetry from `--session-db` and exit |
| `--metrics-port` | int | — (off) | Daemon: Prometheus scrape `127.0.0.1:N` → `PrometheusTelemetrySink` (P16-051) |

Time-series push (`--ts-backend influx|timescale` + Influx/Timescale flags/env) is a separate channel; in Python (P16-052) it goes through `InfluxTelemetrySink` on the telemetry bus. See Time-series below.

Webhook route alerts stay under `alerts.webhook` / `--alert-webhook` (P10); HTTP emit is `WebhookTelemetrySink` (P16-050), ADR_ALERTS payload unchanged.
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

Python (P16-052): `--ts-backend` attaches `InfluxTelemetrySink` to the telemetry bus (no SessionStore dual-emit). Java still uses SessionStore dual-emit until a separate wire.

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
