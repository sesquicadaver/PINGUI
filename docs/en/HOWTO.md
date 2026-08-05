> **Language:** English · [Українська](../HOWTO.md)

# HOWTO — quick scenarios

Short steps for daily use. Full UI description: [USER_GUIDE.md](USER_GUIDE.md).

## Start the GUI

```bash
cd java
./pingui-java.sh
```

Or from the repository root (if the wrapper is set up):

```bash
./pingui.sh
```

UI language: **Language** menu or `./pingui-java.sh -- --lang en` (codes: `uk`, `en`, `es`, `it`, `pl`, `cs`, `lv`, `lt`, `et`).

## Add a target and start monitoring

1. Enter an IPv4 / hostname in the field under the list.
2. **Add** (or Enter).
3. Enable the checkbox next to the target — tracing / ping starts.
4. Select the row — the route graph appears on the right (in trace mode).

## Simple and Extended view

- **Simple** — compact window (~580 px), core actions.
- **Extended** — wider panel, Expert ping, history, extra tools.

Toggle is on the monitor-mode toolbar (not the **Language** menu).

## Ping only

For slow environments (e.g. Windows / `tracert`) or when you only need RTT:

1. Open the target / probe mode settings.
2. Enable **Ping only** — no full traceroute.

## Save the target list

**Save** (or Ctrl/Cmd+S) writes the list to the YAML profile used at startup.

## SQLite and history

Session is in RAM by default. Optional DB: **Database…** menu or CLI `--session-db`. Route-change history is available in Extended mode after SQLite is enabled.

## In-app help

- **F1** / **Help** menu — short help and shortcuts.
- User details: [USER_GUIDE.md](USER_GUIDE.md).
- Developer and DevOps docs stay UK/EN under `docs/` (other languages are not required).
