# MVP — технічна специфікація PINGUI

Короткий звод вимог для поточного релізу (in-memory desktop monitor).

## Ціль

Linux desktop-додаток (PyQt6): моніторинг до **10 цілей** у списку, traceroute-подібне трасування ICMP,
RTT по hop, детекція зміни маршруту, топологічна візуалізація. Дані **лише в RAM** на час сесії.

## Функціональні вимоги

| ID | Вимога | Реалізація |
|----|--------|------------|
| F-01 | 0–10 цілей у YAML-конфігу | `config.py`, `config/hosts.example.yaml` |
| F-02 | Редагування списку в GUI (додати / змінити / видалити / зберегти) | `ui/main_window.py` |
| F-03 | Трасування лише для цілей з увімкненим чекбоксом | `monitor/worker.py` |
| F-04 | Не більше 10 активних трас одночасно | `worker.set_host_enabled`, `config.MAX_HOSTS` |
| F-05 | Вертикальний граф маршруту (згори вниз) | `ui/graph_canvas.py` |
| F-06 | Попередній маршрут — сіра колонка зліва; поточний — справа | `graph_canvas`, `session_store.inactive_route` |
| F-07 | Last known IP для hop-ів з таймаутом | `monitor/route_history.py` |
| F-08 | Лог змін маршруту та помилок probe | `main_window._on_route_changed`, `_on_probe_error` |
| F-09 | Raw ICMP (CAP_NET_RAW або root) | `icmp/raw_socket.py`, `pingui.sh --deploy` |
| F-10 | CLI: `--config`, `--interval`, `--max-hops`, `--timeout`, `--verbose` | `__main__.py` |

## Нефункціональні вимоги

- Python ≥ 3.11, venv `--copies` для `setcap`
- CI: ruff, mypy, pytest, coverage ≥ 80% (`pyproject.toml`, `scripts/ci_venv.sh`)
- Тести лише у venv; мережеві тести позначені `@pytest.mark.network`
- Без заглушок у production-шляхах (anti-stub review у PR)

## Поза MVP (backlog)

Див. [ROADMAP.md](../ROADMAP.md) — SQLite, geo-map, експорт, jitter/loss тощо.

## Трасування вимог

Повна матриця «вимога → модуль → тести»: [LIVING_SPEC.md](LIVING_SPEC.md).
