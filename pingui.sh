#!/usr/bin/env bash
# PINGUI — Python edition (Linux): venv, cap_net_raw, PyQt6 GUI.
#
#   ./pingui.sh              GUI
#   ./pingui.sh --deploy     повне розгортання + CI
#   ./pingui.sh --destroy    видалити .venv та локальні артефакти
#   ./pingui.sh --help       довідка
#
# Java edition: java/pingui-java.sh (окремий launcher, без цього скрипта).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

VENV="${ROOT}/.venv"
PYTHON=""
PIP="${VENV}/bin/pip"
CONFIG="${ROOT}/config/hosts.example.yaml"

MODE="run"
SKIP_TESTS=0
FORCE_VENV=0
QUIET=0

log() {
  [[ "$QUIET" -eq 0 ]] && printf '[pingui] %s\n' "$*"
}

die() {
  printf '[pingui] ПОМИЛКА: %s\n' "$*" >&2
  exit 1
}

usage() {
  cat <<'EOF'
PINGUI — монітор маршрутів (Python edition, Linux)

  ./pingui.sh
      Запуск GUI. Мінімальна перевірка venv і cap_net_raw, без зайвого виводу.

  ./pingui.sh --deploy
      Повне розгортання: системні пакети (apt), venv (--copies), cap_net_raw,
      ruff + mypy + pytest. Опції лише з --deploy:
        --skip-tests   без pytest/ruff/mypy
        --force-venv   перестворити .venv

  ./pingui.sh --destroy
      Видалити .venv, кеші (__pycache__, .pytest_cache, .mypy_cache, .ruff_cache),
      .coverage, build/dist, *.egg-info.
      Системні пакети apt і стан ОС до розгортання не змінюються.

  ./pingui.sh --help
      Ця довідка.

Java edition (cross-platform): java/pingui-java.sh (Unix) або java/pingui-java.bat (Windows).

Після розгортання список цілей редагується в GUI (Зберегти → config/hosts.example.yaml).
EOF
}

for arg in "$@"; do
  case "$arg" in
    --deploy) MODE="deploy" ;;
    --destroy) MODE="destroy" ;;
    --help|-h) MODE="help" ;;
    --skip-tests)
      [[ "$MODE" == deploy ]] || die "Опція --skip-tests лише з --deploy."
      SKIP_TESTS=1
      ;;
    --force-venv)
      [[ "$MODE" == deploy ]] || die "Опція --force-venv лише з --deploy."
      FORCE_VENV=1
      ;;
    --run)
      echo "Застаріло: ./pingui.sh --run → просто ./pingui.sh" >&2
      MODE="run"
      ;;
    --java|--java=*)
      die "Java edition: використовуйте java/pingui-java.sh (не pingui.sh --java)."
      ;;
    *)
      die "Невідомий аргумент: $arg (див. ./pingui.sh --help)"
      ;;
  esac
done

if [[ "$MODE" == run ]]; then
  QUIET=1
fi

require_linux() {
  [[ "$(uname -s)" == Linux ]] || die "Python PINGUI працює лише на Linux."
}

require_python() {
  command -v python3 >/dev/null 2>&1 || die "python3 не знайдено. Встановіть Python ≥ 3.11."
  local ver major minor
  ver="$(python3 -c 'import sys; print(f"{sys.version_info.major}.{sys.version_info.minor}")')"
  major="${ver%%.*}"
  minor="${ver#*.}"
  [[ "$major" -ge 3 && "$minor" -ge 11 ]] || die "Потрібен Python ≥ 3.11, знайдено $ver."
}

venv_python_binary() {
  local candidate
  for candidate in \
    "${VENV}/bin/python3.12" \
    "${VENV}/bin/python3.11" \
    "${VENV}/bin/python3.13" \
    "${VENV}/bin/python3"; do
    if [[ -f "$candidate" && ! -L "$candidate" ]]; then
      echo "$candidate"
      return 0
    fi
  done
  return 1
}

venv_uses_symlinks() {
  [[ -L "${VENV}/bin/python3" ]] || [[ -L "${VENV}/bin/python" ]]
}

resolve_python() {
  PYTHON="$(venv_python_binary)" || die "Не знайдено Python у .venv. Запустіть: ./pingui.sh --deploy"
  PIP="${VENV}/bin/pip"
}

install_system_packages() {
  if ! command -v apt-get >/dev/null 2>&1; then
    log "apt-get недоступний — пропуск системних пакетів."
    return 0
  fi
  local packages=(python3-venv libcap2-bin libegl1 libgl1 libxkbcommon0 libxcb-cursor0)
  local missing=()
  local pkg
  for pkg in "${packages[@]}"; do
    if ! dpkg -s "$pkg" >/dev/null 2>&1; then
      missing+=("$pkg")
    fi
  done
  if ((${#missing[@]} == 0)); then
    log "Системні пакети вже встановлені."
    return 0
  fi
  log "Встановлення системних пакетів: ${missing[*]}"
  sudo apt-get update -qq
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq "${missing[@]}"
}

create_venv() {
  log "Створення venv (--copies) у ${VENV}"
  rm -rf "$VENV"
  python3 -m venv --copies "$VENV"
}

ensure_venv() {
  if [[ ! -d "$VENV" ]] || [[ "$FORCE_VENV" -eq 1 ]]; then
    create_venv
    return
  fi
  if venv_uses_symlinks || ! venv_python_binary >/dev/null; then
    log "Перестворення .venv (--copies) для setcap."
    create_venv
  fi
}

install_python_packages() {
  local quiet_flag=()
  [[ "$QUIET" -eq 1 ]] && quiet_flag=(-q)
  log "Встановлення Python-залежностей"
  "$PIP" install "${quiet_flag[@]}" -U pip
  "$PIP" install "${quiet_flag[@]}" -e ".[dev]"
}

pingui_importable() {
  "$PYTHON" -c "import pingui" >/dev/null 2>&1
}

icmp_permission_ok() {
  "$PYTHON" -c "
from pingui.icmp.raw_socket import check_raw_icmp_permission
check_raw_icmp_permission()
" >/dev/null 2>&1
}

apply_cap_net_raw() {
  local targets=()
  local candidate
  for candidate in "${VENV}"/bin/python "${VENV}"/bin/python3 "${VENV}"/bin/python3.*; do
    if [[ -f "$candidate" && ! -L "$candidate" ]]; then
      targets+=("$candidate")
    fi
  done
  if ((${#targets[@]} == 0)); then
    die "Не знайдено бінарники Python у .venv."
  fi
  command -v setcap >/dev/null 2>&1 || die "setcap не знайдено (libcap2-bin)."
  for candidate in "${targets[@]}"; do
    log "cap_net_raw → ${candidate}"
    sudo setcap cap_net_raw+ep "$candidate"
    [[ "$QUIET" -eq 0 ]] && getcap "$candidate"
  done
}

verify_icmp_permission() {
  log "Перевірка raw ICMP"
  if icmp_permission_ok; then
    log "raw ICMP доступний"
    return 0
  fi
  die "Немає доступу до raw ICMP. Запустіть: ./pingui.sh --deploy"
}

run_quality_gates() {
  log "ruff + mypy + pytest"
  export QT_QPA_PLATFORM="${QT_QPA_PLATFORM:-offscreen}"
  "$VENV/bin/ruff" check src tests
  "$VENV/bin/mypy" src/pingui
  "$VENV/bin/pytest" tests -m "not network" --cov=pingui --cov-fail-under=80 -q
  "$PYTHON" "${ROOT}/scripts/check_imports.py"
}

prepare_runtime() {
  if [[ ! -d "$VENV" ]]; then
    require_python
    ensure_venv
  else
    ensure_venv
  fi
  resolve_python
  if ! pingui_importable || [[ "$FORCE_VENV" -eq 1 ]]; then
    install_python_packages
  fi
  if ! icmp_permission_ok; then
    apply_cap_net_raw
  fi
  if ! icmp_permission_ok; then
    die "raw ICMP недоступний після setcap. Перевірте sudo setcap на .venv/bin/python*"
  fi
}

launch_app() {
  unset QT_QPA_PLATFORM
  export QT_LOGGING_RULES="${QT_LOGGING_RULES:-*.debug=false;qt.qpa.*=false}"
  export PYTHONWARNINGS="${PYTHONWARNINGS:-ignore}"
  exec "$PYTHON" -m pingui --config "$CONFIG" "$@"
}

run_gui() {
  require_linux
  prepare_runtime
  launch_app
}

full_deploy() {
  require_linux
  require_python
  install_system_packages
  prepare_runtime
  verify_icmp_permission

  if [[ "$SKIP_TESTS" -eq 0 ]]; then
    run_quality_gates
  else
    log "Пропуск тестів (--skip-tests)."
  fi

  log "Готово."
}

destroy_artifacts() {
  require_linux
  log "Видалення .venv та локальних артефактів (системні пакети не видаляються)"
  rm -rf "${VENV}"
  rm -rf "${ROOT}/.pytest_cache" "${ROOT}/.mypy_cache" "${ROOT}/.ruff_cache"
  rm -f "${ROOT}/.coverage"
  rm -rf "${ROOT}/build" "${ROOT}/dist"
  shopt -s nullglob
  rm -rf "${ROOT}"/*.egg-info
  shopt -u nullglob
  find "${ROOT}/src" "${ROOT}/tests" -type d -name __pycache__ -print0 2>/dev/null \
    | xargs -0 rm -rf 2>/dev/null || true
  log "Готово."
}

main() {
  case "$MODE" in
    help) usage ;;
    run) run_gui ;;
    deploy) full_deploy ;;
    destroy) destroy_artifacts ;;
    *) die "Невідомий режим: $MODE" ;;
  esac
}

main "$@"
