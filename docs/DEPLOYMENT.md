# Розгортання PINGUI

## Системні вимоги

- **ОС:** Linux (x86_64 або arm64)
- **Python:** ≥ 3.11
- **Дисплей:** X11 або Wayland (для GUI)
- **Мережа:** доступ до цілей; raw ICMP socket

### APT-пакети (автоматично з `--deploy`)

`python3-venv`, `libcap2-bin`, `libegl1`, `libgl1`, `libxkbcommon0`, `libxcb-cursor0`

## Python: pingui.sh

```bash
chmod +x pingui.sh
./pingui.sh --deploy    # перше розгортання (Python)
./pingui.sh             # Python GUI
./pingui.sh --destroy   # очистити venv і кеші Python
./pingui.sh --help
```

## Java: java/pingui-java.sh (Linux/macOS) або pingui-java.bat (Windows)

**Linux / macOS**

```bash
cd java
chmod +x pingui-java.sh
./pingui-java.sh --build
./pingui-java.sh
```

**Windows**

```bat
cd java
pingui-java.bat --build
pingui-java.bat
```

Python `--destroy` не стосується Gradle-артефактів у `java/build/`.

### Режим `--deploy`

1. Перевірка Linux і Python ≥ 3.11
2. `apt-get install` відсутніх системних пакетів (якщо є `apt-get`)
3. Створення `.venv` з **`python3 -m venv --copies`** (реальні бінарники для setcap)
4. `pip install -e ".[dev]"`
5. `sudo setcap cap_net_raw+ep` на `.venv/bin/python*`
6. ruff → mypy → pytest (coverage ≥ 80%) → `check_imports.py`

Опції:

- `--skip-tests` — без quality gates
- `--force-venv` — перестворити venv

### Режим GUI (за замовч.)

Тиха підготовка: якщо немає venv або cap — створює/налаштовує без зайвого виводу.
Запуск: `python -m pingui --config config/hosts.example.yaml`.

### Режим `--destroy`

Видаляє: `.venv`, `.pytest_cache`, `.mypy_cache`, `.ruff_cache`, `.coverage`, `build/`, `dist/`, `*.egg-info`, `__pycache__`.
**Не** видаляє системні apt-пакети та `setcap` на системному Python.

## CAP_NET_RAW

ICMP raw socket потребує capability або root.

```bash
# Автоматично
./pingui.sh --deploy

# Перевірка
./scripts/check_caps.sh

# Ручний setcap (якщо deploy не спрацював)
./scripts/setup_caps.sh
```

**Важливо:** `setcap` не працює на symlink. Venv має бути `--copies`.
Якщо `.venv/bin/python3` — symlink на `/usr/bin/python3`, `setup_caps.sh` виведе інструкцію.

Перевірка з Python:

```python
from pingui.icmp.raw_socket import check_raw_icmp_permission
check_raw_icmp_permission()
```

## systemd (опційно)

Приклад unit: `systemd/pingui-dev.service.example`

```ini
WorkingDirectory=/path/to/PINGUI
ExecStart=/path/to/PINGUI/pingui.sh
```

Перед першим запуском сервісу: `./pingui.sh --deploy` від імені користувача сервісу.

## GitHub Actions

`.github/workflows/ci.yml`:

- Ubuntu latest, Python 3.11
- Qt/OpenGL runtime libs
- `bash scripts/ci_venv.sh` + import graph

CI **не** виконує setcap (мережеві тести виключені маркером `not network`).

## Обмеження середовища

- **Тільки venv:** не встановлюйте пакет глобально для розробки.
- **Віртуальне середовище для тестів:** `./pingui.sh --deploy` або `./scripts/ci_venv.sh`.
- Production GUI — на машині користувача з дисплеєм, не в headless CI.

## Troubleshooting

| Симптом | Рішення |
|---------|---------|
| `Raw ICMP requires root or cap_net_raw` | `./pingui.sh --deploy` |
| Qt platform plugin | `sudo apt install libegl1 libgl1 libxkbcommon0 libxcb-cursor0` |
| `.venv` symlink | `rm -rf .venv && ./pingui.sh --deploy --force-venv` |
| Coverage fail | `./pingui.sh --deploy` локально, див. [TESTING.md](TESTING.md) |
