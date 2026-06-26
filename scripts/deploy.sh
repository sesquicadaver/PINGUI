#!/usr/bin/env bash
# PINGUI — розгортання та запуск.
#
#   ./scripts/deploy.sh           повне розгортання (лог + тести)
#   ./scripts/deploy.sh --run     лише GUI (без зайвого виводу)
#   ./scripts/deploy.sh --skip-tests
#   ./scripts/deploy.sh --force-venv
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

VENV="${ROOT}/.venv"
PYTHON=""
PIP="${VENV}/bin/pip"
CONFIG="${ROOT}/config/hosts.example.yaml"

RUN_APP=0
SKIP_TESTS=0
FORCE_VENV=0
QUIET=0

for arg in "$@"; do
  case "$arg" in
    --run) RUN_APP=1 ;;
    --skip-tests) SKIP_TESTS=1 ;;
    --force-venv) FORCE_VENV=1 ;;
    -h|--help)
      cat <<'EOF'
  ./scripts/deploy.sh              розгортання + CI-перевірки
  ./scripts/deploy.sh --run        запуск GUI (тиха підготовка)
  ./scripts/deploy.sh --skip-tests розгортання без тестів
  ./scripts/deploy.sh --force-venv перестворити .venv
EOF
      exit 0
      ;;
    *)
      echo "Невідомий аргумент: $arg (див. --help)" >&2
      exit 1
      ;;
  esac
done

if [[ "$RUN_APP" -eq 1 ]]; then
  QUIET=1
  SKIP_TESTS=1
fi

log() {
  [[ "$QUIET" -eq 0 ]] && printf '[deploy] %s\n' "$*"
}

die() {
  printf '[deploy] ПОМИЛКА: %s\n' "$*" >&2
  exit 1
}

require_linux() {
  [[ "$(uname -s)" == Linux ]] || die "PINGUI працює лише на Linux."
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
  PYTHON="$(venv_python_binary)" || die "Не знайдено Python у .venv."
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
  die "Немає доступу до raw ICMP. Запустіть: ./scripts/deploy.sh (без --run) для налаштування cap_net_raw."
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
  ensure_venv
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

main() {
  if [[ "$RUN_APP" -eq 1 ]]; then
    run_gui
  else
    full_deploy
  fi
}

main "$@"
