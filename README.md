# PINGUI

Linux desktop-додаток для моніторингу маршрутів і пінгу до 10 цілей одночасно.
Дані зберігаються **лише в RAM** протягом сесії. Візуалізація — топологічний граф
(NetworkX + Matplotlib) у PyQt6.

## Вимоги

- Linux (raw ICMP)
- Python ≥ 3.11
- Права `CAP_NET_RAW` або root для ICMP

## Запуск (з кореня репозиторію)

```bash
chmod +x pingui.sh
./pingui.sh              # GUI
./pingui.sh --deploy     # розгортання venv, cap_net_raw, CI
./pingui.sh --destroy    # видалити .venv та локальні кеші
./pingui.sh --help       # довідка
```

`./pingui.sh` без ключів відкриває лише інтерфейс (тиха підготовка venv/cap_net_raw).

У GUI: **Додати**, **Змінити**, **Видалити**, **Зберегти** — редагування списку цілей (до 10).
Трасування лише для цілей із увімкненим чекбоксом. Список зберігається у YAML конфіг.
Неактивний ланцюг показує останні відомі IP hop-ів (навіть якщо в trace був таймаут).

Перше розгортання:

```bash
./pingui.sh --deploy
```

Опції розгортання: `--skip-tests`, `--force-venv` (лише з `--deploy`).

## CLI

```bash
python -m pingui --help
python -m pingui --config config/hosts.example.yaml --interval 2 --max-hops 15
```

| Параметр | Опис |
|----------|------|
| `--config` | YAML зі списком 0–10 хостів |
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

- [ ] `./pingui.sh --deploy`, потім `./pingui.sh`
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
