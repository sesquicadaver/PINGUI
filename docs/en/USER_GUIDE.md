> **Language:** [Ukrainian](../USER_GUIDE.md) · English

# PINGUI user guide

## Launch

```bash
./pingui.sh
```

First time:

```bash
./pingui.sh --deploy
./pingui.sh
```

Window opens: **«PINGUI — Linux session route monitor»**.

## Interface

```
┌──────────────────┬────────────────────────────────────┐
│ Target list      │  Route graph (top → bottom)        │
│ [ ] 8.8.8.8      │  [Your PC] → hop1 → hop2 → target  │
│ [✓] google.com   │  left — previous (gray)            │
│                  │  right — current                   │
├──────────────────┤                                    │
│ [IP or hostname] │                                    │
│ Add Change       │                                    │
│ Delete Save      │                                    │
├──────────────────┤                                    │
│ Status / Log     │                                    │
└──────────────────┴────────────────────────────────────┘
```

## Target list

### Checkbox

- **Enabled** — worker traces route to target in background.
- **Disabled** — target in list only, no ICMP.
- Up to **10** targets can be traced simultaneously (list limit = active limit).

### Add

1. Enter IPv4 or hostname in the field below.
2. Click **Add** or press Enter.
3. New target appears in list (checkbox disabled).

### Change

1. Select target in list.
2. Edit text in input field (or F2 / double-click row).
3. **Change**.

### Delete

1. Select target.
2. **Delete**.

### Save

Writes current list to YAML config file (path from startup, usually `config/hosts.example.yaml`).
Confirmation — line in log.

## Route graph

- Shown for **selected** target in list.
- **Your PC** — local node at chain start.
- **Hop N** — intermediate router; label with IP and average RTT.
- **`*`** — timeout on hop (no response).
- RTT colors: green (<50 ms), yellow (<150 ms), red (≥150 ms), gray — no data.

### Previous vs current route

On IP chain change:

- **Left column (gray)** — previous route; for timeouts shows **last known IPs**.
- **Right column** — current trace.

## Log

- **ROUTE CHANGE** — warning with «was / now».
- **Error [host]** — no ICMP rights, DNS, timeout, etc.
- List operations (added, changed, deleted, saved).

## Status bar

«Last update [host]: HH:MM:SS» — time of last successful trace for selected target.

## Session data

All routes and ping history — **RAM only**. After closing window data is lost.
Saved YAML contains **target list only**, not route history.

## Common issues

| Problem | Action |
|---------|--------|
| Cannot add target | Check IP/hostname format; limit 10 targets |
| Trace not working | Enable checkbox; check `./scripts/check_caps.sh` |
| Empty graph | Enable checkbox and wait for first cycle (~1 s) |
| Input field gray | 10 targets reached — delete one |

## CLI (advanced options)

```bash
.venv/bin/python -m pingui --interval 2 --max-hops 30 --verbose
```

Details: [CONFIGURATION.md](CONFIGURATION.md).
