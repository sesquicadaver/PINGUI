# ROADMAP — PINGUI

**Статус MVP (Sprint 1):** реалізовано — див. `src/pingui/`, `README.md`, `./scripts/ci_venv.sh`.

Базовий документ: **3.txt** (in-memory, PyQt6, 10 хостів, топологічний граф).  
Доповнення з **2.txt**: Linux capabilities, модульність.  
З **1.txt** — лише як backlog (БД, geo-map, експорт), не в MVP.

---

## Ціль MVP

Linux-додаток: моніторинг до 10 цілей, traceroute-подібне тrasування, RTT по hop, детекція зміни маршруту, топологічна карта в GUI, дані тільки в RAM на час сесії.

---

## Структура репозиторію (цільова)

```
PINGUI/
├── pingui.sh
├── pyproject.toml
├── README.md
├── ROADMAP.md
├── docs/
│   └── LIVING_SPEC.md
├── config/
│   └── hosts.example.yaml
├── src/pingui/
│   ├── __init__.py
│   ├── __main__.py
│   ├── config.py
│   ├── models.py
│   ├── icmp/
│   │   ├── __init__.py
│   │   ├── raw_socket.py
│   │   └── tracer.py
│   ├── monitor/
│   │   ├── __init__.py
│   │   ├── session_store.py
│   │   └── worker.py
│   ├── ui/
│   │   ├── __init__.py
│   │   ├── app.py
│   │   ├── main_window.py
│   │   └── graph_canvas.py
│   └── logging_setup.py
├── tests/
│   ├── unit/
│   ├── contract/
│   └── integration/
├── scripts/
│   ├── run_dev.sh          # wrapper → ../pingui.sh
│   └── ci_venv.sh
└── systemd/
    └── pingui-dev.service.example
```

---

## Фаза 0 — Ініціалізація проєкту

| ID | Атомарний таск | Результат | Залежності |
|----|----------------|-----------|------------|
| P0-01 | Створити `pyproject.toml` з Python ≥3.11 | Файл залежностей | — |
| P0-02 | Створити структуру пакету `src/pingui/` | Каркас модулів | P0-01 |
| P0-03 | Додати `.gitignore` | Чистий git | P0-01 |
| P0-04 | Створити venv і `scripts/run_dev.sh` | Відтворюване середовище | P0-01 |
| P0-05 | Написати мінімальний `README.md` | Документація входу | P0-04 |
| P0-06 | Створити `docs/LIVING_SPEC.md` | Living Spec | P0-02 |
| P0-07 | Додати `config/hosts.example.yaml` | Приклад конфігу | P0-02 |
| P0-08 | Налаштувати `ruff` + `mypy` | Статичні чеки | P0-01 |
| P0-09 | Додати `scripts/ci_venv.sh` | CI-скрипт | P0-08 |

---

## Фаза 1 — Доменні моделі та конфіг

| ID | Атомарний таск | Результат | Залежності |
|----|----------------|-----------|------------|
| P1-01 | Dataclass `HopNode` | `models.py` | P0-02 |
| P1-02 | Dataclass `RouteSnapshot` | `models.py` | P1-01 |
| P1-03 | Dataclass `HostSessionData` | `models.py` | P1-01 |
| P1-04 | `load_hosts_config(path) -> list[str]` | `config.py` | P0-07 |
| P1-05 | Unit-тест конфігу | `tests/unit/test_config.py` | P1-04 |
| P1-06 | Оновити `LIVING_SPEC.md` | Spec trace | P1-05 |

---

## Фаза 2 — ICMP / traceroute

| ID | Атомарний таск | Результат | Залежності |
|----|----------------|-----------|------------|
| P2-01 | Обрати scapy vs raw socket | ADR у модулі | P0-05 |
| P2-02 | `resolve_target(host) -> str` | `icmp/raw_socket.py` | P2-01 |
| P2-03 | `send_probe(target_ip, ttl, timeout)` | `icmp/raw_socket.py` | P2-02 |
| P2-04 | Вимір RTT (ms) | `icmp/raw_socket.py` | P2-03 |
| P2-05 | `trace_route(...) -> RouteSnapshot` | `icmp/tracer.py` | P2-04 |
| P2-06 | Timeout-hop як `ip="*"` | `icmp/tracer.py` | P2-05 |
| P2-07 | Зупинка при досягненні target | `icmp/tracer.py` | P2-05 |
| P2-08 | Перевірка CAP_NET_RAW / root | `icmp/raw_socket.py` | P2-03 |
| P2-09 | Contract-тест tracer (mock) | `tests/contract/` | P2-05 |
| P2-10 | Інтеграційний smoke-тест | `tests/integration/` | P2-05 |
| P2-11 | Оновити `LIVING_SPEC.md` | Spec trace | P2-09 |

---

## Фаза 3 — In-memory session store

| ID | Атомарний таск | Результат | Залежності |
|----|----------------|-----------|------------|
| P3-01 | `SessionStore` ініціалізація | `monitor/session_store.py` | P1-03 |
| P3-02 | `update_route(host, snapshot)` | `session_store.py` | P3-01 |
| P3-03 | `append_ping_samples(snapshot)` | `session_store.py` | P3-02 |
| P3-04 | `avg_ping(host, ip)` | `session_store.py` | P3-03 |
| P3-05 | `extract_route_ips(snapshot)` | `session_store.py` | P3-02 |
| P3-06 | Unit-тести session store | `tests/unit/` | P3-05 |
| P3-07 | Оновити `LIVING_SPEC.md` | Spec trace | P3-06 |

---

## Фаза 4 — Background worker (QThread)

| ID | Атомарний таск | Результат | Залежності |
|----|----------------|-----------|------------|
| P4-01 | `LightweightMonitorWorker(QThread)` | `monitor/worker.py` | P0-02 |
| P4-02 | Сигнал `data_received` | `worker.py` | P4-01 |
| P4-03 | Сигнал `route_changed` | `worker.py` | P4-01 |
| P4-04 | Цикл опитування хостів | `worker.py` | P4-02 |
| P4-05 | Кеш і порівняння маршрутів | `worker.py` | P4-03 |
| P4-06 | `stop()` + коректне завершення | `worker.py` | P4-04 |
| P4-07 | Unit-тест детекції зміни маршруту | `tests/unit/` | P4-05 |
| P4-08 | *(Опційно)* ThreadPoolExecutor | `worker.py` | P4-04 |
| P4-09 | Оновити `LIVING_SPEC.md` | Spec trace | P4-07 |

---

## Фаза 5 — GUI (PyQt6)

| ID | Атомарний таск | Результат | Залежності |
|----|----------------|-----------|------------|
| P5-01 | `GraphCanvas` | `ui/graph_canvas.py` | P0-01 |
| P5-02 | Побудова `nx.DiGraph` | `graph_canvas.py` | P5-01 |
| P5-03 | Підписи нод | `graph_canvas.py` | P5-02 |
| P5-04 | Кольорова індикація ping | `graph_canvas.py` | P5-03 |
| P5-05 | Стабільний layout | `graph_canvas.py` | P5-02 |
| P5-06 | `MainWindow` | `ui/main_window.py` | P5-01 |
| P5-07 | `itemClicked` → redraw | `main_window.py` | P5-06 |
| P5-08 | Слот `on_data_received` | `main_window.py` | P4-02 |
| P5-09 | Слот `on_route_changed` | `main_window.py` | P4-03 |
| P5-10 | `closeEvent` | `main_window.py` | P4-06 |
| P5-11 | `app.py` + `__main__.py` | `ui/app.py` | P5-06 |
| P5-12 | Smoke-тест UI (offscreen) | `tests/integration/` | P5-11 |

---

## Фаза 6 — Інтеграція та поліровка

| ID | Атомарний таск | Результат | Залежності |
|----|----------------|-----------|------------|
| P6-01 | Реальна 10-та ціль у config | `hosts.example.yaml` | P0-07 |
| P6-02 | CLI: `--config`, `--interval`, … | `__main__.py` | P5-11 |
| P6-03 | `logging_setup.py` | logging | P4-04 |
| P6-04 | Обробка DNS/permission errors | worker + UI | P2-08 |
| P6-05 | Індикатор «останнє оновлення» | `main_window.py` | P5-08 |
| P6-06 | Manual QA чекліст у README | README | P5-11 |
| P6-07 | Повна матриця `LIVING_SPEC.md` | Spec complete | P6-06 |

---

## Фаза 7 — Якість, CI, anti-stub

| ID | Атомарний таск | Результат | Залежності |
|----|----------------|-----------|------------|
| P7-01 | GitHub Actions CI | `.github/workflows/ci.yml` | P0-09 |
| P7-02 | CI блокує merge при fail | CI policy | P7-01 |
| P7-03 | Anti-stub PR checklist | PR template | P7-01 |
| P7-04 | Contract-тест worker → store | `tests/contract/` | P4-05 |
| P7-05 | Import graph check | `scripts/check_imports.py` | P0-02 |
| P7-06 | Coverage ≥80% core modules | tests | P7-04 |

---

## Фаза 8 — Linux deployment helpers

| ID | Атомарний таск | Результат | Залежності |
|----|----------------|-----------|------------|
| P8-01 | Документувати `setcap` | README | P0-05 |
| P8-02 | `systemd/pingui-dev.service.example` | service file | P5-11 |
| P8-03 | `scripts/check_caps.sh` | smoke script | P8-01 |

---

## Backlog (після MVP)

| ID | Атомарний таск |
|----|----------------|
| B-01 | SQLite persistence між сесіями |
| B-02 | Експорт звітів CSV/HTML |
| B-03 | GeoIP (груба країна) в підписах нод |
| B-04 | Folium geo-map у окремому view |
| B-05 | TimescaleDB/InfluxDB backend |
| B-06 | Jitter/loss statistics по hop |
| B-07 | Редагування списку хostів у GUI |

---

## Критичний шлях

```
P0-01 → P0-02 → P1-01..P1-04 → P2-05 → P3-01..P3-05 → P4-04..P4-06 → P5-06..P5-11 → P6-06
```

---

## Definition of Done (на кожен таск)

1. Код у `src/pingui/`, без заглушок у production-шляху.
2. Unit/contract тест там, де є логіка.
3. `ruff`, `mypy`, `pytest` проходять у venv.
4. Рядок оновлено в `LIVING_SPEC.md`.
5. Якщо таск змінює запуск — оновлено `README` або service file.
