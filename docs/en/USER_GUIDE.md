> **Language:** English · [Українська](../USER_GUIDE.md)

# PINGUI User Guide

## Launch

```bash
./pingui.sh
```

First time:

```bash
./pingui.sh --deploy
./pingui.sh
```

The window **"PINGUI — Linux Session Route Monitor"** opens.

## Interface

```
┌──────────────────┬────────────────────────────────────┐
│ Target list      │  Route graph (top → bottom)        │
│ [ ] 8.8.8.8      │  [Your PC] → hop1 → hop2 → target  │
│ [✓] google.com   │  left — previous (gray)            │
│                  │  right — current                   │
├──────────────────┤                                    │
│ [IP or hostname] │                                    │
│ Add  Change      │                                    │
│ Delete  Save     │                                    │
├──────────────────┤                                    │
│ Status / Log     │                                    │
└──────────────────┴────────────────────────────────────┘
```

## Target List

### Checkbox

- **Enabled** — worker traces the route to the target in the background.
- **Disabled** — target stays in the list only, no ICMP.
- Up to **10** targets can be traced simultaneously (list limit = active trace limit).

### Add

1. Enter an IPv4 address or hostname in the field at the bottom.
2. Click **Add** or press Enter.
3. The new target appears in the list (checkbox disabled).

### Change

1. Select a target in the list.
2. Edit the text in the input field (or F2 / double-click the row).
3. Click **Change**.

### Delete

1. Select a target.
2. Click **Delete**.

### Save

Writes the current list to the YAML config file (path from startup, usually `config/hosts.example.yaml`).
Confirmation appears as a line in the log.

## Route Graph

- Displayed for the **selected** target in the list.
- **Your PC** — local node at the start of the chain.
- **Hop N** — intermediate router; label shows IP and average RTT.
- **`*`** — timeout on hop (no response).
- RTT colors: green (<50 ms), yellow (<150 ms), red (≥150 ms), gray — no data.

### Previous vs Current Route

When the IP chain changes:

- **Left column (gray)** — previous route; **last known IPs** shown for timeouts.
- **Right column** — current trace.

## Log

- **ROUTE CHANGE** — warning with "was / now".
- **Error [host]** — no ICMP permissions, DNS failure, timeout, etc.
- List operations (added, changed, deleted, saved).

## Status Bar

"Last update [host]: HH:MM:SS" — time of the last successful trace for the selected target.

## Session Data

By default routes and ping history live **in RAM** (lost when the window closes).
Optionally: `--session-db` / **Settings → Database…** — SQLite keeps metrics and events across restarts.
Saved YAML holds the **target list** (and persistence path/policy if set), not full hop history without a DB.

## Common Issues

| Problem | Action |
|---------|--------|
| Cannot add target | Check IP/hostname format; 10-target limit |
| Trace not working | Enable checkbox; run `./scripts/check_caps.sh` |
| Empty graph | Enable checkbox and wait for the first cycle (~1 s) |
| Input field grayed out | 10 targets reached — delete one |

## CLI (Advanced Options)

```bash
.venv/bin/python -m pingui --interval 2 --max-hops 30 --verbose
```

Details: [CONFIGURATION.md](CONFIGURATION.md).

## Pro / NOC workflow (Java)

Target scenario for an on-call NOC/SRE shift on the **Java edition** (`cd java && ./pingui-java.sh`). The basic Python GUI above remains for quick session monitoring; below is the pro loop.

### Launch Java GUI

```bash
cd java
./pingui-java.sh -- --config config/hosts.example.yaml --session-db data/ping.db
```

ICMP / raw permissions: see [DEPLOYMENT.md](DEPLOYMENT.md) and `./scripts/check_caps.sh`. UI details: [JAVA.md](JAVA.md).

### Typical shift (15–30 min)

1. **Enable targets** with checkboxes (or `enabled: true` in YAML) — without this there is no trace and no SQLite writes.
2. **Extended view** — graph + diff panel “was → now” (Δ RTT) and **Route history** (24h / 7d); click an event to replay the route on the graph.
3. **Tags** — **Tags** button on the host; filter chips above the list (e.g. `dc`, `vpn`, `customer-x`). Save YAML (**Save**).
4. **Hop labels** on the graph (after IP): country (GeoIP hints) → ASN (`asn_hints.yaml`) → rDNS (async PTR, 5 min TTL). Offline hints: [CONFIGURATION.md](CONFIGURATION.md#geoip-and-map).
5. **Expert ping** — **Expert** checkbox → **Exten.** → presets **MTU probe / DF / DSCP / Burst** from `ping_presets.yaml` (AF `-4`/`-6` is kept). Each preset only fills `ping(8)` flags and shows summary/expect in the dialog. MTU sweep — host-list **MTU** or Expert **MTU wizard…** (Apply → `-M do -s`); the «MTU probe» preset is not the wizard.
6. **Alerts** — webhook / desktop on route change (`alerts:` in YAML or `--alert-webhook`). Per-host rate limit: [CONFIGURATION.md](CONFIGURATION.md).
7. **Persistence** — `--session-db` or **Settings → Database…**; history and `hop_stats` survive restart. Export: `--export-report report.csv`.

### Headless NOC (no GUI)

Same monitor without JavaFX — useful on the shift server:

```bash
cd java
./pingui-java.sh -- --daemon --config config/hosts.example.yaml \
  --session-db data/ping.db --pid-file /tmp/pingui-java.pid \
  --alert-webhook https://hooks.example.com/pingui
```

Status / stop: `--status` / `--stop`. systemd: `systemd/pingui-java.service.example`. Full section: [DEPLOYMENT.md § Java NOC](DEPLOYMENT.md#java-noc-headless-daemon-p12).

### Handoff checklist

| Check | Expectation |
|-------|-------------|
| Enabled hosts | Log shows updates / no constant “Error” lines |
| Route change | Row in **Route history** + webhook (if configured) |
| SQLite | `--session-db` file grows; graph restores after restart |
| Daemon (if used) | `--status` shows running; alerts arrive |
