# PINGUI

Linux desktop-додаток для моніторингу маршрутів і пінгу до 10 цілей одночасно.
Дані зберігаються **лише в RAM** протягом сесії. Візуалізація — топологічний граф
(NetworkX + Matplotlib) у PyQt6.

## Вимоги

- Linux (raw ICMP)
- Python ≥ 3.11
- Права `CAP_NET_RAW` або root для ICMP

## Розгортання та запуск

```bash
chmod +x scripts/deploy.sh
./scripts/deploy.sh              # перше розгортання (лог + CI-перевірки)
./scripts/deploy.sh --run        # запуск GUI (тихо, без зайвого виводу)
```

`--run` відкриває лише інтерфейс програми. Під капотом — мінімальна перевірка venv/cap_net_raw
без pip-логів, тестів і підказок у терміналі.

Перше повне розгортання (один раз):

```bash
./scripts/deploy.sh
```

Опції: `--skip-tests`, `--force-venv`, `--help`.

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
