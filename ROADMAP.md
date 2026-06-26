# ROADMAP — PINGUI

**Статус MVP:** ✅ реалізовано (2026-06-26)

- Запуск: `./pingui.sh` / `./pingui.sh --deploy`
- CI: ruff + mypy + pytest, coverage ≥ 80%
- Документація: `README.md`, `docs/MVP_SPEC.md`, `docs/LIVING_SPEC.md`

---

## Ціль MVP

Linux-додаток: моніторинг до 10 цілей у списку, ICMP traceroute, RTT по hop, детекція зміни маршруту, топологічна карта в GUI, дані тільки в RAM на час сесії, редагування списку цілей у GUI.

---

## Структура репозиторію (актуальна)

```
PINGUI/
├── pingui.sh
├── pyproject.toml
├── README.md
├── ROADMAP.md
├── docs/
│   ├── README.md           # індекс документації
│   ├── USER_GUIDE.md
│   ├── ARCHITECTURE.md
│   ├── DEPLOYMENT.md
│   ├── DEVELOPMENT.md
│   ├── TESTING.md
│   ├── MODULES.md
│   ├── CONFIGURATION.md
│   ├── CONTRIBUTING.md
│   ├── MVP_SPEC.md
│   └── LIVING_SPEC.md
├── config/
│   └── hosts.example.yaml
├── src/pingui/
│   ├── __init__.py
│   ├── __main__.py
│   ├── config.py
│   ├── models.py
│   ├── logging_setup.py
│   ├── icmp/
│   │   ├── raw_socket.py
│   │   └── tracer.py
│   ├── monitor/
│   │   ├── session_store.py
│   │   ├── route_history.py
│   │   ├── route_change.py
│   │   ├── polling.py
│   │   └── worker.py
│   └── ui/
│       ├── app.py
│       ├── main_window.py
│       └── graph_canvas.py
├── tests/
│   ├── conftest.py
│   ├── unit/
│   ├── contract/
│   └── integration/
├── scripts/
│   ├── ci_venv.sh
│   ├── check_caps.sh
│   ├── check_imports.py
│   └── setup_caps.sh
└── systemd/
    └── pingui-dev.service.example
```

---

## Фази (статус)

| Фаза | Опис | Статус |
|------|------|--------|
| P0 | Ініціалізація проєкту, venv, CI | ✅ |
| P1 | Моделі та конфіг | ✅ |
| P2 | ICMP / traceroute | ✅ |
| P3 | In-memory session store | ✅ |
| P4 | Background worker (QThread) | ✅ |
| P5 | GUI (PyQt6 + graph) | ✅ |
| P6 | Інтеграція, CLI, logging | ✅ |
| P7 | CI, anti-stub, coverage ≥ 80% | ✅ |
| P8 | Linux deployment helpers | ✅ |

---

## Backlog (після MVP)

| ID | Задача |
|----|--------|
| B-01 | SQLite persistence між сесіями |
| B-02 | Експорт звітів CSV/HTML |
| B-03 | GeoIP (груба країна) в підписах нод |
| B-04 | Folium geo-map у окремому view |
| B-05 | TimescaleDB/InfluxDB backend |
| B-06 | Jitter/loss statistics по hop |

---

## Definition of Done (на кожну фічу)

1. Код у `src/pingui/`, без заглушок у production-шляхах.
2. Unit/contract/integration тест там, де є логіка.
3. `./pingui.sh --deploy` проходить у venv.
4. Рядок оновлено в `docs/LIVING_SPEC.md`.
5. Якщо змінюється запуск — оновлено `README.md` або service file.

---

## Критичний шлях (MVP — завершено)

```
pingui.sh → config/models → icmp/tracer → session_store → worker → main_window/graph → CI
```
