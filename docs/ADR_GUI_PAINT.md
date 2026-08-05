> **Мова:** Українська · [English](en/ADR_GUI_PAINT.md)

# ADR: GUI paint & window geometry (P24-010)

**Дата:** 2026-08-02  
**Статус:** accepted  
**Гілка:** `beta`

## Контекст

Після зрілого monitor core GUI відставала: jank на `GraphCanvas` (forced buffer churn через `width+1`), forced window resize при toggle Simple/Extended, god-assembly у `MainController.createScene`, важкий I/O на FX thread до `stage.show()`. Фаза 24 (`P24-001`…`009`) впроваджує інкрементальні виправлення; цей ADR фіксує **політику** як SSOT для подальших змін.

Baseline (G0): CI Windows = Monocle Headless (не native Glass) — regression-тести не замінюють manual smoke на desktop Windows.

## Рішення

### 1. Canvas invalidate (без `width+1`)

| Правило | Деталі |
|---------|--------|
| Buffer resize | Лише коли змінюються логічні розміри Region (`resizeCanvasIfNeeded`) |
| Same-size paint | `clearRect` + перемальовка; **заборонено** `setWidth(w+1)` / churn |
| Лічильник | Тести рахують `canvasResizeCount` / `paintCount` (package-visible) |

Native Windows Glass може skip-paint після зняття hack — обов’язковий пункт CHECKLIST (не CI).

### 2. Coalesced redraw

Усі запити на перемальовку йдуть через `requestRedraw()` → один `Platform.runLater` pulse, поки `paintDirty`.

| Вхід | Очікування |
|------|------------|
| Burst `requestRedraw` / drag у одному pulse | ≤ **1** `paintPixels` |
| Layout resize + drag у тому ж pulse | ≤ 1 paint + ≤ 1 buffer resize |
| Off-thread `renderRoute` | Коалесція на FX; без exception |

### 3. Layout cache

`RouteGraphLayout.buildScene` викликається лише коли змінюється route/stats/message (`layoutDirty`). Pan/zoom оновлюють лише `ViewTransform` і просять redraw **без** rebuild scene.

### 4. Color / hover cache

| Кеш | Призначення |
|-----|-------------|
| Static `COLOR_*` з `UiPalette` | Немає `Color.web` у draw loop |
| `nodeFillCache` | Fill за RTT/timeout |
| Hover tip text | Dedupe за `hoveredNodeId` |

### 5. Геометрія при перемиканні режиму

**Старт завжди Simple** (збережений `viewMode` ігнорується). Bounds скидаються до Simple defaults, якщо остання сесія була Extended **або** збережений розмір ≈ `visualBounds` (слід maximize). Перед Simple fit / Extended expand — `setMaximized(false)` / `setFullScreen(false)`. На close у maximized зберігаються останні floating bounds (не розмір екрана). Toggle **у Extended** розширює width+height; divider = `leftPref / stageWidth` (~600 px). Toggle **назад у Simple** стискає Stage до pref chrome.

### 6. SplitPane + geometry persist

| Поле | Збереження |
|------|------------|
| Bounds (x,y,w,h) | `WindowGeometryStore` (XDG / `%APPDATA%`) |
| Split divider | для наступного Extended |
| `UiViewMode` | пишеться на close; **на старті завжди Simple** |

Clamp до `Screen.getVisualBounds()`; invalid → defaults.

**Геометрія vs режим:** cold-start / restart → Simple ~580×700 (+ fit після `show`). Extended defaults ~1400×820 на toggle.

### 7. View assembly + CSS

- Chrome: `io.pingui.ui.view.*` (`MainView.assemble`); `createScene()` тонкий.
- Тема: `UiPalette` + `pingui.css` на Scene; Canvas sync hex з palette; dark — reserved stub (поза фазою 24).

### 8. Deferred startup

```
start → shell Scene → stage.show()
     → background StartupBootstrap.load (YAML/GeoIP/SQLite/SessionStore)
     → FX attachBootstrap → MonitorService + polling
```

До attach: UI disabled + статус «Завантаження…». Shutdown abort-ує late attach (orphan store close). Bootstrap failure → status text, без modal hang.

## Наслідки

- Позитив: стабільний paint budget під drag/pan; відновлювана геометрія; швидший perceived startup.
- Негатив: `MainController` лишається ~850–900 LOC (ціль ≤550 — follow-up); dark mode не product.
- CI Monocle ≠ native Glass — Windows desktop smoke обов’язковий у CHECKLIST.

## Follow-ups (поза P24)

| Тема | Примітка |
|------|----------|
| Dark mode product | Reserved `.theme-dark`; окрема фаза |
| `MainController` ≤550 LOC | Подальший виніс у presenters |
| FXML / MonitorService split | Out of scope фази 24 |

## Посилання

- [ROADMAP.md](ROADMAP.md) — фаза 24
- [CHECKLIST.md](CHECKLIST.md) — GUI smoke + perf / Windows paint
- [JAVA.md](JAVA.md) — UI-шар
- [LIVING_SPEC.md](LIVING_SPEC.md) — матриця P24 → тести
- План: `.omx/plans/gui-architecture-perf-plan.md` (локально)
