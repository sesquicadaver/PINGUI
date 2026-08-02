> **Language:** English · [Українська](../ADR_GUI_PAINT.md)

# ADR: GUI paint & window geometry (P24-010)

**Date:** 2026-08-02  
**Status:** accepted  
**Branch:** `beta`

## Context

After a mature monitor core, the GUI lagged: `GraphCanvas` jank (forced buffer churn via `width+1`), forced window resize on Simple/Extended toggle, god-assembly in `MainController.createScene`, and heavy I/O on the FX thread before `stage.show()`. Phase 24 (`P24-001`…`009`) lands incremental fixes; this ADR records the **policy** as SSOT for later changes.

Baseline (G0): Windows CI uses Monocle Headless (not native Glass) — unit regression does **not** replace manual desktop Windows smoke.

## Decision

### 1. Canvas invalidate (no `width+1`)

| Rule | Details |
|------|---------|
| Buffer resize | Only when Region logical size changes (`resizeCanvasIfNeeded`) |
| Same-size paint | `clearRect` + redraw; **forbid** `setWidth(w+1)` / churn |
| Counters | Tests use package-visible `canvasResizeCount` / `paintCount` |

Native Windows Glass may skip-paint after removing the hack — required CHECKLIST item (not CI).

### 2. Coalesced redraw

All redraw requests go through `requestRedraw()` → one `Platform.runLater` pulse while `paintDirty`.

| Input | Expectation |
|-------|-------------|
| Burst `requestRedraw` / drag in one pulse | ≤ **1** `paintPixels` |
| Layout resize + drag in same pulse | ≤ 1 paint + ≤ 1 buffer resize |
| Off-thread `renderRoute` | Coalesce on FX; no exception |

### 3. Layout cache

`RouteGraphLayout.buildScene` runs only when route/stats/message change (`layoutDirty`). Pan/zoom update `ViewTransform` and request redraw **without** rebuilding the scene.

### 4. Color / hover cache

| Cache | Purpose |
|-------|---------|
| Static `COLOR_*` from `UiPalette` | No `Color.web` in the draw loop |
| `nodeFillCache` | Fill by RTT/timeout |
| Hover tip text | Dedupe by `hoveredNodeId` |

### 5. No forced window resize

Simple ↔ Extended toggle **must not** call `Stage.setWidth/Height` / `applyCss` to “fit” size. Default stage size — once on first show (if no persisted geometry).

### 6. SplitPane + geometry persist

| Field | Persistence |
|-------|-------------|
| Bounds (x,y,w,h) | `WindowGeometryStore` (XDG / `%APPDATA%`) |
| Split divider | Extended mode |
| `UiViewMode` | Last mode |

Clamp to `Screen.getVisualBounds()`; invalid → defaults.

### 7. View assembly + CSS

- Chrome: `io.pingui.ui.view.*` (`MainView.assemble`); thin `createScene()`.
- Theme: `UiPalette` + `pingui.css` on Scene; Canvas hex synced with palette; dark — reserved stub (out of phase 24).

### 8. Deferred startup

```
start → shell Scene → stage.show()
     → background StartupBootstrap.load (YAML/GeoIP/SQLite/SessionStore)
     → FX attachBootstrap → MonitorService + polling
```

Until attach: UI disabled + «Завантаження…» status. Shutdown aborts late attach (orphan store close). Bootstrap failure → status text, no modal hang.

## Consequences

- Positive: stable paint budget under drag/pan; restoreable geometry; faster perceived startup.
- Negative: `MainController` remains ~850–900 LOC (target ≤550 — follow-up); dark mode not a product feature.
- CI Monocle ≠ native Glass — Windows desktop smoke required in CHECKLIST.

## Follow-ups (outside P24)

| Topic | Note |
|-------|------|
| Dark mode product | Reserved `.theme-dark`; separate phase |
| `MainController` ≤550 LOC | Further presenter extraction |
| FXML / MonitorService split | Out of phase 24 scope |

## References

- [ROADMAP.md](ROADMAP.md) — phase 24
- [CHECKLIST.md](CHECKLIST.md) — GUI smoke + perf / Windows paint
- [JAVA.md](JAVA.md) — UI layer
- [LIVING_SPEC.md](LIVING_SPEC.md) — P24 → tests matrix
- Plan: `.omx/plans/gui-architecture-perf-plan.md` (local)
