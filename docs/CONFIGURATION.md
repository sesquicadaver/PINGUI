# Конфігурація PINGUI

## YAML — список цілей

Файл за замовчуванням: `config/hosts.example.yaml`.

```yaml
hosts:
  - "8.8.8.8"
  - "google.com"
```

### Правила

| Параметр | Значення |
|----------|----------|
| Кількість записів | 0–10 |
| Формат запису | IPv4 або hostname (латиниця, цифри, `-`, `.`) |
| Дублікати | Заборонені (case-insensitive) |
| Кодування файлу | UTF-8 |

Порожній список `hosts: []` — валідний; цілі додаються в GUI.

### Збереження з GUI

Кнопка **Зберегти** викликає `save_hosts_config(config_path, hosts)`.
Шлях конфігу передається при старті (`MainWindow.config_path`, зазвичай той самий файл, що й `--config`).

## CLI

```bash
.venv/bin/python -m pingui [OPTIONS]
# або
./pingui.sh    # еквівалент з config/hosts.example.yaml
```

| Опція | Тип | За замовч. | Опис |
|-------|-----|------------|------|
| `--config` | Path | `config/hosts.example.yaml` | YAML з цілями |
| `--interval` | float | `1.0` | Секунди між повними циклами worker |
| `--max-hops` | int | `20` | Максимальний TTL |
| `--timeout` | float | `0.5` | Таймаут одного ICMP probe (с) |
| `--verbose` | flag | off | DEBUG-лог у stderr |

Валідація: `--interval`, `--timeout` > 0; `--max-hops` ≥ 1.

## Змінні середовища

| Змінна | Коли | Опис |
|--------|------|------|
| `QT_QPA_PLATFORM=offscreen` | Тести / headless | Без дисплея |
| `MPLBACKEND=Agg` | Тести | Non-interactive Matplotlib |
| `QT_LOGGING_RULES` | `./pingui.sh` GUI | Приглушення Qt-шуму |
| `PYTHONWARNINGS=ignore` | `./pingui.sh` GUI | Менше warnings у консолі |

## Константи в коді

| Константа | Модуль | Значення |
|-----------|--------|----------|
| `MIN_HOSTS` | `config.py` | 0 |
| `MAX_HOSTS` | `config.py` | 10 |
| `MAX_PING_SAMPLES` | `session_store.py` | 50 |
| `DEFAULT_MAX_HOPS` | `icmp/tracer.py` | 20 |
| `DEFAULT_TIMEOUT` | `icmp/tracer.py` | 0.5 |

## Помилки конфігурації

Клас `ConfigError` (наслідує `ValueError`):

- файл не знайдено;
- некоректна структура YAML;
- невалідний hostname;
- дублікат або перевищення ліміту 10;
- помилка DNS (`resolve_host_ipv4`).

У GUI помилки додаються в текстовий лог з міткою часу.
