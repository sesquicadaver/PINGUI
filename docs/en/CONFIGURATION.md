> **Language:** [Ukrainian](../CONFIGURATION.md) · English

# PINGUI configuration

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

Empty list `hosts: []` — valid; targets added in GUI.

### Save from GUI

**Save** button calls `save_hosts_config(config_path, hosts)`.
Config path passed at startup (`MainWindow.config_path`, usually same file as `--config`).

## CLI

```bash
.venv/bin/python -m pingui [OPTIONS]
# or
./pingui.sh    # equivalent with config/hosts.example.yaml
```

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `--config` | Path | `config/hosts.example.yaml` | YAML with targets |
| `--interval` | float | `1.0` | Seconds between full worker cycles |
| `--max-hops` | int | `20` | Maximum TTL |
| `--timeout` | float | `0.5` | Single ICMP probe timeout (s) |
| `--verbose` | flag | off | DEBUG log to stderr |

Validation: `--interval`, `--timeout` > 0; `--max-hops` ≥ 1.

## Environment variables

| Variable | When | Description |
|----------|------|-------------|
| `QT_QPA_PLATFORM=offscreen` | Tests / headless | No display |
| `MPLBACKEND=Agg` | Tests | Non-interactive Matplotlib |
| `QT_LOGGING_RULES` | `./pingui.sh` GUI | Suppress Qt noise |
| `PYTHONWARNINGS=ignore` | `./pingui.sh` GUI | Fewer console warnings |

## Code constants

| Constant | Module | Value |
|----------|--------|-------|
| `MIN_HOSTS` | `config.py` | 0 |
| `MAX_HOSTS` | `config.py` | 10 |
| `MAX_PING_SAMPLES` | `session_store.py` | 50 |
| `DEFAULT_MAX_HOPS` | `icmp/tracer.py` | 20 |
| `DEFAULT_TIMEOUT` | `icmp/tracer.py` | 0.5 |

## Configuration errors

Class `ConfigError` (extends `ValueError`):

- file not found;
- invalid YAML structure;
- invalid hostname;
- duplicate or exceeds limit of 10;
- DNS error (`resolve_host_ipv4`).

In GUI errors are appended to the text log with timestamp.
