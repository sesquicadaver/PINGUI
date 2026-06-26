# Living Specification — PINGUI

Матриця відповідності «вимога → модуль → тести». Оновлюється при кожній фічі.

**Останнє оновлення:** 2026-06-26 · **Coverage:** ≥ 80% (pytest-cov, `fail_under = 80`) · **Тестів:** 72+ (unit + contract + integration, без `network`)

Джерело вимог MVP: [MVP_SPEC.md](MVP_SPEC.md).

---

## Конфігурація та моделі

| Вимога | Модуль | Тести | Статус |
|--------|--------|-------|--------|
| Завантаження 0–10 хостів з YAML | `config.load_hosts_config` | `tests/unit/test_config.py` | done |
| Збереження списку хостів у YAML | `config.save_hosts_config` | `tests/unit/test_config.py` | done |
| Валідація in-session хоста | `config.validate_session_host` | `tests/unit/test_config.py` | done |
| DNS → IPv4 resolve | `config.resolve_host_ipv4` | `tests/unit/test_config_resolve.py` | done |
| HopNode, RouteSnapshot, HostSessionData | `models.py` | через unit/contract | done |

---

## ICMP і traceroute

| Вимога | Модуль | Тести | Статус |
|--------|--------|-------|--------|
| Raw ICMP probe + RTT | `icmp/raw_socket.py` | `tests/unit/test_raw_socket.py`, `tests/contract/test_tracer.py` | done |
| Перевірка CAP_NET_RAW | `icmp/raw_socket.check_raw_icmp_permission` | manual, `scripts/check_caps.sh` | done |
| trace_route TTL 1..N, timeout `*` | `icmp/tracer.py` | `tests/contract/test_tracer.py` | done |
| Мережева інтеграція tracer | `icmp/tracer.py` | `tests/integration/test_tracer_network.py` (network) | done |

---

## Моніторинг і сесія

| Вимога | Модуль | Тести | Статус |
|--------|--------|-------|--------|
| poll_host_route (pure logic) | `monitor/polling.py` | `tests/unit/test_polling.py` | done |
| Детекція зміни маршруту | `monitor/route_change.py` | `tests/unit/test_route_change.py` | done |
| Last known IP по hop | `monitor/route_history.py` | `tests/unit/test_route_history.py` | done |
| SessionStore: route, ping history, previous | `monitor/session_store.py` | `tests/unit/test_session_store.py` | done |
| inactive_route з last known | `monitor/session_store.py` | `tests/unit/test_session_store.py` | done |
| Worker: add/rename/remove, enabled only | `monitor/worker.py` | `tests/unit/test_worker.py` | done |
| Worker: цикл run(), сигнали Qt | `monitor/worker.py` | `tests/integration/test_worker_run.py` | done |
| Worker → store контракт | `monitor/worker.py`, `session_store.py` | `tests/contract/test_worker_store.py` | done |

---

## GUI

| Вимога | Модуль | Тести | Статус |
|--------|--------|-------|--------|
| GraphCanvas: layout, кольори ping | `ui/graph_canvas.py` | `tests/unit/test_graph_canvas.py` | done |
| MainWindow: список, CRUD, збереження | `ui/main_window.py` | `tests/integration/test_ui_smoke.py` | done |
| Чекбокси enabled, лог, status | `ui/main_window.py` | `tests/integration/test_ui_smoke.py` | done |
| App bootstrap + quiet logging | `ui/app.py`, `logging_setup.py` | omit coverage (entry) | done |
| Headless Qt fixture | `tests/conftest.py` | autouse для integration | done |

---

## CLI та розгортання

| Вимога | Модуль / скрипт | Тести | Статус |
|--------|-----------------|-------|--------|
| CLI парсинг і валідація | `__main__.py` | `tests/unit/test_main.py`, `test_main_cli_validation.py` | done |
| Єдина точка входу | `pingui.sh` | manual QA | done |
| CI pipeline (venv) | `scripts/ci_venv.sh`, `.github/workflows/ci.yml` | CI on push/PR | done |
| Import graph (no cycles) | `scripts/check_imports.py` | deploy / CI | done |
| cap_net_raw helper | `scripts/setup_caps.sh`, `scripts/check_caps.sh` | manual | done |
| systemd приклад | `systemd/pingui-dev.service.example` | — | done |

---

## Anti-stub checklist (PR)

- [ ] Немає необґрунтованих `pass` / `return None` / `Mock` у `src/pingui/`
- [ ] Нові модулі мають відповідні тести або запис у цій матриці
- [ ] `docs/LIVING_SPEC.md` оновлено при зміні поведінки
- [ ] `./pingui.sh --deploy` проходить у venv
