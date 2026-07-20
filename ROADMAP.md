# ROADMAP — PINGUI

> **Мова:** Українська · [English](ROADMAP.en.md)

**Офіційний індекс планів роботи.** Детальний атомарний план: **[docs/ROADMAP.md](docs/ROADMAP.md)** (UK) · **[docs/en/ROADMAP.md](docs/en/ROADMAP.md)** (EN).

## NEXT

| Поле | Значення |
|------|----------|
| **Поточна задача** | **[P20-004](docs/ROADMAP.md#фаза-20--gui-ux-beta-p1p2)** |
| **Правило** | якщо не `DONE` — `/autopilot` = цей ID; якщо `DONE` — зупинитись / чекати явного нового ID. **Не питати** «який пункт?». |

Повна лінійна черга: [docs/ROADMAP.md — Черга виконання](docs/ROADMAP.md#черга-виконання-лінійна) (фаза 20, #60–71).

**Статус MVP:** ✅ реалізовано (2026-06-26)

**Цільова аудиторія Pro-функцій:** NOC/SRE, мережеві інженери, адміни WAN/MPLS.

- Запуск: `./pingui.sh` / `./pingui.sh --deploy` · `java/pingui-java.sh` (розробка на `beta`; production-зріз — `main`)
- CI: ruff + mypy + pytest · `./gradlew check` (обидві гілки)
- Документація: двомовна `docs/` + `docs/en/`

---

## Фази проекту (статус)

| Фаза | Опис | Статус |
|------|------|--------|
| P0–P8 | Python MVP: venv, ICMP, GUI, CI | ✅ |
| **P9** | Java cross-platform edition | ✅ |
| **9** | IPv6 dual-stack (V6-*) | ✅ |
| **PY** | Python CLI/NOC hardening | ✅ |
| **10** | Оповіщення про зміну маршруту | ✅ |
| **11** | Персистентність і таймлайн (Java) | ✅ |
| **12** | Headless / daemon + systemd | ✅ |
| **13** | Ефективність probe (MTR, smart interval, burst) | ✅ P13-001…050 |
| **14** | GUI для профі (diff, теги, ASN/rDNS, presets) | ✅ |
| **15** | Інтеграції (Prometheus, REST API, export) | ✅ |
| **16** | Телеметрія + LOG-server | ✅ (GUI P16-090…094) |
| **17** | Expert ping / MTU discovery | ✅ |
| **18** | Стабільність режимів probe | ✅ |
| **19** | Production hardening (version, CI, coverage, probe-mode debt) | ✅ **DONE** |
| **20** | GUI UX (Simple feedback, confirm, dirty, polish, settings depth) | 🔄 **P20-004** |

---

## Ціль MVP (досягнуто)

Linux desktop-додаток: моніторинг до 10 цілей, ICMP traceroute, RTT по hop, детекція зміни маршруту, топологічна карта в GUI, RAM-only сесія, CRUD цілей у GUI. Java-редакція — крос-платформний паритет.

---

## Backlog (завершено)

| ID | Задача | Статус |
|----|--------|--------|
| B-01…B-06 | SQLite, export, GeoIP, geo-map, timeseries, jitter/loss (Python) | ✅ |
| J-01…J-06 | Java graph, jpackage, raw ICMP, CI, hop stats | ✅ |
| M-001…M-023 | CLI override, Spotless, Checkstyle | ✅ |
| B-001…B-064 | JUnit, CI, UI split, probe refactor, coverage | ✅ |

---

## Порядок робіт

**Єдине джерело «що далі»:** [docs/ROADMAP.md § NEXT](docs/ROADMAP.md#next--єдине-джерело-правди).  
Історичні sprint-таблиці в `docs/ROADMAP.md` — довідкові, не для вибору задачі.

```mermaid
flowchart LR
  F13[Phase 13 done] --> F14[Phase 14 Pro GUI]
  F14 --> F15[Phase 15 Integrations]
  F15 --> F16[Phase 16 Telemetry]
  F16 --> F17[Phase 17 MTU / Self-check DONE]
```

---

## Структура репозиторію (актуальна)

```
PINGUI/
├── pingui.sh                 # Python launcher
├── java/                     # Java edition
├── src/pingui/               # Python
├── tests/                    # pytest
├── docs/
│   ├── ROADMAP.md            # ← детальний план + NEXT + черга (UK)
│   └── en/ROADMAP.md         # ← детальний план + NEXT + queue (EN)
├── config/
├── scripts/
└── systemd/
```

---

## Definition of Done (на кожну фічу)

1. Код без заглушок у production-шляхах.
2. Unit/contract/integration тест там, де є логіка.
3. `./pingui.sh --deploy` або `./gradlew check` green.
4. Рядок у `docs/LIVING_SPEC.md`.
5. README / DEPLOYMENT / CHANGELOG — якщо змінився запуск або UX.
6. Оновити **NEXT** + рядок у **Черзі виконання** (`[x]` → наступний ID, або **DONE** якщо черга порожня).

---

## Критичний шлях (MVP — завершено)

```
pingui.sh → config/models → icmp/tracer → session_store → worker → main_window/graph → CI
```

Task details: [docs/ROADMAP.md](docs/ROADMAP.md).
