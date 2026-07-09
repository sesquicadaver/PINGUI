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

All routes and ping history are **in RAM only**. Data is lost after closing the window.
The saved YAML contains **only the target list**, not route history.

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

Details: [CONFIGURATION.md](../CONFIGURATION.md).
