# PINGUI

Linux desktop-додаток для моніторингу маршрутів і пінгу до 10 цілей одночасно.
Дані зберігаються **лише в RAM** протягом сесії. Візуалізація — топологічний граф
(NetworkX + Matplotlib) у PyQt6.

## Вимоги

- Linux (raw ICMP)
- Python ≥ 3.11
- Права `CAP_NET_RAW` або root для ICMP

## Швидкий старт (venv)

```bash
chmod +x scripts/run_dev.sh scripts/ci_venv.sh scripts/check_caps.sh
./scripts/run_dev.sh
```

Або вручну:

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -e ".[dev]"
python -m pingui --config config/hosts.example.yaml
```

## Raw ICMP capabilities

Без sudo надайте capability інтерпретатору venv:

```bash
sudo setcap cap_net_raw+ep .venv/bin/python3
./scripts/check_caps.sh
```

## CLI

```bash
python -m pingui --help
python -m pingui --config config/hosts.example.yaml --interval 2 --max-hops 15
```

| Параметр | Опис |
|----------|------|
| `--config` | YAML зі списком 1–10 хостів |
| `--interval` | Пауза між циклами (с) |
| `--max-hops` | Максимум TTL |
| `--timeout` | Таймаут probe (с) |
| `--verbose` | Debug-лог |

## CI (локально)

```bash
./scripts/ci_venv.sh
python scripts/check_imports.py
```

## Manual QA чекліст

- [ ] Запуск з venv і валідним `config/hosts.example.yaml`
- [ ] Граф оновлюється для виділеного хоста
- [ ] Перемикання хоста у списку перемальовує граф
- [ ] Лог фіксує зміну маршруту (якщо трапиться)
- [ ] Закриття вікна завершує worker без зависання
- [ ] Без cap_net_raw — зрозуміле повідомлення про помилку

## Структура

Див. [ROADMAP.md](ROADMAP.md) та [docs/LIVING_SPEC.md](docs/LIVING_SPEC.md).

## Джерела ТЗ

- `1.txt` — серверна архітектура (backlog)
- `2.txt` — десктоп PyQt6 + SQLite (спрощено)
- `3.txt` — **MVP**: in-memory, 10 хостів, топограф
