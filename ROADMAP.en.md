# ROADMAP — PINGUI

> **Language:** English · [Українська](ROADMAP.md)

**Official work plan index.** Detailed atomic plan: **[docs/en/ROADMAP.md](docs/en/ROADMAP.md)** (EN) · **[docs/ROADMAP.md](docs/ROADMAP.md)** (UK).

## NEXT

| Field | Value |
|------|----------|
| **Current task** | **[P20-007](docs/en/ROADMAP.md#phase-20--gui-ux-beta-p1p2)** |
| **Rule** | if not `DONE` — `/autopilot` = this ID; if `DONE` — stop / wait for an explicit new ID. **Do not ask** “which item?”. |

Full linear queue: [docs/en/ROADMAP.md — Execution queue](docs/en/ROADMAP.md#execution-queue-linear) (phase 20, #60–71).

**MVP status:** ✅ implemented (2026-06-26)

**Target audience for Pro features:** NOC/SRE, network engineers, WAN/MPLS admins.

- Launch: `./pingui.sh` / `./pingui.sh --deploy` · `java/pingui-java.sh` (develop on `beta`; production snapshot — `main`)
- CI: ruff + mypy + pytest · `./gradlew check` (both branches)
- Documentation: bilingual `docs/` + `docs/en/`

---

## Project phases (status)

| Phase | Description | Status |
|-------|-------------|--------|
| P0–P8 | Python MVP: venv, ICMP, GUI, CI | ✅ |
| **P9** | Java cross-platform edition | ✅ |
| **9** | IPv6 dual-stack (V6-*) | ✅ |
| **PY** | Python CLI/NOC hardening | ✅ |
| **10** | Route change alerts | ✅ |
| **11** | Persistence and timeline (Java) | ✅ |
| **12** | Headless / daemon + systemd | ✅ |
| **13** | Probe efficiency (MTR, smart interval, burst) | ✅ P13-001…050 |
| **14** | Pro GUI (diff, tags, ASN/rDNS, presets) | ✅ |
| **15** | Integrations (Prometheus, REST API, export) | ✅ |
| **16** | Telemetry + LOG-server | ✅ (GUI P16-090…094) |
| **17** | Expert ping / MTU discovery | ✅ |
| **18** | Probe mode stability | ✅ |
| **19** | Production hardening (version, CI, coverage, probe-mode debt) | ✅ **DONE** |
| **20** | GUI UX (Simple feedback, confirm, dirty, polish, settings depth) | 🔄 **P20-007** |

---

## MVP goal (achieved)

Linux desktop app: monitor up to 10 targets, ICMP traceroute, RTT per hop, route change detection, topological map in GUI, RAM-only session, GUI CRUD. Java edition — cross-platform parity.

---

## Backlog (completed)

| ID | Task | Status |
|----|------|--------|
| B-01…B-06 | SQLite, export, GeoIP, geo-map, timeseries, jitter/loss (Python) | ✅ |
| J-01…J-06 | Java graph, jpackage, raw ICMP, CI, hop stats | ✅ |
| M-001…M-023 | CLI override, Spotless, Checkstyle | ✅ |
| B-001…B-064 | JUnit, CI, UI split, probe refactor, coverage | ✅ |

---

## Work order

**Single source of “what’s next”:** [docs/en/ROADMAP.md § NEXT](docs/en/ROADMAP.md#next--single-source-of-truth).  
Historical sprint tables in `docs/en/ROADMAP.md` are reference-only, not for task selection.

```mermaid
flowchart LR
  F13[Phase 13 done] --> F14[Phase 14 Pro GUI]
  F14 --> F15[Phase 15 Integrations]
  F15 --> F16[Phase 16 Telemetry]
  F16 --> F17[Phase 17 MTU / Self-check DONE]
```

---

## Repository structure (current)

```
PINGUI/
├── pingui.sh                 # Python launcher
├── java/                     # Java edition
├── src/pingui/               # Python
├── tests/                    # pytest
├── docs/
│   ├── ROADMAP.md            # ← detailed plan + NEXT + queue (UK)
│   └── en/ROADMAP.md         # ← detailed plan + NEXT + queue (EN)
├── config/
├── scripts/
└── systemd/
```

---

## Definition of Done (per feature)

1. No stubs in production paths.
2. Unit/contract/integration tests where logic exists.
3. `./pingui.sh --deploy` or `./gradlew check` green.
4. Row in `docs/LIVING_SPEC.md`.
5. README / DEPLOYMENT / CHANGELOG — if launch or UX changed.
6. Update **NEXT** + the matching **Execution queue** row (`[x]` → next ID, or **DONE** if the queue is empty).

---

## Critical path (MVP — complete)

```
pingui.sh → config/models → icmp/tracer → session_store → worker → main_window/graph → CI
```

Task details: [docs/en/ROADMAP.md](docs/en/ROADMAP.md).
